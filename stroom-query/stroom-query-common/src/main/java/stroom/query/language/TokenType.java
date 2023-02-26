package stroom.query.language;

import java.util.Set;

public enum TokenType {
    // Uncategorized content.
    UNKNOWN,

    PIPE,
    OPEN_BRACKET,
    CLOSE_BRACKET,

    // Structure
    PIPE_GROUP,
    TOKEN_GROUP,
    FUNCTION_GROUP,
    FUNCTION_NAME,
    PIPE_OPERATION,

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

//    // Commands
//    WHERE,
//    TABLE,

    // Logic
    AND,
    OR,
    NOT,

    BY,
    AS,


    // Comments
    COMMENT,
    BLOCK_COMMENT;

//    public static Set<TokenType> COMMANDS = Set.of(WHERE, TABLE);
//
//    public static Set<TokenType> QUERY = Set.of(WHERE, AND, OR, NOT);

    public static Set<TokenType> LOGIC = Set.of(AND, OR, NOT);

    public static boolean isString(AbstractToken token) {
        return TokenType.STRING.equals(token.getTokenType()) ||
                TokenType.SINGLE_QUOTED_STRING.equals(token.getTokenType()) ||
                TokenType.DOUBLE_QUOTED_STRING.equals(token.getTokenType());
    }
}
