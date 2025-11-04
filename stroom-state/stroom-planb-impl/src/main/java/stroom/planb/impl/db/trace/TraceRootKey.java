package stroom.planb.impl.db.trace;

public class TraceRootKey {

    private final byte[] traceId;

    public TraceRootKey(final byte[] traceId) {
        this.traceId = traceId;
    }

    public byte[] getTraceId() {
        return traceId;
    }
}
