/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.expression.v1;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionTokeniser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExpressionTokeniser.class);

    @TestFactory
    Stream<DynamicTest> testBasic() throws ParseException {
        return List
                .of(
                        // Test string tokenisation.
                        TestCase.of("", 0),

                        TestCase.of("A", true),
                        TestCase.of("'B'", 1),

                        TestCase.of("A'B'A'B'A'B'", true),
                        TestCase.of("'B'A'B'A'B'A", true),
                        TestCase.of("A'B'A'B'A'B'A", true),
                        TestCase.of("'B'A'B'A'B'A'B'", true),

                        TestCase.of("FOO'BAR'FOO'BAR'FOO'BAR'", true),
                        TestCase.of("'BAR'FOO'BAR'FOO'BAR'FOO", true),
                        TestCase.of("FOO'BAR'FOO'BAR'FOO'BAR'FOO", true),
                        TestCase.of("'BAR'FOO'BAR'FOO'BAR'FOO'BAR'", true),

                        TestCase.of("ABC", true),
                        TestCase.of("ABC'simple string'DEF", true),
                        TestCase.of("'simple string'DEF'simple string'", true),
                        TestCase.of("'simple string'", 1),
                        TestCase.of("'simple string with ''quoted section'''", 1),

                        TestCase.of("''", 1),
                        TestCase.of("'''", true),
                        TestCase.of("''''", 1),

                        // Test handling of negation
                        TestCase.of("-10", 1),
                        TestCase.of(" -10", 2),
                        TestCase.of("10 - 10", 5),
                        TestCase.of("10+12", 3),
                        TestCase.of("10+-12", 3),
                        TestCase.of("12-11", 3),
                        TestCase.of("-10+20", 3),
                        TestCase.of("50-10+20", 5),
                        TestCase.of("add(-10,20)", 5),
                        TestCase.of("add(10,-20)", 5),
                        TestCase.of("-${val}", 2),
                        TestCase.of("(10+12)", 5),
                        TestCase.of("-(10+12)", 6),
                        TestCase.of("min(-10,-20, -30)", 8),
                        TestCase.of("-'test'", 2),

                        // Test field tokenisation.
                        TestCase.of("${val}", 1),
                        TestCase.of("min(${val})", 3),
                        TestCase.of("max(${val})", 3),
                        TestCase.of("sum(${val})", 3),
                        TestCase.of("min(round(${val}, 4))", 8),
                        TestCase.of("min(roundDay(${val}))", 5),
                        TestCase.of("min(roundMinute(${val}))", 5),
                        TestCase.of("ceiling(${val})", 3),
                        TestCase.of("floor(${val})", 3),
                        TestCase.of("ceiling(floor(min(roundMinute(${val}))))", 9),
                        TestCase.of("ceiling(floor(min(round(${val}))))", 9),
                        TestCase.of("max(${val})-min(${val})", 7),
                        TestCase.of("max(${val})/count()", 6),
                        TestCase.of("round(${val})/(min(${val})+max(${val}))", 13),
                        TestCase.of("concat('this is', 'it')", 6),
                        TestCase.of("concat('it''s a string', 'with a quote')", 6),
                        TestCase.of("'it''s a string'", 1),
                        TestCase.of("stringLength('it''s a string')", 3),
                        TestCase.of("upperCase('it''s a string')", 3),
                        TestCase.of("lowerCase('it''s a string')", 3),
                        TestCase.of("substring('Hello', 0, 1)", 9),
                        TestCase.of("equals(${val}, ${val})", 6),
                        TestCase.of("greaterThan(1, 0)", 6),
                        TestCase.of("lessThan(1, 0)", 6),
                        TestCase.of("greaterThanOrEqualTo(1, 0)", 6),
                        TestCase.of("lessThanOrEqualTo(1, 0)", 6),
                        TestCase.of("1=0", 3),
                        TestCase.of("decode('fred', 'fr.+', 'freda', 'freddy')", 12),

                        // Test fields with non letters.
                        TestCase.of("sum(${user-id})", 3),
                        TestCase.of("sum(${user id})", 3))
                .stream()
//                .sorted(Comparator.comparing(testCase -> testCase.expression))
                .map(testCase ->
                        DynamicTest.dynamicTest(testCase.toString(), () ->
                                test(testCase)));
    }

    private void test(final TestCase testCase) throws ParseException {
        final ExpressionTokeniser expressionTokeniser = new ExpressionTokeniser();
        final List<ExpressionTokeniser.Token> tokens = expressionTokeniser.tokenise(testCase.expression);

        LOGGER.debug("\"{}\" - {} [{}]",
                testCase.expression,
                tokens.size(),
                tokens.stream()
                        .map(token -> token.getType().toString())
                        .collect(Collectors.joining(", ")));

        if (testCase.expectedTokenCount != null) {
            assertThat(tokens)
                    .hasSize(testCase.expectedTokenCount);
        }

        final StringBuilder sb = new StringBuilder();
        for (final ExpressionTokeniser.Token token : tokens) {
            sb.append(token.toString());
        }

        // Make sure all the tokens have captured the expression fully.
        assertThat(sb.toString())
                .isEqualTo(testCase.expression);

        try {
            // Do some basic validation of the tokens.
            final ExpressionValidator expressionValidator = new ExpressionValidator();
            expressionValidator.validate(tokens);

            assertThat(testCase.isFailureExpected)
                    .withFailMessage("Expected failure")
                    .isFalse();
        } catch (final ParseException e) {
            if (!testCase.isFailureExpected) {
                throw e;
            }
        }
    }

    private static class TestCase {

        private final String expression;
        private final boolean isFailureExpected;
        private final Integer expectedTokenCount;

        private TestCase(final String expression,
                         final boolean isFailureExpected,
                         final Integer expectedTokenCount) {
            this.expression = expression;
            this.isFailureExpected = isFailureExpected;
            this.expectedTokenCount = expectedTokenCount;
        }

        static TestCase of(final String expression,
                           final boolean isFailureExpected,
                           final Integer expectedTokenCount) {
            return new TestCase(expression, isFailureExpected, expectedTokenCount);
        }

        static TestCase of(final String expression, final boolean isFailureExpected) {
            return new TestCase(expression, isFailureExpected, null);
        }

        static TestCase of(final String expression) {
            return new TestCase(expression, false, null);
        }

        static TestCase of(final String expression, final Integer expectedTokenCount) {
            return new TestCase(expression, false, expectedTokenCount);
        }

        @Override
        public String toString() {
            return
                    "expr: \"" + expression + "\", expFailure: "
                            + isFailureExpected + ", expTokenCnt: "
                            + expectedTokenCount;
        }
    }
}
