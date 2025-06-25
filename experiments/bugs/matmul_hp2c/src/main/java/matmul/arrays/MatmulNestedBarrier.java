/*
 *  Copyright 2002-2015 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package matmul.arrays;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import es.bsc.compss.api.COMPSs;


public class MatmulNestedBarrier {
	private static int MSIZE;
	private static int BSIZE;

	private static double [][][] A;
	private static double [][][] B;
	private static double [][][] C;


	private static void usage() {
		System.out.println("    Usage: matmul.arrays.Matmul <MSize> <BSize>");
	}

	public static void main(String[] args) throws Exception {
		calcMatmul(args);
	}

	public static void calcMatmul(String[] args) throws Exception{
		Instant t0 = Instant.now();
		// Check and get parameters
		if (args.length != 2) {
			usage();
			throw new Exception("[ERROR] Incorrect number of parameters");
		}
		MSIZE = Integer.parseInt(args[0]);
		BSIZE = Integer.parseInt(args[1]);

		// Initialize matrices
		System.out.println("[LOG] MSIZE parameter value = " + MSIZE);
		System.out.println("[LOG] BSIZE parameter value = " + BSIZE);
		A = initializeMatrix();
		B = initializeMatrix();
		C = new double[MSIZE][MSIZE][BSIZE*BSIZE];

		// Compute matrix multiplication C = A x B
		computeMultiplication(A, B, C, MSIZE, BSIZE);

		// Uncomment the following line if you wish to see the result in the stdout
		printMatrix(C, "C (Result)  ");
		// Uncomment the following line if you wish to store the result in a file
		//storeMatrix("c_result.txt");

		// End
		Instant t1 = Instant.now();
		System.out.println("[LOG] Main program finished. Elapsed time: " + Duration.between(t0,t1));
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

	public static double[] initializeBlock(int size) {
		System.out.println("INIT BLOCK EDGE");
		double[] block = new double[size*size];
		for (int i = 0; i < size*size; ++i) {
			block[i] = (double)(Math.random()*10.0);
		}
		return block;
	}

	private static double[][][] computeMultiplication(double[][][] a, double[][][] b, double[][][] c,
													  int msize, int bsize) {
		System.out.println("RUNNING MATMUL EDGE");
		// Allocate result matrix C
		System.out.println("[LOG] Allocating C matrix space");

		// Compute result
		System.out.println("[LOG] Computing Result");
		for (int i = 0; i < msize; i++) {
			for (int j = 0; j < msize; j++) {
				for (int k = 0; k < msize; k++) {
					multiplyAccumulative(a[i][k], b[k][j], c[i][j]);
				}
			}
		}
		System.out.println("CCCCCCCCCCCCCCCCCCCCCCC");
		int M = (int)Math.sqrt(c.length);
		for (int i = 0; i < M; i++) {
			for (int j = 0; j < M; j++) {
				for (int k = 0; k < M; k++) {
					System.out.println(c[i][j][k]);
				}
			}
		}
		System.out.println("CCCCCCCCCCCCCCCCCCCCCCC");

		COMPSs.barrier();
		return c;
	}

	public static void multiplyAccumulative(double[] A, double[] B, double[] C) {
		int M = (int)Math.sqrt(A.length);
		for (int i = 0; i < M; i++) {
			for (int j = 0; j < M; j++) {
				for (int k = 0; k < M; k++) {
					C[i*M + j] += A[i*M + k] * B[k*M + j];
					System.out.println("MULT " + C[i*M + j]);
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private static void printMatrix(double[][][] matrix, String name) {
		System.out.println("MATRIX " + name);
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

}

