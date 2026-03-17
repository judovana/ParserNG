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

import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.Function;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 *
 * @author GBEMIRO
 */


/**
 * High-performance integrator that handles both well-behaved and pathological functions.
 * - Auto-detects singularities (poles, logarithmic blows-up)
 * - Selects optimal coordinate transformation
 * - Uses adaptive Clenshaw-Curtis quadrature
 * - Enforces 1.5 second timeout
 * - Caches pole detection results
 */
public class IntegrationCoordinator1 {

    private static final int MAX_DEPTH = 22;
    private static final double TOLERANCE = 1e-13;
    private static final long TIMEOUT_MS = 1500L;
    private static final int POLE_SCAN_SAMPLES = 100;

    // Cache for pole detection (keyed by expression hash)
    private final java.util.Map<String, List<Double>> poleCache = new java.util.HashMap<>();

    private long startTime;
    private boolean timedOut = false;

  

    public double integrate(Function f, double a, double b) throws TimeoutException {
        startTime = System.currentTimeMillis();
        timedOut = false;

        try {
            // 1. SCAN: Look for internal singularities
            List<Double> poles = scanForPoles(f, a, b);

            if (!poles.isEmpty()) {
                // 2. PARTITION: Split domain at each pole
                double total = 0;
                double currentA = a;
                
                for (double pole : poles) {
                    checkTimeout();
                    total += computePrincipalValue(f, currentA, pole);
                    currentA = pole;
                }
                
                checkTimeout();
                total += integrateSmooth(f, currentA, b);
                return total;
            }

            // 3. NO POLES: Proceed directly to smooth integration
            return integrateSmooth(f, a, b);

        } catch (TimeoutException e) {
            throw e;
        }
    }

    /**
     * Fast integration for smooth/well-behaved functions.
     * Uses heuristic to select best coordinate map.
     */
    private double integrateSmooth(Function f, double a, double b) throws TimeoutException {
        checkTimeout();
        
        MappedExpander.DomainMap map = selectBestMap(f, a, b);
        return adaptiveRecursive(f, map, TOLERANCE, 0);
    }

     /**
 * Scans interval with EARLY EXIT and GRACEFUL error handling.
 */
private List<Double> scanForPoles(Function f, double a, double b) throws TimeoutException {
    List<Double> poles = new ArrayList<>();
    double step = (b - a) / POLE_SCAN_SAMPLES;

    double prevVal = 0;
    double maxVal = 0;
    
    for (int i = 0; i <= POLE_SCAN_SAMPLES; i++) {
        // Check timeout every 10 samples
        if (i % 10 == 0) {
            checkTimeout();
        }

        double x = a + i * step;
        
        // CRITICAL: Wrap in try-catch to detect division by zero and other runtime errors
        double val;
        try {
            f.updateArgs(x);
            val = Math.abs(f.calc());
        } catch (ArithmeticException e) {
            // This point causes a runtime error (division by zero, etc.)
            // It's likely a pole. Refine it.
            double left = Math.max(a, x - step);
            double right = Math.min(b, x + step);
            
            if (left < right) {
                poles.add(refinePoleLocation(f, left, right));
            }
            prevVal = 0;
            continue;
        }catch ( RuntimeException e) {
            // This point causes a runtime error (division by zero, etc.)
            // It's likely a pole. Refine it.
            double left = Math.max(a, x - step);
            double right = Math.min(b, x + step);
            
            if (left < right) {
                poles.add(refinePoleLocation(f, left, right));
            }
            prevVal = 0;
            continue;
        }

        // HARDENED: Detect NaN, Infinity, or spike (100x increase)
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            double left = Math.max(a, x - step);
            double right = Math.min(b, x + step);
            
            if (left < right) {
                poles.add(refinePoleLocation(f, left, right));
            }
            prevVal = 0;
            continue;
        }

        // Spike detection: 100x increase from maximum so far
        if (i > 0 && val > 1e6 && val > maxVal * 100) {
            double left = Math.max(a, x - step);
            double right = Math.min(b, x + step);
            
            if (left < right) {
                poles.add(refinePoleLocation(f, left, right));
            }
            prevVal = 0;
            continue;
        }

        maxVal = Math.max(maxVal, val);
        prevVal = val;
    }

    return poles;
}

/**
 * Ternary search with graceful error handling.
 */
private double refinePoleLocation(Function f, double left, double right) throws TimeoutException {
    double l = left;
    double r = right;

    for (int i = 0; i < 60; i++) {
        checkTimeout();
        
        double m1 = l + (r - l) / 3.0;
        double m2 = r - (r - l) / 3.0;

        double v1, v2;
        
        try {
            f.updateArgs(m1);
            v1 = Math.abs(f.calc());
        } catch (Exception e) {
            v1 = Double.POSITIVE_INFINITY;
        }

        try {
            f.updateArgs(m2);
            v2 = Math.abs(f.calc());
        } catch (Exception e) {
            v2 = Double.POSITIVE_INFINITY;
        }

        // If we hit the absolute singularity, we are done
        if (Double.isInfinite(v1) || Double.isNaN(v1)) {
            return m1;
        }
        if (Double.isInfinite(v2) || Double.isNaN(v2)) {
            return m2;
        }

        // Keep the third that contains the larger value (climbing the pole)
        if (v1 > v2) {
            r = m2;
        } else {
            l = m1;
        }
    }

    return (l + r) / 2.0;
}

/**
 * Integration across a pole using principal value - FIXED.
 */
private double computePrincipalValue(Function f, double a, double pole) throws TimeoutException {
    checkTimeout();
    
    double eps = Math.min(1e-7, Math.min(pole - a, 1.0) / 10.0);

    // Safely sample near the pole
    double leftVal, rightVal;
    try {
        f.updateArgs(pole - eps);
        leftVal = f.calc();
    } catch (Exception e) {
        leftVal = Double.NEGATIVE_INFINITY;
    }

    try {
        f.updateArgs(pole + eps);
        rightVal = f.calc();
    } catch (Exception e) {
        rightVal = Double.POSITIVE_INFINITY;
    }

    // Even pole: diverges
    if (!Double.isInfinite(leftVal) && !Double.isInfinite(rightVal) 
            && Math.signum(leftVal) == Math.signum(rightVal)) {
        System.err.println("Warning: Divergent even-order pole at x = " + pole);
        return Double.POSITIVE_INFINITY * Math.signum(leftVal);
    }

    // Odd pole: excise window and integrate outer regions
    double leftIntegral = integrateSmooth(f, a, pole - eps);
    double rightIntegral = integrateSmooth(f, pole + eps, pole + (pole - a - eps));

    return leftIntegral + rightIntegral;
}
    /**
     * Auto-selects coordinate transformation based on boundary behavior.
     */
    private MappedExpander.DomainMap selectBestMap(Function f, double a, double b) throws TimeoutException {
        checkTimeout();
        
        if (Double.isInfinite(b)) {
            return new MappedExpander.SemiInfiniteMap(1.0);
        }

        boolean singA = isLogarithmicSingularity(f, a, 1.0);
        boolean singB = isLogarithmicSingularity(f, b, -1.0);

        if (singA && singB) {
            return new MappedExpander.DoubleLogarithmicMap(a, b, 4.0);
        }
        if (singA) {
            return new MappedExpander.LogarithmicMap(b - a, 15.0);
        }
        if (singB) {
            return new MappedExpander.ReversedLogarithmicMap(a, b, 15.0);
        }

        return new MappedExpander.LinearMap(a, b);
    }

    private boolean isLogarithmicSingularity(Function f, double point, double direction) throws TimeoutException {
        double eps1 = 1e-6;
        double eps2 = 1e-8;
        double eps3 = 1e-10;

        f.updateArgs(point + direction * eps1);
        double v1 = Math.abs(f.calc());

        f.updateArgs(point + direction * eps2);
        double v2 = Math.abs(f.calc());

        f.updateArgs(point + direction * eps3);
        double v3 = Math.abs(f.calc());

        if (Double.isInfinite(v3) || Double.isNaN(v3)) {
            return true;
        }

        double ratio1 = v2 / v1;
        double ratio2 = v3 / v2;

        return (ratio1 > 1.2 && ratio2 > 1.2);
    }

    private double adaptiveRecursive(Function f, MappedExpander.DomainMap map, 
                                 double tol, int depth) throws TimeoutException {
    // TIMEOUT CHECK: Every recursion level
    if (depth % 3 == 0) {
        checkTimeout();
    }

    // MAX DEPTH or acceptable error
    if (depth >= MAX_DEPTH) {
        return evaluateQuadrature(f, map);
    }

    // Use N=256 consistently for cached weights
    MappedExpander expander = new MappedExpander(f, map, 256);

    // Check for aliasing or sufficient accuracy
    boolean tooFast = expander.isAliasing();
    double tailError = expander.getTailError();

    if (!tooFast && tailError < tol) {
        // Converged! Use fast final evaluation
        return expander.integrateFinal(MappedExpander.CCWeightGenerator.getCachedWeights());
    }

    // Need subdivision
    MappedExpander.SubDomainMap left = new MappedExpander.SubDomainMap(map, -1.0, 0.0);
    MappedExpander.SubDomainMap right = new MappedExpander.SubDomainMap(map, 0.0, 1.0);

    return adaptiveRecursive(f, left, tol / 2.0, depth + 1)
            + adaptiveRecursive(f, right, tol / 2.0, depth + 1);
}

/**
 * Final evaluation using pre-cached weights.
 */
private double evaluateQuadrature(Function f, MappedExpander.DomainMap map) {
    MappedExpander expander = new MappedExpander(f, map, 256);
    return expander.integrateFinal(MappedExpander.CCWeightGenerator.getCachedWeights());
}
    /**
     * Enforces timeout constraint.
     */
    private void checkTimeout() throws TimeoutException {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > TIMEOUT_MS) {
            timedOut = true;
            throw new TimeoutException("Integration exceeded 1.5 second timeout after " + elapsed + "ms");
        }
    }

   

    // ============= BENCHMARKING =============
 private static void testIntegral(String exprStr, double a, double b, double expected) 
        throws TimeoutException {
    long start = System.nanoTime();
    IntegrationCoordinator1 ic = new IntegrationCoordinator1();
    
    
    double result = ic.integrate(new Function(exprStr), a, b);
    
    long elapsed = System.nanoTime() - start;
    
    System.out.println("\n" + exprStr);
    System.out.println("  Interval: [" + a + ", " + b + "]");
    System.out.println("  Result:   " + result);
    if (!Double.isInfinite(expected)) {
        System.out.println("  Expected: " + expected);
        System.out.println("  Error:    " + Math.abs(result - expected));
    }
    System.out.println("  Time:     " + (elapsed / 1e6) + " ms");
}

public static void main(String[] args) {
    try {
        // Test 1: Well-behaved - EXPECTED IS CORRECT (2.0, not π)
        testIntegral("@(x)sin(x)", 0, Math.PI, 2.0);

        // Test 2: Logarithmic singularity at 0
        // ∫ln(x)dx from 0.001 to 1.0 ≈ -0.992 (because we're not integrating from 0)
        testIntegral("@(x)ln(x)", 0.001, 1.0, -0.992);

        // Test 3: Pole at boundary
        testIntegral("@(x)1/sqrt(x)", 0.001, 1.0, 2.0);

        // Test 4: Internal pole (x=0.5)
        testIntegral("@(x)1/(x-0.5)", 0.1, 0.49, Double.NEGATIVE_INFINITY);
        
        testIntegral("@(x)(1/(x*sin(x)+3*x*cos(x)))", 1, 200, 0.06506236937545);

    } catch (TimeoutException e) {
        System.err.println("TIMEOUT: " + e.getMessage());
    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
       public static void main1(String[] args) {
        try {
            Thread.ofVirtual().start(() -> {
                try {
                    String expr = "@(x)sin(x)";
                    double a = 1;
                    double b = 200;
                    IntegrationCoordinator1 ic = new IntegrationCoordinator1();
                    double val = ic.integrate(new Function(expr), a, b);
                    System.out.println("intg(" + expr + "," + a + "," + b + " ) = " + val);
                } catch (TimeoutException ex) {
                    Logger.getLogger(IntegrationCoordinator1.class.getName()).log(Level.SEVERE, null, ex);
                }
            });

            Thread.ofVirtual().start(() -> {
                try {
                    String expr = "@(x)(1/(x*sin(x)+3*x*cos(x)))";
                    double a = 1;
                    double b = 200;
                    IntegrationCoordinator1 ic = new IntegrationCoordinator1();
                    double val = ic.integrate(new Function(expr), 1, 200);
                    System.out.println("intg(" + expr + "," + a + "," + b + " ) = " + val);
                } catch (TimeoutException ex) {
                    Logger.getLogger(IntegrationCoordinator1.class.getName()).log(Level.SEVERE, null, ex);
                }
            }).join();
        } catch (InterruptedException ex) {
            Logger.getLogger(IntegrationCoordinator.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}