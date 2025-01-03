package stroom.planb.impl.dao;

import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.planb.impl.dao.RangedState.Key;
import stroom.planb.impl.dao.RangedState.Value;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record RangedState(Key key, Value value) implements KV<Key, Value> {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Key key;
        private Value value;

        public Builder() {
        }

        public Builder(final RangedState state) {
            this.key = state.key;
            this.value = state.value;
        }

        public Builder key(final Key key) {
            this.key = key;
            return this;
        }

        public Builder value(final Value value) {
            this.value = value;
            return this;
        }

        public RangedState build() {
            return new RangedState(key, value);
        }
    }

    public record Key(long keyStart, long keyEnd) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private long keyStart;
            private long keyEnd;

            public Builder() {
            }

            public Builder(final Key key) {
                this.keyStart = key.keyStart;
                this.keyEnd = key.keyEnd;
            }

            public Builder keyStart(final long keyStart) {
                this.keyStart = keyStart;
                return this;
            }

            public Builder keyEnd(final long keyEnd) {
                this.keyEnd = keyEnd;
                return this;
            }

            public Key build() {
                return new Key(keyStart, keyEnd);
            }
        }
    }

    public record Value(byte typeId, ByteBuffer byteBuffer) {

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

            public Builder(final Value value) {
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

            public Value build() {
                return new Value(typeId, byteBuffer);
            }
        }
    }
}
