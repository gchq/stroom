package stroom.planb.impl.data;

import stroom.lmdb2.KV;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.query.language.functions.Val;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public final class TemporalState extends KV<TemporalKey, Val> implements PlanBValue {

    @JsonCreator
    public TemporalState(@JsonProperty("key") final TemporalKey key,
                         @JsonProperty("value") final Val value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<TemporalState, Builder, TemporalKey, Val> {

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
}
