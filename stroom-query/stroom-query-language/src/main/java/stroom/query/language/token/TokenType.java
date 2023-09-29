package stroom.query.language.token;

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

    // Strings
    WHITESPACE,
    SINGLE_QUOTED_STRING,
    DOUBLE_QUOTED_STRING,
    STRING,
    PARAM,

    DATE_TIME,
    DURATION,
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
    VIS,

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
            HAVING,
            VIS);

    public static final Set<TokenType> CONDITIONS = Set.of(
            EQUALS,
            NOT_EQUALS,
            GREATER_THAN,
            GREATER_THAN_OR_EQUAL_TO,
            LESS_THAN,
            LESS_THAN_OR_EQUAL_TO,
            IS_NULL,
            IS_NOT_NULL,
            BETWEEN);

    public static final Set<TokenType> NUMERIC_OPERATOR = Set.of(
            ORDER,
            DIVISION,
            MULTIPLICATION,
            MODULUS,
            PLUS,
            MINUS);

    public static final Set<TokenType> VALUES = Set.of(
            SINGLE_QUOTED_STRING,
            DOUBLE_QUOTED_STRING,
            STRING,
            NUMBER
            );

    public static final Set<TokenType> ALL = Set.of(values());

    public static boolean isString(AbstractToken token) {
        return token != null
                && (TokenType.STRING.equals(token.getTokenType()) ||
                TokenType.SINGLE_QUOTED_STRING.equals(token.getTokenType()) ||
                TokenType.DOUBLE_QUOTED_STRING.equals(token.getTokenType()) ||
                TokenType.PARAM.equals(token.getTokenType()));
    }
}
