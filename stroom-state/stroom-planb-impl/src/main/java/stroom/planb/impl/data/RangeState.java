package stroom.planb.impl.data;

import stroom.lmdb2.KV;
import stroom.planb.impl.data.RangeState.Key;
import stroom.query.language.functions.Val;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public final class RangeState extends KV<Key, Val> implements PlanBValue {

    @JsonCreator
    public RangeState(@JsonProperty("key") final Key key,
                      @JsonProperty("value") final Val value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<RangeState, Builder, Key, Val> {

        private Builder() {
        }

        private Builder(final RangeState key) {
            super(key);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public RangeState build() {
            return new RangeState(key, value);
        }
    }

    @JsonPropertyOrder({"keyStart", "keyEnd"})
    @JsonInclude(Include.NON_NULL)
    public static class Key {

        @JsonProperty
        private final long keyStart;
        @JsonProperty
        private final long keyEnd;

        @JsonCreator
        public Key(@JsonProperty("keyStart") final long keyStart,
                   @JsonProperty("keyEnd") final long keyEnd) {
            this.keyStart = keyStart;
            this.keyEnd = keyEnd;
        }

        public long getKeyStart() {
            return keyStart;
        }

        public long getKeyEnd() {
            return keyEnd;
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private long keyStart;
            private long keyEnd;

            private Builder() {
            }

            private Builder(final Key key) {
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
