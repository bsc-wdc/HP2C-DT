package es.bsc.hp2c.common.funcs;

/**
 * Wrapper of the Instance/Runnable and Workflow objects obtained from reflection.
 * Holds the different action objects that may be loaded from using standalone funcs or COMPSs-related funcs.
 * Possible cases:
 * - Function is synchronous: therefore `classInstance` will be a Runnable object.
 *   The class `c` will be stored but not used by the trigger procedure
 * - Function is asynchronous (workflow or task): `classInstance` is Object and Class `c` will be required by the
 *   trigger to deploy the workflow/task
 */
public class Action {
    private final Object classInstance;
    private final Class<?> c;

    public Action(Object classInstance, Class<?> c) {
        this.classInstance = classInstance;
        this.c = c;
    }

    public Object getInstance() {
        return classInstance;
    }

    public Class<?> getInstrumentedClass() {
        return c;
    }

    public Runnable getRunnable() {
        if (classInstance instanceof Runnable) {
            return (Runnable) classInstance;
        } else {
            throw new IllegalArgumentException("Instance " + classInstance + " does not implement Runnable");
        }
    }

    public void run() {
        if (classInstance instanceof Runnable) {
            Runnable r = (Runnable) classInstance;
            r.run();
        } else {
            COMPSsHandler.runWorkflow(c, classInstance);
        }
    }
}