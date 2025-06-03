package stroom.planb.impl.db.temporalstate;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.PlanBValue;
import stroom.planb.impl.db.temporalstate.TemporalState.Key;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public class TemporalState extends KV<Key, Val> implements PlanBValue {

    @JsonCreator
    public TemporalState(@JsonProperty("key") final Key key,
                         @JsonProperty("value") final Val value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<TemporalState, Builder, Key, Val> {

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

    @JsonPropertyOrder({"name", "effectiveTime"})
    @JsonInclude(Include.NON_NULL)
    public static class Key {

        @JsonProperty
        private final Val name;
        @JsonProperty
        private final Instant effectiveTime;

        @JsonCreator
        public Key(@JsonProperty("name") final Val name,
                   @JsonProperty("effectiveTime") final Instant effectiveTime) {
            this.name = name;
            this.effectiveTime = effectiveTime;
        }

        public Val getName() {
            return name;
        }

        public Instant getEffectiveTime() {
            return effectiveTime;
        }

        @Override
        public String toString() {
            return name.toString();
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private Val name;
            private Instant effectiveTime;

            private Builder() {
            }

            private Builder(final Key key) {
                this.name = key.name;
                this.effectiveTime = key.effectiveTime;
            }

            public Builder name(final Val name) {
                this.name = name;
                return this;
            }

            public Builder name(final String name) {
                this.name = ValString.create(name);
                return this;
            }

            public Builder effectiveTime(final Instant effectiveTime) {
                this.effectiveTime = effectiveTime;
                return this;
            }

            public Key build() {
                return new Key(name, effectiveTime);
            }
        }
    }
}
