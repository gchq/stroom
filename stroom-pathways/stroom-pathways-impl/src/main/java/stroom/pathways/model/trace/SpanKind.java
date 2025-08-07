package stroom.pathways.model.trace;

public enum SpanKind {
    SPAN_KIND_UNSPECIFIED(0),
    SPAN_KIND_INTERNAL(1),
    SPAN_KIND_SERVER(2),
    SPAN_KIND_CLIENT(3),
    SPAN_KIND_PRODUCER(4),
    SPAN_KIND_CONSUMER(5);

    private final int value;

    SpanKind(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
