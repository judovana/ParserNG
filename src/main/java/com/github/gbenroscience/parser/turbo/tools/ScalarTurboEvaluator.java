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

import com.github.gbenroscience.math.Maths;
import com.github.gbenroscience.math.differentialcalculus.Derivative;
import com.github.gbenroscience.math.numericalmethods.NumericalIntegrator;
import com.github.gbenroscience.math.numericalmethods.TurboRootFinder;
import com.github.gbenroscience.math.quadratic.QuadraticSolver;
import com.github.gbenroscience.math.quadratic.Quadratic_Equation;
import com.github.gbenroscience.math.tartaglia.Tartaglia_Equation;
import com.github.gbenroscience.parser.Bracket;
import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.TYPE;
import static com.github.gbenroscience.parser.TYPE.ALGEBRAIC_EXPRESSION;
import static com.github.gbenroscience.parser.TYPE.MATRIX;
import com.github.gbenroscience.parser.Variable;
import com.github.gbenroscience.parser.methods.Declarations;
import com.github.gbenroscience.parser.methods.Method;
import com.github.gbenroscience.parser.methods.MethodRegistry;
import com.github.gbenroscience.util.FunctionManager;
import com.github.gbenroscience.util.VariableManager;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Turbo compiler optimized for PURE SCALAR expressions.
 *
 * Compiles mathematical expressions to native bytecode using MethodHandles and
 * LambdaMetafactory. First call takes ~5-10ms, subsequent calls return cached
 * version. Runtime performance: ~5-10 ns/op (vs 55+ ns/op interpreted).
 *
 * This is the refactored version of TurboCompiler that returns
 * FastCompositeExpression for compatibility with the matrix turbo compiler.
 *
 * Key optimizations: - MethodHandle call chains for zero-copy execution -
 * Constant folding at compile time - Direct array access for variables -
 * Inlined arithmetic operations - Degree/Radian conversion at compile time
 *
 * Performance targets: - First compilation: ~5-10 ms - Per-evaluation (cached):
 * ~5-10 ns - Scalar vs Interpreted: ~5-10x faster
 *
 * @author GBEMIRO
 */
public class ScalarTurboEvaluator implements TurboExpressionEvaluator {

    public static final MethodHandle SCALAR_GATEKEEPER_HANDLE;
    public static final MethodHandle VECTOR_GATEKEEPER_HANDLE;
    public static final MethodHandle VECTOR_2_GATEKEEPER_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // Define the method types: (String, double[]) -> double/double[]
            MethodType scalarType = MethodType.methodType(double.class, String.class, double[].class);
            MethodType vectorType = MethodType.methodType(double[].class, String.class, double[].class);
            MethodType vector2Type = MethodType.methodType(double[].class, String.class, String.class);

            /**
             * For non stats methods that return a double array
             */
            SCALAR_GATEKEEPER_HANDLE = lookup.findStatic(ScalarTurboEvaluator.class, "scalarStatsGatekeeper", scalarType);

            /**
             * For stats methods that return a double array
             */
            VECTOR_GATEKEEPER_HANDLE = lookup.findStatic(ScalarTurboEvaluator.class, "vectorStatsGatekeeper", vectorType);

            /**
             * For non stats methods that return a double array
             */
            VECTOR_2_GATEKEEPER_HANDLE = lookup.findStatic(ScalarTurboEvaluator.class, "vectorNonStatsGatekeeper", vector2Type);
//public static double[] vectorNonStatsGatekeeper(String method, String funcHandle)
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError("Failed to initialize Stats Gatekeepers: " + e.getMessage());
        }
    }

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // Common method types
    private static final MethodType MT_DOUBLE_D = MethodType.methodType(double.class, double.class);
    private static final MethodType MT_DOUBLE_DD = MethodType.methodType(double.class, double.class, double.class);
    private static final MethodType MT_SAFE_WRAP = MethodType.methodType(double.class, double[].class);

    // 1. ThreadLocal holding a reusable array of EvalResults to avoid GC pressure
    private static final ThreadLocal<MathExpression.EvalResult[]> WRAPPER_CACHE
            = ThreadLocal.withInitial(() -> {
                MathExpression.EvalResult[] arr = new MathExpression.EvalResult[8];
                for (int i = 0; i < 8; i++) {
                    arr[i] = new MathExpression.EvalResult();
                }
                return arr;
            });

    private MathExpression.Token[] postfix;

    private static final Set<String> FAST_PATH_METHODS = new HashSet<>(Arrays.asList(
            "acsc_deg",
            "sech", "tan-¹_rad", "acot_rad",
            "cos-¹_deg", "asin_grad", "sqrt", "acos_grad", "acot_deg",
            "cube", "exp", "ln-¹", "asec_rad", "asec_deg",
            "acosh", "diff", "sec_grad", "ceil",
            "csc_rad", "tanh-¹", "comb", "tan-¹_deg", "acsch", "acot_grad",
            "sec_rad", "fact", "acoth", "atanh",
            "log", "tan_grad", "tan-¹_grad",
            "st_err", "coth-¹", "min", "log-¹",
            "cot-¹_grad", "sech-¹", "pow",
            "csc_deg", "cos-¹_rad", "tan_rad", "max",
            "sin-¹_deg", "intg", "cot-¹_deg",
            "alog", "acsc_rad", "abs",
            "sin-¹_rad", "tan_deg", "lg", "sec_deg", "atan_deg", "ln",
            "sinh-¹", "asin_rad", "acos_deg", "cov", "mode",
            "atan_rad", "asech", "cos_grad", "cot-¹_rad", "asec_grad",
            "s_d", "acos_rad", "alg", "aln",
            "sinh", "cos_rad", "rnd", "rng",
            "t_root", "acsc_grad", "square", "csch-¹",
            "sec-¹_grad", "asin_deg", "cos_deg", "perm", "csc_grad",
            "sec-¹_deg", "sin_deg", "sin-¹_grad", "cot_deg",
            "coth", "cbrt", "sec-¹_rad", "tanh",
            "cos-¹_grad", "lg-¹", "plot", "root",
            "cot_rad", "atan_grad", "sin_grad", "cot_grad",
            "csc-¹_grad", "length", "csc-¹_deg", "cosh-¹", "cosh",
            "csc-¹_rad", "sin_rad", "csch", "asinh", "now", "nanos"
    

    ///////////////////////////////////////////////////////

    ));

    public ScalarTurboEvaluator(MathExpression.Token[] postfix) {
        this.postfix = postfix;
    }

    /**
     * Hardened production bridge. Zero allocation for arity <= 8. Safely scales
     * for any arity without crashing.
     */
    public static double invokeRegistryMethod(int methodId, double[] argsValues) {
        MathExpression.EvalResult[] wrappers = WRAPPER_CACHE.get();
        int arity = argsValues.length;

        // DEFENSIVE: If we hit a rare function with > 8 arguments, 
        // expand the cache for this specific thread instead of crashing.
        if (arity > wrappers.length) {
            int newSize = Math.max(arity, wrappers.length * 2);
            MathExpression.EvalResult[] newWrappers = new MathExpression.EvalResult[newSize];
            // Copy existing objects to avoid re-initializing everything
            System.arraycopy(wrappers, 0, newWrappers, 0, wrappers.length);
            for (int i = wrappers.length; i < newSize; i++) {
                newWrappers[i] = new MathExpression.EvalResult();
            }
            wrappers = newWrappers;
            WRAPPER_CACHE.set(wrappers);
        }

        // Map primitives to the cached objects
        for (int i = 0; i < arity; i++) {
            wrappers[i].wrap(argsValues[i]);
        }

        // Use a fresh result container (lightweight object) to ensure 
        // thread-local results don't leak between calls.
        MathExpression.EvalResult resultContainer = new MathExpression.EvalResult();

        return MethodRegistry.getAction(methodId)
                .calc(resultContainer, arity, wrappers).scalar;
    }

    /**
     * Compile scalar expression to EvalResult-wrapped bytecode.
     *
     * The compilation process: 1. Build MethodHandle chain from postfix tokens
     * 2. Each handle transforms (double[]) -> double 3. Binary ops: combine
     * left & right, permute args 4. Wrap result in EvalResult.wrap(scalar)
     *
     * @return A FastCompositeExpression that returns wrapped scalar
     * @throws Throwable if compilation fails
     *
     * @Override public FastCompositeExpression compile() throws Throwable { //
     * This now yields a handle with signature (double[])Object MethodHandle
     * scalarHandle = compileScalar(postfix);
     *
     * return new FastCompositeExpression() {
     * @Override public MathExpression.EvalResult apply(double[] variables) {
     * try { // invoke() now returns Double (boxed) or double[] Object result =
     * scalarHandle.invoke(variables);
     *
     * if (result instanceof double[]) { return new
     * MathExpression.EvalResult().wrap((double[]) result); } return new
     * MathExpression.EvalResult().wrap(((Number) result).doubleValue()); }
     * catch (Throwable t) { throw new RuntimeException("Turbo evaluation
     * failed", t); } }
     *
     * @Override public double applyScalar(double[] variables) { try { Object
     * result = scalarHandle.invoke(variables);
     *
     * if (result instanceof Number) { return ((Number) result).doubleValue(); }
     * // Coercion: If the user calls a vector function in a scalar context, //
     * we return the first element to prevent a crash. double[] arr = (double[])
     * result; return (arr != null && arr.length > 0) ? arr[0] : Double.NaN; }
     * catch (Throwable t) { throw new RuntimeException("Turbo primitive
     * execution failed", t); } } }; }
     */
    @Override
    public FastCompositeExpression compile() throws Throwable {
        // The base handle as defined by your compiler (returns Object)
        MethodHandle genericHandle = compileScalar(postfix);

        // Specialized handle for scalar path: (double[])double
        // This forces the JIT to see the primitive return path
        MethodHandle scalarPrimitiveHandle = genericHandle.asType(
                MethodType.methodType(double.class, double[].class));

        return new FastCompositeExpression() {
            @Override
            public MathExpression.EvalResult apply(double[] variables) {
                try {
                    // Keep Object for the general/vector path
                    Object result = genericHandle.invokeExact(variables);
                    if (result instanceof double[]) {
                        return new MathExpression.EvalResult().wrap((double[]) result);
                    }
                    return new MathExpression.EvalResult().wrap((double) result);
                } catch (Throwable t) {
                    throw new RuntimeException("Turbo evaluation failed", t);
                }
            }

            @Override
            public double applyScalar(double[] variables) {
                try {
                    // Use invokeExact on the specialized primitive handle
                    // This will result in ZERO allocations
                    return (double) scalarPrimitiveHandle.invokeExact(variables);
                } catch (ClassCastException cce) {
                    // Fallback for cases where the result is actually a double[] 
                    // but called in a scalar context
                    try {
                        double[] arr = (double[]) genericHandle.invokeExact(variables);
                        return (arr != null && arr.length > 0) ? arr[0] : Double.NaN;
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                } catch (Throwable t) {
                    throw new RuntimeException("Turbo primitive execution failed", t);
                }
            }
        };
    }

    private static boolean isIntrinsic(String name, int arity) {
        // Only arity 1 and 2 are candidates for primitive MethodHandle optimization
        if (arity < 1 || arity > 2) {
            return false;
        }

        String lowerName = name.toLowerCase();

        // Check if the full name (e.g., "sin_deg") is in the fast path
        if (FAST_PATH_METHODS.contains(lowerName)) {
            return true;
        }

        // Check if the base name (e.g., "sin") is in the fast path
        if (lowerName.contains("_")) {
            String base = lowerName.split("_")[0];
            return FAST_PATH_METHODS.contains(base);
        }

        return false;
    }

    /**
     * Internal: Compile to raw scalar MethodHandle (double[] -> double).
     */
    private static MethodHandle compileScalar(MathExpression.Token[] postfix) throws Throwable {
        Stack<MethodHandle> stack = new Stack<>();
        for (MathExpression.Token t : postfix) {
            switch (t.kind) {
               /* case MathExpression.Token.NUMBER:
                    if (t.name != null && !t.name.isEmpty()) {
                        // Variable: load from array at frameIndex
                        int frameIndex = t.frameIndex;

                        // 1. Get the JVM-intrinsic array element getter for double[]
                        // Signature: (double[], int) -> double
                        MethodHandle arrayGetter = MethodHandles.arrayElementGetter(double[].class);

                        // 2. BAKE the index into the handle (Currying)
                        // Transforms (double[], int) -> double INTO (double[]) -> double
                        MethodHandle directVarHandle = MethodHandles.insertArguments(arrayGetter, 1, frameIndex);

                        stack.push(directVarHandle);

                    } else {
                        // Constant value: (double[]) -> double (ignoring the array)
                        MethodHandle constant = MethodHandles.constant(double.class, t.value);
                        stack.push(MethodHandles.dropArguments(constant, 0, double[].class));
                    }
                    break;
                */
                case MathExpression.Token.NUMBER://public static double getVar(double[] vars, int index)
                    if (t.name != null && !t.name.isEmpty()) {
                        // Variable: load from array at frameIndex
                        int frameIndex = t.frameIndex;
                        // 1. Get the base method handle for our helper
                        MethodHandle getter = LOOKUP.findStatic(ScalarTurboEvaluator.class, "getVar",
                                MethodType.methodType(double.class, double[].class, int.class));

                        // 2. BAKE the index into the handle (Currying)
                        // This transforms (double[], int) -> double into (double[]) -> double
                        MethodHandle directVarHandle = MethodHandles.insertArguments(getter, 1, frameIndex);

                        stack.push(directVarHandle);

                    } else {
                        // Constant value: (double[]) -> double (ignoring the array)
                        MethodHandle constant = MethodHandles.constant(double.class, t.value);
                        stack.push(MethodHandles.dropArguments(constant, 0, double[].class));
                    }
                    break;

                case MathExpression.Token.OPERATOR:
                    if (t.isPostfix) {
                        MethodHandle operand = stack.pop();
                        stack.push(applyUnaryOp(t.opChar, operand));
                    } else {
                        MethodHandle right = stack.pop();
                        MethodHandle left = stack.pop();
                        stack.push(applyBinaryOp(t.opChar, left, right));
                    }
                    break;

                case MathExpression.Token.FUNCTION:
                case MathExpression.Token.METHOD:
                    String name = t.name.toLowerCase();
                    if (Method.isPureStatsMethod(name)) {
                        int arity = t.arity;
                        String[] rawArgs = t.getRawArgs();
                        double[] data = new double[arity];

                        for (int i = 0; i < arity; i++) {
                            stack.pop();
                            data[i] = Double.parseDouble(rawArgs[i]);
                        }
 
                        MethodHandle finalOp;
                        if (name.equals(Declarations.SORT) || name.equals(Declarations.MODE)) {
                            finalOp = MethodHandles.insertArguments(VECTOR_GATEKEEPER_HANDLE, 0, name, data);
                        } else {
                            finalOp = MethodHandles.insertArguments(SCALAR_GATEKEEPER_HANDLE, 0, name, data);
                        }

// CRITICAL: You must change the return type to Object.class.
// This prevents the unboxing logic from being triggered at the call site.
                        finalOp = finalOp.asType(finalOp.type().changeReturnType(Object.class));

// Now add the variables parameter: (double[]) -> Object
                        finalOp = MethodHandles.dropArguments(finalOp, 0, double[].class);

                        stack.push(finalOp);
                        break;
                    } else if (name.equals(Declarations.QUADRATIC) || name.equals(Declarations.TARTAGLIA_ROOTS)) {
                        int arity = t.arity;
                        String[] rawArgs = t.getRawArgs();
                        stack.pop();

                        MethodHandle finalOp = MethodHandles.insertArguments(VECTOR_2_GATEKEEPER_HANDLE, 0, name, rawArgs[0]);

// CRITICAL: You must change the return type to Object.class.
// This prevents the unboxing logic from being triggered at the call site.
                        finalOp = finalOp.asType(finalOp.type().changeReturnType(Object.class));

// Now add the variables parameter: (double[]) -> Object
                        finalOp = MethodHandles.dropArguments(finalOp, 0, double[].class);

                        stack.push(finalOp);
                        break;
                    } else if (name.equals(Declarations.GENERAL_ROOT)) {
                        int arity = t.arity;
                        for (int i = 0; i < arity; i++) {
                            stack.pop();
                        }
                        // The array contains [fName|expr, x1, x2, iterations] N.B functionBody is usally an anonymous function name or a defined function name
                        String[] args = t.getRawArgs();
                        if (args.length != arity) {
                            throw new RuntimeException("Invalid input. Expression did not pass token compiler phase");
                        }
                        if (args.length > 4) {
                            throw new RuntimeException("Invalid input. Argument count for general root is invalid. Expected: <=4 Found " + args.length);
                        }

                        String fNameOrExpr = args[0];
                        Function f = Variable.isVariableString(fNameOrExpr) ? FunctionManager.lookUp(fNameOrExpr) : FunctionManager.add(fNameOrExpr);

                        String varName = f.getIndependentVariables().get(0).getName();
                        double lower = args.length > 1 ? Double.parseDouble(args[1]) : -2;
                        double upper = args.length > 2 ? Double.parseDouble(args[2]) : 2;
                        int iterations = args.length > 3 ? Integer.parseInt(args[3]) : TurboRootFinder.DEFAULT_ITERATIONS;

                        // 1. Recursive compilation of the target function body
                        MathExpression innerExpr = f.getMathExpression();
                        int xSlot = innerExpr.getVariable(varName).getFrameIndex();
                        // Compiles the body to its own MethodHandle tree
                        MethodHandle targetHandle = compileScalar(innerExpr.getCachedPostfix());

                        // 2. Symbolic derivative for Newtonian acceleration
                        MethodHandle derivHandle = null;
                        try {
                            String diffExpr = "diff(" + fNameOrExpr + ",1)";
                            String derivString = Derivative.eval(diffExpr).textRes;
                            derivHandle = compileScalar(FunctionManager.lookUp(derivString).getMathExpression().getCachedPostfix());
                        } catch (Exception e) {
                            e.printStackTrace();
                            derivHandle = null;
                        }

                        // 3. Bind the execution bridge
                        // Signature: (MethodHandle, MethodHandle, int, double, double) -> double
                        MethodHandle bridge = LOOKUP.findStatic(ScalarTurboEvaluator.class, "executeTurboRoot",
                                MethodType.methodType(double.class, MethodHandle.class, MethodHandle.class,
                                        int.class, double.class, double.class, int.class));

                        // 4. Curry the arguments into a single operation handle
                        MethodHandle currentHandle = MethodHandles.insertArguments(bridge, 0,
                                targetHandle, derivHandle, xSlot, lower, upper, iterations);

                        // 5. Adapt the handle to accept the standard (double[]) input frame
                        currentHandle = MethodHandles.dropArguments(currentHandle, 0, double[].class);
//double executeTurboRoot(MethodHandle baseHandle, MethodHandle derivHandle, int xSlot, double lower, double upper, int iterations)
                        // The handle is now ready to be pushed to the compiler's compilation stack
                        stack.push(currentHandle);
                        break;
                    } else if (name.equals("print")) {
                        int arity = t.arity;

                        // 1. Pop args from stack to maintain RPN integrity
                        for (int i = 0; i < arity; i++) {
                            stack.pop();
                        }

                        // 2. Retrieve the raw arguments (variable names/constants)
                        String[] rawArgs = t.getRawArgs();

                        if (rawArgs == null || rawArgs.length != arity) {
                            throw new RuntimeException("Compile Error: Print arity mismatch.");
                        }

                        try {
                            // 3. Resolve the bridge method: matches double executePrint(String[])
                            MethodHandle bridge = LOOKUP.findStatic(ScalarTurboEvaluator.class, "executePrint",
                                    MethodType.methodType(double.class, String[].class));

                            // 4. Bind the String array as the ONLY argument (index 0)
                            // We cast rawArgs to Object so insertArguments treats the array as a single value
                            MethodHandle finalPrintHandle = MethodHandles.insertArguments(bridge, 0, (Object) rawArgs);

                            // 5. Adapt to Turbo signature: double(double[])
                            // The double[] input is ignored since executePrint uses lookups
                            stack.push(MethodHandles.dropArguments(finalPrintHandle, 0, double[].class));

                        } catch (Exception e) {
                            // Log the actual cause to help debugging
                            throw new RuntimeException("Failed to bind print handle: " + e.getMessage(), e);
                        }
                        break;
                    } else if (name.equals("intg")) {
                        //[F, 2.0, 3.0, 10000]
                        int arity = t.arity;
                        // Pop args from stack (RPN order)
                        for (int i = 0; i < arity; i++) {
                            stack.pop();
                        }

                        String[] rawArgs = t.getRawArgs(); // [ExpressionName, Lower, Upper, Iterations]
                        if (rawArgs.length != arity) {
                            throw new RuntimeException("Invalid input. Expression did not pass token compiler phase");
                        }
                        if (rawArgs.length != 3 && rawArgs.length != 4) {
                            throw new RuntimeException("Invalid input. Incomplete arguments for definite integral function: `intg`");
                        }

                        // 1. COMPILE the target expression into a MethodHandle immediately
                        Function f = FunctionManager.lookUp(rawArgs[0]);
                        MathExpression innerExpr = f.getMathExpression();
                        MethodHandle compiledInner = compileScalar(innerExpr.getCachedPostfix());

                        double lower = Double.parseDouble(rawArgs[1]);
                        double upper = Double.parseDouble(rawArgs[2]);
                        int iterations = (int) ((arity == 4) ? (int) Double.parseDouble(rawArgs[3]) : (int) ((upper - lower) / 0.05));
                        String[] vars = innerExpr.getVariablesNames();
                        Integer[] slots = innerExpr.getSlots();

                        // 2. Resolve a bridge method that takes the PRE-COMPILED handle
                        MethodHandle bridge = LOOKUP.findStatic(ScalarTurboEvaluator.class, "executeTurboIntegral",
                                MethodType.methodType(double.class, Function.class, MethodHandle.class, double.class, double.class, int.class,
                                        String[].class, Integer[].class));
                        //executeTurboIntegral(Function f, MethodHandle handle, double lower, double upper, int iterations, String[] vars, Integer[] slots)

                        // 3. Bind the constants (The Compiled Handle and the Bounds)
                        MethodHandle finalIntgHandle = MethodHandles.insertArguments(bridge, 0, f, compiledInner, lower, upper, iterations, vars, slots);

                        // 4. Push to stack as (double[]) -> double
                        stack.push(MethodHandles.dropArguments(finalIntgHandle, 0, double[].class));
                        break;
                    } else if (name.equals("diff")) {
                        // 1. POP the arguments from the stack to balance it!
                        // Since diff(expr, var, order) has 3 args, we must pop 3 times.
                        for (int i = 0; i < t.arity; i++) {
                            stack.pop();
                        }

                        String[] args = t.getRawArgs();

                        if (args == null || args.length == 0) {
                            throw new IllegalArgumentException("Method 'diff' requires arguments.");
                        }
                        if (args.length != t.arity) {
                            throw new RuntimeException("Invalid input. Expression did not pass token compiler phase");
                        }
                        if (args.length > 3) {
                            throw new RuntimeException("Invalid input. Argument count for general root is invalid. Expected: <=3 Found " + args.length);
                        }

                        String returnHandle = null;
                        double evalPoint = -1;
                        int order = -1;
                        // 1. Resolve Expression/Handle
                        String targetExpr = args[0];

                        // 3. Symbolic Derivation
                        MathExpression.EvalResult solution = null;
                        switch (args.length) {
                            case 1:
                                targetExpr = args[0];
                                order = 1;
                                solution = Derivative.eval("diff(" + targetExpr + "," + order + ")");
                                break;
                            case 2:
                                targetExpr = args[0];
                                if (com.github.gbenroscience.parser.Number.isNumber(args[1])) {//order
                                    order = Integer.parseInt(args[1]);
                                    solution = Derivative.eval("diff(" + targetExpr + "," + order + ")");
                                } else if (Variable.isVariableString(args[1])) {//Function handle
                                    returnHandle = args[1];
                                    FunctionManager.lockDown(returnHandle, args);
                                    solution = Derivative.eval("diff(" + targetExpr + "," + returnHandle + ")");
                                }

                                break;
                            case 3:
                                targetExpr = args[0];
                                if (com.github.gbenroscience.parser.Number.isNumber(args[2])) {//order
                                    order = Integer.parseInt(args[2]);
                                } else if (Variable.isVariableString(args[1])) {//Function handle
                                    throw new RuntimeException("The 3rd argument of the diff command is the order of differentiation! It must be a whole number!");
                                }

                                if (com.github.gbenroscience.parser.Number.isNumber(args[1])) {//order
                                    evalPoint = Integer.parseInt(args[1]);
                                    solution = Derivative.eval("diff(" + targetExpr + "," + evalPoint + "," + order + ")");
                                } else if (Variable.isVariableString(args[1])) {//Function handle
                                    returnHandle = args[1];
                                    FunctionManager.lockDown(returnHandle, args);
                                    solution = Derivative.eval("diff(" + targetExpr + "," + returnHandle + "," + order + ")");
                                }

                                break;

                            default:
                                throw new AssertionError();
                        }
                        /*
     * diff(F) Evaluate F's grad func and return the result 
     * diff(F,v) Evaluate F's grad func and store the result in a function pointer called v 
     * diff(F,n) Evaluate F's grad func n times 
     * diff(F,v,n) Evaluate F's grad func n times and store the result in a function pointer called v 
     * diff(F,x,n) Evaluate F's grad func n times and calculate the result at x
                         */

                        // 4. Recursive Compilation into the MethodHandle Tree
                        if (solution.getType() == TYPE.NUMBER) {
                            double val = solution.scalar;
                            MethodHandle constant = MethodHandles.constant(double.class, val);
                            stack.push(MethodHandles.dropArguments(constant, 0, double[].class));
                        } else if (solution.getType() == TYPE.STRING) {
                            // Reparse the solution string and compile it.
                            // This effectively "inlines" the derivative logic.
                            MathExpression solutionExpr = new MathExpression(solution.textRes, true);
                            stack.push(compileScalar(solutionExpr.getCachedPostfix()));
                        } else {
                            // Reparse the solution string and compile it.
                            // This effectively "inlines" the derivative logic.
                            throw new RuntimeException("Invalid expression passed to `diff` method: " + FunctionManager.lookUp(targetExpr));
                        }
                        break;
                    }

                    // --- Standard Intrinsic / Slow-Path for other Functions/Methods ---
                    int arity = t.arity;

                    // 1. Check if we can bypass the Registry for a Fast-Path Intrinsic
                    if (isIntrinsic(name, arity)) {
                        if (arity == 1) {
                            // Primitive Unary Path: (double) -> double
                            MethodHandle operand = stack.pop();
                            MethodHandle fn = getUnaryFunctionHandle(name);
                            stack.push(MethodHandles.filterArguments(fn, 0, operand));
                        } else if (arity == 2) {
                            // Primitive Binary Path: (double, double) -> double
                            MethodHandle right = stack.pop();
                            MethodHandle left = stack.pop();
                            MethodHandle fn = getBinaryFunctionHandle(name);
                            MethodHandle combined = MethodHandles.filterArguments(fn, 0, left, right);
                            stack.push(MethodHandles.permuteArguments(combined, MT_SAFE_WRAP, 0, 0));
                        }
                    } else {
                        // 2. Fallback: SLOW-PATH (Bridge + Registry + Object Wrapping)
                        List<MethodHandle> args = new ArrayList<>(arity);
                        for (int i = 0; i < arity; i++) {
                            args.add(0, stack.pop());
                        }
                        stack.push(compileFunction(t, args));
                    }
                    break;
            }
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Invalid postfix expression: stack size = " + stack.size());
        }

        MethodHandle resultHandle = stack.pop();

        // Ensure type is (double[]) -> double
        // return resultHandle.asType(MT_SAFE_WRAP);
// THE FIX: Explicitly cast the handle's return type to Object.
// This effectively "blinds" the JVM's auto-unboxing logic so it 
// just hands you the raw reference (whether it's a Double or a double[]).
        return resultHandle.asType(MethodType.methodType(Object.class, double[].class));
    }

    private static MethodHandle compileFunction(MathExpression.Token t, List<MethodHandle> argumentHandles) throws Throwable {
        // 1. Get the unique ID from MethodRegistry
        int methodId = MethodRegistry.getMethodID(t.name);

        // 2. Setup the bridge handle: (int methodId, double[] args) -> double
        MethodHandle bridge = LOOKUP.findStatic(ScalarTurboEvaluator.class, "invokeRegistryMethod",
                MethodType.methodType(double.class, int.class, double[].class));

        // 3. Bind the methodId so the resulting handle only needs the double[]
        MethodHandle boundBridge = MethodHandles.insertArguments(bridge, 0, methodId);

        // 4. Transform the handle to accept N individual double arguments instead of one double[]
        // Signature changes from (double[]) -> double   TO   (double, double, ...) -> double
        MethodHandle collector = boundBridge.asCollector(double[].class, argumentHandles.size());

        // 5. Pipe the results of the sub-expression handles into the collector's arguments
        // We use collectArguments to "pre-fill" the collector with the outputs of our argument tree
        for (int i = 0; i < argumentHandles.size(); i++) {
            collector = MethodHandles.collectArguments(collector, i, argumentHandles.get(i));
        }

        return collector;
    }

    // ========== BINARY OPERATORS ==========
    /**
     * Apply binary operator by combining left and right operands.
     *
     * Transforms: - left: (double[]) -> double - right: (double[]) -> double
     * Result: (double[]) -> double
     */
    private static MethodHandle applyBinaryOp(char op, MethodHandle left, MethodHandle right) throws Throwable {
        MethodHandle opHandle = getBinaryOpHandle(op);

        // filterArguments produces: (double[], double[]) -> double
        MethodHandle combined = MethodHandles.filterArguments(opHandle, 0, left, right);

        // permuteArguments collapses them back to: (double[]) -> double
        // Both left and right use the SAME input array (0, 0)
        return MethodHandles.permuteArguments(combined, MT_SAFE_WRAP, 0, 0);
    }

    /**
     * Get the MethodHandle for a binary operator.
     */
    private static MethodHandle getBinaryOpHandle(char op) throws Throwable {
        switch (op) {
            case '+':
                return LOOKUP.findStatic(ScalarTurboEvaluator.class, "add", MT_DOUBLE_DD);
            case '-':
                return LOOKUP.findStatic(ScalarTurboEvaluator.class, "subtract", MT_DOUBLE_DD);
            case '*':
                return LOOKUP.findStatic(ScalarTurboEvaluator.class, "multiply", MT_DOUBLE_DD);
            case '/':
                return LOOKUP.findStatic(ScalarTurboEvaluator.class, "divide", MT_DOUBLE_DD);
            case '%':
                return LOOKUP.findStatic(ScalarTurboEvaluator.class, "modulo", MT_DOUBLE_DD);
            case '^':
                return LOOKUP.findStatic(Math.class, "pow", MT_DOUBLE_DD);
            case 'P':
                return LOOKUP.findStatic(Maths.class, "permutation", MT_DOUBLE_DD);
            case 'C':
                return LOOKUP.findStatic(Maths.class, "combination", MT_DOUBLE_DD);
            default:
                throw new IllegalArgumentException("Unsupported binary operator: " + op);
        }
    }

    public static double executePrint(String[] args) throws Throwable {
        double defReturnType = -1.0;
        for (String arg : args) {
            Function v = FunctionManager.lookUp(arg);
            if (v != null) {
                switch (v.getType()) {
                    case ALGEBRAIC_EXPRESSION:
                        System.out.println(v.toString());
                        return defReturnType;
                    case MATRIX:
                        System.out.println(v.getName() + "=" + v.getMatrix().toString());
                        return defReturnType;
                    default:
                        System.out.println(v.toString());
                        return defReturnType;
                }
            }
            Variable myVar = VariableManager.lookUp(arg);
            if (myVar != null) {
                System.out.println(myVar);
                return defReturnType;
            } else if (com.github.gbenroscience.parser.Number.isNumber(arg)) {
                System.out.println(arg);
                return defReturnType;
            } else {
                System.out.println("null");
                return defReturnType;
            }
        }
        return defReturnType;
    }

    public static double executeTurboIntegral(Function f, MethodHandle handle, double lower, double upper, int iterations, String[] vars, Integer[] slots) throws Throwable {
        MethodHandle primitiveHandle = handle.asType(
                MethodType.methodType(double.class, double[].class)
        );
        // NumericalIntegral intg = new NumericalIntegral(f, lower, upper, iterations, primitiveHandle, vars, slots);
        //  return intg.findHighRangeIntegralTurbo();
        NumericalIntegrator numericalIntegrator = new NumericalIntegrator(f, primitiveHandle, lower, upper, vars, slots);
        return numericalIntegrator.integrate(f);
    }

    public static double scalarStatsGatekeeper(String method, double[] data) {
        return executeScalarReturningStatsMethod(null, data, method);
    }

// For things like SORT, MODE
    public static double[] vectorStatsGatekeeper(String method, double[] data) {
        return executeVectorReturningStatsMethod(null, data, method);
    }

    public static double[] vectorNonStatsGatekeeper(String method, String funcHandle) {
        if (method.equals(Declarations.QUADRATIC)) {
            return executeQuadraticRoot(funcHandle);
        } else if (method.equals(Declarations.TARTAGLIA_ROOTS)) {
            return executeTartagliaRoot(funcHandle);
        }
        return new double[]{};
    }

    private static double executeScalarReturningStatsMethod(MethodHandle handle, double[] args, String method) {
        int n = args.length;
        if (n == 0 && !method.equals(Declarations.RANDOM)) {
            return Double.NaN; // Safety guard for empty arrays
        }

        switch (method) {
            case Declarations.LIST_SUM:
            case Declarations.SUM: {
                double total = 0.0;
                // The JIT compiler will aggressively unroll this primitive loop
                for (int i = 0; i < n; i++) {
                    total += args[i];
                }
                return total;
            }

            case Declarations.PROD: {
                double prod = 1.0;
                for (int i = 0; i < n; i++) {
                    prod *= args[i];
                }
                return prod;
            }

            case Declarations.AVG:
            case Declarations.MEAN: {
                double mTotal = 0.0;
                for (int i = 0; i < n; i++) {
                    mTotal += args[i];
                }
                return mTotal / n;
            }

            case Declarations.MEDIAN: {
                // In-place sort is incredibly fast for small arrays and avoids allocation
                Arrays.sort(args);
                int mid = n / 2;
                if (n % 2 == 0) {
                    return (args[mid - 1] + args[mid]) * 0.5;
                }
                return args[mid];
            }

            case Declarations.MIN: {
                double min = args[0];
                for (int i = 1; i < n; i++) {
                    if (args[i] < min) {
                        min = args[i];
                    }
                }
                return min;
            }

            case Declarations.MAX: {
                double max = args[0];
                for (int i = 1; i < n; i++) {
                    if (args[i] > max) {
                        max = args[i];
                    }
                }
                return max;
            }
            case Declarations.NOW: {
                return System.currentTimeMillis();
            }
            case Declarations.NANOS: {
                return System.nanoTime();
            }
            case Declarations.RANGE: {
                double rMin = args[0];
                double rMax = args[0];
                for (int i = 1; i < n; i++) {
                    if (args[i] < rMin) {
                        rMin = args[i];
                    } else if (args[i] > rMax) {
                        rMax = args[i];
                    }
                }
                return rMax - rMin;
            }

            case Declarations.MID_RANGE: {
                double mrMin = args[0];
                double mrMax = args[0];
                for (int i = 1; i < n; i++) {
                    if (args[i] < mrMin) {
                        mrMin = args[i];
                    } else if (args[i] > mrMax) {
                        mrMax = args[i];
                    }
                }
                return (mrMax + mrMin) * 0.5;
            }

            case Declarations.VARIANCE: {
                if (n < 2) {
                    return 0.0;
                }
                // Welford's Algorithm: One-pass, cache-friendly, numerically stable
                double mean = 0.0;
                double M2 = 0.0;
                for (int i = 0; i < n; i++) {
                    double delta = args[i] - mean;
                    mean += delta / (i + 1);
                    M2 += delta * (args[i] - mean);
                }
                return M2 / (n - 1); // Sample variance
            }

            case Declarations.STD_DEV: {
                if (n < 2) {
                    return 0.0;
                }
                double mean = 0.0;
                double M2 = 0.0;
                for (int i = 0; i < n; i++) {
                    double delta = args[i] - mean;
                    mean += delta / (i + 1);
                    M2 += delta * (args[i] - mean);
                }
                return Math.sqrt(M2 / (n - 1));
            }

            case Declarations.STD_ERR: {
                if (n < 2) {
                    return 0.0;
                }
                double mean = 0.0;
                double M2 = 0.0;
                for (int i = 0; i < n; i++) {
                    double delta = args[i] - mean;
                    mean += delta / (i + 1);
                    M2 += delta * (args[i] - mean);
                }
                double stdDev = Math.sqrt(M2 / (n - 1));
                return stdDev / Math.sqrt(n);
            }

            case Declarations.COEFFICIENT_OF_VARIATION: {
                if (n < 2) {
                    return Double.NaN;
                }
                double mean = 0.0;
                double M2 = 0.0;
                for (int i = 0; i < n; i++) {
                    double delta = args[i] - mean;
                    mean += delta / (i + 1);
                    M2 += delta * (args[i] - mean);
                }
                if (mean == 0.0) {
                    return Double.NaN; // Guard against /0
                }
                double stdDev = Math.sqrt(M2 / (n - 1));
                return stdDev / mean;
            }

            case Declarations.ROOT_MEAN_SQUARED: {
                double sumSq = 0.0;
                for (int i = 0; i < n; i++) {
                    sumSq += (args[i] * args[i]);
                }
                return Math.sqrt(sumSq / n);
            }

            case Declarations.RANDOM:
                // ThreadLocalRandom is vastly superior to Math.random() for high-throughput calls
                return ThreadLocalRandom.current().nextDouble();

            default:
                return Double.NaN;
        }
    }

    private static double[] executeVectorReturningStatsMethod(MethodHandle handle, double[] args, String method) {
        int n = args.length;

        switch (method) {
            case Declarations.MODE: {
                Arrays.sort(args); // Sort first to group identical values

                // First pass: Find the maximum frequency
                int maxCount = 0;
                int currentCount = 1;
                for (int i = 1; i < n; i++) {
                    if (args[i] == args[i - 1]) {
                        currentCount++;
                    } else {
                        if (currentCount > maxCount) {
                            maxCount = currentCount;
                        }
                        currentCount = 1;
                    }
                }
                if (currentCount > maxCount) {
                    maxCount = currentCount;
                }

                // Second pass: Collect all values that match maxCount
                // We use a temporary list or a precisely sized array
                double[] tempModes = new double[n];
                int modeIdx = 0;
                currentCount = 1;

                // Handle single element case
                if (n == 1) {
                    return new double[]{args[0]};
                }

                for (int i = 1; i < n; i++) {
                    if (args[i] == args[i - 1]) {
                        currentCount++;
                    } else {
                        if (currentCount == maxCount) {
                            tempModes[modeIdx++] = args[i - 1];
                        }
                        currentCount = 1;
                    }
                }
                if (currentCount == maxCount) {
                    tempModes[modeIdx++] = args[n - 1];
                }

                // Return a trimmed array containing only the modes
                return Arrays.copyOf(tempModes, modeIdx);
            }
            case Declarations.SORT: {
                // Arrays.sort mutates the array in-place extremely fast.
                Arrays.sort(args);
                // Since this method strictly returns a double, returning the array itself isn't possible here.
                // Returning args[0] gives the caller the first element, while the array remains sorted in memory.
                return args;
            }

            default:
                return null;
        }
    }

    /**
     * Execution bridge for the TurboRootFinder. This is invoked by the compiled
     * MethodHandle chain.
     *
     * @param baseHandle
     * @param derivHandle
     * @param xSlot
     * @param iterations
     * @param lower
     * @param upper
     * @return
     */
    public static double executeTurboRoot(MethodHandle baseHandle, MethodHandle derivHandle,
            int xSlot, double lower, double upper, int iterations) {
        // We use a default iteration cap of 1000 for the turbo version
        TurboRootFinder trf = new TurboRootFinder(baseHandle, derivHandle, xSlot, lower, upper, iterations);
        return trf.findRoots();
    }

    public static double[] executeQuadraticRoot(String funcHandle) {
        Function f = FunctionManager.lookUp(funcHandle);
        String input = f.expressionForm();
        input = input.substring(1);//remove the @
        int closeBracOfAt = Bracket.getComplementIndex(true, 0, input);
        input = input.substring(closeBracOfAt + 1);
        if (input.startsWith("(")) {
            input = input.substring(1, input.length() - 1);
            input = input.concat("=0");
        }
        Quadratic_Equation solver = new Quadratic_Equation(input);
        QuadraticSolver alg = solver.getAlgorithm();
        if (alg.isComplex()) {
            return alg.solutions;
        } else {
            return new double[]{alg.solutions[0], alg.solutions[2]};
        }
    }

    public static double[] executeTartagliaRoot(String funcHandle) {
        Function f = FunctionManager.lookUp(funcHandle);
        String input = f.expressionForm();
        input = input.substring(1);//remove the @
        int closeBracOfAt = Bracket.getComplementIndex(true, 0, input);
        input = input.substring(closeBracOfAt + 1);
        if (input.startsWith("(")) {
            input = input.substring(1, input.length() - 1);
            input = input.concat("=0");
        }
        Tartaglia_Equation solver = new Tartaglia_Equation(input);
        solver.getAlgorithm().solve();
        double[] solns = solver.getAlgorithm().solutions;
        return solns;
    }

    // ========== UNARY OPERATORS ==========
    /**
     * Apply unary operator by filtering the operand.
     *
     * Transforms: - operand: (double[]) -> double - unaryOp: (double) -> double
     * Result: (double[]) -> double
     */
    private static MethodHandle applyUnaryOp(char op, MethodHandle operand) throws Throwable {
        MethodHandle unaryOp = getUnaryOpHandle(op);
        // unaryOp: (double) -> double. operand: (double[]) -> double
        return MethodHandles.filterArguments(unaryOp, 0, operand);
    }

    /**
     * Get the MethodHandle for a unary operator.
     */
    private static MethodHandle getUnaryOpHandle(char op) throws Throwable {
        switch (op) {
            case '√':
                return LOOKUP.findStatic(Math.class, "sqrt", MT_DOUBLE_D);
            case 'R':
                // Cube root (internal representation for ³√)
                return LOOKUP.findStatic(Math.class, "cbrt", MT_DOUBLE_D);
            case '!':
                // Factorial
                return LOOKUP.findStatic(Maths.class, "fact", MT_DOUBLE_D);
            case '²':
                // Square: x^2
                MethodHandle pow2 = LOOKUP.findStatic(Math.class, "pow", MT_DOUBLE_DD);
                return MethodHandles.insertArguments(pow2, 1, 2.0);
            case '³':
                // Cube: x^3
                MethodHandle pow3 = LOOKUP.findStatic(Math.class, "pow", MT_DOUBLE_DD);
                return MethodHandles.insertArguments(pow3, 1, 3.0);
            default:
                throw new IllegalArgumentException("Unsupported unary operator: " + op);
        }
    }

    // ========== FUNCTIONS ==========
    /**
     * Apply a function (method or user-defined) with given arity.
     *
     * Supports: - Arity 1: single input function - Arity 2: binary input
     * function - Higher arities: delegates to method registry
     */
    private static MethodHandle applyFunction(MathExpression.Token t, MethodHandle[] args) throws Throwable {
        String name = t.name.toLowerCase();

        // Handle Arity 1
        if (t.arity == 1) {
            MethodHandle fn = getUnaryFunctionHandle(name);
            return MethodHandles.filterArguments(fn, 0, args[0]);
        }

        // Handle Arity 2
        if (t.arity == 2) {
            MethodHandle fn = getBinaryFunctionHandle(name);
            MethodHandle combined = MethodHandles.filterArguments(fn, 0, args[0], args[1]);
            return MethodHandles.permuteArguments(combined, MT_SAFE_WRAP, 0, 0);
        }

        throw new UnsupportedOperationException("Unsupported arity for function: " + name);
    }

    /**
     * Get unary function handle (arity 1).
     *
     * Supports: - Trigonometric: sin, cos, tan, asin, acos, atan (with DRG
     * variants) - Logarithmic: log, ln, log10 - Power/Root: sqrt, cbrt -
     * Rounding: floor, ceil, abs - Other: exp, fact
     */
    private static MethodHandle getUnaryFunctionHandle(String name) throws Throwable {
        String lower = name.toLowerCase();
        String base;
        String unit = "rad";

        if (lower.contains("_")) {
            String[] parts = lower.split("_");
            base = parts[0];
            unit = parts[1];
        } else {
            base = lower;
        }

        MethodHandle mh;

        switch (base) {
            case "sin":
                mh = LOOKUP.findStatic(Math.class, "sin", MT_DOUBLE_D);
                break;
            case "cos":
                mh = LOOKUP.findStatic(Math.class, "cos", MT_DOUBLE_D);
                break;
            case "tan":
                mh = LOOKUP.findStatic(Math.class, "tan", MT_DOUBLE_D);
                break;
            case "asin":
            case "sin-¹":
                mh = LOOKUP.findStatic(Math.class, "asin", MT_DOUBLE_D);
                break;
            case "acos":
            case "cos-¹":
                mh = LOOKUP.findStatic(Math.class, "acos", MT_DOUBLE_D);
                break;
            case "atan":
            case "tan-¹":
                mh = LOOKUP.findStatic(Math.class, "atan", MT_DOUBLE_D);
                break;
            case "sec":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "sec", MT_DOUBLE_D);
                break;
            case "csc":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "csc", MT_DOUBLE_D);
                break;
            case "cot":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "cot", MT_DOUBLE_D);
                break;

            case "asec":
            case "sec-¹":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "asec", MT_DOUBLE_D);
                break;
            case "acsc":
            case "csc-¹":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "acsc", MT_DOUBLE_D);
                break;
            case "acot":
            case "cot-¹":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "acot", MT_DOUBLE_D);
                break;
            case "sinh":
                mh = LOOKUP.findStatic(Math.class, "sinh", MT_DOUBLE_D);
                break;
            case "cosh":
                mh = LOOKUP.findStatic(Math.class, "cosh", MT_DOUBLE_D);
                break;
            case "tanh":
                mh = LOOKUP.findStatic(Math.class, "tanh", MT_DOUBLE_D);
                break;
            case "sech":
                mh = LOOKUP.findStatic(Maths.class, "sech", MT_DOUBLE_D); // Assuming Maths helper
                break;
            case "csch":
                mh = LOOKUP.findStatic(Maths.class, "csch", MT_DOUBLE_D);
                break;
            case "coth":
                mh = LOOKUP.findStatic(Maths.class, "coth", MT_DOUBLE_D);
                break;

// --- INVERSE HYPERBOLICS ---
            case "asinh":
            case "sinh-¹":
                mh = LOOKUP.findStatic(Maths.class, "asinh", MT_DOUBLE_D);
                break;
            case "acosh":
            case "cosh-¹":
                mh = LOOKUP.findStatic(Maths.class, "acosh", MT_DOUBLE_D);
                break;
            case "atanh":
            case "tanh-¹":
                mh = LOOKUP.findStatic(Maths.class, "atanh", MT_DOUBLE_D);
                break;
            case "asech":
            case "sech-¹":
                mh = LOOKUP.findStatic(Maths.class, "asech", MT_DOUBLE_D);
                break;
            case "acsch":
            case "csch-¹":
                mh = LOOKUP.findStatic(Maths.class, "acsch", MT_DOUBLE_D);
                break;
            case "acoth":
            case "coth-¹":
                mh = LOOKUP.findStatic(Maths.class, "acoth", MT_DOUBLE_D);
                break;

// --- ADDITIONAL LOG / EXP ---
            case "log":
            case "alg": // Anti-log base 10
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "alg", MT_DOUBLE_D);
                break;
            case "ln-¹":
                mh = LOOKUP.findStatic(Math.class, "exp", MT_DOUBLE_D); // ln-¹ is just e^x
                break;
            case "lg-¹":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "alg", MT_DOUBLE_D);
                break;

// --- ROUNDING / MISC ---
            case "ceil":
                mh = LOOKUP.findStatic(Math.class, "ceil", MT_DOUBLE_D);
                break;
            case "rnd":
                mh = LOOKUP.findStatic(Math.class, "round", MT_DOUBLE_D); // Note: might need cast logic
                break;
            case "sqrt":
                mh = LOOKUP.findStatic(Math.class, "sqrt", MT_DOUBLE_D);
                break;
            case "cbrt":
                mh = LOOKUP.findStatic(Math.class, "cbrt", MT_DOUBLE_D);
                break;
            case "exp":
                mh = LOOKUP.findStatic(Math.class, "exp", MT_DOUBLE_D);
                break;
            case "ln":
            case "aln":
                mh = LOOKUP.findStatic(Math.class, "log", MT_DOUBLE_D);
                break;
            case "lg":
                mh = LOOKUP.findStatic(Math.class, "log10", MT_DOUBLE_D);
                break;
            case "abs":
                mh = LOOKUP.findStatic(Math.class, "abs", MT_DOUBLE_D);
                break;
            case "fact":
                mh = LOOKUP.findStatic(Maths.class, "fact", MT_DOUBLE_D);
                break;
            case "square":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "square", MT_DOUBLE_D);
                break;
            case "cube":
                mh = LOOKUP.findStatic(ScalarTurboEvaluator.class, "cube", MT_DOUBLE_D);
                break;
            default:
                throw new UnsupportedOperationException("No fast-path for: " + base);
        }

        // Bake the unit conversion into the MethodHandle chain
        switch (unit) {
            case "deg":
                return chainToRadians(mh);
            case "grad":
                return chainGradToRadians(mh);
            default:
                return mh;
        }
    }

    public static double sec(double x) {
        return 1.0 / Math.cos(x);
    }

    public static double csc(double x) {
        return 1.0 / Math.sin(x);
    }

    public static double cot(double x) {
        return 1.0 / Math.tan(x);
    }

    public static double asec(double x) {
        return Math.acos(1.0 / x);
    }

    public static double acsc(double x) {
        return Math.asin(1.0 / x);
    }

    public static double acot(double x) {
        return Math.atan(1.0 / x);
    }

    /**
     * Chain Math.toRadians into a trigonometric function. This keeps the
     * conversion in the compiled bytecode.
     *
     * Pattern: trigOp(toRadians(x)) is compiled as a single chain.
     */
    private static MethodHandle chainToRadians(MethodHandle trigOp) throws Throwable {
        MethodHandle toRad = LOOKUP.findStatic(Math.class, "toRadians", MT_DOUBLE_D);
        return MethodHandles.filterArguments(trigOp, 0, toRad);
    }

    private static MethodHandle chainGradToRadians(MethodHandle trigOp) throws Throwable {
        // 1 grad = PI / 200 radians
        MethodHandle toRad = MethodHandles.filterArguments(
                LOOKUP.findStatic(Math.class, "multiply", MT_DOUBLE_DD), // Custom multiply or use a constant
                0, MethodHandles.constant(double.class, Math.PI / 200.0)
        );
        // Simplified: Just use a helper method for clarity
        MethodHandle gradToRad = LOOKUP.findStatic(ScalarTurboEvaluator.class, "gradToRad", MT_DOUBLE_D);
        return MethodHandles.filterArguments(trigOp, 0, gradToRad);
    }

    public static double gradToRad(double grads) {
        return grads * (Math.PI / 200.0);
    }

    public static double getVar(double[] vars, int index) {
        return vars[index];
    }

    /**
     * Get binary function handle (arity 2).
     *
     * Supports: - Power operations: pow - Trigonometric: atan2 - Comparison:
     * min, max
     */
    private static MethodHandle getBinaryFunctionHandle(String name) throws Throwable {
        switch (name.toLowerCase()) {
            case "pow":
                return LOOKUP.findStatic(Math.class, "pow", MT_DOUBLE_DD);
            case "atan2":
                return LOOKUP.findStatic(Math.class, "atan2", MT_DOUBLE_DD);
            case "min":
                return LOOKUP.findStatic(Math.class, "min", MT_DOUBLE_DD);
            case "max":
                return LOOKUP.findStatic(Math.class, "max", MT_DOUBLE_DD);
            case "log":
                return LOOKUP.findStatic(Math.class, "max", MT_DOUBLE_DD);
            case "diff":
                return LOOKUP.findStatic(Math.class, "max", MT_DOUBLE_DD);
            case "intg":
                return LOOKUP.findStatic(Math.class, "max", MT_DOUBLE_DD);
            case "comb":
            case "perm":
                // Redirecting to your Maths library for permutations/combinations
                return LOOKUP.findStatic(Maths.class,
                        name.equals("comb") ? "combination" : "permutation", MT_DOUBLE_DD);
            default:
                throw new UnsupportedOperationException("Binary fast-path not found: " + name);
        }
    }

    public static MethodHandle createConstantHandle(double value) {
        MethodHandle c = MethodHandles.constant(double.class, value);
        return MethodHandles.dropArguments(c, 0, double[].class);
    }

    // ========== INLINE ARITHMETIC HELPERS ==========
    /**
     * Inlined addition: a + b
     */
    public static double add(double a, double b) {
        return a + b;
    }

    /**
     * Inlined subtraction: a - b
     */
    public static double subtract(double a, double b) {
        return a - b;
    }

    /**
     * Inlined multiplication: a * b
     */
    public static double multiply(double a, double b) {
        return a * b;
    }

    /**
     * Inlined division: a / b with zero-check
     */
    public static double divide(double a, double b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return a / b;
    }

    /**
     * Inlined modulo: a % b
     */
    public static double modulo(double a, double b) {
        return a % b;
    }

}
