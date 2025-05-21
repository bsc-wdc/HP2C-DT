In this file, we describe several COMPSs bugs encountered while running tests for the project. For each issue, we 
provide a brief explanation, steps to reproduce it, and relevant logs.

# Bug 1: COMPSs Agent with 1 CPU is Faster Than with 2 or 4 CPUs
#### Summary
When comparing execution times of COMPSs agents with different CPU counts:
- The agent with only 1 CPU outperformed those with 2 and 4 CPUs
- This occurred even for large matrices
- Initial executions were similar to executions without COMPSs (which also outperformed the 2 and 4 CPU cases)

After moving the code within the task to an external class:
- 2 and 4 CPU agents improved by 2x
- 1 CPU agent improved by ~20x
- The 1 CPU external version is 4x faster than the 4 CPU version for large matrices (e.g., matrix size 4, block size 64)

Other examples (external version):
- msize 8, bsize 100: 147s (4CPUs), 21s (1CPU), 19s (sequential)
- msize 4, bsize 256: 140s (4CPUs), 39s (1CPU), 39s (sequential)
- msize 4, bsize 512: 450s (4CPUs), 150s (1CPU)

The image is using `compss/compss:3.3`.

#### Steps to Reproduce
```bash
git clone https://gitlab.bsc.es/wdc/projects/hp2cdt.git
git checkout 174-bug-list-agents
```

Compile the java code:

```bash
cd "${REPO_PATH}/experiments/bugs/matmul_simple_external"

mvn clean package
```

**Test 4 CPU agent (external version):**

```bash
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/experiments/bugs/matmul_simple_external/jar/matmul.jar \
--log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 \
--project=${REPO_PATH}/experiments/response_time/scripts/server_project.xml

compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 --cei="matmul.arrays.MatmulServerItf" \
matmul.arrays.Matmul 4 64 #(Result: ~4s)
```

**Test 1 CPU agent (external version):**
```bash
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/experiments/bugs/matmul_simple_external/jar/matmul.jar \
--log_dir=/tmp/Agent1 --rest_port=46301 --comm_port=46302 \
--project=${REPO_PATH}/experiments/response_time_agents/scripts/single_cpu_project.xml

compss_agent_call_operation --master_node=127.0.0.1 --master_port=46301 --cei="matmul.arrays.MatmulEdgeItf" \
matmul.arrays.Matmul 4 64 #(Result: ~1s)
```

**Test sequential execution(external version)**
```bash
java -cp ${REPO_PATH}/experiments/bugs/matmul_simple_external/jar/matmul.jar matmul.arrays.Matmul 4 64 #(Result ~0.6s)
```

# Bug 2: Null Pointer / Concurrent Access
#### Summary
When trying to execute the tutorial's matmul function with agents, it first returns a NullPointerException, and then a 
ConcurrentModificationException. This happens particularly when:
- The edge is offloading tasks to the agent running in the server
- The matrix is quite large

If the matmul is not being offloaded to the server or the matrix is smaller (e.g., matrix size 4, block size 64), no problem occurs.

#### Steps to Reproduce
Compile the java code:

```bash
cd "${REPO_PATH}/experiments/bugs/matmul_simple"

mvn clean package
```

```bash
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/experiments/bugs/matmul_simple/jar/matmul.jar \
--log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 \
--project=${REPO_PATH}/experiments/response_time/scripts/edge_project.xml

compss_agent_start --hostname=127.0.0.2 --classpath=${REPO_PATH}/experiments/bugs/matmul_simple/jar/matmul.jar \
--log_dir=/tmp/Agent2 --rest_port=46201 --comm_port=46202 \
--project=${REPO_PATH}/experiments/response_time/scripts/server_project.xml

compss_agent_add_resources --agent_node=127.0.0.1 --agent_port=46101 --cpu=4 127.0.0.2 Port=46202

compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 --cei="matmul.arrays.MatmulServerItf" \
matmul.arrays.Matmul 8 64
```

#### Current Behavior
It executes some tasks and then throws a NullPointerException.

#### Expected Behavior
The edge properly executes the call.

#### Workaround
The previous execution returns the correct matrix multiplication result when using a new COMPSs image, adding the 
following code at `TaskResult.getLocations(AppMonitor.java:299)` where it calls `createRDLfromLD`:

```java
boolean done = false;
while (!done){
    try { 
        createRDfromLD(.....);
        done = true;
    }catch (ConcurrentModificationException e) {
    }
}
```

#### Relevant Logs
```bash
root@02d82e139a7e:/tmp/server/jobs# cat job164_NEW.err 
[EXECUTOR] executeTask - Error in task execution
es.bsc.compss.types.execution.exceptions.JobExecutionException: Error executing the instrumented method!
	at es.bsc.compss.invokers.JavaNestedInvoker.runMethod(JavaNestedInvoker.java:276)
	at es.bsc.compss.invokers.JavaInvoker.invokeMethod(JavaInvoker.java:104)
	at es.bsc.compss.invokers.Invoker.invoke(Invoker.java:336)
	at es.bsc.compss.invokers.Invoker.runInvocation(Invoker.java:292)
	at es.bsc.compss.executor.Executor.runInvocation(Executor.java:512)
	at es.bsc.compss.executor.Executor.redirectStreamsAndRun(Executor.java:448)
	at es.bsc.compss.executor.Executor.filesWrapperAndRun(Executor.java:387)
	at es.bsc.compss.executor.Executor.sandBoxWrapperAndRun(Executor.java:358)
	at es.bsc.compss.executor.Executor.resourcesWrapperAndRun(Executor.java:337)
	at es.bsc.compss.executor.Executor.totalTimerAndTracingWrapperAndRun(Executor.java:311)
	at es.bsc.compss.executor.Executor.execute(Executor.java:287)
	at es.bsc.compss.executor.Executor.processInvocation(Executor.java:253)
	at es.bsc.compss.types.execution.InvocationExecutionRequest.run(InvocationExecutionRequest.java:60)
	at es.bsc.compss.executor.Executor.processRequests(Executor.java:223)
	at es.bsc.compss.executor.Executor.run(Executor.java:175)
	at es.bsc.compss.execution.ExecutionPlatform$4.run(ExecutionPlatform.java:269)
	at java.lang.Thread.run(Thread.java:750)
Caused by: es.bsc.compss.types.execution.exceptions.JobExecutionException: ERROR: Exception executing task (user code)
	at es.bsc.compss.invokers.JavaInvoker.runMethod(JavaInvoker.java:168)
	at es.bsc.compss.invokers.JavaNestedInvoker.runMethod(JavaNestedInvoker.java:274)
	... 16 more
Caused by: java.lang.reflect.InvocationTargetException
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:498)
	at es.bsc.compss.invokers.JavaInvoker.runMethod(JavaInvoker.java:152)
	... 17 more
Caused by: java.lang.NullPointerException
	at es.bsc.compss.loader.total.ArrayAccessWatcher.arrayReadDouble(ArrayAccessWatcher.java:106)
	at sun.reflect.GeneratedMethodAccessor21.invoke(Unknown Source)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:498)
	at es.bsc.compss.loader.LoaderUtils.runMethodOnObject(LoaderUtils.java:539)
	at matmul.arrays.MatmulImpl.multiplyAccumulative(MatmulImpl.java:26)
	... 22 more
```

# Bug 3: Wrong task offloading
#### Summary
For some of the tests executed here, we used the architecture constraint to control whether a task should be offloaded 
or not. The process is as follows:

- Deploy the first agent (with `arm` architecture)
- Deploy the second agent (with `amd64` architecture)
- Add the second agent as a resource to the first agent
- Execute an `arm` task on the first agent

In this setup, the task will always be offloaded to the second agent, which cannot execute it due to the architecture 
mismatch. As a result, the second agent will report an error (see the *Relevant Logs* section).

#### Steps to reproduce
Compile the java code:

```bash
cd "${REPO_PATH}/experiments/bugs/matmul_hp2c"

mvn clean package
```

Execute the following commands:
```bash
# Deploy agent 1
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/experiments/bugs/matmul_hp2c/jar/matmul.jar \
--log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 \
--project=${REPO_PATH}/experiments/response_time/scripts/edge_project.xml

# Deploy agent 2
compss_agent_start --hostname=127.0.0.2 --classpath=${REPO_PATH}/experiments/bugs/matmul_hp2c/jar/matmul.jar \
--log_dir=/tmp/Agent2 --rest_port=46201 --comm_port=46202 \
--project=${REPO_PATH}/experiments/response_time/scripts/server_project.xml

# Allow agent 1 to offload tasks to agent 2
compss_agent_add_resources --agent_node=127.0.0.1 --agent_port=46101 --cpu=4 127.0.0.2 Port=46202

# Call operation
compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 \
--cei="matmul.arrays.MatmulEdgeSimpleBarrierItf" matmul.arrays.MatmulSimpleBarrier 4 4
```

#### Possible fix
The problem seems to be fixed when the receiving architecture is specified (check the following curl command for adding 
resources), so it should be enough to add a new architecture flag to the `compss_agent_add_resources` command.

This is the proper way to add the resource specifying the architecture:
```bash
curl -s -X PUT http://127.0.0.1:46101/COMPSs/addResources \
  -H 'content-type: application/xml' \
  -d '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<newResource>
  <externalResource>
    <name>127.0.0.2</name>
    <description>
      <processors>
        <processor>
          <name>MainProcessor</name>
          <type>CPU</type>
          <architecture>amd64</architecture>
          <computingUnits>4</computingUnits>
          <internalMemory>-1.0</internalMemory>
          <propName>[unassigned]</propName>
          <propValue>[unassigned]</propValue>
          <speed>-1.0</speed>
        </processor>
      </processors>
      <memorySize>-1</memorySize>
      <memoryType>[unassigned]</memoryType>
      <storageSize>-1.0</storageSize>
      <storageType>[unassigned]</storageType>
      <operatingSystemDistribution>[unassigned]</operatingSystemDistribution>
      <operatingSystemType>[unassigned]</operatingSystemType>
      <operatingSystemVersion>[unassigned]</operatingSystemVersion>
      <pricePerUnit>-1.0</pricePerUnit>
      <priceTimeUnit>-1</priceTimeUnit>
      <value>0.0</value>
      <wallClockLimit>-1</wallClockLimit>
    </description>
    <adaptor>es.bsc.compss.agent.comm.CommAgentAdaptor</adaptor>
    <resourceConf xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ResourcesExternalAdaptorProperties">
      <Property>
        <Name>Port</Name>
        <Value>46202</Value>
      </Property>
    </resourceConf>
  </externalResource>
</newResource>'
```

#### Relevant logs

In the second agent:

```
Initializing Worker 
[ERRMGR]  -  WARNING: No task could be scheduled to any of the available resources.
                      This could end up blocking COMPSs. Will check it again in 20 seconds.
                      Possible causes: 
                          -Network problems: non-reachable nodes, sshd service not started, etc.
                          -There isn't any computing resource that fits the defined tasks constraints.
                      If this happens 2 more times, the runtime will shutdown.
[ERRMGR]  -  WARNING: No task could be scheduled to any of the available resources.
                      This could end up blocking COMPSs. Will check it again in 20 seconds.
                      Possible causes: 
                          -Network problems: non-reachable nodes, sshd service not started, etc.
                          -There isn't any computing resource that fits the defined tasks constraints.
                      If this happens 1 more time, the runtime will shutdown.
[ERRMGR]  -  ERROR:   Unschedulable tasks detected.
                      COMPSs has found tasks with constraints that cannot be fulfilled.
                      Shutting down COMPSs now...
[ERRMGR]  -  ERROR:   Unschedulable tasks detected.
                      COMPSs has found tasks with constraints that cannot be fulfilled.
                      Shutting down COMPSs now...
```

# Bugs with hp2c framework
#### Summary
The following bugs appear when trying to execute tests with `matmul` functions in the HP2C framework. In these tests, 
the idea was to:
1. Read a value from a sensor
2. Calculate a matrix multiplication
3. Print the elapsed time since the data generation

We were also trying to test the differences between executing the functions on the edge and offloading them to the server. 
To do this, we developed 6 different funcs - 3 for the edge and 3 for the server (the code is the same, but with 
different architecture constraints (arm/amd64)).

The source codes are uploaded to the HP2C repository in the branch `174-bug-list-agents` at 
`${REPO_PATH}/components/common/src/main/java/es/bsc/hp2c/common/funcs`. These funcs are:

- `Matmul[Edge/Server]Simple`: Executes a `matmul` as shown in the COMPSs tutorial, but declaring only `multiplyAccumulative` as a task.
- `Matmul[Edge/Server]SimpleBarrier`: Executes the same code but adds a COMPSs barrier after the matrix multiplication.
- `Matmul[Edge/Server]NestedBarrier`: The method is very similar to the previous ones, but the `multiplyAccumulative` 
method is called from another function. This was done to execute the barrier within this external function and avoid 
blocking the rest of the tasks in the agent.

## Local execution
In this case, the agents will be deployed in the local machine. 

The executions of these methods work for the edge versions (without offloading the tasks to the server agent), but it 
does not for the external functions. In these cases, it returns a nested exception (check relevant logs section).

### Steps to reproduce
Compile the java code:

```bash
cd "${REPO_PATH}/experiments/bugs/matmul_hp2c"

mvn clean package
```

Then, we should:
```bash
# Deploy agent 1
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/experiments/bugs/matmul_hp2c/jar/matmul.jar \
--log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 \
--project=${REPO_PATH}/experiments/response_time/scripts/edge_project.xml

# Deploy agent 2
compss_agent_start --hostname=127.0.0.2 --classpath=${REPO_PATH}/experiments/bugs/matmul_hp2c/jar/matmul.jar \
--log_dir=/tmp/Agent2 --rest_port=46201 --comm_port=46202 \
--project=${REPO_PATH}/experiments/response_time/scripts/server_project.xml

# Allow agent 1 to offload tasks to agent 2
compss_agent_add_resources --agent_node=127.0.0.1 --agent_port=46101 --cpu=4 127.0.0.2 Port=46202

# Call operation
compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 \
--cei="matmul.arrays.MatmulServerSimpleBarrierItf" matmul.arrays.MatmulSimpleBarrier 4 4
```

### Relevant logs
```bash
es.bsc.compss.types.execution.exceptions.JobExecutionException: Cannot load value due to nested exception
	at es.bsc.compss.invokers.Invoker.processParameter(Invoker.java:284)
	at es.bsc.compss.invokers.Invoker.<init>(Invoker.java:137)
	at es.bsc.compss.invokers.JavaInvoker.<init>(JavaInvoker.java:64)
	at es.bsc.compss.invokers.JavaNestedInvoker.<init>(JavaNestedInvoker.java:69)
	at es.bsc.compss.executor.Executor.selectNativeMethodInvoker(Executor.java:534)
	at es.bsc.compss.executor.Executor.runInvocation(Executor.java:476)
	at es.bsc.compss.executor.Executor.redirectStreamsAndRun(Executor.java:450)
	at es.bsc.compss.executor.Executor.filesWrapperAndRun(Executor.java:389)
	at es.bsc.compss.executor.Executor.sandBoxWrapperAndRun(Executor.java:360)
	at es.bsc.compss.executor.Executor.resourcesWrapperAndRun(Executor.java:339)
	at es.bsc.compss.executor.Executor.totalTimerAndTracingWrapperAndRun(Executor.java:313)
	at es.bsc.compss.executor.Executor.execute(Executor.java:289)
	at es.bsc.compss.executor.Executor.processInvocation(Executor.java:255)
	at es.bsc.compss.types.execution.InvocationExecutionRequest.run(InvocationExecutionRequest.java:60)
	at es.bsc.compss.executor.Executor.processRequests(Executor.java:225)
	at es.bsc.compss.executor.Executor.run(Executor.java:177)
	at es.bsc.compss.execution.ExecutionPlatform$4.run(ExecutionPlatform.java:269)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: es.bsc.compss.types.execution.exceptions.UnloadableValueException: Cannot load value due to nested exception
	at es.bsc.compss.types.COMPSsMaster.loadParam(COMPSsMaster.java:1307)
	at es.bsc.compss.invokers.Invoker.processParameter(Invoker.java:231)
	... 17 more
Caused by: java.io.FileNotFoundException: d34v1_1746449594016.IT (No existe el archivo o el directorio)
	at java.base/java.io.FileInputStream.open0(Native Method)
	at java.base/java.io.FileInputStream.open(FileInputStream.java:219)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:157)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:112)
	at es.bsc.compss.util.serializers.XMLSerializer.deserialize(XMLSerializer.java:80)
	at es.bsc.compss.util.serializers.Serializer.deserialize(Serializer.java:119)
	at es.bsc.compss.util.FileOpsManager.deserialize(FileOpsManager.java:466)
	at es.bsc.compss.util.FileOpsManager.deserializeSync(FileOpsManager.java:411)
	at es.bsc.compss.types.COMPSsMaster.loadParam(COMPSsMaster.java:1305)
	... 18 more
```


## Remote execution

##### MatmulEdgeSimple
###### Steps to reproduce
- Broker machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
```

```bash
# Update the json file with the new method name 
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulEdgeSimple"' \
~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json

~/hp2cdt/deployments/deploy_edges.sh test_response_time --comm=bsc_subnet
```

###### Current behaviour 
The app creates 2 tasks and then gives an infinite null loop.

###### Relevant logs
```bash
[(13546)    API]  -  Getting object with hash code 2080050798
[(13547)    API]  -  Object obtained null
[(13547)    API]  -  Getting object with hash code 2080050798
[(13547)    API]  -  Object obtained null
[(13547)    API]  -  Getting object with hash code 1962274151
[(13548)    API]  -  Object obtained null
[(13548)    API]  -  Getting object with hash code 2081950943
[(13548)    API]  -  Object obtained null
[(13548)    API]  -  Getting object with hash code 2080050798
[(13549)    API]  -  Object obtained null
[(13549)    API]  -  Getting object with hash code 2080050798
[(13549)    API]  -  Object obtained null
[(13550)    API]  -  Getting object with hash code 1962274151
[(13550)    API]  -  Object obtained null
[(13550)    API]  -  Getting object with hash code 2081950943
[(13551)    API]  -  Object obtained null
[(13551)    API]  -  Getting object with hash code 2080050798
[(13552)    API]  -  Object obtained null
[(13552)    API]  -  Getting object with hash code 2080050798
[(13552)    API]  -  Object obtained null
[(13552)    API]  -  Getting object with hash code 1962274151
[(13553)    API]  -  Object obtained null
```

##### MatmulEdgeSimpleBarrier
###### Steps to reproduce
- Broker machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
```

```bash
# Update the json file with the new method name
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulEdgeSimpleBarrier"' \ 
~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
~/hp2cdt/deployments/deploy_edges.sh test_response_time --comm=bsc_subnet
```

###### Current behaviour
The app creates 2 tasks and then gives an infinite null loop.

###### Relevant logs
```bash
[(13546)    API]  -  Getting object with hash code 2080050798
[(13547)    API]  -  Object obtained null
[(13547)    API]  -  Getting object with hash code 2080050798
[(13547)    API]  -  Object obtained null
[(13547)    API]  -  Getting object with hash code 1962274151
[(13548)    API]  -  Object obtained null
[(13548)    API]  -  Getting object with hash code 2081950943
[(13548)    API]  -  Object obtained null
[(13548)    API]  -  Getting object with hash code 2080050798
[(13549)    API]  -  Object obtained null
[(13549)    API]  -  Getting object with hash code 2080050798
[(13549)    API]  -  Object obtained null
[(13550)    API]  -  Getting object with hash code 1962274151
[(13550)    API]  -  Object obtained null
[(13550)    API]  -  Getting object with hash code 2081950943
[(13551)    API]  -  Object obtained null
[(13551)    API]  -  Getting object with hash code 2080050798
[(13552)    API]  -  Object obtained null
[(13552)    API]  -  Getting object with hash code 2080050798
[(13552)    API]  -  Object obtained null
[(13552)    API]  -  Getting object with hash code 1962274151
[(13553)    API]  -  Object obtained null
```

##### MatmulEdgeNestedBarrier
This execution works properly.


##### MatmulServerSimple
###### Steps to reproduce
- Server machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
connect-server # connect to the server machine
```

```bash
~/hp2cdt/deployments/deploy_server_agent_name.sh test_response_time --comm=bsc_subnet
```
-Broker machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
```

```bash
# Update the json file with the new method name
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulServerSimple"' \
~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
~/hp2cdt/deployments/deploy_edges.sh test_response_time --comm=bsc_subnet
```

###### Current behaviour
The app creates some tasks and then starts an infinite "Object obtained null" loop. However, in some of the task logs 
there is an error "Cannot load value due to nested exception" (check relevant logs).

###### Relevant logs
```bash
es.bsc.compss.types.execution.exceptions.JobExecutionException: Cannot load value due to nested exception
	at es.bsc.compss.invokers.Invoker.processParameter(Invoker.java:284)
	at es.bsc.compss.invokers.Invoker.<init>(Invoker.java:137)
	at es.bsc.compss.invokers.JavaInvoker.<init>(JavaInvoker.java:64)
	at es.bsc.compss.invokers.JavaNestedInvoker.<init>(JavaNestedInvoker.java:69)
	at es.bsc.compss.executor.Executor.selectNativeMethodInvoker(Executor.java:534)
	at es.bsc.compss.executor.Executor.runInvocation(Executor.java:476)
	at es.bsc.compss.executor.Executor.redirectStreamsAndRun(Executor.java:450)
	at es.bsc.compss.executor.Executor.filesWrapperAndRun(Executor.java:389)
	at es.bsc.compss.executor.Executor.sandBoxWrapperAndRun(Executor.java:360)
	at es.bsc.compss.executor.Executor.resourcesWrapperAndRun(Executor.java:339)
	at es.bsc.compss.executor.Executor.totalTimerAndTracingWrapperAndRun(Executor.java:313)
	at es.bsc.compss.executor.Executor.execute(Executor.java:289)
	at es.bsc.compss.executor.Executor.processInvocation(Executor.java:255)
	at es.bsc.compss.types.execution.InvocationExecutionRequest.run(InvocationExecutionRequest.java:60)
	at es.bsc.compss.executor.Executor.processRequests(Executor.java:225)
	at es.bsc.compss.executor.Executor.run(Executor.java:177)
	at es.bsc.compss.execution.ExecutionPlatform$4.run(ExecutionPlatform.java:269)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: es.bsc.compss.types.execution.exceptions.UnloadableValueException: Cannot load value due to nested exception
	at es.bsc.compss.types.COMPSsMaster.loadParam(COMPSsMaster.java:1307)
	at es.bsc.compss.invokers.Invoker.processParameter(Invoker.java:231)
	... 17 more
Caused by: java.io.FileNotFoundException: d9v1_1745492230313.IT (No such file or directory)
	at java.base/java.io.FileInputStream.open0(Native Method)
	at java.base/java.io.FileInputStream.open(FileInputStream.java:219)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:157)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:112)
	at es.bsc.compss.util.serializers.XMLSerializer.deserialize(XMLSerializer.java:80)
	at es.bsc.compss.util.serializers.Serializer.deserialize(Serializer.java:119)
	at es.bsc.compss.util.FileOpsManager.deserialize(FileOpsManager.java:466)
	at es.bsc.compss.util.FileOpsManager.deserializeSync(FileOpsManager.java:411)
	at es.bsc.compss.types.COMPSsMaster.loadParam(COMPSsMaster.java:1305)
	... 18 more
```

##### MatmulServerSimpleBarrier
###### Steps to reproduce
- Server machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
connect-server # connect to the server machine
```

```bash
~/hp2cdt/deployments/deploy_server_agent_name.sh test_response_time --comm=bsc_subnet
```
-Broker machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
```

```bash
# Update the json file with the new method name
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulServerSimpleBarrier"' \
~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
~/hp2cdt/deployments/deploy_edges.sh test_response_time --comm=bsc_subnet
```

###### Current behaviour
The behaviour is similar to the previous one, but this case, there is no infinite "Object obtained null" loop.

###### Relevant logs
```bash
es.bsc.compss.types.execution.exceptions.JobExecutionException: Cannot load value due to nested exception
	at es.bsc.compss.invokers.Invoker.processParameter(Invoker.java:284)
	at es.bsc.compss.invokers.Invoker.<init>(Invoker.java:137)
	at es.bsc.compss.invokers.JavaInvoker.<init>(JavaInvoker.java:64)
	at es.bsc.compss.invokers.JavaNestedInvoker.<init>(JavaNestedInvoker.java:69)
	at es.bsc.compss.executor.Executor.selectNativeMethodInvoker(Executor.java:534)
	at es.bsc.compss.executor.Executor.runInvocation(Executor.java:476)
	at es.bsc.compss.executor.Executor.redirectStreamsAndRun(Executor.java:450)
	at es.bsc.compss.executor.Executor.filesWrapperAndRun(Executor.java:389)
	at es.bsc.compss.executor.Executor.sandBoxWrapperAndRun(Executor.java:360)
	at es.bsc.compss.executor.Executor.resourcesWrapperAndRun(Executor.java:339)
	at es.bsc.compss.executor.Executor.totalTimerAndTracingWrapperAndRun(Executor.java:313)
	at es.bsc.compss.executor.Executor.execute(Executor.java:289)
	at es.bsc.compss.executor.Executor.processInvocation(Executor.java:255)
	at es.bsc.compss.types.execution.InvocationExecutionRequest.run(InvocationExecutionRequest.java:60)
	at es.bsc.compss.executor.Executor.processRequests(Executor.java:225)
	at es.bsc.compss.executor.Executor.run(Executor.java:177)
	at es.bsc.compss.execution.ExecutionPlatform$4.run(ExecutionPlatform.java:269)
	at java.base/java.lang.Thread.run(Thread.java:829)
Caused by: es.bsc.compss.types.execution.exceptions.UnloadableValueException: Cannot load value due to nested exception
	at es.bsc.compss.types.COMPSsMaster.loadParam(COMPSsMaster.java:1307)
	at es.bsc.compss.invokers.Invoker.processParameter(Invoker.java:231)
	... 17 more
Caused by: java.io.FileNotFoundException: d13v1_1745499645293.IT (No such file or directory)
	at java.base/java.io.FileInputStream.open0(Native Method)
	at java.base/java.io.FileInputStream.open(FileInputStream.java:219)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:157)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:112)
	at es.bsc.compss.util.serializers.XMLSerializer.deserialize(XMLSerializer.java:80)
	at es.bsc.compss.util.serializers.Serializer.deserialize(Serializer.java:119)
	at es.bsc.compss.util.FileOpsManager.deserialize(FileOpsManager.java:466)
	at es.bsc.compss.util.FileOpsManager.deserializeSync(FileOpsManager.java:411)
	at es.bsc.compss.types.COMPSsMaster.loadParam(COMPSsMaster.java:1305)
	... 18 more

```

##### MatmulServerNestedBarrier
###### Steps to reproduce
- Server machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
connect-server # connect to the server machine
```

```bash
~/hp2cdt/deployments/deploy_server_agent_name.sh test_response_time --comm=bsc_subnet
```
-Broker machine
```bash
ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53 # connect to the broker machine
```

```bash
# Update the json file with the new method name
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulServerNestedBarrier"' \
~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
~/hp2cdt/deployments/deploy_edges.sh test_response_time --comm=bsc_subnet
```

###### Current behaviour
In this case the tasks are generated properly, but there is the "Object obtained null" loop again.
