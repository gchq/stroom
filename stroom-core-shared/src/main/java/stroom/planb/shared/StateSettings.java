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
public final class StateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final StateKeySchema keySchema;
    @JsonProperty
    private final StateValueSchema valueSchema;

    @JsonCreator
    public StateSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                         @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                         @JsonProperty("overwrite") final Boolean overwrite,
                         @JsonProperty("retention") final RetentionSettings retention,
                         @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                         @JsonProperty("keySchema") final StateKeySchema keySchema,
                         @JsonProperty("valueSchema") final StateValueSchema valueSchema) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
        this.keySchema = NullSafe.requireNonNullElse(keySchema, new StateKeySchema.Builder().build());
        this.valueSchema = NullSafe.requireNonNullElse(valueSchema, new StateValueSchema.Builder().build());
    }

    public StateKeySchema getKeySchema() {
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
        final StateSettings that = (StateSettings) o;
        return Objects.equals(keySchema, that.keySchema) &&
               Objects.equals(valueSchema, that.valueSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keySchema, valueSchema);
    }

    @Override
    public String toString() {
        return "StateSettings{" +
               super.toString() +
               ", keySchema=" + keySchema +
               ", valueSchema=" + valueSchema +
               '}';
    }

    public static class Builder extends AbstractBuilder<StateSettings, Builder> {

        private StateKeySchema keySchema;
        private StateValueSchema valueSchema;

        public Builder() {
        }

        public Builder(final StateSettings settings) {
            super(settings);
            if (settings != null) {
                this.keySchema = settings.keySchema;
                this.valueSchema = settings.valueSchema;
            }
        }

        public Builder keySchema(final StateKeySchema keySchema) {
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
        public StateSettings build() {
            return new StateSettings(
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
