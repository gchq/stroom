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
        "period",
        "timeZone"
})
@JsonInclude(Include.NON_NULL)
public class HistogramKeySchema {

    @JsonProperty
    private final HistogramKeyType keyType;
    @JsonProperty
    private final HistogramPeriod period;
    @JsonProperty
    private final UserTimeZone timeZone;

    @JsonCreator
    public HistogramKeySchema(@JsonProperty("keyType") final HistogramKeyType keyType,
                              @JsonProperty("period") final HistogramPeriod period,
                              @JsonProperty("timeZone") final UserTimeZone timeZone) {
        this.keyType = keyType;
        this.period = period;
        this.timeZone = timeZone;
    }

    public HistogramKeyType getKeyType() {
        return keyType;
    }

    public HistogramPeriod getPeriod() {
        return period;
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
               period == that.period &&
               Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, period, timeZone);
    }

    @Override
    public String toString() {
        return "HistogramKeySchema{" +
               "keyType=" + keyType +
               ", period=" + period +
               ", timeZone=" + timeZone +
               '}';
    }

    public static class Builder extends AbstractBuilder<HistogramKeySchema, Builder> {

        private HistogramKeyType keyType = HistogramKeyType.TAGS;
        private HistogramPeriod period = HistogramPeriod.SECOND;
        private UserTimeZone timeZone = UserTimeZone.utc();

        public Builder() {
        }

        public Builder(final HistogramKeySchema schema) {
            this.keyType = schema.keyType;
            this.period = schema.period;
            this.timeZone = schema.timeZone;
        }

        public Builder keyType(final HistogramKeyType keyType) {
            this.keyType = keyType;
            return self();
        }

        public Builder period(final HistogramPeriod period) {
            this.period = period;
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
                    period,
                    timeZone);
        }
    }
}
