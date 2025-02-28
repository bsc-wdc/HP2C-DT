package es.bsc.hp2c.server.funcs;

import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONObject;
import es.bsc.compss.types.annotations.Parameter;
import es.bsc.compss.types.annotations.Constraints;
import es.bsc.compss.types.annotations.task.Method;
import es.bsc.compss.types.annotations.parameter.Direction;
import es.bsc.compss.types.annotations.parameter.Type;

import java.util.ArrayList;
import java.util.Map;

/**
 * This class implements a COMPSs function to multiply two matrices.
 */
public class MatMul extends Func {
    private int n; // matrix size --> nxn

    public MatMul(Map<String, ArrayList<Sensor<?, ?>>> sensors,
                  Map<String, ArrayList<Actuator<?>>> actuators,
                  JSONObject others) throws IllegalArgumentException {
        super(sensors, actuators, others);
        this.n = others.optInt("n", 10);
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

        System.out.println("[MatMul] Matrix multiplication result:");
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                System.out.print(C[i][j] + "\t");
            }
            System.out.println();
        }
    }

    @Method(declaringClass = "es.bsc.hp2cdt.server.funcs.MatMul")
    public static double[][] multiplyMatrices(
            @Parameter(type = Type.OBJECT, direction = Direction.IN) double[][] A,
            @Parameter(type = Type.OBJECT, direction = Direction.IN) double[][] B) {
        int n = A.length;
        double[][] C = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    C[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return C;
    }

    public static interface COMPSsItf {
        @Constraints(computingUnits = "1")
        @Method(declaringClass = "es.bsc.hp2c.server.funcs.MatMul")
        double[][] multiplyMatrices(
                @Parameter(type = Type.OBJECT, direction = Direction.IN) double[][] A,
                @Parameter(type = Type.OBJECT, direction = Direction.IN) double[][] B
        );
    }
}

