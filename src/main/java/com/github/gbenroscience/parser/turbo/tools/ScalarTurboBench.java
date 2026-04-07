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
import com.github.gbenroscience.parser.STRING;
import com.github.gbenroscience.util.FunctionManager;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author GBEMIRO Benchmarks for ScalarTurboCompiler vs Interpreted evaluation.
 * Tests basic arithmetic, trig functions, and complex expressions.
 */
public class ScalarTurboBench {

    private static final int N = 1000000;
    private static boolean useWidening = true;

    public static void main(String[] args) throws Throwable {
          String rpt = STRING.repeating("=", 80);
        System.out.println(rpt);
        System.out.println("SCALAR TURBO COMPILER BENCHMARKS");
        System.out.println(rpt);

        benchmarkExpressionRotor();
        benchmarkPointRotor();
        benchmarkDiffCalculus();
        benchmarkDiffCalculusAlgebraic();
        
        // runVariableStressTest();
        coldRun("x=3;3*x^2+4*x+5", true);
        coldRun("x=3;3*x^2+4*x+5", false);
        testQuadratic();
        testTartaglia();
        testGeneralRoot();
        benchmarkBasicArithmetic();
        benchmarkSum();
        benchmarkSort();
        benchmarkIntegralCalculus();
        benchmarkComplexIntegralCalculus();
        benchmarkPrinting();
        benchmarkTrigonometric();
        benchmarkComplexExpression(false);
        benchmarkComplexExpression(true);
        benchmarkWithVariablesSimple();
        benchmarkWithVariablesAdvanced();
        benchmarkConstantFolding();
        benchmarkUnaryOps();
        
    }

    private static FastCompositeExpression get(MathExpression me) throws Throwable {
        return new ScalarTurboEvaluator(me, useWidening).compile();
    }

    private static FastCompositeExpression get(MathExpression me, boolean useWidening) throws Throwable {
        return new ScalarTurboEvaluator(me, useWidening).compile();
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
        FastCompositeExpression compiled = get(interpreted);
        // Warm up turbo JIT
        double[] vars = new double[3];
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
        FastCompositeExpression compiled = get(interpreted);
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
        FastCompositeExpression compiled = get(interpreted);
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
        FastCompositeExpression compiled = get(interpreted, true);
        // Warm up turbo JIT
        double[] vars = new double[0];
        MathExpression.EvalResult evr = compiled.apply(vars);

        System.out.printf("Expression: %s%n", evr.scalar);
    }

    private static void benchmarkDiffCalculusAlgebraic() throws Throwable {
        System.out.println("\n=== DIFFERENTIAL CALCULUS; FOLDING OFF===\n");
        //String expr = "diff(@(x)cos(x)+sin(x),2,1)";
        String expr = "diff(@(x)(sin(x)+cos(x)), 1)";

        // Warm up JIT
        MathExpression interpreted = new MathExpression(expr, false);
        MathExpression.EvalResult ev = interpreted.solveGeneric();

        System.out.printf("Expression--interpreted: %s%n", ev.textRes);
        System.out.println("turbo: " + FunctionManager.lookUp(ev.textRes));

        // Compile to turbo
        FastCompositeExpression compiled = get(interpreted, true);
        // Warm up turbo JIT
        double[] vars = new double[1];
        MathExpression.EvalResult evr = compiled.apply(vars);

        System.out.printf("Expression--turbo: %s%n", evr.textRes);
        System.out.println("turbo: " + FunctionManager.lookUp(evr.textRes));
    }

    /**
     * Tests rotor action on an expression
     *
     * @throws Throwable
     */
    private static void benchmarkExpressionRotor() throws Throwable {

        System.out.println("\n=== EXPRESSION ROTOR; FOLDING OFF===\n");

        String expression = "z1=@(x,y)sin(x+y-3*x);rot(z1, pi, @(1,3)(1,0,1),@(1,3)(1,1,0))";
        MathExpression interpreted = new MathExpression(expression);
        MathExpression.EvalResult ev = interpreted.solveGeneric();

        System.out.printf("Expression: %s%n", expression);
        System.out.println("interpreted: " + ev.textRes);

        // Compile to turbo
        FastCompositeExpression compiled = get(interpreted, true);
        // Warm up turbo JIT
        double[] vars = new double[1];
        MathExpression.EvalResult evr = compiled.apply(vars);
        System.out.println("turbo: " + evr);
    }

    /**
     * Tests rotor action on an expression
     *
     * @throws Throwable
     */
    private static void benchmarkPointRotor() throws Throwable {

        System.out.println("\n=== POINT ROTOR; FOLDING OFF===\n");
        String expression = "p=@(1,3)(4,2,5);q=@(1,3)(12,3,-1);rot(p,q, pi, @(1,3)(1,0,1),@(1,3)(1,1,0))";
        MathExpression interpreted = new MathExpression(expression);

        MathExpression.EvalResult ev = interpreted.solveGeneric(); 
        System.out.printf("Expression: %s%n", expression);
        System.out.println("interpreted: " + ev);

        // Compile to turbo
        FastCompositeExpression compiled = get(interpreted, true);
        // Warm up turbo JIT
        double[] vars = new double[1];
        MathExpression.EvalResult evr = compiled.apply(vars);
        System.out.println("turbo: " + evr);
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
        FastCompositeExpression compiled = get(interpreted);

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
        FastCompositeExpression compiled = get(interpreted);
        // Warm up turbo JIT
        double[] vars = new double[0];
        MathExpression.EvalResult evr = compiled.apply(vars);
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
        FastCompositeExpression compiled = get(interpreted);
        // Warm up turbo JIT
        double[] vars = new double[0];
        MathExpression.EvalResult evr = compiled.apply(vars);
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
        FastCompositeExpression compiled = get(interpreted);

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
        FastCompositeExpression compiled = get(interpreted);

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
        FastCompositeExpression compiled = get(interpreted);

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

        double[] res = new double[3];
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            interpreted.updateSlot(xSlot, 2.5);
            interpreted.updateSlot(ySlot, 3.7);
            interpreted.updateSlot(zSlot, 1.2);
            res[0] = interpreted.solveGeneric().scalar;
        }

        double intDur = System.nanoTime() - start;

        MathExpression turbo = new MathExpression(expr, false);
        FastCompositeExpression compiled = get(interpreted);

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

        double[] vars = new double[1];

        double[] v = interpreted.solveGeneric().vector;
        FastCompositeExpression compiled = get(interpreted);
        MathExpression.EvalResult evv = compiled.apply(vars);
        double[] v1 = evv.vector; 
        System.out.println("v = " + Arrays.toString(v));
        System.out.println("v1 = " + Arrays.toString(v1));
    }

    static public void testTartaglia() throws Throwable {//-1.8719243686213027618871370090528, -3.2052577019546360952204703423861
        System.out.println("\n=== TARTAGLIA ROOTS: SIMPLE; ===\n");

        String expr = "t_root(@(x)3*x^3-4*x-18)";

        MathExpression interpreted = new MathExpression(expr, false);

        double[] vars = new double[0];

        double[] v = interpreted.solveGeneric().vector;
        FastCompositeExpression compiled = get(interpreted);
        double[] v1 = compiled.apply(vars).vector;
        System.out.println("v = " + Arrays.toString(v));
        System.out.println("v1 = " + Arrays.toString(v1));
    }

    static public void testGeneralRoot() throws Throwable {//-1.8719243686213027618871370090528, -3.2052577019546360952204703423861
        System.out.println("\n=== GENERAL ROOTS: SIMPLE; ===\n");

        String expr = "root(@(x)3*x^3-4*x-18,2)";

        MathExpression interpreted = new MathExpression(expr, false);

        double[] vars = new double[0];

        double v = interpreted.solveGeneric().scalar;
        FastCompositeExpression compiled = get(interpreted);
        double v1 = compiled.applyScalar(vars);
        System.out.println("v = " + v);
        System.out.println("v1 = " + v1);
    }

    private static void benchmarkConstantFolding() throws Throwable {
        System.out.println("\n=== CONSTANT FOLDING; FOLDING OFF ===\n");

        String expr = "2^10 + 3^5 - 4! + sqrt(256)";

        MathExpression turbo = new MathExpression(expr, true);
        FastCompositeExpression compiled = get(turbo);  //folding info will be picked up automatically!

        double[] vars = new double[0];
        double[] res = new double[1];
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            res[0] = turbo.solveGeneric().scalar;
        }
        double interpretedDur = System.nanoTime() - start;
        System.out.println("res = " + res[0]);

        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            res[0] = compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;
        System.out.println("res = " + res[0]);

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("(All constants - folds to single value at compile time)%n");
        System.out.printf("Interpreted:     %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:     %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
    }

    private static void benchmarkUnaryOps() throws Throwable {
        System.out.println("\n=== UNARY OPS; FOLDING OFF ===\n");

        String expr = "2²+3³+√9";//

        MathExpression turbo = new MathExpression(expr, false);

        double[] res = new double[1];
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            res[0] = turbo.solveGeneric().scalar;
        }
        double interpretedDur = System.nanoTime() - start;
        System.out.println("res = " + res[0]);

        FastCompositeExpression compiled = get(turbo);//folding info will be picked up automatically!

        double[] vars = new double[0];

        start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            res[0] = compiled.applyScalar(vars);
        }
        double turboDur = System.nanoTime() - start;
        System.out.println("res = " + res[0]);

        System.out.printf("Expression: %s%n", expr);
        System.out.printf("(All constants - folds to single value at compile time)%n");
        System.out.printf("Interpreted:     %.2f ns/op%n", interpretedDur / N);
        System.out.printf("Turbo:     %.2f ns/op%n", turboDur / N);
        System.out.printf("Speedup:     %.1fx%n", (double) interpretedDur / turboDur);
    }

    private static void coldRun(String expr, boolean normalMode) {
        int n = 0;
        MathExpression me = new MathExpression(expr);
        if (normalMode) {
            for (int i = 0; i < n; i++) {
                me.solveGeneric();
            }

            long start = System.nanoTime();
            double v = me.solveGeneric().scalar;
            double dur = System.nanoTime() - start;
            System.out.println("value = " + v + ", dur: " + dur + "ns");
        } else {
            try {
                FastCompositeExpression fce = get(me);
                double vars[] = new double[10];
                vars[0] = 3;
                for (int i = 0; i < n; i++) {
                    fce.applyScalar(vars);
                }

                long start = System.nanoTime();
                double v = fce.applyScalar(vars);
                double dur = System.nanoTime() - start;
                System.out.println("value = " + v + ", dur: " + dur + "ns");
            } catch (Throwable ex) {
                Logger.getLogger(ScalarTurboBench.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static void runVariableStressTest() throws Throwable {
 String rpt =  STRING.repeating("=", 40);

        System.out.println("\n" + rpt);
        System.out.println("FINDING THE CROSSOVER POINT (WIDENING VS ARRAY)");
        System.out.println(rpt);

        for (int varCount = 1; varCount <= 40; varCount++) {
            // Create a simple sum expression: x0 + x1 + x2 ... + xN
            StringBuilder sb = new StringBuilder("x0");
            for (int i = 1; i < varCount; i++) {
                sb.append("+x").append(i); 
            }
            String expr = sb.toString();
            MathExpression me = new MathExpression(expr, false);

            double[] vals = new double[varCount];
            Arrays.fill(vals, 1.0);
            // Benchmark Widening (Evaluator 2)
            FastCompositeExpression wide = new ScalarTurboEvaluator(me, true).compile();
            for (int i = 0; i < 20000; i++) {
                wide.applyScalar(vals); // Deep Warmup
            }
            long start = System.nanoTime();
            for (int i = 0; i < N; i++) {
                wide.applyScalar(vals);
            }
            double wideDur = (System.nanoTime() - start) / (double) N;

            // Benchmark Array (Evaluator 1)
            FastCompositeExpression array = new ScalarTurboEvaluator(me, false).compile();
            for (int i = 0; i < 20000; i++) {
                array.applyScalar(vals); // Deep Warmup
            }
            start = System.nanoTime();
            for (int i = 0; i < N; i++) {
                array.applyScalar(vals);
            }
            double arrayDur = (System.nanoTime() - start) / (double) N;

            String winner = wideDur < arrayDur ? "WIDENING" : "ARRAY";
            System.out.printf("Vars: %2d | Wide: %6.2f ns | Array: %6.2f ns | Winner: %s%n",
                    varCount, wideDur, arrayDur, winner);

            // If Array wins three times in a row, we've found the definitive threshold
            if (arrayDur < wideDur && varCount > 8) {
                // You can use this data to tune MIN_VAR_COUNT_FOR_ARRAY_BASED_EVALUATOR
            }
        }
    }
}
