package stroom.security.shared;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum HashAlgorithm implements HasDisplayValue, HasPrimitiveValue {
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
    public static final HashAlgorithm DEFAULT = HashAlgorithm.SHA3_256;

    public static final PrimitiveValueConverter<HashAlgorithm> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(HashAlgorithm.class, HashAlgorithm.values());

    private final String displayValue;
    private final byte primitiveValue;

    HashAlgorithm(final String displayValue, final int primitiveValue) {
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
