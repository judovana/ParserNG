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
package com.github.gbenroscience.parser.turbo;

import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.turbo.tools.FastCompositeExpression;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author GBEMIRO
 */
public class TurboJet {

    private static int[] randomData;

    static int dataLen = 0;

    static AtomicInteger cursor = new AtomicInteger();

    static {
        randomData = splitLongIntoDigits(System.currentTimeMillis());
        dataLen = randomData.length;
    }

    public static void main(String[] args) throws Throwable {
        //MathExpression expr = new MathExpression("sin(4)-cos(3*2^4)+(3+sin(2))/(cos(3)-2^2)");
        
        
         MathExpression statExpr = new MathExpression("listsum(2,3,4,5,1,8,9,2)");
         
        FastCompositeExpression statTurbo = statExpr.compileTurbo();
        System.out.println("sort(2,3,4,5,1,8,9,2): "+statTurbo.apply(new double[]{}));
        
         MathExpression expr = new MathExpression("sin(3*x)+cos(x+2*y)-4*z^2");
        expr.setWillFoldConstants(true);
        FastCompositeExpression turbo = expr.compileTurbo();

        // Move data outside to avoid allocation overhead
        double[] vars = new double[3];
        double sink = 0; // "Sink" to prevent the JIT from dead-code eliminating the loop

        int batches = 1000;
        double N = 1000000; // Increase N for better resolution

        System.out.println("TEST Turbo mode");
        for (int j = 0; j < batches; j++) {
            long start = System.nanoTime();
            for (int i = 0; i < N; i++) {
                double incr = cursor.getAndIncrement() % dataLen;
                vars[0] = randomData[(int) incr];
                vars[1] = randomData[(int) incr] - 0.1;
                vars[2] = randomData[(int) incr] - 0.2;

                // Pure evaluation
                sink = turbo.applyScalar(vars);
            }
            long end = System.nanoTime();

            double dur = (end - start) / N;
            if (j % 100 == 0) { // Print less frequently to avoid IO interference
                System.out.printf("Batch %d - Result: %f | Speed: %.2f ns\n", j, sink, dur);
            }
        }

        System.out.println("TEST Interpreter mode");
        for (int j = 0; j < batches; j++) {
            long start = System.nanoTime();
            int xSlot = expr.hasVariable("x") ? expr.getVariable("x").getFrameIndex(): -1;
            int ySlot =  expr.hasVariable("y") ? expr.getVariable("y").getFrameIndex(): -1;
            int zSlot =  expr.hasVariable("z") ? expr.getVariable("z").getFrameIndex(): -1;

            for (int i = 0; i < N; i++) {
                 double incr = cursor.getAndIncrement() % dataLen;
                vars[0] = randomData[(int) incr];
                vars[1] = randomData[(int) incr] - 0.1;
                vars[2] = randomData[(int) incr] - 0.2;
                expr.updateSlot(xSlot, vars[0]);
                expr.updateSlot(ySlot, vars[1]);
                expr.updateSlot(zSlot, vars[2]);
                // Pure evaluation
                sink = expr.solveGeneric().scalar;
            }
            long end = System.nanoTime();

            double dur = (end - start) / N;
            if (j % 100 == 0) { // Print less frequently to avoid IO interference
                System.out.printf("Batch %d - Result: %f | Speed: %.2f ns\n", j, sink, dur);
            }
        }
    }

    public final static int[] splitLongIntoDigits(long n) {
        if (n == 0) {
            return new int[]{0}; // Special case for zero
        }

        boolean isNegative = n < 0;
        long temp = Math.abs(n); // Work with the absolute value
        List<Integer> digitList = new ArrayList<>();

        while (temp > 0) {
            // Get the last digit using modulo 10
            digitList.add((int) (temp % 10));
            // Remove the last digit using integer division
            temp /= 10;
        }

        // The digits are in reverse order, so reverse the list
        Collections.reverse(digitList);

        // Convert the ArrayList<Integer> to a primitive int[] array
        int[] digits = new int[digitList.size()];
        for (int i = 0; i < digitList.size(); i++) {
            digits[i] = digitList.get(i);
        }

        // Handle the sign if necessary (e.g. if the original number was negative, you might need 
        // to handle the representation of the sign explicitly, depending on requirements)
        // For simply getting the sequence of digits, the absolute value is sufficient.
        return digits;
    }

}
