/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.language.functions;

import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:LineLength")
class TestExpressionTokeniser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExpressionTokeniser.class);

    @TestFactory
    Stream<DynamicTest> testBasic() {
        return List
                .of(
                        // Test string tokenisation.
                        TestCase.of("", "", true, 0),

                        TestCase.of("A", "<STRING>A</STRING>"),
                        TestCase.of("'B'", "<SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING>", 1),

                        TestCase.of("A'B'A'B'A'B'", "<STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING>"),
                        TestCase.of("'B'A'B'A'B'A", "<SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING>"),
                        TestCase.of("A'B'A'B'A'B'A", "<STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING>"),
                        TestCase.of("'B'A'B'A'B'A'B'", "<SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING><STRING>A</STRING><SINGLE_QUOTED_STRING>'B'</SINGLE_QUOTED_STRING>"),

                        TestCase.of("FOO'BAR'FOO'BAR'FOO'BAR'", "<STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING>"),
                        TestCase.of("'BAR'FOO'BAR'FOO'BAR'FOO", "<SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING>"),
                        TestCase.of("FOO'BAR'FOO'BAR'FOO'BAR'FOO", "<STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING>"),
                        TestCase.of("'BAR'FOO'BAR'FOO'BAR'FOO'BAR'", "<SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING><STRING>FOO</STRING><SINGLE_QUOTED_STRING>'BAR'</SINGLE_QUOTED_STRING>"),

                        TestCase.of("ABC", "<STRING>ABC</STRING>"),
                        TestCase.of("ABC'simple string'DEF", "<STRING>ABC</STRING><SINGLE_QUOTED_STRING>'simple string'</SINGLE_QUOTED_STRING><STRING>DEF</STRING>"),
                        TestCase.of("'simple string'DEF'simple string'", "<SINGLE_QUOTED_STRING>'simple string'</SINGLE_QUOTED_STRING><STRING>DEF</STRING><SINGLE_QUOTED_STRING>'simple string'</SINGLE_QUOTED_STRING>"),
                        TestCase.of("'simple string'", "<SINGLE_QUOTED_STRING>'simple string'</SINGLE_QUOTED_STRING>", 1),
                        TestCase.of("'simple string with ''quoted section'''", "<SINGLE_QUOTED_STRING>'simple string with '</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>'quoted section'</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>''</SINGLE_QUOTED_STRING>", 3),

                        TestCase.of("''", "<SINGLE_QUOTED_STRING>''</SINGLE_QUOTED_STRING>", 1),
                        TestCase.of("'''", "<SINGLE_QUOTED_STRING>''</SINGLE_QUOTED_STRING><STRING>'</STRING>"),
                        TestCase.of("''''", "<SINGLE_QUOTED_STRING>''</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>''</SINGLE_QUOTED_STRING>", 2),

                        // Test handling of negation
                        TestCase.of("-10", "<MINUS>-</MINUS><NUMBER>10</NUMBER>", 2),
                        TestCase.of(" -10", "<WHITESPACE> </WHITESPACE><MINUS>-</MINUS><NUMBER>10</NUMBER>", 3),
                        TestCase.of("10 - 10", "<NUMBER>10</NUMBER><WHITESPACE> </WHITESPACE><MINUS>-</MINUS><WHITESPACE> </WHITESPACE><NUMBER>10</NUMBER>", 5),
                        TestCase.of("10+12", "<NUMBER>10</NUMBER><PLUS>+</PLUS><NUMBER>12</NUMBER>", 3),
                        TestCase.of("10+-12", "<NUMBER>10</NUMBER><PLUS>+</PLUS><MINUS>-</MINUS><NUMBER>12</NUMBER>", 4),
                        TestCase.of("12-11", "<NUMBER>12</NUMBER><MINUS>-</MINUS><NUMBER>11</NUMBER>", 3),
                        TestCase.of("-10+20", "<MINUS>-</MINUS><NUMBER>10</NUMBER><PLUS>+</PLUS><NUMBER>20</NUMBER>", 4),
                        TestCase.of("50-10+20", "<NUMBER>50</NUMBER><MINUS>-</MINUS><NUMBER>10</NUMBER><PLUS>+</PLUS><NUMBER>20</NUMBER>", 5),
                        TestCase.of("add(-10,20)", "<FUNCTION_NAME>add</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><MINUS>-</MINUS><NUMBER>10</NUMBER><COMMA>,</COMMA><NUMBER>20</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("add(10,-20)", "<FUNCTION_NAME>add</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><NUMBER>10</NUMBER><COMMA>,</COMMA><MINUS>-</MINUS><NUMBER>20</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("-${val}", "<MINUS>-</MINUS><PARAM>${val}</PARAM>", 2),
                        TestCase.of("(10+12)", "<OPEN_BRACKET>(</OPEN_BRACKET><NUMBER>10</NUMBER><PLUS>+</PLUS><NUMBER>12</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 5),
                        TestCase.of("-(10+12)", "<MINUS>-</MINUS><OPEN_BRACKET>(</OPEN_BRACKET><NUMBER>10</NUMBER><PLUS>+</PLUS><NUMBER>12</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 6),
                        TestCase.of("min(-10,-20, -30)", "<FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><MINUS>-</MINUS><NUMBER>10</NUMBER><COMMA>,</COMMA><MINUS>-</MINUS><NUMBER>20</NUMBER><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><MINUS>-</MINUS><NUMBER>30</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 12),
                        TestCase.of("-'test'", "<MINUS>-</MINUS><SINGLE_QUOTED_STRING>'test'</SINGLE_QUOTED_STRING>", 2),

                        // Test field tokenisation.
                        TestCase.of("${val}", "<PARAM>${val}</PARAM>", 1),
                        TestCase.of("min(${val})", "<FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 4),
                        TestCase.of("max(${val})", "<FUNCTION_NAME>max</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 4),
                        TestCase.of("sum(${val})", "<FUNCTION_NAME>sum</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 4),
                        TestCase.of("min(round(${val}, 4))", "<FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>round</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><NUMBER>4</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET>", 10),
                        TestCase.of("min(roundDay(${val}))", "<FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>roundDay</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("min(roundMinute(${val}))", "<FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>roundMinute</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("ceiling(${val})", "<FUNCTION_NAME>ceiling</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 4),
                        TestCase.of("floor(${val})", "<FUNCTION_NAME>floor</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 4),
                        TestCase.of("ceiling(floor(min(roundMinute(${val}))))", "<FUNCTION_NAME>ceiling</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>floor</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>roundMinute</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET>", 13),
                        TestCase.of("ceiling(floor(min(round(${val}))))", "<FUNCTION_NAME>ceiling</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>floor</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>round</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET>", 13),
                        TestCase.of("max(${val})-min(${val})", "<FUNCTION_NAME>max</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><MINUS>-</MINUS><FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 9),
                        TestCase.of("max(${val})/count()", "<FUNCTION_NAME>max</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><DIVISION>/</DIVISION><FUNCTION_NAME>count</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET>", 8),
                        TestCase.of("round(${val})/(min(${val})+max(${val}))", "<FUNCTION_NAME>round</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><DIVISION>/</DIVISION><OPEN_BRACKET>(</OPEN_BRACKET><FUNCTION_NAME>min</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><PLUS>+</PLUS><FUNCTION_NAME>max</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET><CLOSE_BRACKET>)</CLOSE_BRACKET>", 16),
                        TestCase.of("concat('this is', 'it')", "<FUNCTION_NAME>concat</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><SINGLE_QUOTED_STRING>'this is'</SINGLE_QUOTED_STRING><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><SINGLE_QUOTED_STRING>'it'</SINGLE_QUOTED_STRING><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("concat('it''s a string', 'with a quote')", "<FUNCTION_NAME>concat</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><SINGLE_QUOTED_STRING>'it'</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>'s a string'</SINGLE_QUOTED_STRING><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><SINGLE_QUOTED_STRING>'with a quote'</SINGLE_QUOTED_STRING><CLOSE_BRACKET>)</CLOSE_BRACKET>", 8),
                        TestCase.of("'it''s a string'", "<SINGLE_QUOTED_STRING>'it'</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>'s a string'</SINGLE_QUOTED_STRING>", 2),
                        TestCase.of("stringLength('it''s a string')", "<FUNCTION_NAME>stringLength</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><SINGLE_QUOTED_STRING>'it'</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>'s a string'</SINGLE_QUOTED_STRING><CLOSE_BRACKET>)</CLOSE_BRACKET>", 5),
                        TestCase.of("upperCase('it''s a string')", "<FUNCTION_NAME>upperCase</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><SINGLE_QUOTED_STRING>'it'</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>'s a string'</SINGLE_QUOTED_STRING><CLOSE_BRACKET>)</CLOSE_BRACKET>", 5),
                        TestCase.of("lowerCase('it''s a string')", "<FUNCTION_NAME>lowerCase</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><SINGLE_QUOTED_STRING>'it'</SINGLE_QUOTED_STRING><SINGLE_QUOTED_STRING>'s a string'</SINGLE_QUOTED_STRING><CLOSE_BRACKET>)</CLOSE_BRACKET>", 5),
                        TestCase.of("substring('Hello', 0, 1)", "<FUNCTION_NAME>substring</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><SINGLE_QUOTED_STRING>'Hello'</SINGLE_QUOTED_STRING><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><NUMBER>0</NUMBER><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><NUMBER>1</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 10),
                        TestCase.of("equals(${val}, ${val})", "<FUNCTION_NAME>equals</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${val}</PARAM><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><PARAM>${val}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("greaterThan(1, 0)", "<FUNCTION_NAME>greaterThan</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><NUMBER>1</NUMBER><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><NUMBER>0</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("lessThan(1, 0)", "<FUNCTION_NAME>lessThan</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><NUMBER>1</NUMBER><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><NUMBER>0</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("greaterThanOrEqualTo(1, 0)", "<FUNCTION_NAME>greaterThanOrEqualTo</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><NUMBER>1</NUMBER><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><NUMBER>0</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("lessThanOrEqualTo(1, 0)", "<FUNCTION_NAME>lessThanOrEqualTo</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><NUMBER>1</NUMBER><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><NUMBER>0</NUMBER><CLOSE_BRACKET>)</CLOSE_BRACKET>", 7),
                        TestCase.of("1=0", "<NUMBER>1</NUMBER><EQUALS>=</EQUALS><NUMBER>0</NUMBER>", 3),
                        TestCase.of("decode('fred', 'fr.+', 'freda', 'freddy')", "<FUNCTION_NAME>decode</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><SINGLE_QUOTED_STRING>'fred'</SINGLE_QUOTED_STRING><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><SINGLE_QUOTED_STRING>'fr.+'</SINGLE_QUOTED_STRING><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><SINGLE_QUOTED_STRING>'freda'</SINGLE_QUOTED_STRING><COMMA>,</COMMA><WHITESPACE> </WHITESPACE><SINGLE_QUOTED_STRING>'freddy'</SINGLE_QUOTED_STRING><CLOSE_BRACKET>)</CLOSE_BRACKET>", 13),

                        // Test fields with non letters.
                        TestCase.of("sum(${user-id})", "<FUNCTION_NAME>sum</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${user-id}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 4),
                        TestCase.of("sum(${user id})", "<FUNCTION_NAME>sum</FUNCTION_NAME><OPEN_BRACKET>(</OPEN_BRACKET><PARAM>${user id}</PARAM><CLOSE_BRACKET>)</CLOSE_BRACKET>", 4))
                .stream()
//                .sorted(Comparator.comparing(testCase -> testCase.expression))
                .map(testCase ->
                        DynamicTest.dynamicTest(testCase.toString(), () ->
                                test(testCase)));
    }

    private void test(final TestCase testCase) throws ParseException {
        try {
            final List<Token> tokens = Tokeniser.parse(testCase.expression);
            LOGGER.debug("\"{}\" - {} [{}]",
                    testCase.expression,
                    tokens.size(),
                    tokens.stream()
                            .map(token -> token.getTokenType().toString())
                            .collect(Collectors.joining(", ")));

            if (testCase.expectedTokenCount != null) {
                assertThat(tokens)
                        .hasSize(testCase.expectedTokenCount);
            }

            final StringBuilder sb = new StringBuilder();
            for (final Token token : tokens) {
                sb.append(token.toString());
            }

            // Make sure all the tokens have captured the expression fully.
            assertThat(sb.toString())
                    .isEqualTo(testCase.expectedTokens);


            // Ensure there are no unidentified tokens.
            final TokenGroup tokenGroup = StructureBuilder.create(tokens);
            for (final AbstractToken token : tokenGroup.getChildren()) {
                if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                    throw new ParseException("Unexpected expression content '" + token + "'", token.getStart());
                }
            }

//            // Do some basic validation of the tokens.
//            final ExpressionValidator expressionValidator = new ExpressionValidator();
//            expressionValidator.validate(tokens);

            assertThat(testCase.isFailureExpected)
                    .withFailMessage("Expected failure")
                    .isFalse();
        } catch (final ParseException | TokenException e) {
            if (!testCase.isFailureExpected) {
                throw e;
            }
        }
    }

    private static class TestCase {

        private final String expression;
        private final String expectedTokens;
        private final boolean isFailureExpected;
        private final Integer expectedTokenCount;

        private TestCase(final String expression,
                         final String expectedTokens,
                         final boolean isFailureExpected,
                         final Integer expectedTokenCount) {
            this.expression = expression;
            this.expectedTokens = expectedTokens;
            this.isFailureExpected = isFailureExpected;
            this.expectedTokenCount = expectedTokenCount;
        }

        static TestCase of(final String expression,
                           final String expectedTokens,
                           final boolean isFailureExpected,
                           final Integer expectedTokenCount) {
            return new TestCase(expression, expectedTokens, isFailureExpected, expectedTokenCount);
        }

        static TestCase of(final String expression,
                           final String expectedTokens,
                           final boolean isFailureExpected) {
            return new TestCase(expression, expectedTokens, isFailureExpected, null);
        }

        static TestCase of(final String expression, final String expectedTokens) {
            return new TestCase(expression, expectedTokens, false, null);
        }

        static TestCase of(final String expression,
                           final String expectedTokens,
                           final Integer expectedTokenCount) {
            return new TestCase(expression, expectedTokens, false, expectedTokenCount);
        }

        @Override
        public String toString() {
            return "TestCase{" +
                    "expression='" + expression + '\'' +
                    ", expectedTokens='" + expectedTokens + '\'' +
                    ", isFailureExpected=" + isFailureExpected +
                    ", expectedTokenCount=" + expectedTokenCount +
                    '}';
        }
    }
}
