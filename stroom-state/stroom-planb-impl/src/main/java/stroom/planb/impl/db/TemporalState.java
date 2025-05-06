package stroom.planb.impl.db;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.TemporalState.Key;
import stroom.planb.impl.db.state.StateValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public class TemporalState extends KV<Key, StateValue> implements PlanBValue {

    @JsonCreator
    public TemporalState(@JsonProperty("key") final Key key,
                         @JsonProperty("value") final StateValue value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<TemporalState, Builder, Key, StateValue> {

        private Builder() {
        }

        private Builder(final TemporalState key) {
            super(key);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public TemporalState build() {
            return new TemporalState(key, value);
        }
    }

    @JsonPropertyOrder({"bytes", "effectiveTime"})
    @JsonInclude(Include.NON_NULL)
    public static class Key {

        @JsonProperty
        private final byte[] bytes;
        @JsonProperty
        private final long effectiveTime;

        @JsonCreator
        public Key(@JsonProperty("bytes") final byte[] bytes,
                   @JsonProperty("effectiveTime") final long effectiveTime) {
            this.bytes = bytes;
            this.effectiveTime = effectiveTime;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public long getEffectiveTime() {
            return effectiveTime;
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
            private long effectiveTime;

            private Builder() {
            }

            private Builder(final Key key) {
                this.bytes = key.bytes;
                this.effectiveTime = key.effectiveTime;
            }

            public Builder name(final byte[] bytes) {
                this.bytes = bytes;
                return this;
            }

            public Builder name(final String name) {
                this.bytes = name.getBytes(StandardCharsets.UTF_8);
                return this;
            }

            public Builder effectiveTime(final long effectiveTime) {
                this.effectiveTime = effectiveTime;
                return this;
            }

            public Builder effectiveTime(final Instant effectiveTime) {
                this.effectiveTime = effectiveTime.toEpochMilli();
                return this;
            }

            public Key build() {
                return new Key(bytes, effectiveTime);
            }
        }
    }
}
