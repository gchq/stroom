package stroom.planb.impl.db.state;

import stroom.lmdb2.KV;
import stroom.planb.impl.db.PlanBValue;
import stroom.query.language.functions.Val;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"key", "value"})
@JsonInclude(Include.NON_NULL)
public class State extends KV<Val, Val> implements PlanBValue {

    @JsonCreator
    public State(@JsonProperty("key") final Val key,
                 @JsonProperty("value") final Val value) {
        super(key, value);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractKVBuilder<State, Builder, Val, Val> {

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
