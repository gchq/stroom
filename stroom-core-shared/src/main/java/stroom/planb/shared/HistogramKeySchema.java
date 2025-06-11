package stroom.planb.shared;

import stroom.query.api.UserTimeZone;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "keyType",
        "hashLength",
        "temporalResolution",
        "timeZone"
})
@JsonInclude(Include.NON_NULL)
public class HistogramKeySchema {

    @JsonProperty
    private final KeyType keyType;
    @JsonPropertyDescription("The hash length to use for foreign keys")
    @JsonProperty
    private final HashLength hashLength;
    @JsonProperty
    private final TemporalResolution temporalResolution;
    @JsonProperty
    private final UserTimeZone timeZone;

    @JsonCreator
    public HistogramKeySchema(@JsonProperty("keyType") final KeyType keyType,
                              @JsonProperty("hashLength") final HashLength hashLength,
                              @JsonProperty("temporalResolution") final TemporalResolution temporalResolution,
                              @JsonProperty("timeZone") final UserTimeZone timeZone) {
        this.keyType = keyType;
        this.hashLength = hashLength;
        this.temporalResolution = temporalResolution;
        this.timeZone = timeZone;
    }

    public KeyType getKeyType() {
        return keyType;
    }

    public HashLength getHashLength() {
        return hashLength;
    }

    public TemporalResolution getTemporalResolution() {
        return temporalResolution;
    }

    public UserTimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HistogramKeySchema that = (HistogramKeySchema) o;
        return keyType == that.keyType &&
               hashLength == that.hashLength &&
               temporalResolution == that.temporalResolution &&
               Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, hashLength, temporalResolution, timeZone);
    }

    @Override
    public String toString() {
        return "HistogramKeySchema{" +
               "keyType=" + keyType +
               ", hashLength=" + hashLength +
               ", temporalResolution=" + temporalResolution +
               ", timeZone=" + timeZone +
               '}';
    }

    public static class Builder extends AbstractBuilder<HistogramKeySchema, Builder> {

        private KeyType keyType = KeyType.TAGS;
        private HashLength hashLength = HashLength.INTEGER;
        private TemporalResolution temporalResolution = TemporalResolution.SECOND;
        private UserTimeZone timeZone = UserTimeZone.utc();

        public Builder() {
        }

        public Builder(final HistogramKeySchema schema) {
            this.keyType = schema.keyType;
            this.hashLength = schema.hashLength;
            this.temporalResolution = schema.temporalResolution;
            this.timeZone = schema.timeZone;
        }

        public Builder keyType(final KeyType keyType) {
            this.keyType = keyType;
            return self();
        }

        public Builder hashLength(final HashLength hashLength) {
            this.hashLength = hashLength;
            return self();
        }

        public Builder temporalResolution(final TemporalResolution temporalResolution) {
            this.temporalResolution = temporalResolution;
            return self();
        }

        public Builder timeZone(final UserTimeZone timeZone) {
            this.timeZone = timeZone;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public HistogramKeySchema build() {
            return new HistogramKeySchema(
                    keyType,
                    hashLength,
                    temporalResolution,
                    timeZone);
        }
    }
}
