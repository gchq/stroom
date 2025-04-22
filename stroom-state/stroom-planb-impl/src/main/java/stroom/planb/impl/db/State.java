package stroom.planb.impl.db;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.State.Key;

import java.nio.charset.StandardCharsets;

public class State extends KV<Key, StateValue> implements PlanBValue  {

    public State(final Key key, final StateValue value) {
        super(key, value);
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
