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

package stroom.dashboard.expression.v1;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

class ExpressionTokeniser {

    private static final EnumSet<Token.Type> VALUE_TYPES = EnumSet.of(
            Token.Type.NUMBER,
            Token.Type.FIELD);

    List<Token> tokenise(final String expression) {
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

            // Convert subtraction followed by a number into a minus number depending on context.
            // Must be done last once all other tokens have been established so we have the context.
            tokens = handleMinusNumbers(tokens);
        }

        return tokens;
    }

    private List<Token> handleMinusNumbers(final List<Token> input) {
        List<Token> output = new ArrayList<>(input);
        int i = 1; // start at one as we need the curr and prev types
        int listSize = output.size();
        while (i < listSize) {
            final Token currToken = output.get(i);
            final Token.Type currType = currToken.getType();
            final Token.Type prevType = i == 0
                    ? null
                    : output.get(i - 1).getType();

            if (Token.Type.NUMBER.equals(currType)
                    && Token.Type.SUBTRACTION.equals(prevType)) {
                // "...-10" or "...-${field}" so now see what came before to establish if "-"
                // is subtraction or negation

                if (!isTypeFoundBeforeThisIndex(Token.Type.NUMBER, output, i - 1)) {
                    // e.g. "add(-10,20)", "add(10,-20)", "10 > -10"
                    output = mergeTokens(output, i - 1, i, Token.Type.NUMBER);
                    // Two list items merged so adjust position and size
                    listSize--;
                    i--;
                }
            }
            i++;
        }
        return output;
    }

    /**
     * @param type   The type to test for
     * @param tokens
     * @param idx    The index of tokens to look for type before this one.
     * @return
     */
    private boolean isTypeFoundBeforeThisIndex(final Token.Type type,
                                               final List<Token> tokens,
                                               final int idx) {
        if (idx != 0) {
            for (int i = idx - 1; i >= 0; i--) {
                final Token.Type currType = tokens.get(i).getType();
                if (Token.Type.WHITESPACE.equals(currType)) {
                    // Whitespace has no meaning so ignore it and move on
                } else {
                    return type.equals(currType);
                }
            }
        }
        return false;
    }

    private List<Token> mergeTokens(final List<Token> input,
                                    final int tokenIdx1,
                                    final int tokenIdx2,
                                    final Token.Type newType) {
        if (tokenIdx2 != tokenIdx1 + 1) {
            throw new RuntimeException("Indexes must be contiguous " + tokenIdx1 + " " + tokenIdx2);
        }

        final List<Token> output = new ArrayList<>(input.size() - 1);

        if (tokenIdx1 > 0) {
            // Add tokens before the pair being merged
            output.addAll(input.subList(0, tokenIdx1));
        }

        final Token mergedToken = Token.merge(newType, input.get(tokenIdx1), input.get(tokenIdx2));
        output.add(mergedToken);

        if (tokenIdx2 < input.size() - 1) {
            // Add tokens after the pair being merged
            output.addAll(input.subList(tokenIdx2 + 1, input.size()));
        }

        return output;
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
        int i = start;
        while (i <= token.end) {
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

                    // Move the start and our position
                    start = i + type.identifier.length;
                    if (type.identifier.length > 1) {
                        // identifier is multi-char so advance position to stop us re-matching
                        // a sub-set of it.
                        i += type.identifier.length - 1;
                    }
                    // matched our token so break out
                    break;
                }
            }
            i++;
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

    static class Token implements Param {

        // refuses to compile if I create the reverse comparator in one step, who knows why
        private static Comparator<Type> IDENTIFIER_LENGTH_COMPARATOR = Comparator.comparing(t -> t.identifier.length);
        private static Comparator<Type> IDENTIFIER_LENGTH_COMPARATOR_REVERSED = IDENTIFIER_LENGTH_COMPARATOR.reversed();

        // array of simple tokens reverse sorted on identifier length
        static final Type[] SIMPLE_TOKENS = Stream.of(
                Type.COMMA,
                Type.ORDER,
                Type.DIVISION,
                Type.MULTIPLICATION,
                Type.MODULUS,
                Type.ADDITION,
                Type.SUBTRACTION,
                Type.EQUALS,
                Type.GREATER_THAN,
                Type.GREATER_THAN_OR_EQUAL_TO,
                Type.LESS_THAN,
                Type.LESS_THAN_OR_EQUAL_TO)
                .sorted(IDENTIFIER_LENGTH_COMPARATOR_REVERSED)
                .toArray(Type[]::new);

        private final Type type;
        private final char[] expression;
        private final int start;
        private final int end;

        /**
         * @param start zero based, inclusive
         * @param end   zero based, inclusive
         */
        Token(final Type type, final char[] expression, final int start, final int end) {
            this.type = type;
            this.expression = expression;
            this.start = start;
            this.end = end;
        }

        Type getType() {
            return type;
        }

        int getStart() {
            return start;
        }

        Token asNewType(final Type type) {
            return new Token(type, expression, start, end);
        }

        static Token merge(final Type newType, final Token token1, final Token token2) {
            if (token2.start != token1.end + 1) {
                throw new RuntimeException("Tokens being merged must be contiguous");
            }
            return new Token(newType, token1.expression, token1.start, token2.end);
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
            MODULUS("%"),
            ADDITION("+"),
            SUBTRACTION("-"),
            EQUALS("="),
            GREATER_THAN(">"),
            GREATER_THAN_OR_EQUAL_TO(">="),
            LESS_THAN("<"),
            LESS_THAN_OR_EQUAL_TO("<=");

            private final char[] identifier;

            Type(final String identifier) {
                this.identifier = identifier.toCharArray();
            }

            public boolean isOneOf(final Type... types) {
                for (final Type type : types) {
                    if (this.equals(type)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
