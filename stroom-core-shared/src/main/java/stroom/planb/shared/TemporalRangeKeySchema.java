package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "rangeType",
        "temporalPrecision"
})
@JsonInclude(Include.NON_NULL)
public class TemporalRangeKeySchema extends RangeKeySchema {

    private static final TemporalPrecision DEFAULT_TEMPORAL_PRECISION = TemporalPrecision.MILLISECOND;

    @JsonProperty
    private final TemporalPrecision temporalPrecision;

    @JsonCreator
    public TemporalRangeKeySchema(@JsonProperty("rangeType") final RangeType rangeType,
                                  @JsonProperty("temporalPrecision") final TemporalPrecision temporalPrecision) {
        super(rangeType);
        this.temporalPrecision = NullSafe.requireNonNullElse(temporalPrecision, DEFAULT_TEMPORAL_PRECISION);
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
        final TemporalRangeKeySchema that = (TemporalRangeKeySchema) o;
        return temporalPrecision == that.temporalPrecision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), temporalPrecision);
    }

    @Override
    public String toString() {
        return "TemporalRangeKeySchema{" +
               "rangeType=" + rangeType +
               ", temporalPrecision=" + temporalPrecision +
               '}';
    }

    public static class Builder extends AbstractBuilder<TemporalRangeKeySchema, Builder> {

        private RangeType rangeType;
        private TemporalPrecision temporalPrecision;

        public Builder() {
        }

        public Builder(final TemporalRangeKeySchema schema) {
            if (schema != null) {
                this.rangeType = schema.rangeType;
                this.temporalPrecision = schema.temporalPrecision;
            }
        }

        public Builder rangeType(final RangeType rangeType) {
            this.rangeType = rangeType;
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
        public TemporalRangeKeySchema build() {
            return new TemporalRangeKeySchema(rangeType, temporalPrecision);
        }
    }
}
