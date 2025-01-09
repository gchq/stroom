package stroom.planb.impl.io;

import stroom.planb.impl.io.State.Key;

import java.nio.charset.StandardCharsets;

public record State(Key key, StateValue value) implements KV<Key, StateValue> {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Key key;
        private StateValue value;

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

        public Builder value(final StateValue value) {
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
}
