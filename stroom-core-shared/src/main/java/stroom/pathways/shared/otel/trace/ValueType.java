package stroom.pathways.shared.otel.trace;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum ValueType implements HasPrimitiveValue {
    STRING(0),
    BOOLEAN(1),
    INTEGER(2),
    DOUBLE(3),
    ARRAY_VALUE(4),
    KEY_VALUE_LIST(5),
    BYTES(6);

    public static final PrimitiveValueConverter<ValueType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(ValueType.class, ValueType.values());

    private final byte primitiveValue;

    ValueType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
