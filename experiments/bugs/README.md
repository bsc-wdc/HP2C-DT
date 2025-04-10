# Agents Testing Bugs

### Bug 1: Object Obtained Null
#### Summary
When the edge is executed and connected to the broker (using HP2C framework), it returns an infinite 
"Object obtained null" loop when trying to execute a matmul function.

#### Steps to Reproduce
1. Clone HP2CDT repository: `git clone https://gitlab.bsc.es/wdc/projects/hp2cdt.git`
2. Checkout to branch 163-experiments-for-paper: `git checkout 163-experiments-for-paper`
3. Pull edge image: `docker pull hp2c/edge`
4. Run the edge: `path/to/repo/deployments/deploy_edges.sh test_response_time --comm=bsc`

#### Current Behavior
It executes some tasks and then starts to print an infinite null loop.

#### Expected Behavior
The edge properly executes 1 call of MatmulEdge and prints the resultant matrix (through `docker logs hp2c/edge`).

#### Relevant Logs
```bash
[(15548)    API]  -  Creating task from method multiplyAccumulative in es.bsc.hp2c.common.funcs.MatMulEdgeNestedBarrier for application 7464871512537282480
[(15548)    API]  -  There are 3 parameters
[(15548)    API]  -   Parameter 0 has type OBJECT_T
[(15548)    API]  -   Parameter 1 has type OBJECT_T
[(15548)    API]  -   Parameter 2 has type OBJECT_T
[(15555)    API]  -  Barrier for app 7464871512537282480 with noMoreTasks = false
Timestamp difference: 94340000
[(15646)    API]  -  Registering CoreElement calcMatmul(LONG_T,OBJECT_T,OBJECT_T,INT_T,INT_T)
[(15646)    API]  -      - Implementation 0:
[(15647)    API]  -  Method declared in class es.bsc.hp2c.common.funcs.MatMulEdgeNestedBarrier.calcMatmul: calcMatmul
[(15647)    API]  -  Registering CoreElement multiplyAccumulative(OBJECT_T,OBJECT_T,OBJECT_T)
[(15647)    API]  -      - Implementation 0:
[(15647)    API]  -  Method declared in class es.bsc.hp2c.common.funcs.MatMulEdgeNestedBarrier.multiplyAccumulative: multiplyAccumulative
[(15648)    API]  -  Getting object with hash code 974326958
[(15649)    API]  -  Request object transfer 32
[(15649)    API]  -  Requesting getting object d32v1_1742224934677.IT
[(15652)    API]  -  Object retrieved. Set new version to: d32v2_1742224934677.IT
[(15653)    API]  -  Object obtained 1582426020
[(15653)    API]  -  Getting object with hash code 420918602
[(15654)    API]  -  Request object transfer 30
[(15654)    API]  -  Requesting getting object d30v1_1742224934677.IT
[(15657)    API]  -  Object retrieved. Set new version to: d30v2_1742224934677.IT
[(15658)    API]  -  Object obtained 539639581
[(15658)    API]  -  Getting object with hash code 1288823261
[(15659)    API]  -  Request object transfer 31
[(15659)    API]  -  Requesting getting object d31v1_1742224934677.IT
[(15661)    API]  -  Object retrieved. Set new version to: d31v2_1742224934677.IT
[(15662)    API]  -  Object obtained 1514467793
[(15662)    API]  -  Getting object with hash code 974326958
[(15663)    API]  -  Object obtained null
[(15664)    API]  -  Getting object with hash code 974326958
[(15664)    API]  -  Object obtained null
[(15665)    API]  -  Getting object with hash code 420918602
[(15665)    API]  -  Object obtained null (editado) 
```


---

### Bug 2: Null Pointer / Concurrent Access
#### Summary
When trying to execute the tutorial's matmul function with agents, it first returns a NullPointerException, and then a ConcurrentModificationException. This happens particularly when:
- The edge is offloading tasks to the agent running in the server
- The matrix is quite large

If the matmul is not being offloaded to the server or the matrix is smaller (e.g., matrix size 4, block size 64), no problem occurs.

The image is using `compss/compss:3.3`.

#### Steps to Reproduce
1. Deploy agent 1: `compss_agent_start --hostname=127.0.0.1 --classpath=/path/to/repo/hp2cdt/experiments/response_time_agents/matmul/jar/matmul.jar --log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 --project=/path/to/repo/hp2cdt/experiments/response_time/scripts/edge_project.xml`
2. Deploy agent 2: `compss_agent_start --hostname=127.0.0.2 --classpath=/path/to/repo/hp2cdt/experiments/response_time_agents/matmul/jar/matmul.jar --log_dir=/tmp/Agent2 --rest_port=46201 --comm_port=46202 --project=/path/to/repo/hp2cdt/experiments/response_time/scripts/server_project.xml`
3. Add resources: `compss_agent_add_resources --agent_node=127.0.0.1 --agent_port=46101 --cpu=4 127.0.0.2 Port=46202`
4. Call operation: `compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 --cei="matmul.arrays.MatmulServerItf" matmul.arrays.Matmul 8 64`

#### Current Behavior
It executes some tasks and then throws a NullPointerException.

#### Expected Behavior
The edge properly executes the call and returns the message "Job completed after..."

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

---

### Bug 3: COMPSs Agent with 1 CPU is Faster Than with 2 or 4 CPUs
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
1. Clone HP2CDT repository: `git clone https://gitlab.bsc.es/wdc/projects/hp2cdt.git`
2. Checkout to branch implement-compss-section: `git checkout implement-compss-section`

**Test 4 CPU agent (external version):**
3. Start agent: `compss_agent_start --hostname=127.0.0.1 --classpath=/path/to/repo/hp2cdt/experiments/response_time_agents/matmul_simple_external/jar/matmul.jar --log_dir=/tmp/Agent1 --rest_port=46101 --comm_port=46102 --project=/path/to/repo/hp2cdt/experiments/response_time/scripts/server_project.xml`
4. Call: `compss_agent_call_operation --master_node=127.0.0.1 --master_port=46101 --cei="matmul.arrays.MatmulServerItf" matmul.arrays.Matmul 4 64` (Result: ~4s)

**Test 1 CPU agent (external version):**
5. Start agent: `compss_agent_start --hostname=127.0.0.1 --classpath=/path/to/repo/hp2cdt/experiments/response_time_agents/matmul_simple_external/jar/matmul.jar --log_dir=/tmp/Agent1 --rest_port=46301 --comm_port=46302 --project=/path/to/repo/hp2cdt/experiments/response_time_agents/scripts/single_cpu_project.xml`
6. Call: `compss_agent_call_operation --master_node=127.0.0.1 --master_port=46301 --cei="matmul.arrays.MatmulEdgeItf" matmul.arrays.Matmul 4 64` (Result: ~1s)

**Test sequential execution(external version)**
7. Run: `java -cp /path/to/repo/hp2cdt/experiments/response_time_agents/matmul_simple_external/jar/matmul.jar matmul.arrays.Matmul 4 64` (Result ~0.6s)