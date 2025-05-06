package stroom.planb.impl.db.state;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum AutoKeyType implements HasPrimitiveValue {
    ZERO(0),
    BYTE(1),
    SHORT(2),
    INT(3),
    LONG(4),
    FLOAT(5),
    DOUBLE(6),
    UNSIGNED(7),
    BYTES(8),
    HASH(9),
    LOOKUP(10);

    public static final PrimitiveValueConverter<AutoKeyType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AutoKeyType.class, AutoKeyType.values());

    private final byte primitiveValue;

    AutoKeyType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
