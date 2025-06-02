package stroom.planb.shared;

import stroom.query.api.UserTimeZone;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "keyType",
        "temporalResolution",
        "timeZone"
})
@JsonInclude(Include.NON_NULL)
public class HistogramKeySchema {

    @JsonProperty
    private final HistogramKeyType keyType;
    @JsonProperty
    private final HistogramTemporalResolution temporalResolution;
    @JsonProperty
    private final UserTimeZone timeZone;

    @JsonCreator
    public HistogramKeySchema(@JsonProperty("keyType") final HistogramKeyType keyType,
                              @JsonProperty("temporalResolution") final HistogramTemporalResolution temporalResolution,
                              @JsonProperty("timeZone") final UserTimeZone timeZone) {
        this.keyType = keyType;
        this.temporalResolution = temporalResolution;
        this.timeZone = timeZone;
    }

    public HistogramKeyType getKeyType() {
        return keyType;
    }

    public HistogramTemporalResolution getTemporalResolution() {
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
               temporalResolution == that.temporalResolution &&
               Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, temporalResolution, timeZone);
    }

    @Override
    public String toString() {
        return "HistogramKeySchema{" +
               "keyType=" + keyType +
               ", temporalResolution=" + temporalResolution +
               ", timeZone=" + timeZone +
               '}';
    }

    public static class Builder extends AbstractBuilder<HistogramKeySchema, Builder> {

        private HistogramKeyType keyType = HistogramKeyType.TAGS;
        private HistogramTemporalResolution temporalResolution = HistogramTemporalResolution.SECOND;
        private UserTimeZone timeZone = UserTimeZone.utc();

        public Builder() {
        }

        public Builder(final HistogramKeySchema schema) {
            this.keyType = schema.keyType;
            this.temporalResolution = schema.temporalResolution;
            this.timeZone = schema.timeZone;
        }

        public Builder keyType(final HistogramKeyType keyType) {
            this.keyType = keyType;
            return self();
        }

        public Builder temporalResolution(final HistogramTemporalResolution temporalResolution) {
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
                    temporalResolution,
                    timeZone);
        }
    }
}
