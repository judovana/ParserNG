package com.github.gbenroscience.parser.turbo.tools;

import com.github.gbenroscience.math.Maths;
import com.github.gbenroscience.math.matrix.expressParser.Matrix;
import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.MathExpression.EvalResult;
import com.github.gbenroscience.parser.TYPE;
import com.github.gbenroscience.util.FunctionManager;
import java.lang.invoke.*;
import java.util.*;

/**
 * Turbo compiler optimized for ParserNG's flat-array Matrix implementation.
 *
 * * @author GBEMIRO
 */
/**
 * Allocation-free Turbo compiler optimized for ParserNG's flat-array Matrix
 * implementation. Uses compile-time bound ResultCaches to eliminate object and
 * array allocations during execution.
 */
public class FlatMatrixTurboCompiler implements TurboExpressionCompiler {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // ========== THE RESULT CACHE ==========
    /**
     * Holds the mutable state for a single node in the execution tree. Bound
     * into the MethodHandle chain at compile-time.
     */
    public static class ResultCache {

        public final EvalResult result = new EvalResult();
        public double[] matrixData;
        public Matrix matrix;

        // Secondary buffer for re-entrant operations like Matrix Power
        private double[] matrixData2;
        private Matrix matrix2;

        public Matrix getMatrixBuffer(int rows, int cols) {
            int size = rows * cols;
            if (matrixData == null || matrixData.length != size) {
                matrixData = new double[size];
                matrix = new Matrix(matrixData, rows, cols);
            } else if (matrix.getRows() != rows || matrix.getCols() != cols) {
                matrix = new Matrix(matrixData, rows, cols);
            }
            return matrix;
        }

        /**
         * Provides a secondary buffer to avoid overwriting primary data during
         * complex loops like power functions.
         */
        public Matrix getSecondaryBuffer(int rows, int cols) {
            int size = rows * cols;
            if (matrixData2 == null || matrixData2.length != size) {
                matrixData2 = new double[size];
                matrix2 = new Matrix(matrixData2, rows, cols);
            } else if (matrix2.getRows() != rows || matrix2.getCols() != cols) {
                matrix2 = new Matrix(matrixData2, rows, cols);
            }
            return matrix2;
        }
    }

    // ========== COMPILER CORE ==========
    @Override
    public FastCompositeExpression compile(
            MathExpression.Token[] postfix,
            MathExpression.VariableRegistry registry) throws Throwable {

        Stack<MethodHandle> stack = new Stack<>();

        for (MathExpression.Token t : postfix) {
            switch (t.kind) {
                case MathExpression.Token.NUMBER:
                    stack.push(compileTokenAsEvalResult(t));
                    break;

                case MathExpression.Token.OPERATOR:
                    if (t.isPostfix) {
                        MethodHandle operand = stack.pop();
                        stack.push(compileUnaryOpOnEvalResult(t.opChar, operand));
                    } else {
                        MethodHandle right = stack.pop();
                        MethodHandle left = stack.pop();
                        stack.push(compileBinaryOpOnEvalResult(t.opChar, left, right));
                    }
                    break;

                case MathExpression.Token.FUNCTION:
                case MathExpression.Token.METHOD:
                    MethodHandle[] args = new MethodHandle[t.arity];
                    for (int i = t.arity - 1; i >= 0; i--) {
                        args[i] = stack.pop();
                    }
                    stack.push(compileMatrixFunction(t, args));
                    break;
            }
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Invalid postfix stack state.");
        }

        MethodHandle resultHandle = stack.pop();
        final MethodHandle finalHandle = resultHandle.asType(
                MethodType.methodType(EvalResult.class, double[].class));

        return (double[] variables) -> {
            try {
                return (EvalResult) finalHandle.invokeExact(variables);
            } catch (Throwable e) {
                throw new RuntimeException("Turbo matrix execution failed", e);
            }
        };
    }

    // ========== COMPILATION PRIMITIVES ==========
    private MethodHandle compileTokenAsEvalResult(MathExpression.Token t) throws Throwable {

        // Check if it's a named entity (Variable, Constant, or Function Pointer)
        if (t.name != null && !t.name.isEmpty()) {

            // PATH 1: Standard Variable (From translate -> Fallback: Treat as Variable/Constant)
            // It has a slot in the execution frame.
            if (t.frameIndex >= 0) {
                MethodHandle loadScalar = MethodHandles.arrayElementGetter(double[].class);
                loadScalar = MethodHandles.insertArguments(loadScalar, 1, t.frameIndex);

                // Zero-allocation wrap
                ResultCache cache = new ResultCache();
                MethodHandle wrap = LOOKUP.findVirtual(EvalResult.class, "wrap",
                        MethodType.methodType(EvalResult.class, double.class));
                MethodHandle boundWrap = wrap.bindTo(cache.result);

                return MethodHandles.collectArguments(boundWrap, 0, loadScalar);
            }

            // PATH 2: Function Reference / Matrix Literal / Global Constant
            // (From translate -> Identify Functions/Anonymous Functions NOT followed by '(')
            EvalResult constant = new EvalResult();
            Function func = FunctionManager.lookUp(t.name);

            if (func != null) {
                if (func.getType() == TYPE.MATRIX) {
                    constant.wrap(func.getMatrix());
                } else if (func.getType() == TYPE.ALGEBRAIC_EXPRESSION) {
                    // If it's a pointer to an equation, wrap the string/AST reference
                    constant.wrap(func.getMathExpression().getExpression());
                } else {
                    // Evaluates to a scalar
                    constant.wrap(func.calc());
                }
            } else {
                constant.wrap(t.value);
                // It might be a matrix literal directly assigned (t.matrixValue)
                /* if (t.matrixValue != null) {
                    constant.wrap(t.matrixValue);
                } else {
                    constant.wrap(t.value);
                }*/
            }

            // Bake the resolved entity into the MethodHandle as a constant
            return MethodHandles.dropArguments(
                    MethodHandles.constant(EvalResult.class, constant), 0, double[].class);
        }

        // PATH 3: Pure Number Literal (From translate -> Identify Numbers)
        // t.name is null, it's just raw math like "5.0"
        EvalResult constant = new EvalResult().wrap(t.value);
        return MethodHandles.dropArguments(
                MethodHandles.constant(EvalResult.class, constant), 0, double[].class);
    }

    private MethodHandle compileBinaryOpOnEvalResult(char op, MethodHandle left, MethodHandle right) throws Throwable {
        // 1. Match the exact signature: (char, EvalResult, EvalResult, ResultCache)
        MethodHandle dispatcher = LOOKUP.findStatic(FlatMatrixTurboCompiler.class, "dispatchBinaryOp",
                MethodType.methodType(EvalResult.class, char.class, EvalResult.class, EvalResult.class, ResultCache.class));

        // 2. Create the unique cache for this node
        ResultCache nodeCache = new ResultCache();

        // 3. Bind 'op' to index 0 and 'nodeCache' to index 3
        // After these insertions, the MethodHandle expects only (EvalResult left, EvalResult right)
        dispatcher = MethodHandles.insertArguments(dispatcher, 3, nodeCache); // Bind tail first to avoid index shifting
        dispatcher = MethodHandles.insertArguments(dispatcher, 0, op);        // Bind head

        // 4. Combine with the recursive handles for left and right operands
        // This feeds the output of 'left' into the first EvalResult slot and 'right' into the second
        dispatcher = MethodHandles.collectArguments(dispatcher, 0, left);
        dispatcher = MethodHandles.collectArguments(dispatcher, 1, right);

        // 5. Final type alignment to accept the double[] variables array
        return MethodHandles.permuteArguments(dispatcher,
                MethodType.methodType(EvalResult.class, double[].class), 0, 0);
    }

    private MethodHandle compileUnaryOpOnEvalResult(char op, MethodHandle operand) throws Throwable {
        MethodHandle dispatcher = LOOKUP.findStatic(FlatMatrixTurboCompiler.class, "dispatchUnaryOp",
                MethodType.methodType(EvalResult.class, EvalResult.class, char.class, ResultCache.class));

        ResultCache nodeCache = new ResultCache();
        dispatcher = MethodHandles.insertArguments(dispatcher, 1, op, nodeCache);
        return MethodHandles.filterArguments(dispatcher, 0, operand);
    }

    private MethodHandle compileMatrixFunction(MathExpression.Token t, MethodHandle[] args) throws Throwable {
        String funcName = t.name.toLowerCase();

        MethodHandle dispatcher = LOOKUP.findStatic(FlatMatrixTurboCompiler.class, "dispatchMatrixFunction",
                MethodType.methodType(EvalResult.class, EvalResult[].class, String.class, ResultCache.class));

        ResultCache nodeCache = new ResultCache();
        dispatcher = MethodHandles.insertArguments(dispatcher, 1, funcName, nodeCache);

        MethodHandle collector = LOOKUP.findStatic(FlatMatrixTurboCompiler.class, "collectArgsArray",
                MethodType.methodType(EvalResult[].class, EvalResult[].class)).asVarargsCollector(EvalResult[].class);
        collector = collector.asType(MethodType.methodType(EvalResult[].class,
                Collections.nCopies(t.arity, EvalResult.class).toArray(new Class[0])));

        MethodHandle finalFunc = MethodHandles.collectArguments(dispatcher, 0, collector);

        for (int i = 0; i < args.length; i++) {
            finalFunc = MethodHandles.collectArguments(finalFunc, i, args[i]);
        }

        int[] reorder = new int[args.length];
        return MethodHandles.permuteArguments(finalFunc,
                MethodType.methodType(EvalResult.class, double[].class), reorder);
    }

    public static EvalResult[] collectArgsArray(EvalResult... args) {
        return args;
    }

    // ========== RUNTIME DISPATCHERS ==========
    private static EvalResult dispatchBinaryOp(char op, EvalResult left, EvalResult right, ResultCache cache) {
        int leftType = left.type;
        int rightType = right.type;

        switch (op) {
            case '+':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(left.scalar + right.scalar);
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_MATRIX) {
                    cache.result.wrap(flatMatrixAdd(left.matrix, right.matrix, cache));
                } else {
                    throw new UnsupportedOperationException("Addition mismatch: " + leftType + " and " + rightType);
                }
                break;

            case '-':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(left.scalar - right.scalar);
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_MATRIX) {
                    cache.result.wrap(flatMatrixSubtract(left.matrix, right.matrix, cache));
                } else {
                    throw new UnsupportedOperationException("Subtraction mismatch");
                }
                break;

            case '*':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(left.scalar * right.scalar);
                } else if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_MATRIX) {
                    // SCALAR * MATRIX
                    cache.result.wrap(flatMatrixScalarMultiply(left.scalar, right.matrix, cache));
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_SCALAR) {
                    // MATRIX * SCALAR
                    cache.result.wrap(flatMatrixScalarMultiply(right.scalar, left.matrix, cache));
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_MATRIX) {
                    // MATRIX * MATRIX
                    cache.result.wrap(flatMatrixMultiply(left.matrix, right.matrix, cache));
                }
                break;
            case '^':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(Math.pow(left.scalar, right.scalar));
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_SCALAR) {
                    // MATRIX ^ SCALAR (Matrix Power)
                    cache.result.wrap(flatMatrixPower(left.matrix, right.scalar, cache));
                } else {
                    throw new UnsupportedOperationException("Power mismatch: Cannot raise " + leftType + " to " + rightType);
                }
                break;

            default:
                throw new UnsupportedOperationException("Operator not implemented: " + op);
        }
        return cache.result;
    }

// Add this helper if you don't have it yet for Scalar * Matrix
    private static Matrix flatMatrixScalarMultiply(double scalar, Matrix m, ResultCache cache) {
        double[] mF = m.getFlatArray();
        Matrix out = cache.getMatrixBuffer(m.getRows(), m.getCols());
        double[] resF = out.getFlatArray();
        for (int i = 0; i < mF.length; i++) {
            resF[i] = scalar * mF[i];
        }
        return out;
    }

    public static EvalResult dispatchMatrixFunction(EvalResult[] args, String funcName, ResultCache cache) {
        switch (funcName) {
            case "matrix_add":
                return cache.result.wrap(flatMatrixAdd(args[0].matrix, args[1].matrix, cache));
            case "matrix_sub":
                return cache.result.wrap(flatMatrixSubtract(args[0].matrix, args[1].matrix, cache));
            case "matrix_mul":
            case "matrix_multiply":
                return cache.result.wrap(flatMatrixMultiply(args[0].matrix, args[1].matrix, cache));
            case "det":
                return cache.result.wrap(args[0].matrix.determinant());
            case "inverse":
            case "invert":
                return cache.result.wrap(args[0].matrix.inverse());
            case "transpose":
                return cache.result.wrap(args[0].matrix.transpose());
            default:
                throw new UnsupportedOperationException("Function: " + funcName);
        }
    }

    public static EvalResult dispatchUnaryOp(EvalResult operand, char op, ResultCache cache) {
        if (op == '²') {
            if (operand.type == EvalResult.TYPE_SCALAR) {
                return cache.result.wrap(operand.scalar * operand.scalar);
            } else {
                return cache.result.wrap(flatMatrixMultiply(operand.matrix, operand.matrix, cache));
            }
        }
        throw new UnsupportedOperationException("Unary op: " + op);
    }

    // ========== MATH KERNELS (ZERO ALLOCATION) ==========
    private static EvalResult binaryOpScalar(char op, double a, double b, ResultCache cache) {
        switch (op) {
            case '+':
                return cache.result.wrap(a + b);
            case '-':
                return cache.result.wrap(a - b);
            case '*':
                return cache.result.wrap(a * b);
            case '/':
                return cache.result.wrap(a / b);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static EvalResult binaryOpMatrix(char op, Matrix left, Matrix right, ResultCache cache) {
        switch (op) {
            case '+':
                return cache.result.wrap(flatMatrixAdd(left, right, cache));
            case '-':
                return cache.result.wrap(flatMatrixSubtract(left, right, cache));
            case '*':
                return cache.result.wrap(flatMatrixMultiply(left, right, cache));
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static Matrix flatMatrixAdd(Matrix a, Matrix b, ResultCache cache) {
        double[] aF = a.getFlatArray(), bF = b.getFlatArray();
        Matrix out = cache.getMatrixBuffer(a.getRows(), a.getCols());
        double[] resF = out.getFlatArray();
        for (int i = 0; i < aF.length; i++) {
            resF[i] = aF[i] + bF[i];
        }
        return out;
    }

    private static Matrix flatMatrixSubtract(Matrix a, Matrix b, ResultCache cache) {
        double[] aF = a.getFlatArray(), bF = b.getFlatArray();
        Matrix out = cache.getMatrixBuffer(a.getRows(), a.getCols());
        double[] resF = out.getFlatArray();
        for (int i = 0; i < aF.length; i++) {
            resF[i] = aF[i] - bF[i];
        }
        return out;
    }

    private static Matrix flatMatrixMultiply(Matrix a, Matrix b, ResultCache cache) {
        int aR = a.getRows(), aC = a.getCols(), bC = b.getCols();
        Matrix out = cache.getMatrixBuffer(aR, bC);
        double[] aF = a.getFlatArray(), bF = b.getFlatArray(), resF = out.getFlatArray();

        for (int i = 0; i < aR; i++) {
            int iRow = i * aC;
            int outRow = i * bC;
            for (int j = 0; j < bC; j++) {
                double s = 0;
                for (int k = 0; k < aC; k++) {
                    s += aF[iRow + k] * bF[k * bC + j];
                }
                resF[outRow + j] = s;
            }
        }
        return out;
    }

    private static Matrix flatMatrixPower(Matrix m, double exponent, ResultCache cache) {
        int p = (int) exponent;
        if (p < 0) {
            throw new UnsupportedOperationException("Negative matrix power not supported.");
        }
        if (p == 0) {
            return identity(m.getRows(), cache);
        }

        // Initial state
        Matrix base = m;
        Matrix res = null;

        while (p > 0) {
            if ((p & 1) == 1) {
                if (res == null) {
                    res = copyToCache(base, cache);
                } else {
                    // Use a temporary allocation-free multiply
                    res = flatMatrixMultiply(res, base, cache);
                }
            }
            if (p > 1) {
                base = flatMatrixMultiply(base, base, cache);
            }
            p >>= 1;
        }
        return res;
    }

    private static Matrix copyToCache(Matrix source, ResultCache cache) {
        Matrix target = cache.getSecondaryBuffer(source.getRows(), source.getCols());
        System.arraycopy(source.getFlatArray(), 0, target.getFlatArray(), 0, source.getFlatArray().length);
        return target;
    }

    private static Matrix identity(int dim, ResultCache cache) {
        Matrix id = cache.getMatrixBuffer(dim, dim);
        double[] data = id.getFlatArray();
        java.util.Arrays.fill(data, 0.0);
        for (int i = 0; i < dim; i++) {
            data[i * dim + i] = 1.0;
        }
        return id;
    }
}
