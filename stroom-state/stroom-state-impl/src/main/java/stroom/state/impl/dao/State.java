package stroom.state.impl.dao;

import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record State(
        String key,
        byte typeId,
        ByteBuffer value) {

    public String getValueAsString() {
        return switch (typeId) {
            case StringValue.TYPE_ID -> new String(value.duplicate().array(), StandardCharsets.UTF_8);
            case FastInfosetValue.TYPE_ID -> FastInfosetUtil.byteBufferToString(value.duplicate());
            default -> null;
        };
    }

    public static class Builder {

        private String key;
        private byte typeId;
        private ByteBuffer value;

        public Builder() {
        }

        public Builder(final State state) {
            this.key = state.key;
            this.typeId = state.typeId;
            this.value = state.value;
        }

        public Builder key(final String key) {
            this.key = key;
            return this;
        }

        public Builder typeId(final byte typeId) {
            this.typeId = typeId;
            return this;
        }

        public Builder value(final ByteBuffer value) {
            this.value = value;
            return this;
        }

        public State build() {
            return new State(key, typeId, value);
        }
    }
}
