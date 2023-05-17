package stroom.query.language;

import java.util.Set;

public enum TokenType {
    // Uncategorized content.
    UNKNOWN,

    OPEN_BRACKET,
    CLOSE_BRACKET,

    // Structure
    TOKEN_GROUP,
    FUNCTION_GROUP,
    FUNCTION_NAME,
//    PIPE_OPERATION,

    // Strings
    WHITESPACE,
    SINGLE_QUOTED_STRING,
    DOUBLE_QUOTED_STRING,
    STRING,

    NUMBER,


    COMMA,

    // Numeric operators
    ORDER,
    DIVISION,
    MULTIPLICATION,
    MODULUS,
    PLUS,
    MINUS,

    // Conditions
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL_TO,
    LESS_THAN,
    LESS_THAN_OR_EQUAL_TO,
    IS_NULL,
    IS_NOT_NULL,

    // Logic
    AND,
    OR,
    NOT,

    // Other keywords
    FROM,
    WHERE,
    EVAL,
    SELECT,
    SORT,
    GROUP,
    FILTER,
    WINDOW,
    LIMIT,
    HAVING,

    // Additional tokens
    BY,
    AS,
    BETWEEN,
    BETWEEN_AND,


    // Comments
    COMMENT,
    BLOCK_COMMENT;

    public static final Set<TokenType> KEYWORDS = Set.of(
            FROM,
            WHERE,
            AND,
            OR,
            NOT,
            FILTER,
            EVAL,
            SELECT,
            SORT,
            GROUP,
            WINDOW,
            LIMIT,
            HAVING);
//
//    public static Set<TokenType> QUERY = Set.of(WHERE, AND, OR, NOT);
//
//    public static Set<TokenType> LOGIC = Set.of(AND, OR, NOT);

    public static boolean isString(AbstractToken token) {
        return TokenType.STRING.equals(token.getTokenType()) ||
                TokenType.SINGLE_QUOTED_STRING.equals(token.getTokenType()) ||
                TokenType.DOUBLE_QUOTED_STRING.equals(token.getTokenType());
    }
}
