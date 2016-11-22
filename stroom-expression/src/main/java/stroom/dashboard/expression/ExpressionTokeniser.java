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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ExpressionTokeniser {

    public List<Token> parse(final String expression) throws ParseException {
        List<Token> tokens = new ArrayList<>();

        if (expression != null) {
            final char[] chars = expression.toCharArray();

            // Tokenise
            tokens.add(new Token(Token.Type.UNKNOWN, chars, 0, chars.length));

            // Extract out string tokens.
            tokens = extractStringTokens(tokens);

            // Extract field references.
            tokens = extractFieldTokens(tokens);

            // Extract functions.
            tokens = extractFunctionTokens(tokens);

        }

        return tokens;
    }

    private List<Token> extractStringTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();

        for (final Token token : input) {
            if (Token.Type.UNKNOWN.equals(token.type)) {
                extractStringToken(token, output, sb);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractStringToken(final Token token, final List<Token> output, final StringBuilder sb) {
        boolean inQuote = false;
        int offset = token.offset;
        for (int i = token.offset; i < token.len; i++) {
            if (token.expression[i] == '\'') {
                // If we are in a quote and get two quotes together then this is
                // an escaped quote.
                if (inQuote && i + 1 < token.len && token.expression[i + 1] == '\'') {
                    sb.append(token.expression[i]);
                    i++;
                } else {
                    inQuote = !inQuote;

                    final String value = sb.toString();
                    sb.setLength(0);

                    if (!inQuote) {
                        output.add(new Token(Token.Type.STRING, token.expression, offset, value.length()));
                    } else if (value.length() > 0) {
                        output.add(new Token(Token.Type.UNKNOWN, token.expression, offset, value.length()));
                    }

                    offset = i;
                }
            } else {
                sb.append(token.expression[i]);
            }
        }

        final String value = sb.toString();
        sb.setLength(0);

        if (value.length() > 0) {
            output.add(new Token(Token.Type.UNKNOWN, token.expression, offset, value.length()));
        }
    }

    private List<Token> extractFieldTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();

        for (final Token token : input) {
            if (Token.Type.UNKNOWN.equals(token.type)) {
                extractFieldToken(token, output, sb);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractFieldToken(final Token token, final List<Token> output, final StringBuilder sb) {
        boolean inRef = false;
        int offset = token.offset;
        for (int i = token.offset; i < token.len; i++) {
            if (!inRef && token.expression[i] == '$' && i + 1 < token.len && token.expression[i + 1] == '{') {
                inRef = true;

                // Output any existing buffered content.
                final String value = sb.toString();
                sb.setLength(0);
                if (value.length() > 0) {
                    output.add(new Token(Token.Type.UNKNOWN, token.expression, offset, value.length()));
                }

                sb.append(token.expression[i]);

                // Record where the field starts.
                offset = i;

            } else if (inRef && token.expression[i] == '}') {
                inRef = false;

                // The current char is part of the field reference so add it to the buffer.
                sb.append(token.expression[i]);

                // Output field token.
                final String value = sb.toString();
                sb.setLength(0);
                if (value.length() > 0) {
                    output.add(new Token(Token.Type.FIELD, token.expression, offset, value.length()));
                }

                // Record where the field ended.
                offset = i + 1;

            } else {
                sb.append(token.expression[i]);
            }
        }

        // Output any remaining buffered content.
        final String value = sb.toString();
        sb.setLength(0);
        if (value.length() > 0) {
            output.add(new Token(Token.Type.UNKNOWN, token.expression, offset, value.length()));
        }
    }

    private List<Token> extractFunctionTokens(final List<Token> input) {
        final List<Token> output = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();

        for (final Token token : input) {
            if (Token.Type.UNKNOWN.equals(token.type)) {
                extractFunctionToken(token, output, sb);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private void extractFunctionToken(final Token token, final List<Token> output, final StringBuilder sb) {
        int offset = token.offset;
        for (int i = token.offset; i < token.len; i++) {
            if (token.expression[i] == '(') {
                // Output any existing buffered content.
                final String value = sb.toString();
                sb.setLength(0);
                if (value.length() > 0) {
                    output.add(new Token(Token.Type.UNKNOWN, token.expression, offset, value.length()));
                }

                sb.append(token.expression[i]);

                // Record where the field starts.
                offset = i + 1;

            } else if (token.expression[i] == ')') {
                // Output any existing buffered content.
                final String value = sb.toString();
                sb.setLength(0);
                if (value.length() > 0) {
                    output.add(new Token(Token.Type.UNKNOWN, token.expression, offset, value.length()));
                }

                output.add(new Token(Token.Type.FUNCTION_END, token.expression, offset, 1));

                // Record where the field ended.
                offset = i + 1;

            } else {
                sb.append(token.expression[i]);
            }
        }

        // Output any remaining buffered content.
        final String value = sb.toString();
        sb.setLength(0);
        if (value.length() > 0) {
            output.add(new Token(Token.Type.UNKNOWN, token.expression, offset, value.length()));
        }
    }


    private static class Token {
        enum Type {
            UNKNOWN, STRING, FIELD, NUMBER, FUNCTION_START, FUNCTION_END, COMMA, ORDER, DIVISION, MULTIPLICATION, ADDITION, SUBTRACTION, EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL_TO, LESS_THAN, LESS_THAN_OR_EQUAL_TO
        }

        private final Type type;
        private final char[] expression;
        private final int offset;
        private final int len;

        Token(final Type type, final char[] expression, final int offset, final int len) {
            this.type = type;
            this.expression = expression;
            this.offset = offset;
            this.len = len;
        }

        @Override
        public String toString() {
            return new String(expression, offset, len);
        }
    }

}
