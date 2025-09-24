package stroom.planb.impl.db.trace;

import stroom.lmdb2.KV;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;

public class TraceRootKey {
    private final byte[] traceId;
    private final NanoTime startTime;

    public TraceRootKey(final byte[] traceId, final NanoTime startTime) {
        this.traceId = traceId;
        this.startTime = startTime;
    }
//
//    public TraceRootKey(final KV<SpanKey, SpanValue> kv) {
//        this.traceId = HexStringUtil.decode(kv.key().getTraceId());
//        this.startTime = NanoTime.fromString(kv.val().getStartTimeUnixNano());
//    }

    public byte[] getTraceId() {
        return traceId;
    }

    public NanoTime getStartTime() {
        return startTime;
    }
}
