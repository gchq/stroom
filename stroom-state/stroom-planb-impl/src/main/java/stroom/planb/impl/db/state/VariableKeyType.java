package stroom.planb.impl.db.state;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum VariableKeyType implements HasPrimitiveValue {
    DIRECT(0),
    LOOKUP(1);

    public static final PrimitiveValueConverter<VariableKeyType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(VariableKeyType.class, VariableKeyType.values());

    private final byte primitiveValue;

    VariableKeyType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
