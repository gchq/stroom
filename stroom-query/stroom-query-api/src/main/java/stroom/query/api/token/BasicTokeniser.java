/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.api.token;

import stroom.query.api.token.QuotedStringToken.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BasicTokeniser {

    private static final char DOUBLE_QUOTE_CHAR = '\"';
    private static final char SINGLE_QUOTE_CHAR = '\'';
    private static final char ESCAPE_CHAR = '\\';
    private static final char[] COMMENT = new char[]{'/', '/'};
    private static final char[] BLOCK_COMMENT_START = new char[]{'/', '*'};
    private static final char[] BLOCK_COMMENT_END = new char[]{'*', '/'};

    private List<Token> tokens;

    private BasicTokeniser(final String string) {
        if (string == null || string.isBlank()) {
            throw new TokenException(null, "Empty query");
        }

        final char[] chars = string.toCharArray();
        final Token unknown = new Token(TokenType.UNKNOWN, chars, 0, chars.length - 1);
        tokens = Collections.singletonList(unknown);

        // Tag quoted strings and comments.
        tokens = extractQuotedTokens(tokens);

        // Add support for parameters.
        tokens = splitParams(tokens);

//        // Tag keywords.
//        TokenType.ALL_KEYWORDS.forEach(token -> {
//            tokens = tagKeyword(token.toString().toLowerCase(Locale.ROOT), token, tokens);
//        });
//
//        // Treat NOT/AND/OR as keywords if they are in an expression to make sure they aren't treated as functions,
//        // e.g. not().
//        List<Token> replaced = new ArrayList<>();
//        TokenType currentKeyword = null;
//        for (final Token token : tokens) {
//            if (TokenType.ALL_KEYWORDS.contains(token.getTokenType())) {
//                currentKeyword = token.getTokenType();
//                replaced.add(token);
//            } else if (currentKeyword != null &&
//                    TokenType.EXPRESSION_KEYWORDS.contains(currentKeyword) &&
//                    TokenType.UNKNOWN.equals(token.getTokenType())) {
//                List<Token> outTokens = new ArrayList<>();
//                outTokens.add(token);
//                outTokens = tagKeyword("and", TokenType.AND, outTokens);
//                outTokens = tagKeyword("or", TokenType.OR, outTokens);
//                outTokens = tagKeyword("not", TokenType.NOT, outTokens);
//                replaced.addAll(outTokens);
//            } else {
//                replaced.add(token);
//            }
//        }
//        tokens = replaced;
//
//        // Treat other conjunctions as keywords.
//        tokens = tagKeyword("in", TokenType.IN, tokens);
//        tokens = tagKeyword("by", TokenType.BY, tokens);
//        tokens = tagKeyword("as", TokenType.AS, tokens);
//        tokens = tagKeyword("between", TokenType.BETWEEN, tokens);
//        tokens = tagKeyword("dictionary", TokenType.DICTIONARY, tokens);
//
//        // Tag functions.
//        tokens = split("([a-z][a-zA-Z]*)(\\()", 1, TokenType.FUNCTION_NAME, tokens);
//
//        // Tag brackets.
//        tokens = split("\\(", 0, TokenType.OPEN_BRACKET, tokens);
//        tokens = split("\\)", 0, TokenType.CLOSE_BRACKET, tokens);
//
//        // Tag null conditions.
//        tokens = split("(^|\\s)(is[\\s]+null)(\\s|$)", 2, TokenType.IS_NULL, tokens);
//        tokens = split("(^|\\s)(is[\\s]+not[\\s]+null)(\\s|$)", 2, TokenType.IS_NOT_NULL, tokens);
//
//        // Tag whitespace.
//        tokens = split("\\s+", 0, TokenType.WHITESPACE, tokens);
//
//        // Tag dates.
//        tokens = split("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z?", 0, TokenType.DATE_TIME, tokens);
//
//        tokens = split(",", 0, TokenType.COMMA, tokens);
//        tokens = split("\\^", 0, TokenType.ORDER, tokens);
//        tokens = split("/", 0, TokenType.DIVISION, tokens);
//        tokens = split("\\*", 0, TokenType.MULTIPLICATION, tokens);
//        tokens = split("%", 0, TokenType.MODULUS, tokens);
//        tokens = split("\\+", 0, TokenType.PLUS, tokens);
//        tokens = split("\\-", 0, TokenType.MINUS, tokens);
//        tokens = split("!=", 0, TokenType.NOT_EQUALS, tokens);
//        tokens = split("<=", 0, TokenType.LESS_THAN_OR_EQUAL_TO, tokens);
//        tokens = split(">=", 0, TokenType.GREATER_THAN_OR_EQUAL_TO, tokens);
//        tokens = split("<", 0, TokenType.LESS_THAN, tokens);
//        tokens = split(">", 0, TokenType.GREATER_THAN, tokens);
//        tokens = split("=", 0, TokenType.EQUALS, tokens);
//
//        // Tag durations.
//        tokens = split("(^|\\s|\\))(\\d+(ns|ms|s|m|h|d|w|M|y))", 2, TokenType.DURATION, tokens);
//        // Tag numbers.
//        tokens = split("(^|\\s|\\))(\\d+(\\.\\d+)?([Ee]-\\d+)?)", 2, TokenType.NUMBER, tokens);
//
//        // Tag everything else as a string.
//        tokens = categorise(TokenType.STRING, tokens);
    }
//
//    public static List<Token> tagKeyword(final String pattern, final TokenType tokenType, final List<Token> tokens) {
//        return split("(^\\s*|[^=]\\s+|\\))(" + pattern + ")(\\s|\\(|$)", 2, tokenType, tokens);
//    }

    public static List<Token> parse(final String string) {
        return new BasicTokeniser(string).tokens;
    }
//
//    public static List<Token> categorise(final TokenType tokenType,
//                                   final List<Token> tokens) {
//        final List<Token> out = new ArrayList<>();
//        for (final Token token : tokens) {
//            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
//                final Token.Builder builder = new Token.Builder()
//                        .tokenType(tokenType)
//                        .chars(token.getChars())
//                        .start(token.getStart())
//                        .end(token.getEnd());
//                out.add(builder.build());
//            } else {
//                out.add(token);
//            }
//        }
//        return out;
//    }
//
//    public static List<Token> split(final String regex,
//                              final int group,
//                              final TokenType tokenType,
//                              final List<Token> tokens) {
//        final List<Token> out = new ArrayList<>();
//        for (final Token token : tokens) {
//            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
//                out.addAll(splitUnknown(regex, group, tokenType, token));
//            } else {
//                out.add(token);
//            }
//        }
//        return out;
//    }
//
//    private static List<Token> splitUnknown(final String regex,
//                                     final int group,
//                                     final TokenType tokenType,
//                                     final Token token) {
//        final Pattern pattern = getPattern(regex);
//        final List<Token> out = new ArrayList<>();
//        final String str = new String(token.getChars(),
//                token.getStart(),
//                token.getEnd() - token.getStart() + 1);
//        final Matcher matcher = pattern.matcher(str);
//
//        int lastPos = 0;
//        while (matcher.find()) {
//            final int start = matcher.start(group);
//            final int end = matcher.end(group);
//            if (start != -1 && end != -1) {
//                if (start > lastPos) {
//                    out.add(new Token.Builder()
//                            .tokenType(token.getTokenType())
//                            .chars(token.getChars())
//                            .start(token.getStart() + lastPos)
//                            .end(token.getStart() + start - 1)
//                            .build());
//                }
//
//                out.add(new Token.Builder()
//                        .tokenType(tokenType)
//                        .chars(token.getChars())
//                        .start(token.getStart() + start)
//                        .end(token.getStart() + end - 1)
//                        .build());
//                lastPos = end;
//            }
//        }
//
//        if (token.getStart() + lastPos <= token.getEnd()) {
//            out.add(new Token.Builder()
//                    .tokenType(token.getTokenType())
//                    .chars(token.getChars())
//                    .start(token.getStart() + lastPos)
//                    .end(token.getEnd())
//                    .build());
//        }
//        return out;
//    }
//
//    private List<Token> splitParam(final String regex,
//                                   final int group,
//                                   final TokenType tokenType,
//                                   final List<Token> tokens) {
//        final Pattern pattern = getPattern(regex);
//        final List<Token> out = new ArrayList<>();
//        for (final Token token : tokens) {
//            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
//                final String str = new String(token.getChars(),
//                        token.getStart(),
//                        token.getEnd() - token.getStart() + 1);
//                final Matcher matcher = pattern.matcher(str);
//
//                int lastPos = 0;
//                while (matcher.find()) {
//                    final int start = matcher.start(group);
//                    final int end = matcher.end(group);
//                    if (start != -1 && end != -1) {
//                        if (start > lastPos) {
//                            out.add(new Token.Builder()
//                                    .tokenType(token.getTokenType())
//                                    .chars(token.getChars())
//                                    .start(token.getStart() + lastPos)
//                                    .end(token.getStart() + start - 1)
//                                    .build());
//                        }
//
//                        out.add(new ParamToken.Builder()
//                                .tokenType(tokenType)
//                                .chars(token.getChars())
//                                .start(token.getStart() + start)
//                                .end(token.getStart() + end - 1)
//                                .build());
//                        lastPos = end;
//                    }
//                }
//
//                if (token.getStart() + lastPos <= token.getEnd()) {
//                    out.add(new Token.Builder()
//                            .tokenType(token.getTokenType())
//                            .chars(token.getChars())
//                            .start(token.getStart() + lastPos)
//                            .end(token.getEnd())
//                            .build());
//                }
//            } else {
//                out.add(token);
//            }
//        }
//        return out;
//    }
//

    private record TokenIndex(int tokenIndex, int charIndex) {


    }

    private record TokenSpan(TokenIndex start, TokenIndex end) {


    }

    public static List<Token> splitParams(final List<Token> tokens) {
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                int lastPos = token.getStart();

                // Break the string into quoted text blocks.
                int dollarStart = -1;
                int bracketStart = -1;
                for (int i = token.getStart(); i <= token.getEnd(); i++) {
                    final char c = token.getChars()[i];
                    if (c == '$') {
                        dollarStart = i;
                    } else if (c == '{') {
                        if (dollarStart >= 0 && dollarStart == i - 1) {
                            bracketStart = i;
                        } else {
                            dollarStart = -1;
                            bracketStart = -1;
                        }
                    } else if (c == '}' && bracketStart >= 0) {
                        if (lastPos < dollarStart) {
                            out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, dollarStart - 1));
                        }
                        out.add(new ParamToken.Builder()
                                .tokenType(TokenType.PARAM)
                                .chars(token.getChars())
                                .start(dollarStart)
                                .end(i)
                                .build());
                        lastPos = i + 1;
                    }
                }

                if (lastPos <= token.getEnd()) {
                    out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, token.getEnd()));
                }

            } else {
                out.add(token);
            }
        }
        return out;
    }

    /**
     * This code is left here in case we decide we want to allow quoted param names.
     *
     * @param tokens
     * @return
     */
    public static List<Token> splitParamsSpan(final List<Token> tokens) {
        final List<TokenSpan> tokenSpans = getSpans(tokens);

        if (tokenSpans.isEmpty()) {
            return tokens;
        }

        final List<Token> out = new ArrayList<>();
        int lastTokenIndex = 0;
        int lastTokenChar = 0;
        for (final TokenSpan tokenSpan : tokenSpans) {
            final TokenIndex start = tokenSpan.start;
            final TokenIndex end = tokenSpan.end;

            // Copy whole tokens before span.
            if (start.tokenIndex > lastTokenIndex) {
                for (int i = lastTokenIndex; i < start.tokenIndex; i++) {
                    final Token token = tokens.get(i);
                    if (lastTokenChar > token.start) {
                        out.add(new Token(token.getTokenType(), token.getChars(), lastTokenChar, token.end));
                    } else {
                        out.add(token);
                    }
                }
            }

            // Break start spanned token if needed.
            final Token token = tokens.get(start.tokenIndex);
            if (token.start < start.charIndex) {
                if (lastTokenChar > token.start) {
                    out.add(new Token(token.getTokenType(), token.getChars(), lastTokenChar, start.charIndex - 1));
                } else {
                    out.add(new Token(token.getTokenType(), token.getChars(), token.start, start.charIndex - 1));
                }
            }

            // Output span.
            out.add(new ParamToken.Builder()
                    .tokenType(TokenType.PARAM)
                    .chars(token.getChars())
                    .start(start.charIndex)
                    .end(end.charIndex)
                    .build());

            lastTokenIndex = end.tokenIndex;
            lastTokenChar = end.charIndex + 1;
        }

        // Output final tokens.
        for (int i = lastTokenIndex; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (lastTokenChar > token.start) {
                out.add(new Token(token.getTokenType(), token.getChars(), lastTokenChar, token.end));
            } else {
                out.add(token);
            }
        }

        return out;
    }

    private static List<TokenSpan> getSpans(final List<Token> tokens) {
        TokenIndex start = null;
        int dollarStart = -1;
        final List<TokenSpan> tokenSpans = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                for (int j = token.getStart(); j <= token.getEnd(); j++) {
                    final char c = token.getChars()[j];
                    if (c == '$') {
                        start = null;
                        dollarStart = j;
                    } else if (c == '{') {
                        if (dollarStart >= 0 && dollarStart == j - 1) {
                            start = new TokenIndex(i, dollarStart);
                        } else {
                            start = null;
                            dollarStart = -1;
                        }
                    } else if (c == '}' && start != null) {
                        final TokenIndex end = new TokenIndex(i, j);
                        tokenSpans.add(new TokenSpan(start, end));
                        start = null;
                        dollarStart = -1;
                    }
                }
            }
        }

        return tokenSpans;
    }

    public static List<Token> extractQuotedTokens(final List<Token> tokens) {
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                // Break the string into quoted text blocks.
                int lastPos = token.getStart();
                boolean inQuote = false;
                boolean escape = false;
                boolean inComment = false;
                boolean inBlockComment = false;
                TokenType outputType = null;
                for (int i = token.getStart(); i <= token.getEnd(); i++) {
                    final char c = token.getChars()[i];
                    if (inQuote) {
                        if (escape) {
                            escape = false;
                        } else {
                            if (c == ESCAPE_CHAR) {
                                escape = true;
                            } else if ((c == DOUBLE_QUOTE_CHAR && outputType == TokenType.DOUBLE_QUOTED_STRING) ||
                                       (c == SINGLE_QUOTE_CHAR && outputType == TokenType.SINGLE_QUOTED_STRING)) {
                                inQuote = false;
                                if (lastPos < i) {
                                    final QuotedStringToken quotedStringToken = new Builder()
                                            .tokenType(outputType)
                                            .chars(token.getChars())
                                            .start(lastPos)
                                            .end(i)
                                            .build();
                                    out.add(quotedStringToken);
                                }
                                lastPos = i + 1;
                            }
                        }
                    } else if (inComment) {
                        if (c == '\n') {
                            inComment = false;
                            out.add(new Token(TokenType.COMMENT, token.getChars(), lastPos, i - 1));
                            lastPos = i;
                        }
                    } else if (inBlockComment) {
                        if (escape) {
                            escape = false;
                        } else {
                            if (c == ESCAPE_CHAR) {
                                escape = true;
                            } else if (testChars(token, BLOCK_COMMENT_END, i)) {
                                inBlockComment = false;
                                out.add(new Token(TokenType.BLOCK_COMMENT, token.getChars(), lastPos, i + 1));
                                lastPos = i + 2;
                            }
                        }
                    } else if (c == DOUBLE_QUOTE_CHAR || c == SINGLE_QUOTE_CHAR) {
                        outputType = c == DOUBLE_QUOTE_CHAR
                                ? TokenType.DOUBLE_QUOTED_STRING
                                : TokenType.SINGLE_QUOTED_STRING;
                        inQuote = true;
                        if (lastPos < i) {
                            out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
                        }
                        lastPos = i;

                    } else if (testChars(token, COMMENT, i)) {
                        // Comment.
                        inComment = true;
                        if (lastPos < i) {
                            out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
                        }
                        lastPos = i;
                    } else if (testChars(token, BLOCK_COMMENT_START, i)) {
                        // Block comment.
                        inBlockComment = true;
                        if (lastPos < i) {
                            out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
                        }
                        lastPos = i;
                    }
                }

                if (lastPos <= token.getEnd()) {
                    TokenType tokenType = TokenType.UNKNOWN;
                    if (inComment) {
                        tokenType = TokenType.COMMENT;
                    } else if (inBlockComment) {
                        tokenType = TokenType.BLOCK_COMMENT;
                    }
                    out.add(new Token(tokenType, token.getChars(), lastPos, token.getEnd()));
                }
            } else {
                out.add(token);
            }
        }
        return out;
    }

    private static boolean testChars(final Token token, final char[] test, final int pos) {
        if (pos + test.length - 1 > token.getEnd()) {
            return false;
        }
        for (int i = 0; i < test.length; i++) {
            final char c1 = token.getChars()[pos + i];
            final char c2 = test[i];
            if (c1 != c2) {
                return false;
            }
        }
        return true;
    }
//
//    private static Pattern getPattern(final String regex) {
//        return PATTERN_CACHE.computeIfAbsent(regex, k ->
//                Pattern.compile(k, Pattern.CASE_INSENSITIVE));
//    }
}
