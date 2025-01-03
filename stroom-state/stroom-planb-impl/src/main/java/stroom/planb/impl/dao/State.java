package stroom.planb.impl.dao;

import stroom.pipeline.refdata.store.FastInfosetUtil;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.planb.impl.dao.State.Key;
import stroom.planb.impl.dao.State.Value;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record State(Key key, Value value) implements KV<Key, Value> {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Key key;
        private Value value;

        public Builder() {
        }

        public Builder(final State state) {
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

        public State build() {
            return new State(key, value);
        }
    }

    public record Key(byte[] bytes) {

        @Override
        public String toString() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private byte[] bytes;

            public Builder() {
            }

            public Builder(final Key key) {
                this.bytes = key.bytes;
            }

            public Builder name(final byte[] bytes) {
                this.bytes = bytes;
                return this;
            }

            public Builder name(final String name) {
                this.bytes = name.getBytes(StandardCharsets.UTF_8);
                return this;
            }

            public Key build() {
                return new Key(bytes);
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
