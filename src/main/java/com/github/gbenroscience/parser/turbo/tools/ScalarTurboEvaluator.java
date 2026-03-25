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

import com.github.gbenroscience.parser.MathExpression;

/**
 *
 * @author GBEMIRO
 */
public class ScalarTurboEvaluator implements TurboExpressionEvaluator {

    private final TurboExpressionEvaluator delegate;
    /**
     * If the number of unique variables in the expression is equal to or
     * greater than
     * {@link ScalarTurboEvaluator#MIN_VAR_COUNT_FOR_ARRAY_BASED_EVALUATOR}, the
     * Array based evaluator will be used
     */
    public static final int MIN_VAR_COUNT_FOR_ARRAY_BASED_EVALUATOR = 15;

    public static final int MAX_ALLOWED_METHOD_ARGS_BY_JVM = 63;

    public ScalarTurboEvaluator(MathExpression me) {
        this(me, useWidening(me.getCachedPostfix()));
    }

    /**
     * USE THIS ONLY IF YOU KNOW WHAT YOU ARE DOING!
     *
     * @param me The root {@link MathExpression}
     * @param useWideningVars If true, you want the turbo evaluator to pass the
     * variables to the evaluator with a widening approach. If false, it will
     * pass it with an array based approach.
     */
    public ScalarTurboEvaluator(MathExpression me, boolean useWideningVars) {
        if (useWideningVars) {
            this.delegate = new ScalarTurboEvaluator2(me);
        } else {
            this.delegate = new ScalarTurboEvaluator1(me);
        }
    }

    @Override
    public FastCompositeExpression compile() throws Throwable {
        return delegate.compile();
    }

    public String getDelegateClass() {
        return delegate.getClass().getSimpleName();
    }

    public TurboExpressionEvaluator getDelegate() {
        return delegate;
    }

    private static int countVariables(MathExpression.Token[] postfix) {
        int maxIndex = -1;
        for (MathExpression.Token t : postfix) {
            if (t.isVariable()) {
                maxIndex = Math.max(maxIndex, t.frameIndex);
            }
        }
        return maxIndex + 1;
    }

    private static boolean useWidening(MathExpression.Token[] postfix) {
        int varCount = countVariables(postfix);
        if (varCount > MAX_ALLOWED_METHOD_ARGS_BY_JVM) {//use array based if more than 63 unique variables are in expression
            return false;
        }
        return varCount < MIN_VAR_COUNT_FOR_ARRAY_BASED_EVALUATOR;
    }

}
