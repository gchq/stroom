package stroom.planb.impl.db;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.State.Key;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.charset.StandardCharsets;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public class State extends KV<Key, StateValue> implements PlanBValue {

    @JsonCreator
    public State(@JsonProperty("key") final Key key,
                 @JsonProperty("value") final StateValue value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<State, Builder, Key, StateValue> {

        private Builder() {
        }

        private Builder(final State key) {
            super(key);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public State build() {
            return new State(key, value);
        }
    }

    @JsonPropertyOrder({"bytes"})
    @JsonInclude(Include.NON_NULL)
    public static class Key {

        @JsonProperty
        private final byte[] bytes;

        @JsonCreator
        public Key(@JsonProperty("bytes") final byte[] bytes) {
            this.bytes = bytes;
        }

        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public String toString() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private byte[] bytes;

            private Builder() {
            }

            private Builder(final Key key) {
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
