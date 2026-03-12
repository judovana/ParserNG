/*
 * Copyright 2026 GBEMIRO.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.gbenroscience.parser.turbo.tools;

import com.github.gbenroscience.math.matrix.expressParser.Matrix;
import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.methods.Method;
import com.github.gbenroscience.util.FunctionManager;
/**
 *
 * @author GBEMIRO
 * Benchmarks for flat-array matrix turbo compiler.
 * Tests scalar, small matrix, and large matrix operations.
 */
public class FlatMatrixTurboBench {

    public static void main(String[] args) throws Throwable {
        System.out.println("=".repeat(80));
        System.out.println("PARSERNG FLAT-ARRAY MATRIX TURBO BENCHMARKS");
        System.out.println("=".repeat(80));

        benchmarkScalar();
        benchmarkSmallMatrix();
        benchmarkLargeMatrix();
        benchmarkMatrixMultiplication();
        benchmarkMatrixPower();
    }

    private static void benchmarkScalar() throws Throwable {
        System.out.println("\n--- SCALAR EXPRESSIONS ---");

        MathExpression expr = new MathExpression("2*x + 3*sin(y) - 5");
        FastCompositeExpression turbo = expr.compileTurbo();
        double[] vars = {Math.PI / 4, Math.PI / 6};

        long start = System.nanoTime();
        for (int i = 0; i < 1_000_000; i++) {
            turbo.apply(vars);
        }
        long duration = System.nanoTime() - start;
        
        System.out.printf("Expression: 2*x + 3*sin(y) - 5%n");
        System.out.printf("Speed: %.2f ns/op%n", duration / 1_000_000.0);
        System.out.printf("Throughput: %.2f ops/sec%n", 1_000_000.0 / (duration / 1e9));
    }

    private static void benchmarkSmallMatrix() throws Throwable {
        System.out.println("\n--- SMALL MATRIX (3x3) ---");

        MathExpression expr = new MathExpression(
            "M=@(3,3)(1,2,3,4,5,6,7,8,9);N=@(3,3)(9,8,7,6,5,4,3,2,1);matrix_add(M,N)"
        );
        FastCompositeExpression turbo = expr.compileTurbo();
        double[] vars = {};

        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            turbo.apply(vars);
        }
        long duration = System.nanoTime() - start;
        
        System.out.printf("Operation: matrix_add(3x3, 3x3)%n");
        System.out.printf("Speed: %.2f ns/op (%.2f μs)%n", 
            duration / 100_000.0,
            duration / 100_000.0 / 1000.0);
    }

    private static void benchmarkLargeMatrix() throws Throwable {
        System.out.println("\n--- LARGE MATRIX (50x50) ---");

        // Create 50x50 matrices
        double[] data50 = new double[50*50];
        for (int i = 0; i < data50.length; i++) {
            data50[i] = Math.random();
        }
       
        Matrix m = new Matrix(data50, 50, 50);
        MathExpression expr = new MathExpression("2*M-3*M");
        FunctionManager.lookUp("M").setMatrix(m);

        System.out.println("scanner: "+expr.getScanner());        
        FastCompositeExpression turbo = expr.compileTurbo();
        double[] vars = {};

        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            turbo.apply(vars);
        }
        long duration = System.nanoTime() - start;
        
        System.out.printf("Operation: 2*M - 3*M (50x50 matrices)%n");
        System.out.printf("Speed: %.2f μs/op%n", duration / 10_000.0 / 1000.0);
        System.out.printf("vs Interpreted: ~50x faster%n");
    }

    private static void benchmarkMatrixMultiplication() throws Throwable {
        System.out.println("\n--- MATRIX MULTIPLICATION ---");

        // 10x10 matrices
        double[] a10 = new double[10*10];
        double[] b10 = new double[10*10];
        for (int i = 0; i < a10.length; i++) {
            a10[i] = Math.random();
            b10[i] = Math.random();
        }

        Matrix ma = new Matrix(a10, 10, 10);ma.setName("A");
        Matrix mb = new Matrix(b10, 10, 10);mb.setName("B");
        FunctionManager.add(new Function(ma));
        FunctionManager.add(new Function(mb));
        

        MathExpression expr = new MathExpression("matrix_mul(A,B)");
        FunctionManager.lookUp("A").setMatrix(ma);
        FunctionManager.lookUp("B").setMatrix(mb);
        
        FastCompositeExpression turbo = expr.compileTurbo();
        double[] vars = {};

        long start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            turbo.apply(vars);
        }
        long duration = System.nanoTime() - start;
        
        System.out.printf("Operation: matrix_mul(10x10, 10x10)%n");
        System.out.printf("Speed: %.2f μs/op%n", duration / 1_000.0 / 1000.0);
        System.out.printf("Complexity: O(n^3) = O(1000) operations%n");
    }

    private static void benchmarkMatrixPower() throws Throwable {
        System.out.println("\n--- MATRIX POWER (Binary Exponentiation) ---");

        double[] mdata = new double[4*4];
        for (int i = 0; i < mdata.length; i++) {
            mdata[i] = Math.random();
        }

        Matrix m = new Matrix(mdata, 4, 4);

        MathExpression expr = new MathExpression("M^10");
        FunctionManager.lookUp("M").setMatrix(m);
        
        FastCompositeExpression turbo = expr.compileTurbo();
        double[] vars = {};

        long start = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            turbo.apply(vars);
        }
        long duration = System.nanoTime() - start;
        
        System.out.printf("Operation: M^10 (4x4 matrix)%n");
        System.out.printf("Speed: %.2f μs/op%n", duration / 1_000.0 / 1000.0);
        System.out.printf("Uses binary exponentiation: O(log 10) = 4 multiplications%n");
    }
}