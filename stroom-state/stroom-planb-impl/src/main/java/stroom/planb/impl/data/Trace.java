package stroom.planb.impl.data;

import stroom.lmdb2.KV;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public final class Trace extends KV<TraceKey, TraceValue> implements PlanBValue {

    @JsonCreator
    public Trace(@JsonProperty("key") final TraceKey key,
                 @JsonProperty("value") final TraceValue value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<Trace, Builder, TraceKey, TraceValue> {

        private Builder() {
        }

        private Builder(final Trace key) {
            super(key);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Trace build() {
            return new Trace(key, value);
        }
    }
}
