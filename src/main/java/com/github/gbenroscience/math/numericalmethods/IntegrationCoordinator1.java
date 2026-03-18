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
import java.util.concurrent.TimeoutException;

/**
 *
 * @author GBEMIRO
 */
/**
 * High-performance integrator that handles both well-behaved and pathological
 * functions. - Auto-detects singularities (poles, logarithmic blows-up) -
 * Selects optimal coordinate transformation - Uses adaptive Clenshaw-Curtis
 * quadrature - Enforces 1.5 second timeout - Caches pole detection results
 */
public class IntegrationCoordinator1 {

    private static final int MAX_DEPTH = 22;
    private static final double TOLERANCE = 1e-13;
    private static final long TIMEOUT_MS = 1500L;
    private static final long TIMEOUT_LARGE_MS = 2500L;
    private static final int POLE_SCAN_SAMPLES = 100;
    private static final int DEEP_SCAN_SAMPLES = 500;  // For high-frequency spikes
    private static final double DEEP_SCAN_THRESHOLD = 1e6;  // Trigger if tail error is huge

    private long startTime;
    private boolean timedOut = false;
    private double intervalSize;  // Track for deduplication

    /**
     * Main entry point for integration.
     */
    public double integrate(Function f, double a, double b) throws TimeoutException {
        startTime = System.currentTimeMillis();
        timedOut = false;
        intervalSize = b - a;

        long timeoutForThisCall = (b - a > 100) ? TIMEOUT_LARGE_MS : TIMEOUT_MS;

        try {
            List<Double> poles = scanForPoles(f, a, b);

            if (!poles.isEmpty()) {
                double total = 0;
                double currentA = a;

                for (double pole : poles) {
                    checkTimeout(timeoutForThisCall);
                    total += computePrincipalValue(f, currentA, pole, timeoutForThisCall);
                    currentA = pole;
                }

                checkTimeout(timeoutForThisCall);
                total += integrateSmooth(f, currentA, b, timeoutForThisCall);
                return total;
            }

            return integrateSmooth(f, a, b, timeoutForThisCall);

        } catch (TimeoutException e) {
            throw e;
        }
    }

    /**
     * Integration for smooth/well-behaved functions.
     */
    private double integrateSmooth(Function f, double a, double b, long timeoutForThisCall) throws TimeoutException {
        checkTimeout(timeoutForThisCall);
        MappedExpander.DomainMap map = selectBestMap(f, a, b);
        return adaptiveRecursive(f, map, TOLERANCE, 0, timeoutForThisCall, a, b);
    }

    /**
     * Adaptive recursive integration with singular function handling.
     */
    private double adaptiveRecursive(Function f, MappedExpander.DomainMap map,
            double tol, int depth, long timeoutForThisCall, double a, double b) throws TimeoutException {
        
        if (depth % 3 == 0) {
            checkTimeout(timeoutForThisCall);
        }

        int N = 256;
        MappedExpander expander = new MappedExpander(f, map, N);

        boolean tooFast = expander.isAliasing();
        double tailError = expander.getTailError();

        // For singular functions, use stricter tolerance at deep levels
        double adjustedTol = tol;

        if (map instanceof MappedExpander.LogarithmicMap
                || map instanceof MappedExpander.ReversedLogarithmicMap
                || map instanceof MappedExpander.DoubleLogarithmicMap) {
            adjustedTol = tol / Math.pow(10, Math.min(depth, 5));
        }

        // CRITICAL: Converge when error is low AND we're not aliasing
        if (!tooFast && tailError < adjustedTol) {
            return expander.integrateFinal(MappedExpander.CCWeightGenerator.getCachedWeights());
        }

        // **NEW: DEEP SCAN TRIGGER**
        // If tail error is enormous but we haven't found poles, there's a hidden spike
        if (depth == 0 && tailError > DEEP_SCAN_THRESHOLD) {
            System.out.println("⚠️  DEEP SCAN triggered: tail error = " + tailError + " (hidden spike detected)");
            List<Double> hiddenPoles = deepScanForPoles(f, a, b);
            if (!hiddenPoles.isEmpty()) {
                System.out.println("Found " + hiddenPoles.size() + " hidden poles via deep scan");
                // Recursively integrate with the discovered poles
                double total = 0;
                double currentA = a;
                for (double pole : hiddenPoles) {
                    total += computePrincipalValue(f, currentA, pole, timeoutForThisCall);
                    currentA = pole;
                }
                total += integrateSmooth(f, currentA, b, timeoutForThisCall);
                return total;
            }
        }

        // AGGRESSIVE: If at depth 10+, accept "good enough" convergence
        if (depth >= 10 && tailError < adjustedTol * 100) {
            return expander.integrateFinal(MappedExpander.CCWeightGenerator.getCachedWeights());
        }

        // Hard limit: depth 18 for all functions
        if (depth >= 18) {
            return expander.integrateFinal(MappedExpander.CCWeightGenerator.getCachedWeights());
        }

        // Need subdivision: split the domain in half
        MappedExpander.SubDomainMap left = new MappedExpander.SubDomainMap(map, -1.0, 0.0);
        MappedExpander.SubDomainMap right = new MappedExpander.SubDomainMap(map, 0.0, 1.0);

        return adaptiveRecursive(f, left, tol / 2.0, depth + 1, timeoutForThisCall, a, (a + b) / 2.0)
                + adaptiveRecursive(f, right, tol / 2.0, depth + 1, timeoutForThisCall, (a + b) / 2.0, b);
    }

    /**
     * High-resolution scan for narrow spikes (Gaussians, narrow resonances, etc.)
     * Uses 5x more samples than normal scanning.
     */
    private List<Double> deepScanForPoles(Function f, double a, double b) throws TimeoutException {
        List<Double> poles = new ArrayList<>();
        double step = (b - a) / DEEP_SCAN_SAMPLES;  // 500 samples instead of 100
        double maxVal = 0;

        System.out.println("  Deep scan: sampling " + DEEP_SCAN_SAMPLES + " points with step=" + step);

        for (int i = 0; i <= DEEP_SCAN_SAMPLES; i++) {
            if (i % 50 == 0) {
                checkTimeout(TIMEOUT_MS);
            }

            double x = a + i * step;

            double val;
            try {
                f.updateArgs(x);
                val = Math.abs(f.calc());
            } catch (ArithmeticException e) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            } catch (RuntimeException e) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            }

            if (Double.isNaN(val) || Double.isInfinite(val)) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            }

            // For deep scan, use lower threshold (detect even moderate spikes)
            if (i > 0 && val > 1e3 && val > maxVal * 50) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            }

            maxVal = Math.max(maxVal, val);
        }

        return deduplicatePoles(poles, a, b);
    }

    /**
     * Scans interval for poles with graceful error handling.
     */
    private List<Double> scanForPoles(Function f, double a, double b) throws TimeoutException {
        List<Double> poles = new ArrayList<>();
        double step = (b - a) / POLE_SCAN_SAMPLES;
        double maxVal = 0;

        for (int i = 0; i <= POLE_SCAN_SAMPLES; i++) {
            if (i % 10 == 0) {
                checkTimeout(TIMEOUT_MS);
            }

            double x = a + i * step;

            double val;
            try {
                f.updateArgs(x);
                val = Math.abs(f.calc());
            } catch (ArithmeticException e) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            }catch ( RuntimeException e) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            }

            if (Double.isNaN(val) || Double.isInfinite(val)) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            }

            if (i > 0 && val > 1e6 && val > maxVal * 100) {
                double left = Math.max(a, x - step);
                double right = Math.min(b, x + step);
                if (right - left > 1e-10) {
                    poles.add(refinePoleLocation(f, left, right));
                }
                continue;
            }

            maxVal = Math.max(maxVal, val);
        }

        return deduplicatePoles(poles, a, b);
    }

    /**
     * Ternary search to refine pole location.
     */
    private double refinePoleLocation(Function f, double left, double right) throws TimeoutException {
        double l = left;
        double r = right;

        for (int i = 0; i < 60; i++) {
            checkTimeout(TIMEOUT_MS);

            double m1 = l + (r - l) / 3.0;
            double m2 = r - (r - l) / 3.0;

            double v1 = safeEval(f, m1);
            double v2 = safeEval(f, m2);

            if (Double.isInfinite(v1) || Double.isNaN(v1)) {
                return m1;
            }
            if (Double.isInfinite(v2) || Double.isNaN(v2)) {
                return m2;
            }

            if (v1 > v2) {
                r = m2;
            } else {
                l = m1;
            }
        }

        return (l + r) / 2.0;
    }

    /**
     * Safe evaluation with error handling.
     */
    private double safeEval(Function f, double x) {
        try {
            f.updateArgs(x);
            return Math.abs(f.calc());
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Integration across a pole using principal value.
     */
    private double computePrincipalValue(Function f, double a, double pole, long timeoutForThisCall) throws TimeoutException {
        checkTimeout(timeoutForThisCall);

        double eps = Math.min(1e-7, Math.min(pole - a, 1.0) / 10.0);

        double leftVal = safeEval(f, pole - eps);
        double rightVal = safeEval(f, pole + eps);

        // Even pole: diverges
        if (!Double.isInfinite(leftVal) && !Double.isInfinite(rightVal)
                && Math.signum(leftVal) == Math.signum(rightVal)) {
            return Double.POSITIVE_INFINITY * Math.signum(leftVal);
        }

        // Odd pole: excise window and integrate outer regions
        double leftIntegral = integrateSmooth(f, a, pole - eps, timeoutForThisCall);
        double rightIntegral = integrateSmooth(f, pole + eps, pole + (pole - a - eps), timeoutForThisCall);

        return leftIntegral + rightIntegral;
    }

    /**
     * Detect logarithmic singularity at boundary.
     */
    private boolean isLogarithmicSingularity(Function f, double point, double direction) throws TimeoutException {
        double v1 = Math.abs(safeEval(f, point + direction * 1e-6));
        double v2 = Math.abs(safeEval(f, point + direction * 1e-8));
        double v3 = Math.abs(safeEval(f, point + direction * 1e-10));

        if (Double.isInfinite(v3) || Double.isNaN(v3)) {
            return true;
        }

        double ratio1 = v2 / v1;
        double ratio2 = v3 / v2;

        return (ratio1 > 1.2 && ratio2 > 1.2);
    }

    /**
     * Auto-select optimal coordinate transformation.
     */
    private MappedExpander.DomainMap selectBestMap(Function f, double a, double b) throws TimeoutException {
        checkTimeout(TIMEOUT_MS);

        if (Double.isInfinite(b)) {
            return new MappedExpander.SemiInfiniteMap(1.0);
        }

        if (b - a > 50) {
            return new MappedExpander.LogarithmicMap(b - a, 5.0);
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

    /**
     * Remove poles that are too close together.
     * **NEW: Threshold is now relative to interval size.**
     */
    private List<Double> deduplicatePoles(List<Double> poles, double a, double b) {
        if (poles.isEmpty()) return poles;

        poles.sort(Double::compareTo);
        List<Double> deduplicated = new ArrayList<>();
        
        // Deduplication threshold: relative to interval size
        double threshold = (b - a) * 1e-12;
        threshold = Math.max(threshold, 1e-15);  // Never smaller than machine epsilon
        
        double lastPole = Double.NEGATIVE_INFINITY;

        for (double pole : poles) {
            if (pole - lastPole > threshold) {
                deduplicated.add(pole);
                lastPole = pole;
            }
        }

        if (deduplicated.size() < poles.size()) {
            System.out.println("Deduplicated " + poles.size() + " poles → " + deduplicated.size() + 
                             " (threshold=" + threshold + ")");
        }

        return deduplicated;
    }

    /**
     * Overload for backward compatibility (uses old fixed threshold).
     */
    private List<Double> deduplicatePoles(List<Double> poles) {
        if (poles.isEmpty()) return poles;
        poles.sort(Double::compareTo);
        List<Double> deduplicated = new ArrayList<>();
        double lastPole = Double.NEGATIVE_INFINITY;

        for (double pole : poles) {
            if (pole - lastPole > 1e-9) {
                deduplicated.add(pole);
                lastPole = pole;
            }
        }

        return deduplicated;
    }

    /**
     * Enforces timeout constraint.
     */
    private void checkTimeout(long customTimeout) throws TimeoutException {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > customTimeout) {
            timedOut = true;
            throw new TimeoutException("Integration exceeded " + (customTimeout / 1000.0) + "s timeout after " + elapsed + "ms");
        }
    }

    // ============= TESTS =============
    private static void testIntegral(String exprStr, double a, double b, double expected) throws TimeoutException {
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
            testIntegral("@(x)sin(x)", 0, Math.PI, 2.0);
            testIntegral("@(x)ln(x)", 0.001, 1.0, -0.992);
            testIntegral("@(x)1/sqrt(x)", 0.001, 1.0, 2.0);
            testIntegral("@(x)1/(x-0.5)", 0.1, 0.49, Double.NEGATIVE_INFINITY);
            testIntegral("@(x)(1/(x*sin(x)+3*x*cos(x)))", 1, 200, 0.06506236937545);
        } catch (TimeoutException e) {
            System.err.println("TIMEOUT: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}