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
package com.github.gbenroscience.parser.turbo;

/**
 *
 * @author GBEMIRO
 */
import com.github.gbenroscience.math.matrix.expressParser.Matrix;
import com.github.gbenroscience.parser.Function;
import com.github.gbenroscience.parser.MathExpression;
import com.github.gbenroscience.parser.methods.Declarations;
import com.github.gbenroscience.parser.turbo.tools.FastCompositeExpression;
import com.github.gbenroscience.parser.turbo.tools.TurboCompilerFactory;
import com.github.gbenroscience.util.FunctionManager;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Random;

public class ParserNGTurboMatrixTest {

    private final Random rand = new Random();
    private final double[] emptyFrame = new double[0];

    @BeforeEach
    void setup() {
        // Clear previous functions to ensure test isolation
        FunctionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5, 10})
    @DisplayName("Turbo Matrix Method Validation")
    void testMatrixMethods(int size) throws Throwable {
        testLinearSolver(size);
        testCofactorAndAdjoint(size);
        testMatrixDivision(size);
        testEigenvalues(size);
    }

    private void testLinearSolver(int n) throws Throwable {
        double[] data = generateRandomArray(n * (n + 1));
        // Create function and embed the matrix
        Matrix m = new Matrix(data, n, n + 1);m.setName("M");
        Function f = new Function(m);
        FunctionManager.add(f);
         MathExpression expr = new MathExpression("linear_sys(M)");
        
        FastCompositeExpression turbo = TurboCompilerFactory.getCompiler(expr.getCachedPostfix())
                                                             .compile(expr.getCachedPostfix(), null);
        
        Matrix res = turbo.applyMatrix(emptyFrame); 
        
        assertNotNull(res);
        assertEquals(n, res.getRows());
        assertEquals(1, res.getCols());
    }

    private void testCofactorAndAdjoint(int n) throws Throwable {
          Matrix m = new Matrix(generateRandomArray(n * n), n, n);m.setName("A");
        FunctionManager.add(new Function(m) );

        // Adjoint test
        MathExpression adjExpr = new MathExpression("adjoint(A)");
        FastCompositeExpression turboAdj = TurboCompilerFactory.getCompiler(adjExpr.getCachedPostfix())
                                                                .compile(adjExpr.getCachedPostfix(), null);
        
        assertEquals(n, turboAdj.applyMatrix(emptyFrame).getRows());

        // Cofactors test
        MathExpression cofExpr = new MathExpression("cofactor(A)");
        FastCompositeExpression turboCof = TurboCompilerFactory.getCompiler(cofExpr.getCachedPostfix())
                                                                .compile(cofExpr.getCachedPostfix(), null);
        
        assertEquals(n, turboCof.applyMatrix(emptyFrame).getRows());
    }

    private void testMatrixDivision(int n) throws Throwable {
        Matrix a = new Matrix(generateRandomArray(n * n), n, n);a.setName("A");
        Matrix b = new Matrix(generateRandomArray(n * n), n, n);b.setName("B");
        FunctionManager.add(new Function(a));
        FunctionManager.add(new Function(b));
        
        MathExpression divExpr = new MathExpression("A / B");
        FastCompositeExpression turboDiv = TurboCompilerFactory.getCompiler(divExpr.getCachedPostfix())
                                                                .compile(divExpr.getCachedPostfix(), null);
        
        assertEquals(n, turboDiv.applyMatrix(emptyFrame).getRows());
    }

    private void testEigenvalues(int n) throws Throwable {
        Matrix e = new Matrix(generateRandomArray(n * n), n, n);e.setName("R");
        FunctionManager.add(new Function(e));
        
        System.out.println("Matrix-"+e);
     
        MathExpression eigenExpr = new MathExpression("eigvalues(R)");
        FastCompositeExpression turbo = TurboCompilerFactory.getCompiler(eigenExpr.getCachedPostfix())
                                                                .compile(eigenExpr.getCachedPostfix(), null);
        
        Matrix res = turbo.applyMatrix(emptyFrame);
        System.out.println("eigValues: "+res);
        assertEquals(1, res.getRows());
        assertEquals(n, res.getCols());
    }

    private double[] generateRandomArray(int size) {
        double[] arr = new double[size];
        for (int i = 0; i < size; i++) {
            arr[i] = 1.0 + (rand.nextDouble() * 10.0);
        }
        return arr;
    }
}