package stroom.query.language;

import stroom.query.language.TokenGroup.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StructureBuilder {

    private StructureBuilder() {

    }

    public static TokenGroup create(final List<Token> tokens) {
        Objects.requireNonNull(tokens, "Null tokens");

        final TokenGroup.Builder builder = new Builder()
                .tokenType(TokenType.TOKEN_GROUP)
                .start(0);

        final List<Token> cleansed = new ArrayList<>();
        for (final Token token : tokens) {
            builder.chars(token.getChars())
                    .end(token.getEnd());

            // Remove whitespace and comments.
            if (!TokenType.WHITESPACE.equals(token.getTokenType()) &&
                    !TokenType.COMMENT.equals(token.getTokenType()) &&
                    !TokenType.BLOCK_COMMENT.equals(token.getTokenType())) {
                cleansed.add(token);
            }
        }

        // Create structure.
        createStructure(cleansed, builder, 0);

        return builder.build();
    }

    private static int createStructure(final List<Token> tokens,
                                       final AbstractTokenGroup.AbstractTokenGroupBuilder<?, ?> out,
                                       final int start) {

        AbstractTokenGroup.AbstractTokenGroupBuilder<?, ?> currentGroup = out;

        boolean ignoreNextAnd = false;
        int index = start;
        for (; index < tokens.size(); index++) {
            final Token token = tokens.get(index);
            final TokenType tokenType = token.getTokenType();

            if (TokenType.FUNCTION_NAME.equals(tokenType)) {
                if (index >= tokens.size() - 1) {
                    throw new TokenException(token, "Orphaned function name");
                }
            } else if (TokenType.OPEN_BRACKET.equals(tokenType)) {

                if (index > 0 && TokenType.FUNCTION_NAME.equals(tokens.get(index - 1).getTokenType())) {
                    final AbstractToken functionName = tokens.get(index - 1);
                    final FunctionGroup.Builder function = new FunctionGroup.Builder()
                            .tokenType(TokenType.FUNCTION_GROUP)
                            .chars(functionName.getChars())
                            .start(functionName.getStart())
                            .end(token.getEnd())
                            .name(functionName.getUnescapedText());
                    index = createStructure(tokens, function, index + 1);
                    currentGroup.add(function.build());

                } else {
                    final TokenGroup.Builder group = new TokenGroup.Builder()
                            .tokenType(TokenType.TOKEN_GROUP)
                            .chars(token.getChars())
                            .start(token.getStart())
                            .end(token.getEnd());
                    index = createStructure(tokens, group, index + 1);
                    currentGroup.add(group.build());
                }
            } else if (TokenType.PIPE.equals(tokenType)) {
                // Ignore pipes.
            } else if (TokenType.KEYWORDS.contains(tokenType)) {
                if (ignoreNextAnd && TokenType.AND.equals(tokenType)) {
                    final Token betweenAnd = new Token.Builder(token).tokenType(TokenType.BETWEEN_AND).build();
                    currentGroup.end(betweenAnd.getEnd());
                    currentGroup.add(betweenAnd);

                } else {
                    if (currentGroup != null && currentGroup != out) {
                        out.add(currentGroup.build());
                    }

                    currentGroup = new KeywordGroup.Builder()
                            .tokenType(tokenType)
                            .chars(token.getChars())
                            .start(token.getStart())
                            .end(token.getEnd());
                }

                ignoreNextAnd = false;

            } else if (TokenType.CLOSE_BRACKET.equals(tokenType)) {
                currentGroup.end(token.getEnd());
                break;
            } else if (TokenType.BETWEEN.equals(tokenType)) {
                // Special case that allows use of `and` for `X between Y and Z`
                ignoreNextAnd = true;
                currentGroup.end(token.getEnd());
                currentGroup.add(token);
            } else {
                currentGroup.end(token.getEnd());
                currentGroup.add(token);
            }
        }

        if (currentGroup != null && currentGroup != out) {
            out.add(currentGroup.build());
        }

        return index;
    }
}
