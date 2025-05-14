package stroom.planb.impl.db.temporalrangedstate;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.PlanBValue;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedState.Key;
import stroom.query.language.functions.Val;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public class TemporalRangedState extends KV<Key, Val> implements PlanBValue {

    @JsonCreator
    public TemporalRangedState(@JsonProperty("key") final Key key,
                               @JsonProperty("value") final Val value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<TemporalRangedState, Builder, Key, Val> {

        private Builder() {
        }

        private Builder(final TemporalRangedState key) {
            super(key);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public TemporalRangedState build() {
            return new TemporalRangedState(key, value);
        }
    }

    @JsonPropertyOrder({"keyStart", "keyEnd", "effectiveTime"})
    @JsonInclude(Include.NON_NULL)
    public static class Key {

        @JsonProperty
        private final long keyStart;
        @JsonProperty
        private final long keyEnd;
        @JsonProperty
        private final Instant effectiveTime;

        @JsonCreator
        public Key(@JsonProperty("keyStart") final long keyStart,
                   @JsonProperty("keyEnd") final long keyEnd,
                   @JsonProperty("effectiveTime") final Instant effectiveTime) {
            this.keyStart = keyStart;
            this.keyEnd = keyEnd;
            this.effectiveTime = effectiveTime;
        }

        public long getKeyStart() {
            return keyStart;
        }

        public long getKeyEnd() {
            return keyEnd;
        }

        public Instant getEffectiveTime() {
            return effectiveTime;
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
            private Instant effectiveTime;

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

            public Builder effectiveTime(final Instant effectiveTime) {
                this.effectiveTime = effectiveTime;
                return this;
            }

            public Key build() {
                return new Key(keyStart, keyEnd, effectiveTime);
            }
        }
    }
}
