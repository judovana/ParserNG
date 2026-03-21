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

import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.util.FunctionManager;
import java.util.Arrays;

/**
 *
 * @author GBEMIRO Benchmarks for ScalarTurboCompiler vs Interpreted evaluation.
 * Tests basic arithmetic, trig functions, and complex expressions.
 */
public class ScalarTurboBench {

    private static final int N = 1000000;

    public static void main(String[] args) throws Throwable {
        System.out.println("=".repeat(80));
        System.out.println("SCALAR TURBO COMPILER BENCHMARKS");
        System.out.println("=".repeat(80));

        testQuadratic();
        testTartaglia();
        testGeneralRoot();
        benchmarkBasicArithmetic();
        benchmarkSum();
        benchmarkSort();
        benchmarkDiffCalculus();
        benchmarkIntegralCalculus();
        benchmarkComplexIntegralCalculus();
        benchmarkPrinting();
        benchmarkTrigonometric();
        benchmarkComplexExpression(false);
        benchmarkComplexExpression(true);
        benchmarkWithVariablesSimple();
        benchmarkWithVariablesAdvanced();
        benchmarkConstantFolding();
    }
    
    
    
      private static void benchmarkPrinting() throws Throwable {
        System.out.println("\n=== TEST PRINTING===\n");
        String expr = "F=@(x,y,z)3*x+y-z^2";
          Function f = FunctionManager.add(expr);
          

          String ex = "A=@(2,2)(4,2,-1,9);print(A,F,x,y,z)";
        System.out.printf("Expression: %s%n", ex); 
        // Warm up JIT
        MathExpression interpreted = new MathExpression(ex, false);
        MathExpression.EvalResult ev = interpreted.solveGeneric();
     

        // Compile to turbo
        FastCompositeExpression compiled = interpreted.compileTurbo();
        // Warm up turbo JIT
        double[] vars = new double[0]; 
        MathExpression.EvalResult evr = compiled.apply(vars);

    }
      

    private static void benchmarkIntegralCalculus() throws Throwable {
        System.out.println("\n=== INTEGRAL CALCULUS; FOLDING OFF===\n");
        //String expr = "diff(@(x)cos(x)+sin(x),2,1)";
        String expr = "intg(@(x)(sin(x)+cos(x)), 1,2)";

        // Warm up JIT
        MathExpression interpreted = new MathExpression(expr, false);
        long time = System.nanoTime();
        MathExpression.EvalResult ev = interpreted.solveGeneric();
        double interpretedDur = System.nanoTime() - time;

        // Compile to turbo
        FastCompositeExpression compiled = interpreted.compileTurbo();
        // Warm up turbo JIT
        double[] vars = new double[0];
        time = System.nanoTime();
        MathExpression.EvalResult evr = compiled.apply(vars);
        double turboDur = System.nanoTime() - time;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted result: %.18f %n", ev.scalar);
        System.out.printf("Interpreted duration:       %.2f ns/op%n", interpretedDur);
        System.out.printf("Turbo result: %.18f %n", evr.scalar);
        System.out.printf("Turbo duration:       %.2f ns%n", turboDur);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
    }

      private static void benchmarkComplexIntegralCalculus() throws Throwable {
        System.out.println("\n=== COMPLEX INTEGRAL CALCULUS; FOLDING OFF===\n");
        //String expr = "diff(@(x)cos(x)+sin(x),2,1)";
        String expr = "intg(@(x)(1/(x*sin(x)+3*x*cos(x))), 0.5, 1.8)";

        // Warm up JIT
        MathExpression interpreted = new MathExpression(expr, false);
        long time = System.nanoTime();
        MathExpression.EvalResult ev = interpreted.solveGeneric();
        double interpretedDur = System.nanoTime() - time;

        // Compile to turbo
        FastCompositeExpression compiled = interpreted.compileTurbo();
        // Warm up turbo JIT
        double[] vars = new double[0];
        time = System.nanoTime();
        MathExpression.EvalResult evr = compiled.apply(vars);
        double turboDur = System.nanoTime() - time;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted result: %.18f %n", ev.scalar);
        System.out.printf("Interpreted duration:       %.2f ns/op%n", interpretedDur);
        System.out.printf("Turbo result: %.18f %n", evr.scalar);
        System.out.printf("Turbo duration:       %.2f ns%n", turboDur);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
    }

      
    private static void benchmarkDiffCalculus() throws Throwable {
        System.out.println("\n=== DIFFERENTIAL CALCULUS; FOLDING OFF===\n");
        //String expr = "diff(@(x)cos(x)+sin(x),2,1)";
        String expr = "diff(@(x)(sin(x)+cos(x)), 2,1)";

        // Warm up JIT
        MathExpression interpreted = new MathExpression(expr, false);
        MathExpression.EvalResult ev = interpreted.solveGeneric();

        System.out.printf("Expression: %s%n", ev.scalar);
        System.out.println("scanner: " + interpreted.getScanner());

        // Compile to turbo
        FastCompositeExpression compiled = interpreted.compileTurbo();
        // Warm up turbo JIT
        double[] vars = new double[0];
        MathExpression.EvalResult evr = compiled.apply(vars);

        System.out.printf("Expression: %s%n", evr.scalar);
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

    private static void benchmarkSum() throws Throwable {
        System.out.println("\n=== SUM; FOLDING OFF===\n");
        //String expr = "diff(@(x)cos(x)+sin(x),2,1)";
        String expr = "listsum(12,1,23,5,13,2,20,30,40,1,1,1,2)";

        // Warm up JIT
        MathExpression interpreted = new MathExpression(expr, false);

        MathExpression.EvalResult ev = interpreted.solveGeneric();

        // Benchmark interpreted
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            interpreted.solveGeneric();
        }
        double interpretedDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);

        // Compile to turbo
        FastCompositeExpression compiled = interpreted.compileTurbo();
        // Warm up turbo JIT
        double[] vars = new double[0];
         MathExpression.EvalResult evr =compiled.apply(vars);
        // Benchmark turbo
        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
           evr = compiled.apply(vars);
        }
        double turboDur = System.nanoTime() - start;

        

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted: %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:       %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
        System.out.printf("Interpreted Result: %s%n", ev.scalar);
        System.out.printf("Turbo Result: %s%n", evr.scalar);
    }
    
     private static void benchmarkSort() throws Throwable {
        System.out.println("\n=== SORT; FOLDING OFF===\n");
        //String expr = "diff(@(x)cos(x)+sin(x),2,1)";
        String expr = "sort(12,1,23,5,13,2,20,30,40,1,1,1,2)";

        // Warm up JIT
        MathExpression interpreted = new MathExpression(expr, false);

        MathExpression.EvalResult ev = interpreted.solveGeneric();

        // Benchmark interpreted
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            interpreted.solveGeneric();
        }
        double interpretedDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);

        // Compile to turbo
        FastCompositeExpression compiled = interpreted.compileTurbo();
        // Warm up turbo JIT
        double[] vars = new double[0];
         MathExpression.EvalResult evr =compiled.apply(vars);
        // Benchmark turbo
        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
           evr = compiled.apply(vars);
        }
        double turboDur = System.nanoTime() - start;

        

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted: %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:       %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
        System.out.printf("Interpreted Result: %s%n", Arrays.toString(ev.vector));
        System.out.printf("Turbo Result: %s%n", Arrays.toString(evr.vector));
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
        System.out.println("\n=== COMPLEX EXPRESSION " + (withFolding ? "WITH FOLDING" : "WITHOUT FOLDING") + " ===\n");

        String expr = "sqrt(16) + 2^3 * sin(0) + 3! - cos(-5) + ln(2.718281828)";

        MathExpression interpreted = new MathExpression(expr, withFolding);
        for (int i = 0; i < 1000; i++) {
            interpreted.solveGeneric();
        }

        MathExpression turbo = new MathExpression(expr, withFolding);
        System.out.println("scanner: " + turbo.getScanner());
        FastCompositeExpression compiled = turbo.compileTurbo();

        double[] vars = new double[0];
        for (int i = 0; i < 1000; i++) {
            compiled.applyScalar(vars);
        }

        long start = System.nanoTime();
        double[] v = {0, 1};
        for (int i = 0; i < N; i++) {
            v[0] = interpreted.solveGeneric().scalar;
        }
        double interpretedDur = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            v[1] = compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("Interpreted: %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:       %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
        System.out.println("values=" + Arrays.toString(v));
    }

    private static void benchmarkWithVariablesSimple() throws Throwable {
        System.out.println("\n=== WITH VARIABLES: SIMPLE; FOLDING OFF ===\n");

        String expr = "x*sin(x)+2";

        MathExpression interpreted = new MathExpression(expr, false);
        int xSlot = interpreted.getVariable("x").getFrameIndex();

        double[] res = new double[2];
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            interpreted.updateSlot(xSlot, 2.5);
            res[0] = interpreted.solveGeneric().scalar;
        }

        double intDur = System.nanoTime() - start;

        MathExpression turbo = new MathExpression(expr, false);
        FastCompositeExpression compiled = turbo.compileTurbo();

        double[] vars = new double[3];
        vars[xSlot] = 2.5;

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
        System.out.println("values=" + Arrays.toString(res));
    }

    private static void benchmarkWithVariablesAdvanced() throws Throwable {
        System.out.println("\n=== WITH VARIABLES: ADVANCED; FOLDING OFF ===\n");

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
        System.out.println("values=" + Arrays.toString(res));
    }
    
     
    static public void testQuadratic() throws Throwable {//-1.8719243686213027618871370090528, -3.2052577019546360952204703423861
        System.out.println("\n=== QUADRATIC ROOTS: SIMPLE; ===\n");

        String expr = "quadratic(@(x)3*x^2-4*x-18)";

        MathExpression interpreted = new MathExpression(expr, false);

        double[] vars = new double[0];

        double[] v = interpreted.solveGeneric().vector;
        TurboExpressionEvaluator tee = TurboEvaluatorFactory.getCompiler(interpreted);
        FastCompositeExpression fce = tee.compile();
        double[] v1 = fce.apply(vars).vector;
        System.out.println("v = "+Arrays.toString(v));
        System.out.println("v1 = "+Arrays.toString(v1));
    }
    
        
    static public void testTartaglia() throws Throwable {//-1.8719243686213027618871370090528, -3.2052577019546360952204703423861
        System.out.println("\n=== TARTAGLIA ROOTS: SIMPLE; ===\n");

        String expr = "t_root(@(x)3*x^3-4*x-18)";

        MathExpression interpreted = new MathExpression(expr, false);

        double[] vars = new double[0];

        double[] v = interpreted.solveGeneric().vector;
        TurboExpressionEvaluator tee = TurboEvaluatorFactory.getCompiler(interpreted);
        FastCompositeExpression fce = tee.compile();
        double[] v1 = fce.apply(vars).vector;
        System.out.println("v = "+Arrays.toString(v));
        System.out.println("v1 = "+Arrays.toString(v1));
    }
   static public void testGeneralRoot() throws Throwable {//-1.8719243686213027618871370090528, -3.2052577019546360952204703423861
        System.out.println("\n=== GENERAL ROOTS: SIMPLE; ===\n");

        String expr = "root(@(x)3*x^3-4*x-18,2)";
          


        MathExpression interpreted = new MathExpression(expr, false);

        double[] vars = new double[0];

        double v = interpreted.solveGeneric().scalar;
        TurboExpressionEvaluator tee = TurboEvaluatorFactory.getCompiler(interpreted);
        FastCompositeExpression fce = tee.compile();
        double v1 = fce.applyScalar(vars);
        System.out.println("v = "+v);
        System.out.println("v1 = "+v1);
    }
    private static void benchmarkConstantFolding() throws Throwable {
        System.out.println("\n=== CONSTANT FOLDING; FOLDING OFF ===\n");

        String expr = "2^10 + 3^5 - 4! + sqrt(256)";

        MathExpression turbo = new MathExpression(expr, true); 
        FastCompositeExpression compiled = turbo.compileTurbo();//folding info will be picked up automatically!

        double[] vars = new double[0];
        double[]res=new double[1];
              long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
           res[0] = turbo.solveGeneric().scalar;
        }
         double interpretedDur = System.nanoTime() - start;
        System.out.println("res = "+res[0]);

         start = System.nanoTime();
        for (int i = 0; i < N; i++) {
           res[0] = compiled.applyScalar(vars);
        }
         double turboDur = System.nanoTime() - start;
        System.out.println("res = "+res[0]);

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("(All constants - folds to single value at compile time)%n");
        System.out.printf("Interpreted:     %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:     %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
    }
}
