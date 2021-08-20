package stroom.security.identity.token;

public enum KeyType {
    USER("user");

    private final String text;

    KeyType(String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    public static KeyType fromText(final String value) {
        // Not enough values to warrant an EnumMap
        if (value != null) {
            String caseInsensitiveValue = value.toLowerCase();
            for (final KeyType keyType : KeyType.values()) {
                if (keyType.getText().equals(caseInsensitiveValue)) {
                    return keyType;
                }
            }
        }
        throw new IllegalArgumentException("Unknown API key type " + value);
    }
}
