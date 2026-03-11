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

/**
 *
 * @author GBEMIRO
 */
  
import com.github.gbenroscience.math.Maths;
import com.github.gbenroscience.math.matrix.expressParser.Matrix;
import com.github.gbenroscience.parser.MathExpression;
import java.lang.invoke.*;
import java.util.*;

/**
 * Turbo compiler optimized for ParserNG's flat-array Matrix implementation.
 * 
 * Key optimizations:
 * - Inlines row/col access calculations (row * cols + col) into bytecode
 * - Uses MethodHandles for zero-copy matrix operations
 * - Leverages flat array's cache-friendly memory layout
 * - Generates specialized code paths for common operations
 * 
 * Performance targets:
 * - Scalar: ~5-10ns (unchanged)
 * - Small matrices (2x2 to 4x4): ~50-100ns
 * - Large matrices (100x100): ~1-2 μs (10-50x vs interpreted)
 * 
 * @author GBEMIRO
 */
public class FlatMatrixTurboCompiler implements TurboExpressionCompiler {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    // MethodType constants
    private static final MethodType MT_MATRIX_ACCESS = 
        MethodType.methodType(double.class, double[].class, int.class, int.class, int.class);
    private static final MethodType MT_MATRIX_SET = 
        MethodType.methodType(void.class, double[].class, int.class, int.class, int.class, double.class);
    private static final MethodType MT_MATRIX_MULTIPLY = 
        MethodType.methodType(Matrix.class, Matrix.class, Matrix.class);
    private static final MethodType MT_MATRIX_ADD = 
        MethodType.methodType(Matrix.class, Matrix.class, Matrix.class);

    @Override
    public FastCompositeExpression compile(
            MathExpression.Token[] postfix,
            MathExpression.VariableRegistry registry) throws Throwable {

        Stack<MethodHandle> stack = new Stack<>();
        
        for (MathExpression.Token t : postfix) {
            switch (t.kind) {
                case MathExpression.Token.NUMBER:
                    stack.push(compileNumberAsEvalResult(t));
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
            throw new IllegalArgumentException("Invalid postfix: stack size = " + stack.size());
        }

        MethodHandle resultHandle = stack.pop();
        final MethodType returnType = 
            MethodType.methodType(MathExpression.EvalResult.class, double[].class);
        
        final MethodHandle finalHandle = resultHandle.asType(returnType);

        return (double[] variables) -> {
            try {
                return (MathExpression.EvalResult) finalHandle.invokeExact(variables);
            } catch (Throwable e) {
                throw new RuntimeException("Turbo matrix execution failed", e);
            }
        };
    }

    // ========== COMPILATION PRIMITIVES ==========

    /**
     * Compile a NUMBER token as an EvalResult.
     * For constants: returns constant EvalResult
     * For variables: looks up from execution frame
     */
    private MethodHandle compileNumberAsEvalResult(MathExpression.Token t) throws Throwable {
        if (t.name != null && !t.name.isEmpty()) {
            // Variable lookup
            int frameIndex = t.frameIndex;
            
            MethodHandle loadScalar = MethodHandles.arrayElementGetter(double[].class);
            loadScalar = MethodHandles.insertArguments(loadScalar, 1, frameIndex);
            
            // Wrap in EvalResult
            MethodHandle wrapScalar = LOOKUP.findVirtual(
                MathExpression.EvalResult.class,
                "wrap",
                MethodType.methodType(MathExpression.EvalResult.class, double.class)
            );
            
            // Create a new EvalResult for each access
            MethodHandle newEvalResult = LOOKUP.findConstructor(
                MathExpression.EvalResult.class,
                MethodType.methodType(void.class)
            );
            
            // Chain: new EvalResult -> wrap(scalar from frame)
            return MethodHandles.foldArguments(
                wrapScalar,
                MethodHandles.filterArguments(loadScalar, 0)
            );
        } else {
            // Constant
            MathExpression.EvalResult constant = new MathExpression.EvalResult().wrap(t.value);
            MethodHandle constantHandle = MethodHandles.constant(MathExpression.EvalResult.class, constant);
            return MethodHandles.dropArguments(constantHandle, 0, double[].class);
        }
    }

    // ========== BINARY OPERATIONS ==========

    /**
     * Compile binary operations that work on EvalResult objects.
     * Dispatches based on type (scalar vs matrix).
     */
    private MethodHandle compileBinaryOpOnEvalResult(
            char op, MethodHandle left, MethodHandle right) throws Throwable {

        // Create a dispatcher that calls the appropriate operation
        MethodHandle dispatcher = LOOKUP.findStatic(
            FlatMatrixTurboCompiler.class,
            "dispatchBinaryOp",
            MethodType.methodType(
                MathExpression.EvalResult.class,
                MathExpression.EvalResult.class,
                MathExpression.EvalResult.class,
                char.class
            )
        );

        // Bind the operator
        dispatcher = MethodHandles.insertArguments(dispatcher, 2, op);

        // Filter arguments: (double[]) -> EvalResult for both left and right
        dispatcher = MethodHandles.filterArguments(dispatcher, 0, left, right);

        // Permute to collapse back: (double[]) -> EvalResult
        return MethodHandles.permuteArguments(
            dispatcher,
            MethodType.methodType(MathExpression.EvalResult.class, double[].class),
            0, 0
        );
    }

    /**
     * Dispatcher for binary operations.
     * Called from compiled code via MethodHandle.
     */
    public static MathExpression.EvalResult dispatchBinaryOp(
            MathExpression.EvalResult left,
            MathExpression.EvalResult right,
            char op) {

        // Both scalars: fast path
        if (left.type == MathExpression.EvalResult.TYPE_SCALAR &&
            right.type == MathExpression.EvalResult.TYPE_SCALAR) {
            return binaryOpScalar(op, left.scalar, right.scalar);
        }

        // Both matrices
        if (left.type == MathExpression.EvalResult.TYPE_MATRIX &&
            right.type == MathExpression.EvalResult.TYPE_MATRIX) {
            return binaryOpMatrix(op, left.matrix, right.matrix);
        }

        // Scalar-matrix broadcast
        if (left.type == MathExpression.EvalResult.TYPE_SCALAR &&
            right.type == MathExpression.EvalResult.TYPE_MATRIX) {
            return binaryOpScalarBroadcast(op, left.scalar, right.matrix);
        }

        if (left.type == MathExpression.EvalResult.TYPE_MATRIX &&
            right.type == MathExpression.EvalResult.TYPE_SCALAR) {
            return binaryOpMatrixScalar(op, left.matrix, right.scalar);
        }

        throw new RuntimeException("Type mismatch for operator: " + op);
    }

    /**
     * Scalar-scalar binary operations (unchanged from ScalarTurboCompiler)
     */
    private static MathExpression.EvalResult binaryOpScalar(char op, double a, double b) {
        MathExpression.EvalResult res = new MathExpression.EvalResult();
        switch (op) {
            case '+': res.wrap(a + b); break;
            case '-': res.wrap(a - b); break;
            case '*': res.wrap(a * b); break;
            case '/': 
                if (b == 0) throw new ArithmeticException("Division by zero");
                res.wrap(a / b);
                break;
            case '%': res.wrap(a % b); break;
            case '^': res.wrap(Math.pow(a, b)); break;
            default: throw new UnsupportedOperationException("Op: " + op);
        }
        return res;
    }

    /**
     * Matrix-matrix binary operations.
     * Optimized for flat array layout.
     */
    private static MathExpression.EvalResult binaryOpMatrix(char op, Matrix left, Matrix right) {
        MathExpression.EvalResult res = new MathExpression.EvalResult();

        switch (op) {
            case '+':
                res.wrap(flatMatrixAdd(left, right));
                break;

            case '-':
                res.wrap(flatMatrixSubtract(left, right));
                break;

            case '*':
                // Matrix multiplication - uses flat array for efficiency
                res.wrap(flatMatrixMultiply(left, right));
                break;

            case '^':
                // Matrix power
                int power = (int) Math.round(right.getElem(0, 0));
                res.wrap(flatMatrixPower(left, power));
                break;

            default:
                throw new UnsupportedOperationException("Matrix op: " + op);
        }
        return res;
    }

    /**
     * Scalar-matrix broadcast: scalar OP matrix
     */
    private static MathExpression.EvalResult binaryOpScalarBroadcast(char op, double scalar, Matrix matrix) {
        MathExpression.EvalResult res = new MathExpression.EvalResult();
        
        double[] flatData = matrix.getFlatArray();
        double[] result = new double[flatData.length];
        
        switch (op) {
            case '+':
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = scalar + flatData[i];
                }
                break;
            case '-':
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = scalar - flatData[i];
                }
                break;
            case '*':
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = scalar * flatData[i];
                }
                break;
            case '/':
                if (Math.abs(scalar) < 1e-10) throw new ArithmeticException("Division by zero");
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = scalar / flatData[i];
                }
                break;
            default:
                throw new UnsupportedOperationException("Scalar-broadcast op: " + op);
        }
        
        res.wrap(new Matrix(result, matrix.getRows(), matrix.getCols()));
        return res;
    }

    /**
     * Matrix-scalar binary operations: matrix OP scalar
     */
    private static MathExpression.EvalResult binaryOpMatrixScalar(char op, Matrix matrix, double scalar) {
        MathExpression.EvalResult res = new MathExpression.EvalResult();
        
        double[] flatData = matrix.getFlatArray();
        double[] result = new double[flatData.length];
        
        switch (op) {
            case '+':
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = flatData[i] + scalar;
                }
                break;
            case '-':
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = flatData[i] - scalar;
                }
                break;
            case '*':
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = flatData[i] * scalar;
                }
                break;
            case '/':
                if (Math.abs(scalar) < 1e-10) throw new ArithmeticException("Division by zero");
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = flatData[i] / scalar;
                }
                break;
            case '^':
                for (int i = 0; i < flatData.length; i++) {
                    result[i] = Math.pow(flatData[i], scalar);
                }
                break;
            default:
                throw new UnsupportedOperationException("Matrix-scalar op: " + op);
        }
        
        res.wrap(new Matrix(result, matrix.getRows(), matrix.getCols()));
        return res;
    }

    // ========== FLAT ARRAY MATRIX OPERATIONS ==========

    /**
     * Optimized flat-array matrix addition.
     * Leverage flat array's sequential memory access for cache efficiency.
     */
    private static Matrix flatMatrixAdd(Matrix a, Matrix b) {
        if (a.getRows() != b.getRows() || a.getCols() != b.getCols()) {
            throw new IllegalArgumentException("Dimension mismatch for addition");
        }

        double[] aFlat = a.getFlatArray();
        double[] bFlat = b.getFlatArray();
        double[] result = new double[aFlat.length];

        // Single loop over flat array - excellent cache locality
        for (int i = 0; i < aFlat.length; i++) {
            result[i] = aFlat[i] + bFlat[i];
        }

        return new Matrix(result, a.getRows(), a.getCols());
    }

    /**
     * Optimized flat-array matrix subtraction.
     */
    private static Matrix flatMatrixSubtract(Matrix a, Matrix b) {
        if (a.getRows() != b.getRows() || a.getCols() != b.getCols()) {
            throw new IllegalArgumentException("Dimension mismatch for subtraction");
        }

        double[] aFlat = a.getFlatArray();
        double[] bFlat = b.getFlatArray();
        double[] result = new double[aFlat.length];

        for (int i = 0; i < aFlat.length; i++) {
            result[i] = aFlat[i] - bFlat[i];
        }

        return new Matrix(result, a.getRows(), a.getCols());
    }

    /**
     * Optimized flat-array matrix multiplication.
     * 
     * Standard algorithm: C[i,j] = sum(A[i,k] * B[k,j]) for all k
     * 
     * Optimizations:
     * - Flat array access: A[i][k] -> aFlat[i*aCols + k]
     * - Single-pass calculation using flat indices
     * - Minimal intermediate allocations
     * 
     * Complexity: O(n^3) but with excellent cache utilization
     */
    private static Matrix flatMatrixMultiply(Matrix a, Matrix b) {
        if (a.getCols() != b.getRows()) {
            throw new IllegalArgumentException(
                "Incompatible dimensions: " + a.getRows() + "x" + a.getCols() +
                " * " + b.getRows() + "x" + b.getCols()
            );
        }

        int aRows = a.getRows();
        int aCols = a.getCols();
        int bCols = b.getCols();

        double[] aFlat = a.getFlatArray();
        double[] bFlat = b.getFlatArray();
        double[] resultFlat = new double[aRows * bCols];

        // i = row of result
        for (int i = 0; i < aRows; i++) {
            // j = column of result
            for (int j = 0; j < bCols; j++) {
                double sum = 0.0;
                
                // k = inner dimension
                for (int k = 0; k < aCols; k++) {
                    // Access: A[i][k] = aFlat[i*aCols + k]
                    // Access: B[k][j] = bFlat[k*bCols + j]
                    sum += aFlat[i * aCols + k] * bFlat[k * bCols + j];
                }
                
                // Result[i][j] = resultFlat[i*bCols + j]
                resultFlat[i * bCols + j] = sum;
            }
        }

        return new Matrix(resultFlat, aRows, bCols);
    }

    /**
     * Matrix exponentiation: A^n using repeated multiplication.
     * Optimized for small n.
     */
    private static Matrix flatMatrixPower(Matrix m, int n) {
        if (n < 0) {
            throw new UnsupportedOperationException("Negative matrix powers not yet supported");
        }

        if (n == 0) {
            // Return identity matrix
            int size = m.getRows();
            if (m.getRows() != m.getCols()) {
                throw new IllegalArgumentException("Identity requires square matrix");
            }
            double[] identity = new double[size * size];
            for (int i = 0; i < size; i++) {
                identity[i * size + i] = 1.0;
            }
            return new Matrix(identity, size, size);
        }

        if (n == 1) {
            return new Matrix(m);
        }

        // Use binary exponentiation for large n
        if (n > 10) {
            return matrixPowerBinary(m, n);
        }

        // Direct multiplication for small n
        Matrix result = new Matrix(m);
        for (int i = 1; i < n; i++) {
            result = flatMatrixMultiply(result, m);
        }
        return result;
    }

    /**
     * Fast matrix exponentiation using binary method: O(log n) multiplications.
     */
    private static Matrix matrixPowerBinary(Matrix base, int exp) {
        if (exp == 1) return new Matrix(base);

        int size = base.getRows();
        if (base.getRows() != base.getCols()) {
            throw new IllegalArgumentException("Power requires square matrix");
        }

        // Identity matrix
        double[] identityFlat = new double[size * size];
        for (int i = 0; i < size; i++) {
            identityFlat[i * size + i] = 1.0;
        }
        Matrix result = new Matrix(identityFlat, size, size);

        Matrix current = new Matrix(base);
        while (exp > 0) {
            if ((exp & 1) == 1) {
                result = flatMatrixMultiply(result, current);
            }
            current = flatMatrixMultiply(current, current);
            exp >>= 1;
        }

        return result;
    }

    // ========== UNARY OPERATIONS ==========

    /**
     * Compile unary operations on EvalResult.
     */
    private MethodHandle compileUnaryOpOnEvalResult(char op, MethodHandle operand) throws Throwable {
        MethodHandle dispatcher = LOOKUP.findStatic(
            FlatMatrixTurboCompiler.class,
            "dispatchUnaryOp",
            MethodType.methodType(
                MathExpression.EvalResult.class,
                MathExpression.EvalResult.class,
                char.class
            )
        );

        dispatcher = MethodHandles.insertArguments(dispatcher, 1, op);
        return MethodHandles.filterArguments(dispatcher, 0, operand);
    }

    /**
     * Dispatcher for unary operations.
     */
    public static MathExpression.EvalResult dispatchUnaryOp(
            MathExpression.EvalResult operand,
            char op) {

        if (operand.type == MathExpression.EvalResult.TYPE_SCALAR) {
            return unaryOpScalar(op, operand.scalar);
        }

        if (operand.type == MathExpression.EvalResult.TYPE_MATRIX) {
            return unaryOpMatrix(op, operand.matrix);
        }

        throw new RuntimeException("Unsupported unary op on type: " + operand.getTypeName());
    }

    /**
     * Scalar unary operations.
     */
    private static MathExpression.EvalResult unaryOpScalar(char op, double val) {
        MathExpression.EvalResult res = new MathExpression.EvalResult();
        switch (op) {
            case '√': res.wrap(Math.sqrt(val)); break;
            case 'R': res.wrap(Math.cbrt(val)); break;
            case '!': res.wrap(Maths.fact(val)); break;
            case '²': res.wrap(val * val); break;
            case '³': res.wrap(val * val * val); break;
            default: throw new UnsupportedOperationException("Unary op: " + op);
        }
        return res;
    }

    /**
     * Matrix unary operations.
     */
    private static MathExpression.EvalResult unaryOpMatrix(char op, Matrix m) {
        MathExpression.EvalResult res = new MathExpression.EvalResult();
        switch (op) {
            case '²':
                res.wrap(flatMatrixMultiply(m, m));
                break;
            case '³': {
                Matrix m2 = flatMatrixMultiply(m, m);
                res.wrap(flatMatrixMultiply(m2, m));
                break;
            }
            default:
                throw new UnsupportedOperationException("Matrix unary op: " + op);
        }
        return res;
    }

    // ========== MATRIX FUNCTIONS ==========

    /**
     * Compile matrix functions: det, inverse, transpose, etc.
     */
    private MethodHandle compileMatrixFunction(
            MathExpression.Token t,
            MethodHandle[] args) throws Throwable {

        String funcName = t.name.toLowerCase();

        MethodHandle dispatcher = LOOKUP.findStatic(
            FlatMatrixTurboCompiler.class,
            "dispatchMatrixFunction",
            MethodType.methodType(
                MathExpression.EvalResult.class,
                MathExpression.EvalResult[].class,
                String.class
            )
        );

        dispatcher = MethodHandles.insertArguments(dispatcher, 1, funcName);

        // Collect arguments into array
        // This is where we'd handle variable arities
        // For now, simplified - you can extend this

        return dispatcher;
    }

    /**
     * Dispatcher for matrix functions.
     * @param args
     * @param funcName
     * @return 
     */
    public static MathExpression.EvalResult dispatchMatrixFunction(
            MathExpression.EvalResult[] args,
            String funcName) {

        MathExpression.EvalResult res = new MathExpression.EvalResult();

        switch (funcName) {
            case "det":
                if (args[0].type != MathExpression.EvalResult.TYPE_MATRIX) {
                    throw new RuntimeException("det() requires matrix argument");
                }
                res.wrap(flatMatrixDeterminant(args[0].matrix));
                break;

            case "invert":
            case "inverse":
                if (args[0].type != MathExpression.EvalResult.TYPE_MATRIX) {
                    throw new RuntimeException("inverse() requires matrix argument");
                }
                res.wrap(flatMatrixInverse(args[0].matrix));
                break;

            case "transpose":
                if (args[0].type != MathExpression.EvalResult.TYPE_MATRIX) {
                    throw new RuntimeException("transpose() requires matrix argument");
                }
                res.wrap(flatMatrixTranspose(args[0].matrix));
                break;

            case "tri_mat":
                if (args[0].type != MathExpression.EvalResult.TYPE_MATRIX) {
                    throw new RuntimeException("tri_mat() requires matrix argument");
                }
                res.wrap(flatMatrixTriangular(args[0].matrix));
                break;

            default:
                throw new UnsupportedOperationException("Function not supported: " + funcName);
        }

        return res;
    }

    /**
     * Determinant calculation using LU decomposition.
     * Optimized for flat arrays.
     */
    private static double flatMatrixDeterminant(Matrix m) {
        if (m.getRows() != m.getCols()) {
            throw new IllegalArgumentException("Determinant requires square matrix");
        }

        // Delegate to existing Matrix.determinant() for now
        // (you could optimize this further with flat-array LU)
        return m.determinant();
    }

    /**
     * Matrix inverse using Gaussian elimination.
     */
    private static Matrix flatMatrixInverse(Matrix m) {
        if (m.getRows() != m.getCols()) {
            throw new IllegalArgumentException("Inverse requires square matrix");
        }

        // Delegate to existing implementation
        return m.inverse();
    }

    /**
     * Matrix transpose.
     * Optimized for flat arrays.
     */
    private static Matrix flatMatrixTranspose(Matrix m) {
        int rows = m.getRows();
        int cols = m.getCols();
        
        double[] original = m.getFlatArray();
        double[] transposed = new double[original.length];

        // A[i][j] in original = original[i*cols + j]
        // A^T[j][i] in transposed = transposed[j*rows + i]

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                transposed[j * rows + i] = original[i * cols + j];
            }
        }

        return new Matrix(transposed, cols, rows);
    }

    /**
     * Triangular matrix reduction.
     */
    private static Matrix flatMatrixTriangular(Matrix m) {
        // Delegate to existing implementation
        return m.reduceToTriangularMatrix();
    }
}