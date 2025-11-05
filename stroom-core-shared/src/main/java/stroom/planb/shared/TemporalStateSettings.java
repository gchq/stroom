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
        "condense",
        "keySchema",
        "valueSchema"
})
@JsonInclude(Include.NON_NULL)
public final class TemporalStateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final DurationSetting condense;
    @JsonProperty
    private final TemporalStateKeySchema keySchema;
    @JsonProperty
    private final StateValueSchema valueSchema;

    @JsonCreator
    public TemporalStateSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                                 @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                                 @JsonProperty("overwrite") final Boolean overwrite,
                                 @JsonProperty("retention") final RetentionSettings retention,
                                 @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                                 @JsonProperty("condense") final DurationSetting condense,
                                 @JsonProperty("keySchema") final TemporalStateKeySchema keySchema,
                                 @JsonProperty("valueSchema") final StateValueSchema valueSchema) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
        this.condense = NullSafe.requireNonNullElse(condense, new DurationSetting.Builder().build());
        this.keySchema = NullSafe.requireNonNullElse(keySchema, new TemporalStateKeySchema.Builder().build());
        this.valueSchema = NullSafe.requireNonNullElse(valueSchema, new StateValueSchema.Builder().build());
    }

    public DurationSetting getCondense() {
        return condense;
    }

    public TemporalStateKeySchema getKeySchema() {
        return keySchema;
    }

    public StateValueSchema getValueSchema() {
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
        final TemporalStateSettings that = (TemporalStateSettings) o;
        return Objects.equals(condense, that.condense) &&
               Objects.equals(keySchema, that.keySchema) &&
               Objects.equals(valueSchema, that.valueSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                condense,
                keySchema,
                valueSchema);
    }

    @Override
    public String toString() {
        return "TemporalStateSettings{" +
               super.toString() +
               ", condense=" + condense +
               ", keySchema=" + keySchema +
               ", valueSchema=" + valueSchema +
               '}';
    }

    public static class Builder extends AbstractBuilder<TemporalStateSettings, Builder> {

        private DurationSetting condense;
        private TemporalStateKeySchema keySchema;
        private StateValueSchema valueSchema;

        public Builder() {
        }

        public Builder(final TemporalStateSettings settings) {
            super(settings);
            if (settings != null) {
                this.condense = settings.condense;
                this.keySchema = settings.keySchema;
                this.valueSchema = settings.valueSchema;
            }
        }

        public Builder condense(final DurationSetting condense) {
            this.condense = condense;
            return self();
        }

        public Builder keySchema(final TemporalStateKeySchema keySchema) {
            this.keySchema = keySchema;
            return self();
        }

        public Builder valueSchema(final StateValueSchema valueSchema) {
            this.valueSchema = valueSchema;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TemporalStateSettings build() {
            return new TemporalStateSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    overwrite,
                    retention,
                    snapshotSettings,
                    condense,
                    keySchema,
                    valueSchema);
        }
    }
}
