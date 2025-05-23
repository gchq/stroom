package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "stateKeyType",
        "hashLength"
})
@JsonInclude(Include.NON_NULL)
public class StateKeySchema {

    @JsonProperty
    final StateKeyType stateKeyType;

    @JsonPropertyDescription("The hash length to use for foreign keys")
    @JsonProperty
    final HashLength hashLength;

    @JsonCreator
    public StateKeySchema(@JsonProperty("stateKeyType") final StateKeyType stateKeyType,
                          @JsonProperty("hashLength") final HashLength hashLength) {
        this.stateKeyType = stateKeyType;
        this.hashLength = hashLength;
    }

    public StateKeyType getStateKeyType() {
        return stateKeyType;
    }

    public HashLength getHashLength() {
        return hashLength;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StateKeySchema that = (StateKeySchema) o;
        return stateKeyType == that.stateKeyType &&
               hashLength == that.hashLength;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateKeyType, hashLength);
    }

    @Override
    public String toString() {
        return "StateKeySchema{" +
               "stateKeyType=" + stateKeyType +
               ", hashLength=" + hashLength +
               '}';
    }

    public static class Builder extends AbstractBuilder<StateKeySchema, Builder> {

        private StateKeyType stateKeyType = StateKeyType.VARIABLE;
        private HashLength hashLength = HashLength.INTEGER;

        public Builder() {
        }

        public Builder(final StateKeySchema schema) {
            this.stateKeyType = schema.stateKeyType;
            this.hashLength = schema.hashLength;
        }

        public Builder stateKeyType(final StateKeyType stateKeyType) {
            this.stateKeyType = stateKeyType;
            return self();
        }

        public Builder hashLength(final HashLength hashLength) {
            this.hashLength = hashLength;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public StateKeySchema build() {
            return new StateKeySchema(
                    stateKeyType,
                    hashLength);
        }
    }
}
