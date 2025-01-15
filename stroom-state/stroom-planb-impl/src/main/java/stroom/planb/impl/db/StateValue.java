package stroom.planb.impl.db;

import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record StateValue(byte typeId, ByteBuffer byteBuffer) {

    @Override
    public String toString() {
        return switch (typeId) {
            case StringValue.TYPE_ID -> new String(byteBuffer.duplicate().array(), StandardCharsets.UTF_8);
            case FastInfosetValue.TYPE_ID -> FastInfosetUtil.byteBufferToString(byteBuffer.duplicate());
            default -> null;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private byte typeId;
        private ByteBuffer byteBuffer;

        public Builder() {
        }

        public Builder(final StateValue value) {
            this.typeId = value.typeId;
            this.byteBuffer = value.byteBuffer;
        }

        public Builder typeId(final byte typeId) {
            this.typeId = typeId;
            return this;
        }

        public Builder byteBuffer(final ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            return this;
        }

        public StateValue build() {
            return new StateValue(typeId, byteBuffer);
        }
    }
}
