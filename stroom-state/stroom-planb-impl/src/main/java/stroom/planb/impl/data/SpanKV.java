package stroom.planb.impl.data;

import stroom.lmdb2.KV;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public final class SpanKV extends KV<SpanKey, SpanValue> implements PlanBValue {

    @JsonCreator
    public SpanKV(@JsonProperty("key") final SpanKey key,
                  @JsonProperty("value") final SpanValue value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<SpanKV, Builder, SpanKey, SpanValue> {

        private Builder() {
        }

        private Builder(final SpanKV key) {
            super(key);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public SpanKV build() {
            return new SpanKV(key, value);
        }
    }
}
