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
import com.github.gbenroscience.parser.MathExpression;
import java.util.Arrays;

/**
 *
 * @author GBEMIRO 
 * Benchmarks for ScalarTurboCompiler vs Interpreted evaluation.
 * Tests basic arithmetic, trig functions, and complex expressions.
 */
public class ScalarTurboBench {
    
    private static final int N = 1000000;

    public static void main(String[] args) throws Throwable {
        System.out.println("=".repeat(80));
        System.out.println("SCALAR TURBO COMPILER BENCHMARKS");
        System.out.println("=".repeat(80));

        benchmarkBasicArithmetic();
        benchmarkTrigonometric();
        benchmarkComplexExpression(false);
        benchmarkComplexExpression(true);
        benchmarkWithVariables();
        benchmarkConstantFolding();
    }

    private static void benchmarkBasicArithmetic() throws Throwable {
        System.out.println("\n=== BASIC ARITHMETIC; FOLDING OFF===\n");
        String expr = "2 + 3 * 4 - 5 / 2 + 1 ^ 3";
        
        // Warm up JIT
        MathExpression interpreted = new MathExpression(expr, false);
        for (int i = 0; i < 1000; i++) {
            interpreted.solveGeneric();
        }

        // Compile to turbo
        MathExpression turbo = new MathExpression(expr, false); 
        FastCompositeExpression compiled = turbo.compileTurbo();
        
        // Warm up turbo JIT
        double[] vars = new double[0];
        for (int i = 0; i < 1000; i++) {
            compiled.applyScalar(vars);
        }

        // Benchmark interpreted
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            interpreted.solveGeneric();
        }
        double interpretedDur = System.nanoTime() - start;

        // Benchmark turbo
        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted: %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:       %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
    }

    private static void benchmarkTrigonometric() throws Throwable {
        System.out.println("\n=== TRIGONOMETRIC FUNCTIONS; FOLDING OFF ===\n");

        String expr = "sin(3.14159/2) + cos(1.5708) * tan(0.785398)";

        MathExpression interpreted = new MathExpression(expr, false);
        for (int i = 0; i < 1000; i++) {
            interpreted.solveGeneric();
        }

        MathExpression turbo = new MathExpression(expr, false);
        FastCompositeExpression compiled = turbo.compileTurbo();
        
        double[] vars = new double[0];
        for (int i = 0; i < 1000; i++) {
            compiled.applyScalar(vars);
        }

        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            interpreted.solveGeneric();
        }
        double interpretedDur = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted: %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:       %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
    }

    private static void benchmarkComplexExpression(boolean withFolding) throws Throwable {
        System.out.println("\n=== COMPLEX EXPRESSION "+(withFolding ? "WITH FOLDING" : "WITHOUT FOLDING" )+" ===\n");

        String expr = "sqrt(16) + 2^3 * sin(0) + 3! - cos(-5) + ln(2.718281828)";

        MathExpression interpreted = new MathExpression(expr, withFolding);
        for (int i = 0; i < 1000; i++) {
            interpreted.solveGeneric();
        }

        MathExpression turbo = new MathExpression(expr, withFolding);
        System.out.println("scanner: "+turbo.getScanner());
        FastCompositeExpression compiled = turbo.compileTurbo();
        
        double[] vars = new double[0];
        for (int i = 0; i < 1000; i++) {
            compiled.applyScalar(vars);
        }

        long start = System.nanoTime();
        double[]v={0,1};
        for (int i = 0; i < N; i++) {
            v[0]=interpreted.solveGeneric().scalar;
        }
        double interpretedDur = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            v[1]=compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted: %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:       %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
        System.out.println("values="+Arrays.toString(v));
    }

    private static void benchmarkWithVariables() throws Throwable {
        System.out.println("\n=== WITH VARIABLES; FOLDING OFF ===\n");

        String expr = "x*sin(x) + y*sin(y) + z / cos(x - y) + sqrt(x^2 + y^2)";

        MathExpression interpreted = new MathExpression(expr, false);
        int xSlot = interpreted.getVariable("x").getFrameIndex();
        int ySlot = interpreted.getVariable("y").getFrameIndex();
        int zSlot = interpreted.getVariable("z").getFrameIndex();
        
        
          double[] res = new double[2];
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            interpreted.updateSlot(xSlot, 2.5);
            interpreted.updateSlot(ySlot, 3.7);
            interpreted.updateSlot(zSlot, 1.2);
            res[0] = interpreted.solveGeneric().scalar;
        }
        
        double intDur = System.nanoTime() - start;
        
         

        MathExpression turbo = new MathExpression(expr, false);
        FastCompositeExpression compiled = turbo.compileTurbo();
        
        double[] vars = new double[3];
        vars[xSlot] = 2.5;
        vars[ySlot] = 3.7;
        vars[zSlot] = 1.2;
        
        for (int i = 0; i < 1000; i++) {
           compiled.applyScalar(vars);
        }

         start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            res[1] = compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Variables: x=2.5, y=3.7, z=1.2%n");
        System.out.printf("Interpreted:     %.2f ns/op%n", intDur / N);
        System.out.printf("Turbo:     %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) intDur / turboDur);
        System.out.println("values="+Arrays.toString(res));
    }

    private static void benchmarkConstantFolding() throws Throwable {
        System.out.println("\n=== CONSTANT FOLDING; FOLDING OFF ===\n");

        String expr = "2^10 + 3^5 - 4! + sqrt(256)";

        MathExpression turbo = new MathExpression(expr, false);
        turbo.setWillFoldConstants(true); // Enable optimization
        FastCompositeExpression compiled = turbo.compileTurbo();
        
        double[] vars = new double[0];
        for (int i = 0; i < 1000; i++) {
            compiled.applyScalar(vars);
        }

        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("(All constants - folds to single value at compile time)%n");
        System.out.printf("Turbo:     %.2f ns/op%n", turboDur / N);
    }
}