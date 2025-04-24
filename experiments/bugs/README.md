# Agents Testing Bugs

### Bug 1: COMPSs Agent with 1 CPU is Faster Than with 2 or 4 CPUs
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
git checkout implement-compss-section
```

**Test 4 CPU agent (external version):**
```bash
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/hp2cdt/experiments/bugs/matmul_simple_external/jar/matmul.jar --log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 --project=${REPO_PATH}/hp2cdt/experiments/response_time/scripts/server_project.xml
compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 --cei="matmul.arrays.MatmulServerItf" matmul.arrays.Matmul 4 64 #(Result: ~4s)
```

**Test 1 CPU agent (external version):**
```bash
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/hp2cdt/experiments/bugs/matmul_simple_external/jar/matmul.jar --log_dir=/tmp/Agent1 --rest_port=46301 --comm_port=46302 --project=${REPO_PATH}/hp2cdt/experiments/bugs/scripts/single_cpu_project.xml
compss_agent_call_operation --master_node=127.0.0.1 --master_port=46301 --cei="matmul.arrays.MatmulEdgeItf" matmul.arrays.Matmul 4 64 #(Result: ~1s)
```

**Test sequential execution(external version)**
```bash
java -cp /path/to/repo/hp2cdt/experiments/bugs/matmul_simple_external/jar/matmul.jar matmul.arrays.Matmul 4 64 #(Result ~0.6s)
```

### Bug 2: Null Pointer / Concurrent Access
#### Summary
When trying to execute the tutorial's matmul function with agents, it first returns a NullPointerException, and then a ConcurrentModificationException. This happens particularly when:
- The edge is offloading tasks to the agent running in the server
- The matrix is quite large

If the matmul is not being offloaded to the server or the matrix is smaller (e.g., matrix size 4, block size 64), no problem occurs.

#### Steps to Reproduce
```bash
compss_agent_start --hostname=127.0.0.1 --classpath=${REPO_PATH}/hp2cdt/experiments/bugs/matmul/jar/matmul.jar --log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 --project=${REPO_PATH}/hp2cdt/experiments/response_time/scripts/edge_project.xml
compss_agent_start --hostname=127.0.0.2 --classpath=${REPO_PATH}/hp2cdt/experiments/bugs/matmul/jar/matmul.jar --log_dir=/tmp/Agent2 --rest_port=46201 --comm_port=46202 --project=${REPO_PATH}/hp2cdt/experiments/response_time/scripts/server_project.xml
compss_agent_add_resources --agent_node=127.0.0.1 --agent_port=46101 --cpu=4 127.0.0.2 Port=46202
compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 --cei="matmul.arrays.MatmulServerItf" matmul.arrays.Matmul 8 64
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

### Bugs with hp2c framework:
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

The agents must be deployed in the OpenStack machines. To do so, the user must do 
`ssh -i ${PATH_TO_KEY}/hp2cdt-ncloud.pem ubuntu@212.128.226.53` to connect to the broker machine (where the edge must be 
deployed), and run `connect-server` to connect to the server machine, where the server will be executed.



##### MatmulEdgeSimple
###### Steps to reproduce
- Broker machine
```bash
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulEdgeSimple"' ~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
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
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulEdgeSimpleBarrier"' ~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
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
###### Steps to reproduce
- Broker machine
```bash
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulEdgeNestedBarrier"' ~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
~/hp2cdt/deployments/deploy_edges.sh test_response_time --comm=bsc_subnet
```

###### Current behaviour
The app doesn’t create any tasks, but it starts executing the matrix multiplication. After about a minute, it throws a 
NullPointerException. It seems to spend that minute trying to create the tasks, and when that fails, it continues 
execution and attempts to print a null matrix.

###### Relevant logs
```bash
java.lang.NullPointerException
	at es.bsc.compss.loader.total.ArrayAccessWatcher.arrayReadObject(ArrayAccessWatcher.java:77)
	at jdk.internal.reflect.GeneratedMethodAccessor16.invoke(Unknown Source)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
	at es.bsc.compss.loader.LoaderUtils.runMethodOnObject(LoaderUtils.java:544)
	at es.bsc.hp2c.common.funcs.MatMulEdgeNestedBarrier_compss52069.printMatrix(MatMulEdgeNestedBarrier.java:133)
	at es.bsc.hp2c.common.funcs.MatMulEdgeNestedBarrier_compss52069.calcMatmul(MatMulEdgeNestedBarrier.java:108)
	at es.bsc.hp2c.common.funcs.MatMulEdgeNestedBarrier_compss52069.run(MatMulEdgeNestedBarrier.java:87)
	at es.bsc.hp2c.common.funcs.Action.run(Action.java:45)
	at es.bsc.hp2c.common.generic.Voltmeter.onRead(Voltmeter.java:69)
	at es.bsc.hp2c.edge.opalrt.OpalComm.distributeValues(OpalComm.java:214)
	at es.bsc.hp2c.edge.opalrt.OpalComm.lambda$startUDPServer$2(OpalComm.java:191)
	at java.base/java.lang.Thread.run(Thread.java:829)
```


##### MatmulServerSimple
###### Steps to reproduce
- Server machine
```bash
~/hp2cdt/deployments/deploy_server_agent_name.sh test_response_time --comm=bsc_subnet
```
-Broker machine
```bash
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulServerSimple"' ~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
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
~/hp2cdt/deployments/deploy_server_agent_name.sh test_response_time --comm=bsc_subnet
```
-Broker machine
```bash
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulServerSimpleBarrier"' ~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
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
~/hp2cdt/deployments/deploy_server_agent_name.sh test_response_time --comm=bsc_subnet
```
-Broker machine
```bash
jq '(.funcs[] | select(.label == "MatMul")."method-name") |= "es.bsc.hp2c.common.funcs.MatMulServerNestedBarrier"' ~/hp2cdt/deployments/test_response_time/setup/edge1.json > tmp.json && mv tmp.json ~/hp2cdt/deployments/test_response_time/setup/edge1.json
~/hp2cdt/deployments/deploy_edges.sh test_response_time --comm=bsc_subnet
```

###### Current behaviour
In this case the tasks are generated properly, but there is the "Object obtained null" loop again.
