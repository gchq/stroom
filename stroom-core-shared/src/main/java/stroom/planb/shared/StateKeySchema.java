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
        "deduplicateLargeKeys",
        "deduplicateThreshold",
        "hashLength"
})
@JsonInclude(Include.NON_NULL)
public class StateKeySchema {

    @JsonProperty
    private final StateKeyType stateKeyType;
    @JsonPropertyDescription("If we are using smart keys then should we deduplicate large keys to save storage space?")
    @JsonProperty
    private final boolean deduplicateLargeKeys;
    @JsonPropertyDescription("Keys longer than the specified byte length will be deduplicated if using smart keys " +
                             "and deduplicating")
    @JsonProperty
    private final int deduplicateThreshold;
    @JsonPropertyDescription("The hash length to use for hashed keys or foreign keys")
    @JsonProperty
    private final HashLength hashLength;

    @JsonCreator
    public StateKeySchema(@JsonProperty("stateKeyType") final StateKeyType stateKeyType,
                          @JsonProperty("deduplicateLargeKeys") final boolean deduplicateLargeKeys,
                          @JsonProperty("deduplicateThreshold") final int deduplicateThreshold,
                          @JsonProperty("hashLength") final HashLength hashLength) {
        this.stateKeyType = stateKeyType;
        this.deduplicateLargeKeys = deduplicateLargeKeys;
        this.deduplicateThreshold = deduplicateThreshold;
        this.hashLength = hashLength;
    }

    public StateKeyType getStateKeyType() {
        return stateKeyType;
    }

    public boolean isDeduplicateLargeKeys() {
        return deduplicateLargeKeys;
    }

    public int getDeduplicateThreshold() {
        return deduplicateThreshold;
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
        return deduplicateLargeKeys == that.deduplicateLargeKeys &&
               deduplicateThreshold == that.deduplicateThreshold &&
               stateKeyType == that.stateKeyType &&
               hashLength == that.hashLength;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateKeyType, deduplicateLargeKeys, deduplicateThreshold, hashLength);
    }

    @Override
    public String toString() {
        return "StateKeySchema{" +
               "stateKeyType=" + stateKeyType +
               ", deduplicateLargeKeys=" + deduplicateLargeKeys +
               ", deduplicateThreshold=" + deduplicateThreshold +
               ", hashLength=" + hashLength +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<StateKeySchema, Builder> {

        private StateKeyType stateKeyType = StateKeyType.AUTO;
        private boolean deduplicateLargeKeys;
        private int deduplicateThreshold = 32;
        private HashLength hashLength = HashLength.INTEGER;

        public Builder() {
        }

        public Builder(final StateKeySchema schema) {
            this.stateKeyType = schema.stateKeyType;
            this.deduplicateLargeKeys = schema.deduplicateLargeKeys;
            this.deduplicateThreshold = schema.deduplicateThreshold;
            this.hashLength = schema.hashLength;
        }

        public Builder stateKeyType(final StateKeyType stateKeyType) {
            this.stateKeyType = stateKeyType;
            return self();
        }

        public Builder deduplicateLargeKeys(final boolean deduplicateLargeKeys) {
            this.deduplicateLargeKeys = deduplicateLargeKeys;
            return self();
        }

        public Builder deduplicateThreshold(final int deduplicateThreshold) {
            this.deduplicateThreshold = deduplicateThreshold;
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
                    deduplicateLargeKeys,
                    deduplicateThreshold,
                    hashLength);
        }
    }
}
