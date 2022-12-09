package stroom.query.language;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokeniser {

//    private static final String[] constants = {
//            "now()",
//            "second()",
//            "minute()",
//            "hour()",
//            "day()",
//            "week()",
//            "month()",
//            "year()"
//    };

    public void parse(final String string) throws ParseException {
        Objects.requireNonNull(string, "Null query");


        // Break the string into quoted text blocks.
        final TokenGroup tokenGroup = extractTokens(string);
        System.out.println(tokenGroup.toString());
    }

    public TokenGroup extractTokens(final String string) {
        char[] chars = string.toCharArray();
        final Token unknown = new Token(TokenType.UNKNOWN, chars, 0, chars.length - 1);
        TokenGroup group = new TokenGroup.Builder()
                .tokenType(TokenType.BRACKET_GROUP)
                .chars(chars)
                .start(0)
                .end(chars.length - 1)
                .add(unknown)
                .build();

        group = extract(group, token ->
                extractQuotedTokens(token, TokenType.DOUBLE_QUOTED_STRING, '\"', '\\'));
        group = extract(group, token ->
                extractQuotedTokens(token, TokenType.SINGLE_QUOTED_STRING, '\'', '\\'));


        // Get brackets and extract hierarchy.
        group = extract(group, token ->
                split(token, "\\(", 0, TokenType.OPEN_BRACKET));
        group = extract(group, token ->
                split(token, "\\)", 0, TokenType.CLOSE_BRACKET));
        group = extractHierarchy(group);

        //  Split into pipe groups
        group = extract(group, token ->
                split(token, "\\|", 0, TokenType.PIPE));
        group = extractPipeGroups(group);


        // Comments
        group = extract(group, token ->
                split(token, "//[^\n]*", 0, TokenType.COMMENT));
        group = extract(group, token ->
                split(token, "/\\*[^*]*\\*/", 0, TokenType.BLOCK_COMMENT));

        // Categorise remaining content.
        group = extract(group, token ->
                split(token, "(^|\\s)(where)(\\s|$)", 2, TokenType.WHERE));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(and)(\\s|\\(|$)", 2, TokenType.AND));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(or)(\\s|\\(|$)", 2, TokenType.OR));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(not)(\\s|\\(|$)", 2, TokenType.NOT));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(by)(\\s|\\(|$)", 2, TokenType.BY));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(as)(\\s|\\(|$)", 2, TokenType.AS));
        group = extractFunctions(group, token ->
                split(token, "([a-zA-Z]+)(\\s*$)", 1, TokenType.FUNCTION));
        group = extract(group, token ->
                split(token, ",", 0, TokenType.COMMA));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(\\^)(\\s|\\(|$)", 2, TokenType.ORDER));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(/)(\\s|\\(|$)", 2, TokenType.DIVISION));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(\\*)(\\s|\\(|$)", 2, TokenType.MULTIPLICATION));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(%)(\\s|\\(|$)", 2, TokenType.MODULUS));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(\\+)(\\s|\\(|$)", 2, TokenType.PLUS));
        group = extract(group, token ->
                split(token, "(^|\\s|\\))(\\-)(\\s|\\(|$)", 2, TokenType.MINUS));
        group = extract(group, token ->
                split(token, "(^|\\s)(!=)(\\s|$)", 2, TokenType.NOT_EQUALS));
        group = extract(group, token ->
                split(token, "(^|\\s)(<=)(\\s|$)", 2, TokenType.LESS_THAN_OR_EQUAL_TO));
        group = extract(group, token ->
                split(token, "(^|\\s)(>=)(\\s|$)", 2, TokenType.GREATER_THAN_OR_EQUAL_TO));
        group = extract(group, token ->
                split(token, "(^|\\s)(<)(\\s|$)", 2, TokenType.LESS_THAN));
        group = extract(group, token ->
                split(token, "(^|\\s)(>)(\\s|$)", 2, TokenType.GREATER_THAN));
        group = extract(group, token ->
                split(token, "(^|\\s)(=)(\\s|$)", 2, TokenType.EQUALS));
        group = extract(group, token ->
                split(token, "(^|\\s)(is null)(\\s|$)", 2, TokenType.IS_NULL));
        group = extract(group, token ->
                split(token, "(^|\\s)(is not null)(\\s|$)", 2, TokenType.IS_NOT_NULL));


        group = extract(group, token ->
                split(token, "(^|\\s|\\))([-]?[0-9]+(\\.[0-9]+)?)(\\s|\\(|$)", 2, TokenType.NUMBER));
        group = extract(group, token ->
                split(token, "^\\s+", 0, TokenType.WHITESPACE));
        group = extract(group, token ->
                split(token, "\\s+$", 0, TokenType.WHITESPACE));
        group = extract(group, token ->
                split(token, ".+", 0, TokenType.STRING));


//        for (final String constant : constants) {
//            tokens = split(tokens, constant, TokenType.CONSTANT);
//        }


        return group;
    }

    private TokenGroup extractFunctions(final TokenGroup in,
                                        final Function<Token, List<Token>> function) {
        final TokenGroup.Builder out = new TokenGroup.Builder()
                .tokenType(in.getTokenType())
                .chars(in.getChars())
                .start(in.getStart())
                .end(in.getEnd());

        Token lastToken = null;
        for (final Token token : in.getChildren()) {
            if (TokenType.BRACKET_GROUP.equals(token.getTokenType()) &&
                    lastToken != null &&
                    TokenType.UNKNOWN.equals(lastToken.getTokenType())) {
                out.addAll(function.apply(lastToken));
                lastToken = null;
            }

            if (lastToken != null) {
                out.add(lastToken);
                lastToken = null;
            }

            if (token instanceof TokenGroup) {
                // Recurse.
                final TokenGroup tokenGroup = extractFunctions((TokenGroup) token, function);
                out.add(tokenGroup);
            } else {
                lastToken = token;
            }
        }

        if (lastToken != null) {
            out.add(lastToken);
        }

        return out.build();
    }

    private TokenGroup extract(final TokenGroup in,
                               final Function<Token, List<Token>> function) {
        final TokenGroup.Builder out = new TokenGroup.Builder()
                .tokenType(in.getTokenType())
                .chars(in.getChars())
                .start(in.getStart())
                .end(in.getEnd());

        for (final Token token : in.getChildren()) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                out.addAll(function.apply(token));
            } else if (token instanceof TokenGroup) {
                // Recurse.
                final TokenGroup tokenGroup = extract((TokenGroup) token, function);
                out.add(tokenGroup);
            } else {
                out.add(token);
            }
        }
        return out.build();
    }

    private List<Token> split(final Token token,
                              final String regex,
                              final int group,
                              final TokenType tokenType) {
        final List<Token> out = new ArrayList<>();

        final String str = new String(token.getChars(), token.getStart(), token.getEnd() - token.getStart() + 1);
        final Matcher matcher = Pattern.compile(regex).matcher(str);

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
            out.add(new Token(token.getTokenType(), token.getChars(), token.getStart() + lastPos, token.getEnd()));
        }

        return out;
    }

//    private List<Token> split(final List<Token> in,
//                              final String sub,
//                              final TokenType tokenType) {
//        final List<Token> out = new ArrayList<>();
//
//        for (final Token token : in) {
//            if (token.getTokenType() == TokenType.UNKNOWN) {
//                out.addAll(split(token, sub, tokenType));
//            } else {
//                out.add(token);
//            }
//        }
//        return out;
//    }
//
//    private List<Token> split(final Token token,
//                              final String sub,
//                              final TokenType tokenType) {
//        final List<Token> out = new ArrayList<>();
//        final String str = token.getText().toLowerCase(Locale.ROOT);
//
//        int lastIndex = 0;
//        int index = str.indexOf(sub, lastIndex);
//        while (index != -1) {
//            if (lastIndex < index) {
//                out.add(new Token(token.getTokenType(), token.getChars(), token.getStart() + lastIndex, token.getStart() + index - 1));
//            }
//            out.add(new Token(tokenType, token.getChars(), token.getStart() + index, token.getStart() + sub.length()));
//            lastIndex = index + sub.length();
//            index = str.indexOf(sub, lastIndex);
//        }
//
//        if (token.getStart() + lastIndex <= token.getEnd()) {
//            out.add(new Token(token.getTokenType(), token.getChars(), token.getStart() + lastIndex, token.getEnd()));
//        }
//
//        return out;
//    }

    private TokenGroup extractHierarchy(final TokenGroup in) {
        TokenGroup.Builder out = new TokenGroup.Builder()
                .tokenType(TokenType.BRACKET_GROUP)
                .chars(in.getChars())
                .start(in.getStart())
                .end(in.getEnd());

        final Stack<TokenGroup.Builder> stack = new Stack<>();
        TokenGroup.Builder current = out;
        stack.push(current);

        int depth = 0;
        for (final Token token : in.getChildren()) {
            if (TokenType.OPEN_BRACKET.equals(token.getTokenType())) {
                depth++;
                current = new TokenGroup.Builder()
                        .tokenType(TokenType.BRACKET_GROUP)
                        .chars(token.getChars())
                        .start(token.getStart());
                stack.push(current);
            } else if (TokenType.CLOSE_BRACKET.equals(token.getTokenType())) {
                if (depth == 0) {
                    throw new TokenException(token, "Close bracket without open");
                }
                depth--;

                final TokenGroup bracketGroup = stack
                        .pop()
                        .end(token.getEnd())
                        .build();
                current = stack.peek();
                current.add(bracketGroup);
            } else {
                current.add(token);
            }
        }

        // Pop any remaining.
        TokenGroup last = null;
        while (!stack.isEmpty()) {
            TokenGroup.Builder parent = stack.pop();
            if (last != null) {
                parent.add(last);
            }
            last = parent.build();
        }

        return last;
    }

    private TokenGroup extractPipeGroups(final TokenGroup in) {
        TokenGroup.Builder out = new TokenGroup.Builder()
                .tokenType(TokenType.BRACKET_GROUP)
                .chars(in.getChars())
                .start(in.getStart())
                .end(in.getEnd());

        TokenGroup.Builder pipeGroup = new TokenGroup.Builder()
                .tokenType(TokenType.PIPE_GROUP)
                .chars(in.getChars())
                .start(in.getStart());
        for (final Token token : in.getChildren()) {
            if (token instanceof TokenGroup) {
                pipeGroup.end(token.getEnd());
                pipeGroup.add(extractPipeGroups((TokenGroup) token));
            } else if (TokenType.PIPE.equals(token.getTokenType())) {
                if (!pipeGroup.isEmpty()) {
                    out.add(pipeGroup.build());
                }
                pipeGroup = new TokenGroup.Builder()
                        .tokenType(TokenType.PIPE_GROUP)
                        .chars(token.getChars())
                        .start(token.getStart())
                        .end(token.getEnd());
            } else {
                pipeGroup
                        .end(token.getEnd())
                        .add(token);
            }
        }

        if (!pipeGroup.isEmpty()) {
            out.add(pipeGroup.build());
        }

        return out.build();
    }

//    private List<Token> extractConditions(final List<Token> in) {
//        final List<Token> out = new ArrayList<>();
//        for (final Token token : in) {
//            if (token.tokenType == TokenType.UNKNOWN) {
//                out.addAll(splitCondition(token));
//            } else {
//                out.add(token);
//            }
//        }
//        return out;
//    }
//
//    private List<Token> splitCondition(final Token token) {
//        final List<Token> out = new ArrayList<>();
//        int lastPos = token.getStart();
//        for (int i = token.getStart(); i <= token.getEnd(); i++) {
//            final char c1 = token.getChars()[i];
//
//            if (c1 == '>' || c1 == '<' || c1 == '=' || c1 == '!') {
//                if (isChar(token, '=', i + 1)) {
//                    if (lastPos < i) {
//                        out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
//                    }
//                    out.add(new Token(TokenType.CONDITION, token.getChars(), i, i + 1));
//                    i++;
//                    lastPos = i + 1;
//                } else if (c1 == '>' || c1 == '<') {
//                    if (lastPos < i) {
//                        out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
//                    }
//                    out.add(new Token(TokenType.CONDITION, token.getChars(), i, i));
//                    lastPos = i + 1;
//                }
//            }
//        }
//        if (lastPos <= token.getEnd()) {
//            final Token remaining = new Token(token.tokenType, token.getChars(), lastPos, token.getEnd());
//            out.add(remaining);
//        }
//        return out;
//    }

//    private boolean isChar(final Token token, final char c, final int index) {
//        if (index < token.getStart() || index > token.getEnd()) {
//            return false;
//        }
//        return token.getChars()[index] == c;
//    }

//    private List<Token> extractCommands(final List<Token> in) {
//        final List<Token> out = new ArrayList<>();
//        boolean afterPipe = false;
//        for (final Token token : in) {
//            if (token.tokenType == TokenType.PIPE) {
//                out.add(token);
//                afterPipe = true;
//            } else {
//                if (afterPipe && token.tokenType == TokenType.UNKNOWN) {
//                    out.addAll(splitCommand(token));
//                } else {
//                    out.add(token);
//                }
//                afterPipe = false;
//            }
//        }
//        return out;
//    }
//
//    private List<Token> splitCommand(final Token t) {
//        final List<Token> out = new ArrayList<>();
//        boolean inCommand = false;
//        int lastPos = t.start;
//        for (int i = t.start; i <= t.end; i++) {
//            final char c = t.chars[i];
//            if (!inCommand) {
//                if (c != ' ') {
//                    if (lastPos < i) {
//                        out.add(new Token(t.tokenType, t.chars, lastPos, i - 1));
//                    }
//                    lastPos = i;
//                    inCommand = true;
//                }
//            } else if (c == ' ') {
//                if (lastPos < i) {
//                    out.add(new Token(TokenType.COMMAND, t.chars, lastPos, i - 1));
//                }
//                inCommand = false;
//                lastPos = i;
//                // Force exit.
//                i = t.end + 1;
//            }
//        }
//        if (lastPos <= t.end) {
//            if (inCommand) {
//                out.add(new Token(TokenType.COMMAND, t.chars, lastPos, t.end));
//            } else {
//                out.add(new Token(t.tokenType, t.chars, lastPos, t.end));
//            }
//        }
//        return out;
//    }

    private List<Token> trim(final Token token) {
        final List<Token> out = new ArrayList<>();
        final List<Token> sub = trimStart(token);
        for (final Token t : sub) {
            if (TokenType.WHITESPACE.equals(t.getTokenType())) {
                out.add(t);
            } else {
                out.addAll(trimEnd(t));
            }
        }
        return out;
    }

    private List<Token> trimStart(final Token token) {
        final List<Token> tokens = new ArrayList<>();
        boolean allWhitespace = true;
        for (int i = token.getStart(); i <= token.getEnd(); i++) {
            final char c = token.getChars()[i];
            if (c != ' ') {
                allWhitespace = false;
                if (i > token.getStart()) {
                    tokens.add(new Token(TokenType.WHITESPACE, token.getChars(), token.getStart(), i - 1));
                }
                tokens.add(new Token(token.getTokenType(), token.getChars(), i, token.getEnd()));
                i = token.getEnd() + 1;
            }
        }
        if (tokens.size() == 0) {
            if (allWhitespace) {
                tokens.add(new Token(TokenType.WHITESPACE, token.getChars(), token.getStart(), token.getEnd()));
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private List<Token> trimEnd(final Token token) {
        final List<Token> tokens = new ArrayList<>();
        boolean allWhitespace = true;
        for (int i = token.getEnd(); i >= token.getStart(); i--) {
            final char c = token.getChars()[i];
            if (c != ' ') {
                allWhitespace = false;
                tokens.add(new Token(token.getTokenType(), token.getChars(), token.getStart(), i));
                if (i < token.getEnd()) {
                    tokens.add(new Token(TokenType.WHITESPACE, token.getChars(), i + 1, token.getEnd()));
                }
                i = token.getStart() - 1;
            }
        }
        if (tokens.size() == 0) {
            if (allWhitespace) {
                tokens.add(new Token(TokenType.WHITESPACE, token.getChars(), token.getStart(), token.getEnd()));
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private List<Token> extractPipes(final List<Token> in) {
        final List<Token> tokens = new ArrayList<>();
        for (final Token token : in) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                int lastPos = token.getStart();
                for (int i = token.getStart(); i <= token.getEnd(); i++) {
                    final char c = token.getChars()[i];
                    if (c == '|') {
                        if (lastPos < i) {
                            tokens.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
                        }
                        tokens.add(new Token(TokenType.PIPE, token.getChars(), i, i));
                        lastPos = i + 1;
                    }
                }
                if (lastPos <= token.getEnd()) {
                    tokens.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, token.getEnd()));
                }
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

//    private TokenGroup extractTokenGroups(final List<Token> in,
//                                          final int start,
//                                          final int end) {
//        final List<Token> tokens = new ArrayList<>();
//        int bracketStart = start;
//        int bracketEnd = end;
//        for (final Token token : in) {
//            if (token.tokenType == TokenType.UNKNOWN) {
//                final int from = Math.max(start, token.getStart());
//                final int to = Math.min(end, token.getEnd());
//                for (int i = from; i <= to; i++) {
//                    if (token.getChars()[i] == '(') {
//                        if (i > bracketStart) {
//
//                        }
//                        final TokenGroup child = extractTokenGroups(in, i, end);
//                        tokens.add(child);
//
//                    }
//                }
//            }
//        }
//    }

//    private List<Token> extractQuotedTokens(final List<Token> in,
//                                            final TokenType outputType,
//                                            final char quoteChar,
//                                            final char escapeChar) {
//        final List<Token> tokens = new ArrayList<>();
//        for (final Token token : in) {
//            if (token.tokenType == TokenType.UNKNOWN) {
//
//                // Break the string into quoted text blocks.
//                int lastPos = token.getStart();
//                boolean inQuote = false;
//                boolean escape = false;
//                for (int i = token.getStart(); i <= token.getEnd(); i++) {
//                    final char c = token.getChars()[i];
//                    if (inQuote) {
//                        if (escape) {
//                            escape = false;
//                        } else {
//                            if (c == escapeChar) {
//                                escape = true;
//                            } else if (c == quoteChar) {
//                                inQuote = false;
//                                if (lastPos < i) {
//                                    tokens.add(new Token(outputType, token.getChars(), lastPos, i));
//                                }
//                                lastPos = i + 1;
//                            }
//                        }
//                    } else if (c == quoteChar) {
//                        inQuote = true;
//                        if (lastPos < i) {
//                            tokens.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
//                        }
//                        lastPos = i;
//                    }
//                }
//
//                if (lastPos <= token.getEnd()) {
//                    tokens.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, token.getEnd()));
//                }
//
//            } else {
//                tokens.add(token);
//            }
//        }
//        return tokens;
//    }

    private List<Token> extractQuotedTokens(final Token token,
                                            final TokenType outputType,
                                            final char quoteChar,
                                            final char escapeChar) {
        final List<Token> out = new ArrayList<>();
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
                                out.add(new Token(outputType, token.getChars(), lastPos, i));
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
        return out;
    }
}
