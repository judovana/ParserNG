package com.github.gbenroscience.parser.turbo;

import com.github.gbenroscience.math.Maths;
import com.github.gbenroscience.parser.MathExpression;
import java.lang.invoke.*;
import java.util.*;

/**
 * Compiles MathExpression postfix tokens to native bytecode using
 * MethodHandles. Uses permuteArguments to merge multiple input arrays into a
 * single source.
 */
public class TurboCompiler {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // Common method types
    private static final MethodType MT_DOUBLE_D = MethodType.methodType(double.class, double.class);
    private static final MethodType MT_DOUBLE_DD = MethodType.methodType(double.class, double.class, double.class);
    private static final MethodType MT_SAFE_WRAP = MethodType.methodType(double.class, double[].class);

    /**
     * Compile a postfix token array to a FastExpression.
     * @param postfix 
     * @param registry 
     * @throws Throwable
     */ 
    public static FastExpression compile(MathExpression.Token[] postfix,
            MathExpression.VariableRegistry registry) throws Throwable {

        Stack<MethodHandle> stack = new Stack<>();

        for (MathExpression.Token t : postfix) {
            switch (t.kind) {
                case MathExpression.Token.NUMBER:
                    if (t.name != null && !t.name.isEmpty()) {
                        // Variable: load from array at frameIndex
                        int frameIndex = t.frameIndex;
                        MethodHandle loader = MethodHandles.arrayElementGetter(double[].class);
                        // result: (double[]) -> double
                        stack.push(MethodHandles.insertArguments(loader, 1, frameIndex));
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
                    MethodHandle[] args = new MethodHandle[t.arity];
                    for (int i = t.arity - 1; i >= 0; i--) {
                        args[i] = stack.pop();
                    }
                    stack.push(applyFunction(t, args));
                    break;
            }
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Invalid postfix expression: stack size = " + stack.size());
        }

        MethodHandle resultHandle = stack.pop();

        // Create wrapper for FastExpression: double apply(double[] vars)
        final MethodHandle finalHandle = resultHandle.asType(MT_SAFE_WRAP);

        // Return a lambda that captures the handle
        return (double[] variables) -> {
            try {
                // invokeExact is the key to 5ns. 
                // Since we used .asType() above, this is a direct call.
                return (double) finalHandle.invokeExact(variables);
            } catch (Throwable t) {
                throw new RuntimeException("Turbo execution failed", t);
            }
        };
    }

    // ========== BINARY OPERATORS ==========
    private static MethodHandle applyBinaryOp(char op, MethodHandle left, MethodHandle right) throws Throwable {
        MethodHandle opHandle = getBinaryOpHandle(op);

        // filterArguments produces: (double[], double[]) -> double
        MethodHandle combined = MethodHandles.filterArguments(opHandle, 0, left, right);

        // permuteArguments collapses them back to: (double[]) -> double
        return MethodHandles.permuteArguments(combined, MT_SAFE_WRAP, 0, 0);
    }

    private static MethodHandle getBinaryOpHandle(char op) throws Throwable {
        switch (op) {
            case '+':
                return LOOKUP.findStatic(TurboCompiler.class, "add", MT_DOUBLE_DD);
            case '-':
                return LOOKUP.findStatic(TurboCompiler.class, "subtract", MT_DOUBLE_DD);
            case '*':
                return LOOKUP.findStatic(TurboCompiler.class, "multiply", MT_DOUBLE_DD);
            case '/':
                return LOOKUP.findStatic(TurboCompiler.class, "divide", MT_DOUBLE_DD);
            case '%':
                return LOOKUP.findStatic(TurboCompiler.class, "modulo", MT_DOUBLE_DD);
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

    // ========== UNARY OPERATORS ==========
    private static MethodHandle applyUnaryOp(char op, MethodHandle operand) throws Throwable {
        MethodHandle unaryOp = getUnaryOpHandle(op);
        // unaryOp: (double) -> double. operand: (double[]) -> double
        return MethodHandles.filterArguments(unaryOp, 0, operand);
    }

    private static MethodHandle getUnaryOpHandle(char op) throws Throwable {
        switch (op) {
            case '√':
                return LOOKUP.findStatic(Math.class, "sqrt", MT_DOUBLE_D);
            case 'R':
                return LOOKUP.findStatic(Math.class, "cbrt", MT_DOUBLE_D);
            case '!':
                return LOOKUP.findStatic(Maths.class, "fact", MT_DOUBLE_D);
            case '²':
                MethodHandle pow2 = LOOKUP.findStatic(Math.class, "pow", MT_DOUBLE_DD);
                return MethodHandles.insertArguments(pow2, 1, 2.0);
            case '³':
                MethodHandle pow3 = LOOKUP.findStatic(Math.class, "pow", MT_DOUBLE_DD);
                return MethodHandles.insertArguments(pow3, 1, 3.0);
            default:
                throw new IllegalArgumentException("Unsupported unary operator: " + op);
        }
    }

    // ========== FUNCTIONS ==========
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

    private static MethodHandle getUnaryFunctionHandle(String name) throws Throwable {
        switch (name) {
            // Radians (Standard Math)
            case "sin":
            case "sin_rad":
                return LOOKUP.findStatic(Math.class, "sin", MT_DOUBLE_D);
            case "cos":
            case "cos_rad":
                return LOOKUP.findStatic(Math.class, "cos", MT_DOUBLE_D);
            case "tan":
            case "tan_rad":
                return LOOKUP.findStatic(Math.class, "tan", MT_DOUBLE_D);

            // Degrees (Automated Conversion)
            case "sin_deg":
                return chainToRadians(LOOKUP.findStatic(Math.class, "sin", MT_DOUBLE_D));
            case "cos_deg":
                return chainToRadians(LOOKUP.findStatic(Math.class, "cos", MT_DOUBLE_D));
            case "tan_deg":
                return chainToRadians(LOOKUP.findStatic(Math.class, "tan", MT_DOUBLE_D));

            case "asin":
                return LOOKUP.findStatic(Math.class, "asin", MT_DOUBLE_D);
            case "acos":
                return LOOKUP.findStatic(Math.class, "acos", MT_DOUBLE_D);
            case "atan":
                return LOOKUP.findStatic(Math.class, "atan", MT_DOUBLE_D);
            case "exp":
                return LOOKUP.findStatic(Math.class, "exp", MT_DOUBLE_D);
            case "log":
            case "ln":
                return LOOKUP.findStatic(Math.class, "log", MT_DOUBLE_D);
            case "log10":
                return LOOKUP.findStatic(Math.class, "log10", MT_DOUBLE_D);
            case "abs":
                return LOOKUP.findStatic(Math.class, "abs", MT_DOUBLE_D);
            case "sqrt":
                return LOOKUP.findStatic(Math.class, "sqrt", MT_DOUBLE_D);
            case "floor":
                return LOOKUP.findStatic(Math.class, "floor", MT_DOUBLE_D);
            case "ceil":
                return LOOKUP.findStatic(Math.class, "ceil", MT_DOUBLE_D);
            case "fact":
                return LOOKUP.findStatic(Maths.class, "fact", MT_DOUBLE_D);
            default:
                throw new UnsupportedOperationException("Function not found: " + name);
        }
    }

    /**
     * Helper to chain Math.toRadians into a trigonometric handle. This keeps
     * the conversion in the compiled bytecode.
     */
    private static MethodHandle chainToRadians(MethodHandle trigOp) throws Throwable {
        MethodHandle toRad = LOOKUP.findStatic(Math.class, "toRadians", MT_DOUBLE_D);
        return MethodHandles.filterArguments(trigOp, 0, toRad);
    }

    private static MethodHandle getBinaryFunctionHandle(String name) throws Throwable {
        switch (name) {
            case "pow":
                return LOOKUP.findStatic(Math.class, "pow", MT_DOUBLE_DD);
            case "atan2":
                return LOOKUP.findStatic(Math.class, "atan2", MT_DOUBLE_DD);
            case "min":
                return LOOKUP.findStatic(Math.class, "min", MT_DOUBLE_DD);
            case "max":
                return LOOKUP.findStatic(Math.class, "max", MT_DOUBLE_DD);
            default:
                throw new UnsupportedOperationException("Binary function not found: " + name);
        }
    }

    // ========== INLINE ARITHMETIC HELPERS ==========
    public static double add(double a, double b) {
        return a + b;
    }

    public static double subtract(double a, double b) {
        return a - b;
    }

    public static double multiply(double a, double b) {
        return a * b;
    }

    public static double divide(double a, double b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return a / b;
    }

    public static double modulo(double a, double b) {
        return a % b;
    }
}
