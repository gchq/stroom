package stroom.security.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/**
 * Distinct from {@link HashAlgorithm}, this one is used only for API keys.
 */
public enum ApiKeyHashAlgorithm implements HasDisplayValue, HasPrimitiveValue {
    // WARNING!!!
    // Don't change the primitive values as they map to values in the DB
    SHA3_256("SHA3-256", 0),
    SHA2_256("SHA2-256", 1),
    BCRYPT("BCrypt", 2),
    ARGON_2("Argon2", 3),
    SHA2_512("SHA2-512", 4),
    ;

    /**
     * The fall-back default value to use if the default has not been set in config.
     */
    public static final ApiKeyHashAlgorithm DEFAULT = ApiKeyHashAlgorithm.SHA3_256;

    public static final PrimitiveValueConverter<ApiKeyHashAlgorithm> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(ApiKeyHashAlgorithm.class, ApiKeyHashAlgorithm.values());

    private final String displayValue;
    private final byte primitiveValue;

    ApiKeyHashAlgorithm(final String displayValue, final int primitiveValue) {
        this.displayValue = displayValue;
        this.primitiveValue = (byte) primitiveValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
