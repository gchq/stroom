package stroom.security.identity.token;

public enum TokenType {
    USER("user"),
    API("api"),
    EMAIL_RESET("email_reset");

    private String tokenTypeText;

    TokenType(String tokenTypeText) {
        this.tokenTypeText = tokenTypeText;
    }

    public String getText() {
        return this.tokenTypeText;
    }

    public static TokenType fromText(final String value) {
        // Not enough values to warrant an EnumMap
        if (value != null) {
            String caseInsensitiveValue = value.toLowerCase();
            for (final TokenType tokenType : TokenType.values()) {
                if (tokenType.getText().equals(caseInsensitiveValue)) {
                    return tokenType;
                }
            }
        }
        throw new IllegalArgumentException("Unknown token type " + value);
    }
}
