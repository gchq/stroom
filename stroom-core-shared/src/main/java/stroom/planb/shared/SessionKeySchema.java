package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "keyType",
        "hashLength",
        "temporalPrecision"
})
@JsonInclude(Include.NON_NULL)
public class SessionKeySchema extends StateKeySchema {

    public static final TemporalPrecision DEFAULT_TEMPORAL_PRECISION = TemporalPrecision.MILLISECOND;

    @JsonProperty
    private final TemporalPrecision temporalPrecision;

    @JsonCreator
    public SessionKeySchema(@JsonProperty("keyType") final KeyType keyType,
                            @JsonProperty("hashLength") final HashLength hashLength,
                            @JsonProperty("temporalPrecision") final TemporalPrecision temporalPrecision) {
        super(keyType, hashLength);
        this.temporalPrecision = temporalPrecision;
    }

    public TemporalPrecision getTemporalPrecision() {
        return temporalPrecision;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SessionKeySchema that = (SessionKeySchema) o;
        return temporalPrecision == that.temporalPrecision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), temporalPrecision);
    }

    @Override
    public String toString() {
        return "TemporalStateKeySchema{" +
               "temporalPrecision=" + temporalPrecision +
               '}';
    }

    public static class Builder extends AbstractBuilder<SessionKeySchema, Builder> {

        private KeyType keyType = KeyType.VARIABLE;
        private HashLength hashLength = HashLength.INTEGER;
        private TemporalPrecision temporalPrecision;

        public Builder() {
        }

        public Builder(final SessionKeySchema schema) {
            this.keyType = schema.keyType;
            this.hashLength = schema.hashLength;
            this.temporalPrecision = schema.temporalPrecision;
        }

        public Builder keyType(final KeyType keyType) {
            this.keyType = keyType;
            return self();
        }

        public Builder hashLength(final HashLength hashLength) {
            this.hashLength = hashLength;
            return self();
        }

        public Builder temporalPrecision(final TemporalPrecision temporalPrecision) {
            this.temporalPrecision = temporalPrecision;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SessionKeySchema build() {
            return new SessionKeySchema(
                    keyType,
                    hashLength,
                    temporalPrecision);
        }
    }
}
