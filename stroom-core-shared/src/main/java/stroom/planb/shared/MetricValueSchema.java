package stroom.planb.shared;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "valueType",
        "storeLatestValue",
        "storeMin",
        "storeMax",
        "storeCount",
        "storeSum"
})
@JsonInclude(Include.NON_NULL)
public class MetricValueSchema {

    @JsonProperty
    private final MaxValueSize valueType;
    @JsonProperty
    private final Boolean storeLatestValue;
    @JsonProperty
    private final Boolean storeMin;
    @JsonProperty
    private final Boolean storeMax;
    @JsonProperty
    private final Boolean storeCount;
    @JsonProperty
    private final Boolean storeSum;

    @JsonCreator
    public MetricValueSchema(@JsonProperty("valueType") final MaxValueSize valueType,
                             @JsonProperty("storeLatestValue") final Boolean storeLatestValue,
                             @JsonProperty("storeMin") final Boolean storeMin,
                             @JsonProperty("storeMax") final Boolean storeMax,
                             @JsonProperty("storeCount") final Boolean storeCount,
                             @JsonProperty("storeSum") final Boolean storeSum) {
        this.valueType = valueType;
        this.storeLatestValue = storeLatestValue;
        this.storeMin = storeMin;
        this.storeMax = storeMax;
        this.storeCount = storeCount;
        this.storeSum = storeSum;
    }

    public MaxValueSize getValueType() {
        return valueType;
    }

    public Boolean getStoreLatestValue() {
        return storeLatestValue;
    }

    public Boolean getStoreMin() {
        return storeMin;
    }

    public Boolean getStoreMax() {
        return storeMax;
    }

    public Boolean getStoreCount() {
        return storeCount;
    }

    public Boolean getStoreSum() {
        return storeSum;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MetricValueSchema that = (MetricValueSchema) o;
        return valueType == that.valueType &&
               Objects.equals(storeLatestValue, that.storeLatestValue) &&
               Objects.equals(storeMin, that.storeMin) &&
               Objects.equals(storeMax, that.storeMax) &&
               Objects.equals(storeCount, that.storeCount) &&
               Objects.equals(storeSum, that.storeSum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valueType, storeLatestValue, storeMin, storeMax, storeCount, storeSum);
    }

    @Override
    public String toString() {
        return "MetricValueSchema{" +
               "valueType=" + valueType +
               ", storeLatestValue=" + storeLatestValue +
               ", storeMin=" + storeMin +
               ", storeMax=" + storeMax +
               ", storeCount=" + storeCount +
               ", storeSum=" + storeSum +
               '}';
    }

    public static class Builder extends AbstractBuilder<MetricValueSchema, Builder> {

        private MaxValueSize valueType = MaxValueSize.TWO;
        private Boolean storeLatestValue;
        private Boolean storeMin;
        private Boolean storeMax;
        private Boolean storeCount;
        private Boolean storeSum;

        public Builder() {
        }

        public Builder(final MetricValueSchema schema) {
            this.valueType = schema.valueType;
        }

        public Builder valueType(final MaxValueSize valueType) {
            this.valueType = valueType;
            return self();
        }

        public Builder storeLatestValue(final Boolean storeLatestValue) {
            this.storeLatestValue = storeLatestValue;
            return self();
        }

        public Builder storeMin(final Boolean storeMin) {
            this.storeMin = storeMin;
            return self();
        }

        public Builder storeMax(final Boolean storeMax) {
            this.storeMax = storeMax;
            return self();
        }

        public Builder storeCount(final Boolean storeCount) {
            this.storeCount = storeCount;
            return self();
        }

        public Builder storeSum(final Boolean storeSum) {
            this.storeSum = storeSum;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public MetricValueSchema build() {
            return new MetricValueSchema(
                    valueType,
                    storeLatestValue,
                    storeMin,
                    storeMax,
                    storeCount,
                    storeSum);
        }
    }
}
