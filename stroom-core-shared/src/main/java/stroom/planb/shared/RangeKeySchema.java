package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "rangeType"
})
@JsonInclude(Include.NON_NULL)
public class RangeKeySchema {

    public static final RangeType DEFAULT_RANGE_TYPE = RangeType.LONG;

    @JsonProperty
    final RangeType rangeType;

    @JsonCreator
    public RangeKeySchema(@JsonProperty("rangeType") final RangeType rangeType) {
        this.rangeType = rangeType;
    }

    public RangeType getRangeType() {
        return rangeType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RangeKeySchema that = (RangeKeySchema) o;
        return rangeType == that.rangeType;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rangeType);
    }

    @Override
    public String toString() {
        return "RangeKeySchema{" +
               "rangeType=" + rangeType +
               '}';
    }

    public static class Builder extends AbstractBuilder<RangeKeySchema, Builder> {

        private RangeType rangeType;

        public Builder() {
        }

        public Builder(final RangeKeySchema schema) {
            this.rangeType = schema.rangeType;
        }

        public Builder rangeType(final RangeType rangeType) {
            this.rangeType = rangeType;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public RangeKeySchema build() {
            return new RangeKeySchema(rangeType);
        }
    }
}
