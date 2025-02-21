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
        // Init fields
        this.driver = driver;
        this.c = c;
        // Get COMPSs variables from host class
        this.getVariables(runtimeHostClass);
    }

    static void runWorkflow(Class<?> instrumentedClass, Object classInstance) {
        // Get method to execute (run)
        Method method = null;
        try {
            method = instrumentedClass.getMethod("run");
        } catch (NoSuchMethodException e) {
            System.err.println("[COMPSsHandler] Could not get run method from instrumented class");
            throw new RuntimeException(e);
        }

        // Invoke
        try {
            method.invoke(classInstance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            System.err.println("[COMPSsHandler] Could not invoke method run of instance " + classInstance);
            throw new RuntimeException(e);
        }
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
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            System.err.println("[COMPSsHandler] Error obtaining or invoking COMPSs getter methods from host class "
                    + runtimeHostClass.getName() + " through reflection");
            throw new RuntimeException(e);
        }
    }

    public Class<?> instrumentClass(String driver)
            throws NotFoundException, CannotCompileException, ClassNotFoundException {

        // Get annotated interface for COMPSs tasks
        // System.out.println(Arrays.toString(c.getDeclaredClasses()));
        Class<?> annotItf = null;
        for (Class<?> innerClass : c.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("COMPSsItf")) {
                // Debug prints
                //  System.out.println(innerClass.getSimpleName());
                //  System.out.println(innerClass.getName());
                //  System.out.println("Found inner interface COMPSsItf in driver " + driver);
                annotItf = innerClass;
                break;
            }
        }
        if (annotItf == null) {
            System.err.println("[COMPSsHandler] Couldn't find inner interface COMPSsItf " +
                    "in driver " + driver + ". Aborting...");
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
            System.err.println("[COMPSsHandler] Error obtaining or invoking COMPSs setter methods "
                    + "for instrumented class " + instrumentedClass.getName() + " through reflection");
            throw new RuntimeException(e);
        }

        // Debug: Print to check for instrumentation
        // printVariables();

        // Get method to be run
        String methodName = "run";
        try {
            // Get the "run" method with no parameters
            method = instrumentedClass.getMethod(methodName, new Class<?>[]{});
            System.out.println("[COMPSsHandler] Method found: " + method);
            // Register Core Elements on Runtime
            List<CoreElementDefinition> ceds = ITFParser.parseITFMethods(annotItf);
            for (CoreElementDefinition ced : ceds) {
                runtime.registerCoreElement(ced);
            }
        } catch (NoSuchMethodException e) {
            System.err.println("[COMPSsHandler] Method" + methodName + "not found in the class: " + instrumentedClass.getName());
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            System.err.println("[COMPSsHandler] Access to the method is not allowed.");
            throw new RuntimeException(e);
        }
        return instrumentedClass;
    }

    private void printVariables() {
        System.out.println("[COMPSsHandler] ** AVAILABLE METHODS IN THE " + driver + " CLASS:");
        Method[] methods = instrumentedClass.getDeclaredMethods();
        for (Method method : methods) {
            System.out.println(" - " + method.getName());
        }

        System.out.println("[COMPSsHandler] ** AVAILABLE FIELDS IN THE " + driver + " CLASS:");
        Field[] fields = instrumentedClass.getDeclaredFields();
        for (Field field : fields) {
            System.out.println(" - " + field.getName());
        }
    }
}