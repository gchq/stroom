package stroom.query.language;

import java.util.Set;

public enum TokenType {
    // Uncategorized content.
    UNKNOWN,

    // Structure
    BRACKET_GROUP,
    OPEN_BRACKET,
    CLOSE_BRACKET,
    PIPE_GROUP,
    PIPE,

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

    // Commands
    WHERE,

    // Logic
    AND,
    OR,
    NOT,

    BY,
    AS,





    FUNCTION,

    // Comments
    COMMENT,
    BLOCK_COMMENT;

    public static Set<TokenType> COMMANDS = Set.of(WHERE, AND, OR, NOT);
}