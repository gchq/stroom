/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.expression;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ExpressionTokeniser {
    public List<Token> tokenise(final String expression) {
        List<Token> tokens = new ArrayList<>();

        if (expression != null) {
            final char[] chars = expression.toCharArray();

            // Tokenise
            tokens.add(new Token(Token.Type.UNIDENTIFIED, chars, 0, chars.length - 1));

            // Extract out string tokens.
            tokens = extractStringTokens(tokens);

            // Extract field references.
            tokens = extractFieldTokens(tokens);

            // Extract functions.
            tokens = extractFunctionTokens(tokens);

            // Extract simple tokens such as commas.
            tokens = extractSimpleTokens(tokens);

            // Extract whitespace.
            tokens = extractWhitespaceTokens(tokens);

            // Extract numeric tokens.
            tokens = extractNumericTokens(tokens);
        }

        return tokens;
    }

    private List<Token> extractStringTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        for (final Token token : input) {
            if (Token.Type.UNIDENTIFIED.equals(token.type)) {
                extractStringToken(token, output);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractStringToken(final Token token, final List<Token> output) {
        boolean inQuote = false;
        int start = token.start;
        for (int i = token.start; i <= token.end; i++) {
            if (token.expression[i] == '\'') {
                // If we are in a quote and get two quotes together then this is
                // an escaped quote.
                if (inQuote && i < token.end && token.expression[i + 1] == '\'') {
                    i++;

                } else {
                    if (inQuote) {
                        // Add the identified string.
                        output.add(new Token(Token.Type.STRING, token.expression, start, i));
                        start = i + 1;

                    } else {
                        if (i > start) {
                            // Add any previous as yet unidentified content.
                            output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, i - 1));
                        }
                        start = i;
                    }

                    inQuote = !inQuote;
                }
            }
        }

        // Add any remaining as yet unidentified content.
        if (token.end >= start) {
            output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, token.end));
        }
    }

    private List<Token> extractFieldTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        for (final Token token : input) {
            if (Token.Type.UNIDENTIFIED.equals(token.type)) {
                extractFieldToken(token, output);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractFieldToken(final Token token, final List<Token> output) {
        boolean inRef = false;
        int start = token.start;
        for (int i = token.start; i <= token.end; i++) {
            if (!inRef && token.expression[i] == '$' && i < token.end && token.expression[i + 1] == '{') {
                inRef = true;

                if (i > start) {
                    // Add any previous as yet unidentified content.
                    output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, i - 1));
                }

                // Record where the field starts.
                start = i;

            } else if (inRef && token.expression[i] == '}') {
                inRef = false;

                // Add the field.
                output.add(new Token(Token.Type.FIELD, token.expression, start, i));

                // Record where the field ended.
                start = i + 1;
            }
        }

        // Add any remaining as yet unidentified content.
        if (token.end >= start) {
            output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, token.end));
        }
    }

    private List<Token> extractFunctionTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        for (final Token token : input) {
            if (Token.Type.UNIDENTIFIED.equals(token.type)) {
                extractFunctionToken(token, output);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractFunctionToken(final Token token, final List<Token> output) {
        int start = token.start;
        for (int i = token.start; i <= token.end; i++) {
            if (token.expression[i] == '(') {
                // Track back to find a function name if there is one.
                int functionStart = i;
                for (int j = i - 1; j >= token.start; j--) {
                    if (Character.isLetter(token.expression[j])) {
                        functionStart = j;
                    } else {
                        break;
                    }
                }

                if (functionStart > start) {
                    output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, functionStart - 1));
                }

                output.add(new Token(Token.Type.FUNCTION_START, token.expression, functionStart, i));

                // Move the start.
                start = i + 1;

            } else if (token.expression[i] == ')') {
                if (i > start) {
                    // Add any previous as yet unidentified content.
                    output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, i - 1));
                }

                output.add(new Token(Token.Type.FUNCTION_END, token.expression, i, i));

                // Move the start.
                start = i + 1;
            }
        }

        // Add any remaining as yet unidentified content.
        if (token.end >= start) {
            output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, token.end));
        }
    }

    private List<Token> extractSimpleTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        for (final Token token : input) {
            if (Token.Type.UNIDENTIFIED.equals(token.type)) {
                extractSimpleToken(token, output);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractSimpleToken(final Token token, final List<Token> output) {
        int start = token.start;
        for (int i = token.start; i <= token.end; i++) {
            for (final Token.Type type : Token.SIMPLE_TOKENS) {

                boolean match = true;
                for (int j = 0; j < type.identifier.length; j++) {
                    if (token.expression[i + j] != type.identifier[j]) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    if (i > start) {
                        // Add any previous as yet unidentified content.
                        output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, i - 1));
                    }

                    output.add(new Token(type, token.expression, i, i + type.identifier.length - 1));

                    // Move the start.
                    start = i + type.identifier.length;
                }
            }
        }

        // Add any remaining as yet unidentified content.
        if (token.end >= start) {
            output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, token.end));
        }
    }

    private List<Token> extractWhitespaceTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        for (final Token token : input) {
            if (Token.Type.UNIDENTIFIED.equals(token.type)) {
                extractWhitespaceToken(token, output);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractWhitespaceToken(final Token token, final List<Token> output) {
        int start = token.start;
        boolean inWhitespace = false;
        for (int i = token.start; i <= token.end; i++) {
            if (Character.isWhitespace(token.expression[i])) {
                if (!inWhitespace) {
                    inWhitespace = true;
                    if (i > start) {
                        // Add any previous as yet unidentified content.
                        output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, i - 1));
                    }

                    // Record where the whitespace starts.
                    start = i;
                }

            } else {
                if (inWhitespace) {
                    // Add the whitespace.
                    output.add(new Token(Token.Type.WHITESPACE, token.expression, start, i - 1));

                    // Record where the whitespace ended.
                    start = i;
                    inWhitespace = false;
                }
            }
        }

        // Add any remaining as yet unidentified content or whitespace.
        if (token.end >= start) {
            if (inWhitespace) {
                output.add(new Token(Token.Type.WHITESPACE, token.expression, start, token.end));
            } else {
                output.add(new Token(Token.Type.UNIDENTIFIED, token.expression, start, token.end));
            }
        }
    }

    private List<Token> extractNumericTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        for (final Token token : input) {
            if (Token.Type.UNIDENTIFIED.equals(token.type)) {
                try {
                    new BigDecimal(token.toString());
                    output.add(new Token(Token.Type.NUMBER, token.expression, token.start, token.end));
                } catch (final NumberFormatException e) {
                    output.add(token);
                }
            } else {
                output.add(token);
            }
        }
        return output;
    }

    public static class Token {
        static Type[] SIMPLE_TOKENS = new Type[]{
                Type.COMMA,
                Type.ORDER,
                Type.DIVISION,
                Type.MULTIPLICATION,
                Type.ADDITION,
                Type.SUBTRACTION,
                Type.EQUALS,
                Type.GREATER_THAN,
                Type.GREATER_THAN_OR_EQUAL_TO,
                Type.LESS_THAN,
                Type.LESS_THAN_OR_EQUAL_TO
        };
        private final Type type;
        private final char[] expression;
        private final int start;
        private final int end;

        Token(final Type type, final char[] expression, final int start, final int end) {
            this.type = type;
            this.expression = expression;
            this.start = start;
            this.end = end;
        }

        public Type getType() {
            return type;
        }

        public int getStart() {
            return start;
        }

        @Override
        public String toString() {
            return new String(expression, start, end + 1 - start);
        }

        enum Type {
            UNIDENTIFIED(""),
            WHITESPACE(" "),
            STRING("'"),
            FIELD("${}"),
            NUMBER("\\d"),
            FUNCTION_START("("),
            FUNCTION_END(")"),
            COMMA(","),
            ORDER("^"),
            DIVISION("/"),
            MULTIPLICATION("*"),
            ADDITION("+"),
            SUBTRACTION("-"),
            EQUALS("="),
            GREATER_THAN(">"),
            GREATER_THAN_OR_EQUAL_TO(">="),
            LESS_THAN("<"),
            LESS_THAN_OR_EQUAL_TO("<=");

            private final char[] identifier;

            private Type(final String identifier) {
                this.identifier = identifier.toCharArray();
            }
        }
    }
}
