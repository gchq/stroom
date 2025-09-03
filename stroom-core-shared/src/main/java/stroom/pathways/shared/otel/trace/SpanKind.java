package stroom.pathways.shared.otel.trace;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum SpanKind implements HasPrimitiveValue {
    SPAN_KIND_UNSPECIFIED(0),
    SPAN_KIND_INTERNAL(1),
    SPAN_KIND_SERVER(2),
    SPAN_KIND_CLIENT(3),
    SPAN_KIND_PRODUCER(4),
    SPAN_KIND_CONSUMER(5);

    public static final PrimitiveValueConverter<SpanKind> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(SpanKind.class, SpanKind.values());

    private final byte primitiveValue;

    SpanKind(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
