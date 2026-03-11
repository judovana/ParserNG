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

/**
 *
 * @author GBEMIRO
 */
public class TurboJet {

    public static void main(String[] args) throws Throwable {
       // MathExpression expr = new MathExpression("sin(4)-cos(3*2^4)+(3+sin(2))/(cos(3)-2^2)");
        MathExpression expr = new MathExpression("sin(3*x)+cos(x+2*y)-4*x");
        expr.setWillFoldConstants(true);
        FastExpression turbo = expr.compileTurbo();

        // Move data outside to avoid allocation overhead
        double[] vars = {Math.PI / 2, 0, 4};
        double sink = 0; // "Sink" to prevent the JIT from dead-code eliminating the loop

        int batches = 1000;
        double N = 100000; // Increase N for better resolution

        for (int j = 0; j < batches; j++) {
            long start = System.nanoTime();
            for (int i = 0; i < N; i++) {
                // Pure evaluation
                sink = turbo.apply(vars);
            }
            long end = System.nanoTime();

            double dur = (end - start) / N;
            if (j % 100 == 0) { // Print less frequently to avoid IO interference
                System.out.printf("Batch %d - Result: %f | Speed: %.2f ns\n", j, sink, dur);
            }
        }
    }
}
