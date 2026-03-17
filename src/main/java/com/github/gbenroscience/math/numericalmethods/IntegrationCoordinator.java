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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author GBEMIRO
 */
public class IntegrationCoordinator {

    private static final int MAX_DEPTH = 25;
    private static final double TOLERANCE = 1e-14; // Near machine precision

    public double integrate(Function f, double a, double b) {
        // 1. SCAN: Look for internal singularities (denominator zeros)
        List<Double> poles = scanForPoles(f, a, b);

        if (!poles.isEmpty()) {
            // 2. PARTITION: If poles exist, split the domain at each pole
            double total = 0;
            double currentA = a;
            for (double pole : poles) {
                total += computePrincipalValue(f, currentA, pole, pole);
                currentA = pole;
            }
            total += integrate(f, currentA, b); // Final segment
            return total;
        }

        // 3. MAP SELECTION: Check boundaries for logarithmic behavior
        MappedExpander.DomainMap map = selectBestMap(f, a, b);

        // 4. EXECUTE: Run the adaptive engine
        return adaptiveRecursive(f, map, TOLERANCE, 0);
    }

    private List<Double> scanForPoles(Function f, double a, double b) {
        List<Double> poles = new ArrayList<>();
        int samples = 200; // Resolution of the scan
        double step = (b - a) / samples;

        double prevVal = 0;
        for (int i = 0; i <= samples; i++) {
            double x = a + i * step;
            f.updateArgs(x);
            double val = Math.abs(f.calc());

            // Check for NaN, Infinity, or a "Spike" (1000x increase from neighbors)
            if (Double.isNaN(val) || Double.isInfinite(val) || (i > 0 && val > 1e6 && val > prevVal * 100)) {
                // Use bisection to "pinpoint" the exact pole location
                poles.add(refinePoleLocation(f, x - step, x));
            }
            prevVal = val;
        }
        return poles;
    }

    private MappedExpander.DomainMap selectBestMap(Function f, double a, double b) {
        if (Double.isInfinite(b)) {
            return new MappedExpander.SemiInfiniteMap(1.0);
        }

        boolean singA = isLogarithmicSingularity(f, a, 1.0);
        boolean singB = isLogarithmicSingularity(f, b, -1.0);

        if (singA && singB) {
            return new MappedExpander.DoubleLogarithmicMap(a, b, 4.0); // Needs both ends stretched
        }
        if (singA) {
            return new MappedExpander.LogarithmicMap(b - a, 15.0);
        }
        if (singB) {
            return new MappedExpander.ReversedLogarithmicMap(a, b, 15.0);
        }

        return new MappedExpander.LinearMap(a, b);
    }

    /**
 * Uses a Ternary Search to find the exact x-coordinate of a pole.
 * It narrows down the interval by evaluating the magnitude of the function,
 * always keeping the segment that contains the highest "spike".
 */
private double refinePoleLocation(Function f, double left, double right) {
    double l = left;
    double r = right;

    // 60 iterations of ternary search provides roughly 1e-16 precision
    for (int i = 0; i < 60; i++) {
        // Divide the interval into thirds
        double m1 = l + (r - l) / 3.0;
        double m2 = r - (r - l) / 3.0;
        
        f.updateArgs(m1);
        double v1 = Math.abs(f.calc());
        
        f.updateArgs(m2);
        double v2 = Math.abs(f.calc());
        
        // If we hit the absolute singularity, we are done
        if (Double.isInfinite(v1) || Double.isNaN(v1)) return m1;
        if (Double.isInfinite(v2) || Double.isNaN(v2)) return m2;
        
        // Keep the third that contains the larger value (climbing the pole)
        if (v1 > v2) {
            r = m2; 
        } else {
            l = m1; 
        }
    }
    
    // Return the midpoint of the microscopic interval
    return (l + r) / 2.0;
}

    /**
     * Handles integration across an internal pole by excising a tiny, symmetric
     * window around the singularity and checking for odd/even divergence.
     */
    private double computePrincipalValue(Function f, double a, double b, double pole) {
        // Create a tiny symmetric window around the pole. 
        // Ensure epsilon doesn't accidentally overshoot the boundaries a or b.
        double eps = Math.min(1e-7, Math.min(pole - a, b - pole) / 10.0);

        f.updateArgs(pole - eps);
        double leftVal = f.calc();

        f.updateArgs(pole + eps);
        double rightVal = f.calc();

        // Check if the pole is "Even" (e.g., 1/x^2). 
        // If both sides share the same sign, they don't cancel out. The integral diverges.
        if (Math.signum(leftVal) == Math.signum(rightVal)) {
            // You can either throw an exception or return Infinity. 
            // Returning Infinity is usually safer for mathematical engines.
            System.err.println("Warning: Divergent even-order pole detected at x = " + pole);
            return Double.POSITIVE_INFINITY * Math.signum(leftVal);
        }

        // It is an "Odd" pole (e.g., 1/x). The region [pole - eps, pole + eps] cancels 
        // itself out to exactly 0.0. We just integrate the remaining outer regions.
        double leftIntegral = integrate(f, a, pole - eps);
        double rightIntegral = integrate(f, pole + eps, b);

        return leftIntegral + rightIntegral;
    }

    /**
     * Enhanced heuristic to detect near-singular behavior. direction: 1.0 for
     * right-side (a+), -1.0 for left-side (b-)
     */
    private boolean isLogarithmicSingularity(Function f, double point, double direction) {
        // Sample points at decreasing logarithmic distances from the boundary
        double eps1 = 1e-6;
        double eps2 = 1e-8;
        double eps3 = 1e-10;

        f.updateArgs(point + direction * eps1);
        double v1 = Math.abs(f.calc());

        f.updateArgs(point + direction * eps2);
        double v2 = Math.abs(f.calc());

        f.updateArgs(point + direction * eps3);
        double v3 = Math.abs(f.calc());

        // Check for immediate blow-up or invalid values
        if (Double.isInfinite(v3) || Double.isNaN(v3)) {
            return true;
        }

        // Ratio test: If the function grows by more than 2x as we get 100x closer,
        // the gradient is likely too steep for standard polynomial approximation.
        double ratio1 = v2 / v1;
        double ratio2 = v3 / v2;

        return (ratio1 > 1.2 && ratio2 > 1.2);
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

    /**
     * Scans the interval for internal singularities or poles. Returns the
     * location of the pole, or Double.NaN if none are found.
     */
    private double findInternalSingularity(Function f, double a, double b) {
        int samples = 100; // Coarse scan
        double step = (b - a) / samples;

        double maxVal = 0;
        double poleCandidate = Double.NaN;

        for (int i = 1; i < samples; i++) {
            double x = a + i * step;
            f.updateArgs(x);
            double y = Math.abs(f.calc());

            // 1. Immediate detection of hard poles
            if (Double.isInfinite(y) || Double.isNaN(y)) {
                return x;
            }

            // 2. Detection of "spikes" (Numerical poles)
            // If the value at x is 1000x larger than the average magnitude 
            // seen so far, it's likely a singularity the adaptive engine will struggle with.
            if (i > 1 && y > 1e6 && y > maxVal * 100) {
                poleCandidate = x;
            }
            maxVal = Math.max(maxVal, y);
        }

        return poleCandidate;
    }

    public static void main(String[] args) {
        try {
            Thread.ofVirtual().start(() -> {
                String expr = "@(x)sin(x)";
                double a = 1;
                double b = 200;
                IntegrationCoordinator ic = new IntegrationCoordinator();
                double val = ic.integrate(new Function(expr), a, b);
                System.out.println("intg(" + expr + "," + a + "," + b + " ) = " + val);
            });

            Thread.ofVirtual().start(() -> {
                String expr = "@(x)(1/(x*sin(x)+3*x*cos(x)))";
                double a = 1;
                double b = 200;
                IntegrationCoordinator ic = new IntegrationCoordinator();
                double val = ic.integrate(new Function(expr), 1, 200);
                System.out.println("intg(" + expr + "," + a + "," + b + " ) = " + val);
            }).join();
        } catch (InterruptedException ex) {
            Logger.getLogger(IntegrationCoordinator.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
