package stroom.query.language;

import stroom.query.language.QuotedStringToken.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokeniser {

    private List<Token> tokens;

    private Tokeniser(final String string) {
        Objects.requireNonNull(string, "Null query");

        char[] chars = string.toCharArray();
        final Token unknown = new Token(TokenType.UNKNOWN, chars, 0, chars.length - 1);
        tokens = Collections.singletonList(unknown);

        // Tag quoted strings.
        extractQuotedTokens(TokenType.DOUBLE_QUOTED_STRING, '\"', '\\');
        extractQuotedTokens(TokenType.SINGLE_QUOTED_STRING, '\'', '\\');

        // Tag commands and functions.
        split("(\\|[\\s]*)([a-z-A-Z_]+)(\\s|$)", 2, TokenType.PIPE_OPERATION);
        split("(^|\\s)([a-z-A-Z_]+)([\\s]*\\()", 2, TokenType.FUNCTION_NAME);

        // Tag brackets.
        split("\\(", 0, TokenType.OPEN_BRACKET);
        split("\\)", 0, TokenType.CLOSE_BRACKET);

        // Tag Comments
        split("//[^\n]*", 0, TokenType.COMMENT);
        split("/\\*[^*]*\\*/", 0, TokenType.BLOCK_COMMENT);

        // Tag whitespace.
        split("(^|\\s)(is[\\s]+null)(\\s|$)", 2, TokenType.IS_NULL);
        split("(^|\\s)(is[\\s]+not[\\s]+null)(\\s|$)", 2, TokenType.IS_NOT_NULL);

        // Tag whitespace.
        split("\\s+", 0, TokenType.WHITESPACE);

        // Tag pipes.
        split("\\|", 0, TokenType.PIPE);

        split("(^|\\s|\\))(and)(\\s|\\(|$)", 2, TokenType.AND);
        split("(^|\\s|\\))(or)(\\s|\\(|$)", 2, TokenType.OR);
        split("(^|\\s|\\))(not)(\\s|\\(|$)", 2, TokenType.NOT);
        split("(^|\\s|\\))(by)(\\s|\\(|$)", 2, TokenType.BY);
        split("(^|\\s|\\))(as)(\\s|\\(|$)", 2, TokenType.AS);
        split(",", 0, TokenType.COMMA);
        split("(^|\\s|\\))(\\^)(\\s|\\(|$)", 2, TokenType.ORDER);
        split("(^|\\s|\\))(/)(\\s|\\(|$)", 2, TokenType.DIVISION);
        split("(^|\\s|\\))(\\*)(\\s|\\(|$)", 2, TokenType.MULTIPLICATION);
        split("(^|\\s|\\))(%)(\\s|\\(|$)", 2, TokenType.MODULUS);
        split("(^|\\s|\\))(\\+)(\\s|\\(|$)", 2, TokenType.PLUS);
        split("(^|\\s|\\))(\\-)(\\s|\\(|$)", 2, TokenType.MINUS);
        split("(^|\\s)(!=)(\\s|$)", 2, TokenType.NOT_EQUALS);
        split("(^|\\s)(<=)(\\s|$)", 2, TokenType.LESS_THAN_OR_EQUAL_TO);
        split("(^|\\s)(>=)(\\s|$)", 2, TokenType.GREATER_THAN_OR_EQUAL_TO);
        split("(^|\\s)(<)(\\s|$)", 2, TokenType.LESS_THAN);
        split("(^|\\s)(>)(\\s|$)", 2, TokenType.GREATER_THAN);
        split("(^|\\s)(=)(\\s|$)", 2, TokenType.EQUALS);

        // Tag numbers.
        split("(^|\\s|\\))([-]?[0-9]+(\\.[0-9]+)?)(\\s|\\(|$)", 2, TokenType.NUMBER);

        // Tag everything else as a string.
        categorise(TokenType.STRING);
    }

    public static List<Token> parse(final String string) {
        return new Tokeniser(string).tokens;
    }

    private void categorise(final TokenType tokenType) {
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                final Token.Builder builder = new Token.Builder()
                        .tokenType(tokenType)
                        .chars(token.getChars())
                        .start(token.getStart())
                        .end(token.getEnd());
                out.add(builder.build());
            } else {
                out.add(token);
            }
        }
        this.tokens = out;
    }

    private void split(final String regex,
                       final int group,
                       final TokenType tokenType) {
        final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                final String str = new String(token.getChars(),
                        token.getStart(),
                        token.getEnd() - token.getStart() + 1);
                final Matcher matcher = pattern.matcher(str);

                int lastPos = 0;
                while (matcher.find()) {
                    final int start = matcher.start(group);
                    final int end = matcher.end(group) - 1;

                    if (start > lastPos) {
                        out.add(new Token(token.getTokenType(),
                                token.getChars(),
                                token.getStart() + lastPos,
                                token.getStart() + start - 1));
                    }

                    out.add(new Token(tokenType, token.getChars(), token.getStart() + start, token.getStart() + end));
                    lastPos = end + 1;
                }

                if (token.getStart() + lastPos <= token.getEnd()) {
                    out.add(new Token(token.getTokenType(),
                            token.getChars(),
                            token.getStart() + lastPos,
                            token.getEnd()));
                }
            } else {
                out.add(token);
            }
        }
        this.tokens = out;
    }

    private void extractQuotedTokens(final TokenType outputType,
                                     final char quoteChar,
                                     final char escapeChar) {
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                // Break the string into quoted text blocks.
                int lastPos = token.getStart();
                boolean inQuote = false;
                boolean escape = false;
                for (int i = token.getStart(); i <= token.getEnd(); i++) {
                    final char c = token.getChars()[i];
                    if (inQuote) {
                        if (escape) {
                            escape = false;
                        } else {
                            if (c == escapeChar) {
                                escape = true;
                            } else if (c == quoteChar) {
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
                    } else if (c == quoteChar) {
                        inQuote = true;
                        if (lastPos < i) {
                            out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
                        }
                        lastPos = i;
                    }
                }

                if (lastPos <= token.getEnd()) {
                    out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, token.getEnd()));
                }
            } else {
                out.add(token);
            }
        }
        this.tokens = out;
    }
}
