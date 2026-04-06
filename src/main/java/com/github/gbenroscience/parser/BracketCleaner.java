/*
 * Copyright 2026 GBEMIRO.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gbenroscience.parser;

import com.github.gbenroscience.parser.methods.Method;
import com.github.gbenroscience.util.VariableManager;
import static com.github.gbenroscience.parser.Operator.*;
import static com.github.gbenroscience.parser.Variable.*;
import java.util.*;

/**
 * Removes excess/redundant brackets from a tokenized mathematical expression **in-place**.
 * 
 * Now fully restores your original safety checks:
 * - isVariableString(...)
 * - isAtOperator(...)          ← handles @(x,y) correctly
 * - Method.isMethodName(...) / FUNCTIONS set
 * - isUnaryPreOperator(...)
 * 
 * Also keeps all the improvements you liked:
 * - Safe for function calls like sin(x), log(...), @(x,y)
 * - Removes redundant nested brackets ((x)) → (x)
 * - Removes single-token brackets (x) → x when safe
 * - Removes empty brackets () when safe
 * - Handles deep nesting (((x))) in multiple passes
 * 
 * @author GBEMIRO
 */
public class BracketCleaner {

    // Fast lookup for known functions (covers Method.isMethodName)
    private static final List<String> FUNCTIONS = Arrays.asList(Method.getAllFunctions());

    /**
     * Removes excess brackets **in-place** and returns the same list for convenience.
     * The input list is modified directly.
     */
    public static List<String> removeExcessBrackets(List<String> scanner) {
        if (scanner == null || scanner.isEmpty()) {
            return scanner;
        }

        boolean changed;
        do {
            changed = false;
            // Build pairMap ONCE per full pass (efficient)
            int[] pairMap = buildPairMap(scanner);

            for (int i = 0; i < scanner.size(); i++) {
                if (!"(".equals(scanner.get(i)) || pairMap[i] == -1) {
                    continue;
                }

                int open = i;
                int close = pairMap[i];

                // 1. Redundant nested brackets: ((x)) → (x)  (safe even after @, sin, etc.)
                if (open + 1 < scanner.size() && pairMap[open + 1] == close - 1) {
                    scanner.remove(close);   // outer )
                    scanner.remove(open);    // outer (
                    changed = true;
                    break;                   // restart with fresh pairMap
                }

                // 2. Single token inside: (x) → x
                //    FULL original safety logic restored (this fixes @(x,y) and all your other cases)
                if (close == open + 2) {
                    boolean mustKeepBrackets = false;

                    if (open > 0) {
                        String prev = scanner.get(open - 1);
                        if (isVariableString(prev) ||
                            isAtOperator(prev) ||           // ← handles @ operator
                            FUNCTIONS.contains(prev) ||     // covers Method.isMethodName
                            isUnaryPreOperator(prev)) {
                            mustKeepBrackets = true;
                        }
                    }
                    // if open == 0 → safe to remove (original behaviour)

                    if (!mustKeepBrackets) {
                        scanner.remove(close);
                        scanner.remove(open);
                        changed = true;
                        break;
                    }
                }

                // 3. Empty brackets: () → remove only if not a function / @ / etc.
                if (close == open + 1) {
                    boolean mustKeepBrackets = false;

                    if (open > 0) {
                        String prev = scanner.get(open - 1);
                        if (isVariableString(prev) ||
                            isAtOperator(prev) ||
                            FUNCTIONS.contains(prev) ||
                            isUnaryPreOperator(prev)) {
                            mustKeepBrackets = true;
                        }
                    }

                    if (!mustKeepBrackets) {
                        scanner.remove(close);
                        scanner.remove(open);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);   // Repeat until no more changes (handles deep nesting)

        return scanner;
    }

    /**
     * Builds a map of matching bracket indices.
     */
    private static int[] buildPairMap(List<String> tokens) {
        int n = tokens.size();
        int[] pairMap = new int[n];
        Arrays.fill(pairMap, -1);
        Deque<Integer> stack = new ArrayDeque<>();

        for (int i = 0; i < n; i++) {
            String t = tokens.get(i);
            if ("(".equals(t)) {
                stack.push(i);
            } else if (")".equals(t) && !stack.isEmpty()) {
                int open = stack.pop();
                pairMap[open] = i;
                pairMap[i] = open;
            }
        }
        return pairMap;
    }
 

    /**
     * Test method – run this to verify @(x,y) and all your cases.
     */
    public static void main(String[] args) {
        // Example with @ operator
        List<String> scanner = new MathExpression("v=@(x,y)sin(3*log((((x-3*y),3)))-4)")
                .getScanner();

        System.out.println("Raw scanner:    " + scanner);
        System.out.println("Cleaned scanner:" + removeExcessBrackets(scanner));
    }
}