package stroom.pathways.shared.otel.trace;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum StatusCode implements HasPrimitiveValue {
    STATUS_CODE_UNSET(0),
    STATUS_CODE_OK(1),
    STATUS_CODE_ERROR(2);

    public static final PrimitiveValueConverter<StatusCode> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(StatusCode.class, StatusCode.values());

    private final byte primitiveValue;

    StatusCode(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
