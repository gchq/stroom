package stroom.planb.impl.io;

import stroom.planb.impl.io.RangedState.Key;

public record RangedState(Key key, StateValue value) implements KV<Key, StateValue> {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Key key;
        private StateValue value;

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

        public Builder value(final StateValue value) {
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
}
