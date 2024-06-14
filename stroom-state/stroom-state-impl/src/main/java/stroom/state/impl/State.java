package stroom.state.impl;

import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record State(
        String map,
        String key,
        Instant effectiveTime,
        byte typeId,
        ByteBuffer value) {

    public String getValueAsString() {
        return switch (typeId) {
            case StringValue.TYPE_ID -> new String(value.duplicate().array(), StandardCharsets.UTF_8);
            case FastInfosetValue.TYPE_ID -> FastInfosetUtil.byteBufferToString(value.duplicate());
            default -> null;
        };
    }
}
