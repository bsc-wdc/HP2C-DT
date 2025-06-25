package es.bsc.hp2c.common.funcs;

import es.bsc.hp2c.common.generic.MsgAlert;
import es.bsc.hp2c.common.generic.Voltmeter;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONObject;
import es.bsc.compss.types.annotations.Parameter;
import es.bsc.compss.types.annotations.Constraints;
import es.bsc.compss.types.annotations.task.Method;
import es.bsc.compss.types.annotations.parameter.Direction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * This class implements a COMPSs function to multiply two matrices.
 */
public class MatMulEdgeSimple extends Func {
    private static int MSIZE;
    private static int BSIZE;

    private static double [][][] A;
    private static double [][][] B;
    private static double [][][] C;
    private int n; // matrix size --> nxn
    private Voltmeter<?> voltmeter;
    private MsgAlert msgAlert;

    public MatMulEdgeSimple(Map<String, ArrayList<Sensor<?, ?>>> sensors,
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
            MSIZE =  others.getInt("msize");
            BSIZE =  others.getInt("bsize");
            A = initializeMatrix();
            B = initializeMatrix();
        } catch (Exception e) {
            throw new FunctionInstantiationException("'n' field must be provided");
        }
    }

    @Override
    public void run() {
        long timestampNanos = voltmeter.getWindow().getLastMeasurement().getTimestamp().getEpochSecond() * 1_000_000_000L
                + voltmeter.getWindow().getLastMeasurement().getTimestamp().getNano();

        // Allocate result matrix C
        System.out.println("[LOG] Allocating C matrix space");
        C = new double[MSIZE][MSIZE][BSIZE*BSIZE];

        // Compute result
        System.out.println("[LOG] Computing Result");
        for (int i = 0; i < MSIZE; i++) {
            for (int j = 0; j < MSIZE; j++) {
                for (int k = 0; k < MSIZE; k++) {
                    multiplyAccumulative(A[i][k], B[k][j], C[i][j]);
                }
            }
        }

        //printMatrix(C);
        try {
            msgAlert.actuate(Long.toString(timestampNanos));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static double[][][] initializeMatrix() {
        double[][][] matrix = new double[MSIZE][MSIZE][BSIZE*BSIZE];
        for (int i = 0; i < MSIZE; ++i) {
            for (int j = 0; j < MSIZE; ++j) {
                matrix[i][j] = initializeBlock(BSIZE);
            }
        }

        return matrix;
    }

    private static void printMatrix(double[][][] matrix) {
        System.out.println("COMPUTED MATRIX: ");
        for (int i = 0; i < MSIZE; i++) {
            for (int j = 0; j < MSIZE; j++) {
                printBlock(matrix[i][j]);
            }
            System.out.println("");
        }
    }

    private static void printBlock(double[] block) {
        for (int k = 0; k < block.length; k++) {
            System.out.print(block[k] + " ");
        }
        System.out.println("");
    }

    public static void multiplyAccumulative(double[] A, double[] B, double[] C) {
        System.out.println("MULTIPLY EdgeSimple");
        int M = (int)Math.sqrt(A.length);
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < M; j++) {
                for (int k = 0; k < M; k++) {
                    C[i*M + j] += A[i*M + k] * B[k*M + j];
                }
            }
        }
    }

    public static double[] initializeBlock(int size) {
        System.out.println("INIT BLOCK EdgeSimple");
        double[] block = new double[size*size];
        for (int i = 0; i < size*size; ++i) {
            block[i] = (double)(Math.random()*10.0);
        }
        return block;
    }

    public static interface COMPSsItf {

        @Constraints(computingUnits = "1", processorArchitecture = "arm")
        @Method(declaringClass = "es.bsc.hp2c.common.funcs.MatMulEdgeSimple")
        void multiplyAccumulative(
                @Parameter double[] A,
                @Parameter double[] B,
                @Parameter(direction = Direction.INOUT)	double[] C
        );
    }
}
