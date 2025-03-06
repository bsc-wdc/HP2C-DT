package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.generic.MsgAlert;
import es.bsc.hp2c.common.generic.Voltmeter;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONObject;
import es.bsc.compss.types.annotations.Parameter;
import es.bsc.compss.types.annotations.Constraints;
import es.bsc.compss.types.annotations.task.Method;
import es.bsc.compss.types.annotations.parameter.Direction;
import es.bsc.compss.types.annotations.parameter.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * This class implements a COMPSs function to multiply two matrices.
 */
public class MatMul extends Func {
    private int n; // matrix size --> nxn
    private Voltmeter<?> voltmeter;
    private MsgAlert msgAlert;

    public MatMul(Map<String, ArrayList<Sensor<?, ?>>> sensors,
                  Map<String, ArrayList<Actuator<?>>> actuators,
                  JSONObject others) throws IllegalArgumentException, FunctionInstantiationException {
        super(sensors, actuators, others);

        ArrayList<Sensor<?,?>> sensorsList;
        if (sensors != null && !sensors.isEmpty()) {
            sensorsList = sensors.values().iterator().next();
        } else {
            throw new IllegalArgumentException("The sensors map is empty or null.");
        }

        ArrayList<Actuator<?>> actuatorsList;
        if (actuators != null && !actuators.isEmpty()) {
            actuatorsList = actuators.values().iterator().next();
        } else {
            throw new IllegalArgumentException("The actuators map is empty or null.");
        }

        if (sensorsList.size() != 1) {
            throw new FunctionInstantiationException("Sensors must be exactly one voltmeter");
        }

        if (actuatorsList.size() != 1) {
            throw new FunctionInstantiationException("Actuators must be exactly one switch");
        }

        Sensor<?, ?> sensor = sensorsList.get(0);
        if (!(sensor instanceof Voltmeter)) {
            throw new FunctionInstantiationException("The sensor must be a voltmeter");
        }
        this.voltmeter = (Voltmeter<?>) sensor;

        Actuator<?> actuator = actuatorsList.get(0);
        if (!(actuator instanceof MsgAlert)) {
            throw new FunctionInstantiationException("The actuator must be a switch");
        }
        this.msgAlert = (MsgAlert) actuator;

        try {
            this.n = others.getInt("n");
        } catch (Exception e) {
            throw new FunctionInstantiationException("'n' field must be provided");
        }
    }

    @Override
    public void run() {
        double[][] A = new double[n][n];
        double[][] B = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] = i + j;
                B[i][j] = i - j;
            }
        }
        // Call the COMPSs task to multiply matrices
        double[][] C = multiplyMatrices(A, B);

        long timestampNanos = voltmeter.getWindow().getLastMeasurement().getTimestamp().getEpochSecond() * 1_000_000_000L
                + voltmeter.getWindow().getLastMeasurement().getTimestamp().getNano();
        try {
            msgAlert.actuate(Long.toString(timestampNanos));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static double[][] multiplyMatrices(double[][] A, double[][] B) {
        int n = A.length;
        double[][] C = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    C[i][j] += A[i][k] * B[k][j];
                }
            }
        }

        System.out.println("[MatMul] Matrix multiplication result:");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                System.out.print(C[i][j] + "\t");
            }
            System.out.println();
        }

        return C;
    }

    public static interface COMPSsItf {
        @Constraints(computingUnits = "1")
        @Method(declaringClass = "es.bsc.hp2c.edge.funcs.MatMul")
        double[][] multiplyMatrices(
                @Parameter(type = Type.OBJECT, direction = Direction.IN) double[][] A,
                @Parameter(type = Type.OBJECT, direction = Direction.IN) double[][] B
        );
    }
}

