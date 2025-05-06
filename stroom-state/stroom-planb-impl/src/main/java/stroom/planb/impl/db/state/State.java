package stroom.planb.impl.db.state;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.PlanBValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public class State extends KV<String, StateValue> implements PlanBValue {

    @JsonCreator
    public State(@JsonProperty("key") final String key,
                 @JsonProperty("value") final StateValue value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<State, Builder, String, StateValue> {

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
}
