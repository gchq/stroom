package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "keyType",
        "hashLength"
})
@JsonInclude(Include.NON_NULL)
public class StateKeySchema {

    static final KeyType DEFAULT_KEY_TYPE = KeyType.VARIABLE;
    static final HashLength DEFAULT_HASH_LENGTH = HashLength.INTEGER;

    @JsonProperty
    final KeyType keyType;

    @JsonPropertyDescription("The hash length to use for foreign keys")
    @JsonProperty
    final HashLength hashLength;

    @JsonCreator
    public StateKeySchema(@JsonProperty("keyType") final KeyType keyType,
                          @JsonProperty("hashLength") final HashLength hashLength) {
        this.keyType = NullSafe.requireNonNullElse(keyType, DEFAULT_KEY_TYPE);
        this.hashLength = NullSafe.requireNonNullElse(hashLength, DEFAULT_HASH_LENGTH);
    }

    public KeyType getKeyType() {
        return keyType;
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
        return keyType == that.keyType &&
               hashLength == that.hashLength;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, hashLength);
    }

    @Override
    public String toString() {
        return "StateKeySchema{" +
               "keyType=" + keyType +
               ", hashLength=" + hashLength +
               '}';
    }

    public static class Builder extends AbstractBuilder<StateKeySchema, Builder> {

        private KeyType keyType;
        private HashLength hashLength;

        public Builder() {
        }

        public Builder(final StateKeySchema schema) {
            if (schema != null) {
                this.keyType = schema.keyType;
                this.hashLength = schema.hashLength;
            }
        }

        public Builder keyType(final KeyType keyType) {
            this.keyType = keyType;
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
            return new StateKeySchema(keyType, hashLength);
        }
    }
}
