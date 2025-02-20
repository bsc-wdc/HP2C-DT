package es.bsc.hp2c.common.funcs;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javassist.CannotCompileException;
import javassist.NotFoundException;

import es.bsc.compss.api.COMPSsRuntime;
import es.bsc.compss.loader.LoaderConstants;
import es.bsc.compss.loader.total.ObjectRegistry;
import es.bsc.compss.loader.total.StreamRegistry;
import es.bsc.compss.types.CoreElementDefinition;
import es.bsc.compss.types.execution.exceptions.JobExecutionException;
import es.bsc.compss.util.parsers.ITFParser;

import es.bsc.compss.loader.total.ITAppModifier;


public class COMPSsHandler {
    private Class<?> instrumentedClass;
    private Method method;
    private final Class<?> c;
    private final String driver;
    private COMPSsRuntime runtime;
    private StreamRegistry streamRegistry;
    private ObjectRegistry objectRegistry;
    private Long appId;

    public COMPSsHandler(Class<?> runtimeHostClass, String driver, Class<?> c) throws ClassNotFoundException {
        this.driver = driver;
        this.c = c;
        System.out.println("======= METHOD_NAME " + driver);
        // Get class from driver

        // Get COMPSs variables from host class
        this.getVariables(runtimeHostClass);
    }

    static void runWorkflow(Class<?> instrumentedClass, Object classInstance) {
        // Get method to execute (run)
        Method method = null;
        try {
            method = instrumentedClass.getMethod("run");
        } catch (NoSuchMethodException e) {
            System.err.println("Could not get run method from instrumented class");
            throw new RuntimeException(e);
        }

        // Invoke
        try {
            method.invoke(classInstance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            System.err.println("Could not invoke method run of instance " + classInstance);
            throw new RuntimeException(e);
        }
        System.out.println("Checkpoint 5.5555: finished invoke");
    }

    public void getVariables(Class <?> runtimeHostClass) {
        Method getter;
        try {
            getter = runtimeHostClass.getDeclaredMethod("getRuntime");
            runtime = (COMPSsRuntime) getter.invoke(null);
            getter = runtimeHostClass.getDeclaredMethod("getStreamRegistry");
            streamRegistry = (StreamRegistry) getter.invoke(null);
            getter = runtimeHostClass.getDeclaredMethod("getObjectRegistry");
            objectRegistry = (ObjectRegistry) getter.invoke(null);
            getter = runtimeHostClass.getDeclaredMethod("getAppId");
            appId = (Long) getter.invoke(null);
            System.out.println("Print all the previous ones: \nruntime: " + runtime);
            System.out.println("streamRegistry: " + streamRegistry);
            System.out.println("objectregistry: " + objectRegistry);
            System.out.println("appId: " + appId);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            System.err.println("Error obtaining or invoking COMPSs getter methods from host class "
                    + runtimeHostClass.getName() + " through reflection");
            throw new RuntimeException(e);
        }
    }

    public Class<?> instrumentClass(String driver)
            throws NotFoundException, CannotCompileException, ClassNotFoundException {

        // Get annotated interface for COMPSs tasks
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.out.println(Arrays.toString(c.getDeclaredClasses()));
        Class<?> annotItf = null;
        for (Class<?> innerClass : c.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("COMPSsItf")) {
                System.out.println(innerClass.getSimpleName());
                System.out.println(innerClass.getName());

                System.out.println("Found inner interface COMPSsItf in driver " + driver);
                annotItf = innerClass;
                break;
            }
        }
        if (annotItf == null) {
            System.out.println("Couldn't find inner interface COMPSsItf in driver " + driver + ". Aborting...");
            throw new RuntimeException();
        }

        // Instrument class
        instrumentedClass = ITAppModifier.modifyToMemory(driver, driver, annotItf, false,
                true, true, false);

        // Initialize COMPSs variables at the instrumented class
        try {
            Method setter = instrumentedClass.getDeclaredMethod("setCOMPSsVariables",
                    Class.forName(LoaderConstants.CLASS_COMPSSRUNTIME_API),
                    Class.forName(LoaderConstants.CLASS_STREAM_REGISTRY),
                    Class.forName(LoaderConstants.CLASS_OBJECT_REGISTRY),
                    Class.forName(LoaderConstants.CLASS_APP_ID));
            setter.invoke(null, runtime, streamRegistry, objectRegistry, appId);
        } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException |
                 InvocationTargetException e) {
            System.err.println("Error obtaining or invoking COMPSs setter methods for instrumented class "
                    + instrumentedClass.getName() + " through reflection");
            throw new RuntimeException(e);
        }

        // Print to check for instrumentation
        printVariables();

        // Get method to be run
        String methodName = "run";
        try {
            // Get the "run" method with no parameters
            method = instrumentedClass.getMethod(methodName, new Class<?>[]{});
            System.out.println("Method found: " + method);
            // Register Core Elements on Runtime
            List<CoreElementDefinition> ceds = ITFParser.parseITFMethods(annotItf);
            for (CoreElementDefinition ced : ceds) {
                runtime.registerCoreElement(ced);
            }
        } catch (NoSuchMethodException e) {
            System.out.println("Method" + methodName + "not found in the class: " + instrumentedClass.getName());
        } catch (SecurityException e) {
            System.out.println("Access to the method is not allowed.");
        }
        return instrumentedClass;
    }

    private void printVariables() {
        System.out.println("** AVAILABLE METHODS IN THE " + driver + " CLASS:");
        Method[] methods = instrumentedClass.getDeclaredMethods();
        for (Method method : methods) {
            System.out.println(" - " + method.getName());
        }

        System.out.println("** AVAILABLE FIELDS IN THE " + driver + " CLASS:");
        Field[] fields = instrumentedClass.getDeclaredFields();
        for (Field field : fields) {
            System.out.println(" - " + field.getName());
        }
    }

    public void runMethod() throws JobExecutionException {
            /*
                    public int executeTask(Long appId, Lang lang, String methodClass, String methodName, boolean isPrioritary,
        int numNodes, boolean isReduce, int reduceChunkSize, boolean isReplicated, boolean isDistributed,
        boolean hasTarget, int parameterCount, OnFailure onFailure, int timeOut, Object... parameters)
                     */
        // long appId;  // TODO:
        // appId = becomesNestedApplication(this.ceiName);

            /* Code un JavaNestedInvoker.runMethod()
            // Register Core Elements on Runtime
            List<CoreElementDefinition> ceds = ITFParser.parseITFMethods(this.ceiClass);
            for (CoreElementDefinition ced : ceds) {
                this.runtime.registerCoreElement(ced);
            }*/

        /* Code un JavaInvoker.runMethod() */
        List<Object> params = new ArrayList<>();
        params.add(Optional.of(10));  // TODO: hard-coded parameter
        int paramCount = this.method.getParameterCount();
        Object[] values = new Object[paramCount];

        Object[] paramDest = values;
        int paramIdx = 0;
        for (Object param : params) {
            if (paramIdx != paramCount - 1) {
                paramDest[paramIdx++] = param;
            } else {
                Parameter reflectionParam = this.method.getParameters()[paramIdx];
                Class<?> paramClass = this.method.getParameters()[paramIdx].getType();
                // If the method has an arbitrary number of parameters, the last parameter is an array.
                if (reflectionParam.isVarArgs()) {
                    int varArgsCount = params.size() - paramCount + 1;
                    paramDest[paramIdx] = Array.newInstance(paramClass.getComponentType(), varArgsCount);
                    paramDest = (Object[]) paramDest[paramIdx];
                    paramIdx = 0;
                    paramDest[paramIdx++] = param;
                } else {
                    paramDest[paramIdx++] = param;
                }
            }
        }

            /* This grabs an instance of the corresponding class if the method is not static
            InvocationParam targetParam = this.invocation.getTarget();
            Object target = null;
            if (targetParam != null) {
                target = targetParam.getValue();
            }*/

        Object retValue;
        try {
            System.out.println("Invoking " + this.method.getName()); // + (target == null ? "" : " on object " + target));
            retValue = this.method.invoke(null, values);  // TODO: I make it static for the moment, check if it could be an instantiation or not
                /*for (InvocationParam np : this.invocation.getResults()) {
                    np.setValue(retValue);
                    if (retValue != null) {
                        np.setValueClass(retValue.getClass());
                    } else {
                        np.setValueClass(null);
                    }
                }*/
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            System.err.println(e.getMessage());
            throw new JobExecutionException("ERROR: Exception executing task", e);
        }
        System.out.println("=== These are the results! " + retValue);
    }
}