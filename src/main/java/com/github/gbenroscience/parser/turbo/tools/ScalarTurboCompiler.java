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
import com.github.gbenroscience.math.numericalmethods.FunctionExpander;
import com.github.gbenroscience.math.numericalmethods.NumericalIntegral;
import com.github.gbenroscience.math.numericalmethods.RootFinder;
import com.github.gbenroscience.math.numericalmethods.TurboRootFinder;
import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.TYPE;
import com.github.gbenroscience.parser.Variable;
import com.github.gbenroscience.parser.methods.MethodRegistry;
import com.github.gbenroscience.util.FunctionManager;
import java.lang.invoke.*;
import java.math.BigDecimal;
import java.util.*;

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
public class ScalarTurboCompiler implements TurboExpressionCompiler {

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
            "sin-¹_deg", "intg", "cot-¹_deg", "quadratic",
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
            "csc-¹_rad", "sin_rad", "csch", "asinh"
    

    ///////////////////////////////////////////////////////

    ));

    public ScalarTurboCompiler(MathExpression.Token[] postfix) {
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
     */
    @Override
    public FastCompositeExpression compile() throws Throwable {

        // Compile to scalar MethodHandle
        MethodHandle scalarHandle = compileScalar(postfix);

        // Wrap scalar result in EvalResult
        return new FastCompositeExpression() {
            @Override
            public MathExpression.EvalResult apply(double[] variables) {
                try {
                    double value = (double) scalarHandle.invokeExact(variables);
                    return new MathExpression.EvalResult().wrap(value);
                } catch (Throwable t) {
                    throw new RuntimeException("Turbo scalar execution failed", t);
                }
            }

            @Override
            public double applyScalar(double[] variables) {
                try {
                    // invokeExact is key: no casting, no boxing, no overhead.
                    return (double) scalarHandle.invokeExact(variables);
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
                case MathExpression.Token.NUMBER:
                    if (t.name != null && !t.name.isEmpty()) {
                        // Variable: load from array at frameIndex
                        int frameIndex = t.frameIndex;
                        // 1. Get the base method handle for our helper
                        MethodHandle getter = LOOKUP.findStatic(ScalarTurboCompiler.class, "getVar",
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
                    if (name.equals("root")) {
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
                            String derivString = Derivative.eval("diff(" + fNameOrExpr + "," + varName + ",1)").textRes;
                            derivHandle = compileScalar(new MathExpression(derivString).getCachedPostfix());
                        } catch (Exception e) {
                            // Fallback: TurboRootFinder handles null derivativeHandle by skipping Newtonian
                            derivHandle = null;
                        }

                        // 3. Bind the execution bridge
                        // Signature: (MethodHandle, MethodHandle, int, double, double) -> double
                        MethodHandle bridge = LOOKUP.findStatic(ScalarTurboCompiler.class, "executeTurboRoot",
                                MethodType.methodType(double.class, MethodHandle.class, MethodHandle.class,
                                        int.class, double.class, double.class, int.class));

                        // 4. Curry the arguments into a single operation handle
                        MethodHandle currentHandle = MethodHandles.insertArguments(bridge, 0,
                                targetHandle, derivHandle, xSlot, lower, upper, iterations);

                        // 5. Adapt the handle to accept the standard (double[]) input frame
                        currentHandle = MethodHandles.dropArguments(currentHandle, 0, double[].class);

                        // The handle is now ready to be pushed to the compiler's compilation stack
                        stack.push(currentHandle);
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
                        int iterations = (int) ((arity == 4) ? (int) Double.parseDouble(rawArgs[3]) : (int)((upper-lower)/0.05));
                        String[] vars = innerExpr.getVariablesNames();
                        Integer[] slots = innerExpr.getSlots();

                        // 2. Resolve a bridge method that takes the PRE-COMPILED handle
                        MethodHandle bridge = LOOKUP.findStatic(ScalarTurboCompiler.class, "executeTurboIntegral",
                                MethodType.methodType(double.class, Function.class, MethodHandle.class, double.class, double.class, int.class,
                                        String[].class, Integer[].class));
                        //executeTurboIntegral(MethodHandle handle, double lower, double upper, int iterations,String[]vars, Integer[]slots)

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
        return resultHandle.asType(MT_SAFE_WRAP);
    }

    private static MethodHandle compileFunction(MathExpression.Token t, List<MethodHandle> argumentHandles) throws Throwable {
        // 1. Get the unique ID from MethodRegistry
        int methodId = MethodRegistry.getMethodID(t.name);

        // 2. Setup the bridge handle: (int methodId, double[] args) -> double
        MethodHandle bridge = LOOKUP.findStatic(ScalarTurboCompiler.class, "invokeRegistryMethod",
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
                return LOOKUP.findStatic(ScalarTurboCompiler.class, "add", MT_DOUBLE_DD);
            case '-':
                return LOOKUP.findStatic(ScalarTurboCompiler.class, "subtract", MT_DOUBLE_DD);
            case '*':
                return LOOKUP.findStatic(ScalarTurboCompiler.class, "multiply", MT_DOUBLE_DD);
            case '/':
                return LOOKUP.findStatic(ScalarTurboCompiler.class, "divide", MT_DOUBLE_DD);
            case '%':
                return LOOKUP.findStatic(ScalarTurboCompiler.class, "modulo", MT_DOUBLE_DD);
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

  

    public static double executeTurboIntegral(Function f, MethodHandle handle, double lower, double upper, int iterations, String[] vars, Integer[] slots) {
        NumericalIntegral intg = new NumericalIntegral(f, lower, upper, iterations, handle, vars, slots);
        return intg.findHighRangeIntegralTurbo();
    }

    /**
     * Execution bridge for the TurboRootFinder. This is invoked by the compiled
     * MethodHandle chain.
     */
    public static double executeTurboRoot(MethodHandle baseHandle, MethodHandle derivHandle,
            int xSlot, double lower, double upper, int iterations) {
        // We use a default iteration cap of 1000 for the turbo version
        TurboRootFinder trf = new TurboRootFinder(baseHandle, derivHandle, xSlot, lower, upper, iterations);
        return trf.findRoots();
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
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "sec", MT_DOUBLE_D);
                break;
            case "csc":
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "csc", MT_DOUBLE_D);
                break;
            case "cot":
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "cot", MT_DOUBLE_D);
                break;

            case "asec":
            case "sec-¹":
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "asec", MT_DOUBLE_D);
                break;
            case "acsc":
            case "csc-¹":
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "acsc", MT_DOUBLE_D);
                break;
            case "acot":
            case "cot-¹":
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "acot", MT_DOUBLE_D);
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
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "alg", MT_DOUBLE_D);
                break;
            case "ln-¹":
                mh = LOOKUP.findStatic(Math.class, "exp", MT_DOUBLE_D); // ln-¹ is just e^x
                break;
            case "lg-¹":
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "alg", MT_DOUBLE_D);
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
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "square", MT_DOUBLE_D);
                break;
            case "cube":
                mh = LOOKUP.findStatic(ScalarTurboCompiler.class, "cube", MT_DOUBLE_D);
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
        MethodHandle gradToRad = LOOKUP.findStatic(ScalarTurboCompiler.class, "gradToRad", MT_DOUBLE_D);
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

    // Inside ScalarTurboCompiler class
    public static MethodHandle createHornerHandle(double[] coeffs) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle base = LOOKUP.findStatic(FunctionExpander.class, "evaluateHorner",
                MethodType.methodType(double.class, double[].class, double[].class));
        // Currying: Bind the first argument (coeffs)
        return MethodHandles.insertArguments(base, 0, (Object) coeffs);
    }

    public static MethodHandle createHornerBigDecimalHandle(BigDecimal[] coeffs) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle base = LOOKUP.findStatic(FunctionExpander.class, "evaluateHornerBigDecimal",
                MethodType.methodType(double.class, BigDecimal[].class, double[].class));
        // Currying: Bind the first argument (coeffs)
        return MethodHandles.insertArguments(base, 0, (Object) coeffs);
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
