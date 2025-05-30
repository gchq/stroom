package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "valueType"
})
@JsonInclude(Include.NON_NULL)
public class HistogramValueSchema {

    @JsonProperty
    private final HistogramValueMax valueType;

    @JsonCreator
    public HistogramValueSchema(@JsonProperty("valueType") final HistogramValueMax valueType) {
        this.valueType = valueType;
    }

    public HistogramValueMax getHistogramValueType() {
        return valueType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HistogramValueSchema that = (HistogramValueSchema) o;
        return valueType == that.valueType;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(valueType);
    }

    @Override
    public String toString() {
        return "HistogramValueSchema{" +
               "valueType=" + valueType +
               '}';
    }

    public static class Builder extends AbstractBuilder<HistogramValueSchema, Builder> {

        private HistogramValueMax valueType = HistogramValueMax.TWO;

        public Builder() {
        }

        public Builder(final HistogramValueSchema schema) {
            this.valueType = schema.valueType;
        }

        public Builder valueType(final HistogramValueMax valueType) {
            this.valueType = valueType;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public HistogramValueSchema build() {
            return new HistogramValueSchema(
                    valueType);
        }
    }
}
