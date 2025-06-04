package stroom.planb.impl.serde.val;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum VariableValType implements HasPrimitiveValue {
    DIRECT(0),
    UID_LOOKUP(1),
    HASH_LOOKUP(2);

    public static final PrimitiveValueConverter<VariableValType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(VariableValType.class, VariableValType.values());

    private final byte primitiveValue;

    VariableValType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
