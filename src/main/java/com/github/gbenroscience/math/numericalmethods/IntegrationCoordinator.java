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
package com.github.gbenroscience.math.numericalmethods;

import com.github.gbenroscience.parser.Function;

/**
 *
 * @author GBEMIRO
 */
public class IntegrationCoordinator {

    private static final int MAX_DEPTH = 25;
    private static final double TOLERANCE = 1e-14; // Near machine precision

    public double integrate(Function f, double a, double b) {
        // 1. Automatic Map Selection
        MappedExpander.DomainMap initialMap;
        if (Double.isInfinite(b)) {
            initialMap = new MappedExpander.SemiInfiniteMap(1.0);
        } else if (isLogarithmicSingularity(f, a, 1.0)) {
            initialMap = new MappedExpander.LogarithmicMap(b - a, 15.0); // Hardened sensitivity
        } else {
            initialMap = new MappedExpander.LinearMap(a, b);
        }

        // 2. Start Recursive Adaptive Engine
        return adaptiveRecursive(f, initialMap, TOLERANCE, 0);
    }

    private boolean isLogarithmicSingularity(Function f, double point, double direction) {
        double eps1 = 1e-7;
        double eps2 = 1e-8;
        double eps3 = 1e-9;

        f.updateArgs(point + direction * eps1);
        double v1 = Math.abs(f.calc());

        f.updateArgs(point + direction * eps2);
        double v2 = Math.abs(f.calc());

        f.updateArgs(point + direction * eps3);
        double v3 = Math.abs(f.calc());

        // 1. Check for immediate blow-up (Poles/NaNs)
        if (Double.isInfinite(v3) || Double.isNaN(v3)) {
            return true;
        }

        // 2. Log-Slope Test: For f(x) ~ x^-a, log(f(x)) is linear with log(x)
        // We check if the rate of growth is accelerating as we get closer.
        double ratio1 = v2 / v1;
        double ratio2 = v3 / v2;

        // If the function is growing by more than a certain factor (e.g., > 1.5x) 
        // over an order-of-magnitude step, it's "hard" for polynomials.
        return (ratio1 > 1.5 && ratio2 > 1.5);
    }
 

    private double adaptiveRecursive(Function f, MappedExpander.DomainMap map, double tol, int depth) {
        MappedExpander expander = new MappedExpander(f, map, 256);

        // Hardened Check: Is the function oscillating too fast for 256 points?
        boolean tooFast = expander.isAliasing();
        double currentError = expander.getTailError();

        // Only converge if the error is low AND we aren't aliasing
        if (!tooFast && (currentError < tol || depth >= MAX_DEPTH)) {
            return expander.integrateFinal(MappedExpander.CCWeightGenerator.getCachedWeights());
        }

        // Force subdivision to "zoom in" on the oscillations
        MappedExpander.SubDomainMap left = new MappedExpander.SubDomainMap(map, -1.0, 0.0);
        MappedExpander.SubDomainMap right = new MappedExpander.SubDomainMap(map, 0.0, 1.0);

        return adaptiveRecursive(f, left, tol / 2.0, depth + 1)
                + adaptiveRecursive(f, right, tol / 2.0, depth + 1);
    }

    public static void main(String[] args) {
        String expr = "@(x)(1/(x*sin(x)+3*x*cos(x)))";
        IntegrationCoordinator ic = new IntegrationCoordinator();
        double val = ic.integrate(new Function(expr), 1, 200);
        System.out.println("val = " + val);
    }

}
