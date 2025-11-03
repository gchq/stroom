package stroom.planb.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "maxStoreSize",
        "synchroniseMerge",
        "overwrite",
        "retention",
        "snapshotSettings",
        "keySchema",
        "valueSchema"
})
@JsonInclude(Include.NON_NULL)
public final class MetricSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final MetricKeySchema keySchema;
    @JsonProperty
    private final MetricValueSchema valueSchema;

    @JsonCreator
    public MetricSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                          @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                          @JsonProperty("overwrite") final Boolean overwrite,
                          @JsonProperty("retention") final RetentionSettings retention,
                          @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                          @JsonProperty("keySchema") final MetricKeySchema keySchema,
                          @JsonProperty("valueSchema") final MetricValueSchema valueSchema) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
        this.keySchema = NullSafe.requireNonNullElse(keySchema, new MetricKeySchema.Builder().build());
        this.valueSchema = NullSafe.requireNonNullElse(valueSchema, new MetricValueSchema.Builder().build());
    }

    public MetricKeySchema getKeySchema() {
        return keySchema;
    }

    public MetricValueSchema getValueSchema() {
        return valueSchema;
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
        final MetricSettings that = (MetricSettings) o;
        return Objects.equals(keySchema, that.keySchema) &&
               Objects.equals(valueSchema, that.valueSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keySchema, valueSchema);
    }

    @Override
    public String toString() {
        return "MetricSettings{" +
               super.toString() +
               ", keySchema=" + keySchema +
               ", valueSchema=" + valueSchema +
               '}';
    }

    public static class Builder extends AbstractBuilder<MetricSettings, Builder> {

        private MetricKeySchema keySchema;
        private MetricValueSchema valueSchema;

        public Builder() {
        }

        public Builder(final MetricSettings settings) {
            super(settings);
            if (settings != null) {
                this.keySchema = settings.keySchema;
                this.valueSchema = settings.valueSchema;
            }
        }

        public Builder keySchema(final MetricKeySchema keySchema) {
            this.keySchema = keySchema;
            return self();
        }

        public Builder valueSchema(final MetricValueSchema valueSchema) {
            this.valueSchema = valueSchema;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public MetricSettings build() {
            return new MetricSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    overwrite,
                    retention,
                    snapshotSettings,
                    keySchema,
                    valueSchema);
        }
    }
}
