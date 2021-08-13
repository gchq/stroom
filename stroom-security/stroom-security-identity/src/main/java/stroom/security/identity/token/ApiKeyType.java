package stroom.security.identity.token;

public enum ApiKeyType {
    USER("user");

    private final String text;

    ApiKeyType(String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    public static ApiKeyType fromText(final String value) {
        // Not enough values to warrant an EnumMap
        if (value != null) {
            String caseInsensitiveValue = value.toLowerCase();
            for (final ApiKeyType apiKeyType : ApiKeyType.values()) {
                if (apiKeyType.getText().equals(caseInsensitiveValue)) {
                    return apiKeyType;
                }
            }
        }
        throw new IllegalArgumentException("Unknown API key type " + value);
    }
}
