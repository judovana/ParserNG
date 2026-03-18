package com.github.gbenroscience.parser.turbo.tools;

import com.github.gbenroscience.math.Maths;
import com.github.gbenroscience.math.matrix.expressParser.Matrix;
import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.MathExpression.EvalResult;
import com.github.gbenroscience.parser.ParserResult;
import com.github.gbenroscience.parser.TYPE;
import com.github.gbenroscience.parser.methods.Declarations;
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
public final class FlatMatrixTurboCompiler implements TurboExpressionCompiler {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // ========== THE RESULT CACHE ==========
    /**
     * Holds the mutable state for a single node in the execution tree. Bound
     * into the MethodHandle chain at compile-time.
     */
    public static final class ResultCache {

        public final EvalResult result = new EvalResult();
        public double[] matrixData;
        public Matrix matrix;

        public double[] eigenValueBuffer;
        // Secondary buffer for re-entrant operations like Matrix Power
        private double[] matrixData2;
        private Matrix matrix2;

        // Secondary buffer for re-entrant operations like Matrix Power
        private double[] matrixData3;
        private Matrix matrix3;

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
         *
         * @param rows
         * @param cols
         * @return
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

        public Matrix getTertiaryBuffer(int rows, int cols) {
            int size = rows * cols;
            if (matrixData3 == null || matrixData3.length != size) {
                matrixData3 = new double[size];
                matrix3 = new Matrix(matrixData3, rows, cols);
            } else if (matrix3.getRows() != rows || matrix3.getCols() != cols) {
                matrix3 = new Matrix(matrixData3, rows, cols);
            }
            return matrix3;
        }

        public double[] getEigenBuffer(int n) {
            if (eigenValueBuffer == null || eigenValueBuffer.length != n) {
                eigenValueBuffer = new double[n];
            }
            return eigenValueBuffer;
        }
    }

    private MathExpression.Token[] postfix;

    public FlatMatrixTurboCompiler(MathExpression.Token[] postfix) {
        this.postfix = postfix;
    }

    // ========== COMPILER CORE ==========
    @Override
    public FastCompositeExpression compile() throws Throwable {

        Stack<MethodHandle> stack = new Stack<>();

        for (MathExpression.Token t : postfix) {
            switch (t.kind) {
                case MathExpression.Token.NUMBER:
                    MethodHandle leaf;
                    if (t.name != null && !t.name.isEmpty()) {
                        // --- Named Reference Path (Matrix or Variable) ---
                        if (t.v != null) {
                            // It's a scalar variable (uses frameIndex)
                            leaf = compileVariableLookupByIndex(t.frameIndex);
                        } else {
                            // It's a Matrix in the FunctionManager
                            Function f = FunctionManager.lookUp(t.name);
                            if (f != null && f.getMatrix() != null) {
                                MathExpression.EvalResult res = new MathExpression.EvalResult();
                                res.wrap(f.getMatrix());
                                // Create constant ()MathExpression.EvalResult and transform to (double[])MathExpression.EvalResult
                                leaf = MethodHandles.constant(MathExpression.EvalResult.class, res);
                                leaf = MethodHandles.dropArguments(leaf, 0, double[].class);
                            } else {
                                // It's a function reference string (e.g., for diff)
                                MathExpression.EvalResult res = new MathExpression.EvalResult();
                                res.wrap(t.name);
                                leaf = MethodHandles.constant(MathExpression.EvalResult.class, res);
                                leaf = MethodHandles.dropArguments(leaf, 0, double[].class);
                            }
                        }
                    } else {
                        // --- Direct Numeric Literal Path ---
                        MathExpression.EvalResult res = new MathExpression.EvalResult();
                        res.wrap(t.value);
                        leaf = MethodHandles.constant(MathExpression.EvalResult.class, res);
                        leaf = MethodHandles.dropArguments(leaf, 0, double[].class);
                    }

                    // CRITICAL: Push to stack so the subsequent OPERATOR/FUNCTION can pop it
                    stack.push(leaf);
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
                    if (t.name.equalsIgnoreCase("print")) {
                        // 1. Pop the evaluated handles off the stack (we don't need them for printing raw names)
                        for (int i = 0; i < t.arity; i++) {
                            stack.pop();
                        }

                        // 2. Resolve the bridge to executeMatrixPrint
                        MethodHandle bridge = LOOKUP.findStatic(FlatMatrixTurboCompiler.class, "executeMatrixPrint",
                                MethodType.methodType(EvalResult.class, String[].class));

                        // 3. Bind the raw string arguments into the handle
                        String[] rawArgs = t.getRawArgs();
                        MethodHandle finalPrintHandle = MethodHandles.insertArguments(bridge, 0, (Object) rawArgs);

                        // 4. Adapt to the required signature: EvalResult(double[])
                        // This makes it compatible with your matrix stack
                        stack.push(MethodHandles.dropArguments(finalPrintHandle, 0, double[].class));
                    } else {
                        // Standard matrix function logic
                        MethodHandle[] args = new MethodHandle[t.arity];
                        for (int i = t.arity - 1; i >= 0; i--) {
                            args[i] = stack.pop();
                        }
                        stack.push(compileMatrixFunction(t, args));
                    }
                    break;
                default:
                    System.out.println("Unknown Token Kind: " + t.kind + " Name: " + t.name);
                    break;
            }
        }

        if (stack.size() != 1) {
            throw new IllegalArgumentException("Invalid postfix stack state.");
        }

        MethodHandle resultHandle = stack.pop();
        final MethodHandle finalHandle = resultHandle.asType(
                MethodType.methodType(EvalResult.class, double[].class));

        return new FastCompositeExpression() {
            @Override
            public EvalResult apply(double[] variables) {
                try {
                    return (EvalResult) finalHandle.invokeExact(variables);
                } catch (Throwable e) {
                    throw new RuntimeException("Turbo matrix execution failed", e);
                }
            }

            @Override
            public double applyScalar(double[] variables) {
                return -1.0;
            }

        };
    }

    private MethodHandle compileVariableLookupByIndex(int frameIndex) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle getter = MethodHandles.lookup().findStatic(
                this.getClass(),
                "getVariableFromFrame",
                MethodType.methodType(EvalResult.class, double[].class, int.class)
        );

        // Bind the frameIndex so the handle only takes double[] variables at runtime
        return MethodHandles.insertArguments(getter, 1, frameIndex);
    }

    /**
     * Runtime helper: pulls a value from the frame and wraps it in an
     * EvalResult. This is called by the MethodHandle tree during apply().
     */
    private static EvalResult getVariableFromFrame(double[] variables, int index) {
        MathExpression.EvalResult res = new MathExpression.EvalResult();
        // Safety check for array bounds
        double val = (index < variables.length) ? variables[index] : 0.0;
        res.wrap(val);
        return res;
    }

    /**
     * Compiles a scalar variable lookup into a MethodHandle that pulls from the
     * double[] variables frame at runtime.
     */
    private MethodHandle compileVariableLookup(String name, MathExpression.VariableRegistry registry) throws NoSuchMethodException, IllegalAccessException {
        // 1. Get the index of the variable in the execution frame
        int index = (registry != null) ? registry.getSlot(name) : -1;

        if (index >= 0) {
            // Create a handle that performs: return new EvalResult(variables[index])
            // We use a helper method to handle the wrapping logic cleanly
            MethodHandle getter = MethodHandles.lookup().findStatic(
                    this.getClass(),
                    "getVariableFromFrame",
                    MethodType.methodType(EvalResult.class, double[].class, int.class)
            );

            // Bind the specific index so the handle only needs the double[] at runtime
            return MethodHandles.insertArguments(getter, 1, index);
        } else {
            // Handle undefined variables (perhaps return 0.0 or throw error)
            throw new IllegalArgumentException("Variable '" + name + "' not found in registry.");
        }
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
        //  dispatcher = MethodHandles.collectArguments(dispatcher, 0, left);
        //  dispatcher = MethodHandles.collectArguments(dispatcher, 1, right);
        dispatcher = MethodHandles.filterArguments(dispatcher, 0, left, right);

        // 5. Final type alignment to accept the double[] variables array
        return MethodHandles.permuteArguments(dispatcher,
                MethodType.methodType(EvalResult.class, double[].class), 0, 0);
    }

    private MethodHandle compileUnaryOpOnEvalResult(char op, MethodHandle operand) throws Throwable {
        // 1. Signature: (char, EvalResult, ResultCache) -> EvalResult
        MethodHandle dispatcher = LOOKUP.findStatic(FlatMatrixTurboCompiler.class, "dispatchUnaryOp",
                MethodType.methodType(EvalResult.class, char.class, EvalResult.class, ResultCache.class));

        // 2. Node-specific cache
        ResultCache nodeCache = new ResultCache();

        // 3. Bind 'op' (index 0) and 'nodeCache' (index 2)
        // Resulting signature: (EvalResult) -> EvalResult
        dispatcher = MethodHandles.insertArguments(dispatcher, 2, nodeCache);
        dispatcher = MethodHandles.insertArguments(dispatcher, 0, op);

        // 4. Combine: dispatcher(operand(double[]))
        // This pipes the output of the operand handle into the dispatcher.
        // Resulting signature: (double[]) -> EvalResult
        return MethodHandles.collectArguments(dispatcher, 0, operand);
    }

    /**
     * Bridge for the matrix compiler to call the scalar print logic.
     *
     * @param args
     */
    public static EvalResult executeMatrixPrint(String[] args) throws Throwable {
        // Call your existing logic
        double result = ScalarTurboCompiler.executePrint(args);
        // Wrap the -1.0 (or whatever double) into a scalar result
        return new EvalResult().wrap(result);
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
                }
                break;

            case '-':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(left.scalar - right.scalar);
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_MATRIX) {
                    cache.result.wrap(flatMatrixSubtract(left.matrix, right.matrix, cache));
                }
                break;

            case '*':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(left.scalar * right.scalar);
                } else if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_MATRIX) {
                    cache.result.wrap(flatMatrixScalarMultiply(left.scalar, right.matrix, cache));
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(flatMatrixScalarMultiply(right.scalar, left.matrix, cache));
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_MATRIX) {
                    // Buffer management for matrix multiplication
                    Matrix out = cache.getMatrixBuffer(left.matrix.getRows(), right.matrix.getCols());
                    cache.result.wrap(flatMatrixMultiply(left.matrix, right.matrix, out));
                }
                break;

            case '/':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    // Scalar / Scalar
                    cache.result.wrap(left.scalar / right.scalar);
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_MATRIX) {
                    // Matrix / Matrix (A * B^-1)
                    double det = right.matrix.determinant();
                    if (Math.abs(det) < 1e-15) {
                        throw new ArithmeticException("Matrix B is singular");
                    }

                    Matrix adjB = right.matrix.adjoint();
                    Matrix invB = flatMatrixScalarMultiply(1.0 / det, adjB, cache);
                    Matrix out = cache.getMatrixBuffer(left.matrix.getRows(), invB.getCols());
                    cache.result.wrap(flatMatrixMultiply(left.matrix, invB, out));
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_SCALAR) {
                    // Matrix / Scalar (A * (1/s))
                    if (Math.abs(right.scalar) < 1e-15) {
                        throw new ArithmeticException("Division by zero scalar");
                    }
                    cache.result.wrap(flatMatrixScalarMultiply(1.0 / right.scalar, left.matrix, cache));
                } else if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_MATRIX) {
                    // Scalar / Matrix (s * B^-1)
                    double det = right.matrix.determinant();
                    if (Math.abs(det) < 1e-15) {
                        throw new ArithmeticException("Matrix is singular");
                    }

                    Matrix adjB = right.matrix.adjoint();
                    // Multiply s * (1/det) then apply to the adjoint matrix
                    cache.result.wrap(flatMatrixScalarMultiply(left.scalar / det, adjB, cache));
                }
                break;

            case '^':
                if (leftType == EvalResult.TYPE_SCALAR && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(Math.pow(left.scalar, right.scalar));
                } else if (leftType == EvalResult.TYPE_MATRIX && rightType == EvalResult.TYPE_SCALAR) {
                    cache.result.wrap(flatMatrixPower(left.matrix, right.scalar, cache));
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

    /**
     * Efficiently fills a flat-array modal matrix with eigenvectors. Maps each
     * eigenvector v to a column in the modal matrix: modalMatrix[row, col] =
     * v[row]
     */
    private static void fillEigenVectorMatrix(Matrix source, Matrix modalMatrix) {
        double[] eigenValues = source.computeEigenValues();
        int n = eigenValues.length;
        double[] modalF = modalMatrix.getFlatArray();

        for (int col = 0; col < n; col++) {
            double lambda = eigenValues[col];
            // v is a normalized eigenvector for the current eigenvalue
            double[] v = source.computeEigenVector(lambda);

            // Map the vector v as a COLUMN in our flat modalMatrix
            for (int row = 0; row < n; row++) {
                // Index in flat array: row * totalCols + currentCol
                modalF[row * n + col] = v[row];
            }
        }
    }

    private static void fillMinor(double[] src, double[] dst, int rowExc, int colExc, int n) {
        int d = 0;
        for (int i = 0; i < n; i++) {
            if (i == rowExc) {
                continue;
            }
            int rowOff = i * n;
            for (int j = 0; j < n; j++) {
                if (j == colExc) {
                    continue;
                }
                dst[d++] = src[rowOff + j];
            }
        }
    }

    private static double computeDeterminantTurbo(Matrix minor, ResultCache cache) {
        int n = minor.getRows();
        if (n == 1) {
            return minor.getFlatArray()[0];
        }

        // Get a scratchpad so we don't ruin the minor buffer
        Matrix work = cache.getTertiaryBuffer(n, n);
        System.arraycopy(minor.getFlatArray(), 0, work.getFlatArray(), 0, n * n);

        double det = 1.0;
        double[] data = work.getFlatArray();

        for (int i = 0; i < n; i++) {
            int pivot = i;
            // Partial Pivoting for stability
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(data[j * n + i]) > Math.abs(data[pivot * n + i])) {
                    pivot = j;
                }
            }

            if (pivot != i) {
                work.swapRow(i, pivot);
                det *= -1;
            }

            double v = data[i * n + i];
            if (Math.abs(v) < 1e-18) {
                return 0; // Singular
            }
            det *= v;

            for (int row = i + 1; row < n; row++) {
                double factor = data[row * n + i] / v;
                for (int col = i + 1; col < n; col++) {
                    data[row * n + col] -= factor * data[i * n + col];
                }
            }
        }
        return det;
    }

    private static void solveEquationInto(Matrix input, Matrix solnMatrix, ResultCache cache) {
        int rows = input.getRows();
        int cols = input.getCols();

        // 1. Get a scratchpad for the triangular reduction so we don't mutate input
        Matrix matrixLoader = cache.getSecondaryBuffer(rows, cols);
        System.arraycopy(input.getFlatArray(), 0, matrixLoader.getFlatArray(), 0, rows * cols);

        // 2. Perform In-Place Triangular Reduction
        matrixLoader.reduceToTriangularMatrixInPlace();

        double[] mArr = matrixLoader.getFlatArray();
        double[] sArr = solnMatrix.getFlatArray();

        // 3. Back-Substitution (Optimized for Flat Arrays)
        for (int row = rows - 1; row >= 0; row--) {
            double sum = 0;
            // Sum products of known values
            for (int col = row + 1; col < cols - 1; col++) {
                sum += mArr[row * cols + col] * sArr[col];
            }
            // solve: x = (B - sum) / coefficient
            double b = mArr[row * cols + (cols - 1)];
            double coefficient = mArr[row * cols + row];
            sArr[row] = (b - sum) / coefficient;
        }
    }

    public static EvalResult dispatchMatrixFunction(EvalResult[] args, String funcName, ResultCache cache) {
        switch (funcName) {
            case Declarations.MATRIX_ADD:
                return cache.result.wrap(flatMatrixAdd(args[0].matrix, args[1].matrix, cache));
            case Declarations.MATRIX_SUBTRACT:
                return cache.result.wrap(flatMatrixSubtract(args[0].matrix, args[1].matrix, cache));
            case Declarations.MATRIX_MULTIPLY:
            case "matrix_multiply":
                Matrix out = cache.getMatrixBuffer(args[0].matrix.getRows(), args[1].matrix.getCols());
                return cache.result.wrap(flatMatrixMultiply(args[0].matrix, args[1].matrix, out));
            case Declarations.DETERMINANT:
                return cache.result.wrap(args[0].matrix.determinant());
            case Declarations.INVERSE_MATRIX:
                return cache.result.wrap(args[0].matrix.inverse());
            case Declarations.MATRIX_TRANSPOSE:
                return cache.result.wrap(args[0].matrix.transpose());
            case Declarations.TRIANGULAR_MATRIX:
                args[0].matrix.reduceToTriangularMatrixInPlace();
                return cache.result.wrap(args[0].matrix);
            case Declarations.MATRIX_EIGENVALUES:
                // 1. Get raw data and dimensions
                double[] inputData = args[0].matrix.getFlatArray();
                int rows = args[0].matrix.getRows();

                // 2. Compute using the static provider (ThreadLocal internally)
                // This returns a raw flat array: [Re, Im, Re, Im...]
                Matrix evals = EigenProvider.getEigenvalues(rows, inputData);
                return cache.result.wrap(evals);

            case Declarations.MATRIX_EIGENVEC:
                // 1. Get raw data and dimensions
                double[] inputDataVec = args[0].matrix.getFlatArray();
                int n = args[0].matrix.getRows();

                // 2. Compute the Modal Matrix (N x N)
                // EigenEngineTurbo handles the Householder/QR/Back-substitution
                Matrix modalMatrix = EigenProvider.getEigenvectors(n, inputDataVec);
                return cache.result.wrap(modalMatrix);
            case Declarations.LINEAR_SYSTEM:
                Matrix input;
                if (args.length == 1) {
                    // High-frequency path: Matrix variable provided
                    if (args[0] == null || args[0].matrix == null) {
                        return cache.result.wrap(ParserResult.UNDEFINED_ARG);
                    }
                    input = args[0].matrix;
                } else {
                    // Inline mode: coefficients passed as individual arguments
                    n = (int) ((-1 + Math.sqrt(1 + 4 * args.length)) / 2.0);
                    input = cache.getMatrixBuffer(n, n + 1);
                    double[] flat = input.getFlatArray();
                    for (int i = 0; i < args.length; i++) {
                        flat[i] = args[i].scalar;
                    }
                }

                int nRows = input.getRows();
                Matrix result = cache.getMatrixBuffer(nRows, 1);

                // Core numerical execution
                solveEquationInto(input, result, cache);

                return cache.result.wrap(result);
            case Declarations.MATRIX_COFACTORS:
                input = args[0].matrix;
                if (!input.isSquareMatrix()) {
                    throw new ArithmeticException("Cofactor matrix requires a square matrix.");
                }

                n = input.getRows();
                result = cache.getMatrixBuffer(n, n);
                double[] inData = input.getFlatArray();
                double[] outData = result.getFlatArray();

                // Minor scratchpad: (n-1) * (n-1)
                Matrix minorBuf = cache.getSecondaryBuffer(n - 1, n - 1);

                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        // Fill minorBuf with the submatrix (omitting row i, col j)
                        fillMinor(inData, minorBuf.getFlatArray(), i, j, n);

                        // Calculate determinant of the minor using our scratchpad-safe method
                        double minorDet = computeDeterminantTurbo(minorBuf, cache);

                        // Checkerboard sign: + if (i+j) is even, - if odd
                        double sign = ((i + j) % 2 == 0) ? 1.0 : -1.0;
                        outData[i * n + j] = sign * minorDet;
                    }
                }
                return cache.result.wrap(result);
            case Declarations.MATRIX_ADJOINT:
                input = args[0].matrix; // This comes from the previous MethodHandle in the tree
                n = input.getRows();

                // Primary buffer for the final Adjoint result
                Matrix adjoint = cache.getMatrixBuffer(n, n);
                inData = input.getFlatArray();
                double[] adjData = adjoint.getFlatArray();

                // Use Secondary/Tertiary for the cofactor math
                minorBuf = cache.getSecondaryBuffer(n - 1, n - 1);

                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        // 1. Get the minor
                        fillMinor(inData, minorBuf.getFlatArray(), i, j, n);

                        // 2. Determinant of the minor
                        double minorDet = computeDeterminantTurbo(minorBuf, cache);

                        // 3. Checkerboard sign
                        double sign = ((i + j) % 2 == 0) ? 1.0 : -1.0;

                        /* * THE ADJOINT SECRET: 
             * Instead of placing at [i * n + j], we place at [j * n + i].
             * This transposes the cofactor matrix while we build it.
                         */
                        adjData[j * n + i] = sign * minorDet;
                    }
                }
                return cache.result.wrap(adjoint);
            case Declarations.MATRIX_DIVIDE:
                Matrix A = args[0].matrix;
                Matrix B = args[1].matrix;
                n = B.getRows(); // B must be square
                int m = A.getCols();

                // 1. Create an Augmented Matrix [B | A]
                // Size: n rows, (n + m) columns
                Matrix augmented = cache.getSecondaryBuffer(n, n + m);
                double[] augF = augmented.getFlatArray();
                double[] bF = B.getFlatArray();
                double[] aF = A.getFlatArray();

                // Fill [B] into the left side
                for (int i = 0; i < n; i++) {
                    System.arraycopy(bF, i * n, augF, i * (n + m), n);
                }
                // Fill [A] into the right side
                for (int i = 0; i < n; i++) {
                    System.arraycopy(aF, i * m, augF, i * (n + m) + n, m);
                }

                // 2. Reduce [B | A] to [UpperTriangular | A']
                // We use the same partial pivoting logic
                augmented.reduceToTriangularMatrixInPlace();

                // 3. Back-Substitution for all columns of A' simultaneously
                result = cache.getMatrixBuffer(n, m);
                double[] resF = result.getFlatArray();

                for (int k = 0; k < m; k++) { // For each column in A
                    for (int i = n - 1; i >= 0; i--) {
                        double sum = 0;
                        for (int j = i + 1; j < n; j++) {
                            sum += augF[i * (n + m) + j] * resF[j * m + k];
                        }
                        double b_val = augF[i * (n + m) + n + k];
                        double pivot = augF[i * (n + m) + i];
                        resF[i * m + k] = (b_val - sum) / pivot;
                    }
                }

                return cache.result.wrap(result);
            case Declarations.MATRIX_EDIT:
                Matrix target = args[0].matrix;
                int row = (int) args[1].scalar;
                int col = (int) args[2].scalar;
                double newVal = args[3].scalar;

                rows = target.getRows();
                int cols = target.getCols();

                // Copy target to our result buffer
                Matrix edited = cache.getMatrixBuffer(rows, cols);
                System.arraycopy(target.getFlatArray(), 0, edited.getFlatArray(), 0, rows * cols);

                // Apply the edit
                edited.getFlatArray()[row * cols + col] = newVal;

                return cache.result.wrap(edited);

            default:
                throw new UnsupportedOperationException("Function: " + funcName);
        }
    }

    public static EvalResult dispatchUnaryOp(EvalResult operand, char op, ResultCache cache) {
        if (op == '²') {
            if (operand.type == EvalResult.TYPE_SCALAR) {
                return cache.result.wrap(operand.scalar * operand.scalar);
            } else {
                // FIX: Use cache to get the output buffer
                Matrix out = cache.getMatrixBuffer(operand.matrix.getRows(), operand.matrix.getCols());
                return cache.result.wrap(flatMatrixMultiply(operand.matrix, operand.matrix, out));
            }
        }
        throw new UnsupportedOperationException("Unary op: " + op);
    }

    // ========== MATH KERNELS (ZERO ALLOCATION) ==========
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

    private static Matrix flatMatrixMultiply(Matrix a, Matrix b, Matrix outBuffer) {
        int aR = a.getRows(), aC = a.getCols(), bC = b.getCols();
        double[] aF = a.getFlatArray(), bF = b.getFlatArray(), resF = outBuffer.getFlatArray();

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
        return outBuffer;
    }

    private static Matrix flatMatrixPower(Matrix m, double exponent, ResultCache cache) {
        int p = (int) exponent;
        int rows = m.getRows();
        int cols = m.getCols();

        // 1. Handle base cases
        if (p < 0) {
            throw new UnsupportedOperationException("Negative matrix power not supported.");
        }
        if (p == 0) {
            return identity(rows, cache);
        }
        if (p == 1) {
            return copyToCache(m, cache);
        }

        // 2. Initialize 'res' as Identity Matrix
        // We start 'res' as identity so we can multiply the base into it
        Matrix res = identity(rows, new ResultCache());

        // 3. 'base' starts as a copy of the input so we don't mutate the original
        Matrix base = new Matrix(m.getFlatArray().clone(), rows, cols);

        while (p > 0) {
            if ((p & 1) == 1) {
                // result = result * base
                // We create a new Matrix for the result to avoid I/O collision
                res = flatMatrixMultiply(res, base, new Matrix(new double[rows * cols], rows, cols));
            }

            p >>= 1;

            if (p > 0) {
                // base = base * base (Squaring step)
                // CRITICAL: We MUST use a fresh array here so the multiplication 
                // doesn't read and write from the same flat array.
                base = flatMatrixMultiply(base, base, new Matrix(new double[rows * cols], rows, cols));
            }
        }

        // 4. Move the final result into the provided cache for the engine to return
        Matrix finalBuffer = cache.getMatrixBuffer(rows, cols);
        System.arraycopy(res.getFlatArray(), 0, finalBuffer.getFlatArray(), 0, rows * cols);

        return finalBuffer;
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

    public static final class EigenProvider {

        /**
         * An internal engine instance that handles the high-performance
         * numerical algorithms.
         */
        private static final EigenEngineTurbo ENGINE = new EigenEngineTurbo();

        /**
         * Returns the eigenvalues of a matrix. * Since eigenvalues can be
         * complex, this returns an n x 2 Matrix where Column 0 is the Real part
         * and Column 1 is the Imaginary part.
         *
         * @param n The dimension of the square matrix (n x n).
         * @param matrixData The flat 1D array of the matrix data.
         * @return A Matrix object of size n x 2.
         */
        public static Matrix getEigenvalues(int n, double[] matrixData) {
            // ENGINE returns [r1, i1, r2, i2, ... rN, iN] (length 2n)
            double[] evals = ENGINE.getEigenvalues(n, matrixData);

            // Wrap the 2n array into an n-row, 2-column Matrix
            return new Matrix(evals, n, 2);
        }

        /**
         * Returns the eigenvectors of a matrix as a square Matrix. * This
         * method is "Turbo" optimized because it computes eigenvalues once and
         * passes them directly to the vector solver, avoiding the expensive QR
         * algorithm repetition.
         *
         * * @param n The dimension of the square matrix (n x n).
         * @param matrixData The flat 1D array of the matrix data.
         * @return A Matrix object of size n x n where columns are eigenvectors.
         */
        public static Matrix getEigenvectors(int n, double[] matrixData) {
            // Instantiate a temporary Matrix object to access the optimized 
            // compute/get methods logic.
            Matrix m = new Matrix(matrixData, n, n);

            // 1. Calculate eigenvalues required for the vector shift
            double[] evals = m.computeEigenValues();

            // 2. Generate the n x n eigenvector matrix using the values above
            return m.getEigenVectorMatrix(evals);
        }
    }
}
