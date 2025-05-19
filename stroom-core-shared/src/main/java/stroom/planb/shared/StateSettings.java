package stroom.planb.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "maxStoreSize",
        "synchroniseMerge",
        "snapshotSettings",
        "retention",
        "overwrite",
        "stateKeySchema",
        "stateValueSchema"
})
@JsonInclude(Include.NON_NULL)
public class StateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final DurationSetting retention;
    @JsonProperty
    private final Boolean overwrite;
    @JsonProperty
    private final StateKeySchema stateKeySchema;
    @JsonProperty
    private final StateValueSchema stateValueSchema;

    @JsonCreator
    public StateSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                         @JsonProperty("synchroniseMerge") final boolean synchroniseMerge,
                         @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                         @JsonProperty("retention") final DurationSetting retention,
                         @JsonProperty("overwrite") final Boolean overwrite,
                         @JsonProperty("stateKeySchema") final StateKeySchema stateKeySchema,
                         @JsonProperty("stateValueSchema") final StateValueSchema stateValueSchema) {
        super(maxStoreSize, synchroniseMerge, snapshotSettings);
        this.retention = retention;
        this.overwrite = overwrite;
        this.stateKeySchema = stateKeySchema;
        this.stateValueSchema = stateValueSchema;
    }

    public DurationSetting getRetention() {
        return retention;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public StateKeySchema getStateKeySchema() {
        return stateKeySchema;
    }

    public StateValueSchema getStateValueSchema() {
        return stateValueSchema;
    }

    public boolean overwrite() {
        return overwrite == null || overwrite;
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
        return Objects.equals(retention, that.retention) &&
               Objects.equals(overwrite, that.overwrite) &&
               Objects.equals(stateKeySchema, that.stateKeySchema) &&
               Objects.equals(stateValueSchema, that.stateValueSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), retention, overwrite, stateKeySchema, stateValueSchema);
    }

    @Override
    public String toString() {
        return "StateSettings{" +
               "retention=" + retention +
               ", overwrite=" + overwrite +
               ", stateKeySchema=" + stateKeySchema +
               ", stateValueSchema=" + stateValueSchema +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<StateSettings, Builder> {

        private DurationSetting retention;
        private Boolean overwrite;
        private StateKeySchema stateKeySchema;
        private StateValueSchema stateValueSchema;

        public Builder() {
        }

        public Builder(final StateSettings settings) {
            super(settings);
            this.retention = settings.retention;
            this.overwrite = settings.overwrite;
            this.stateKeySchema = settings.stateKeySchema;
            this.stateValueSchema = settings.stateValueSchema;
        }

        public Builder retention(final DurationSetting retention) {
            this.retention = retention;
            return self();
        }

        public Builder overwrite(final Boolean overwrite) {
            this.overwrite = overwrite;
            return self();
        }

        public Builder stateKeySchema(final StateKeySchema stateKeySchema) {
            this.stateKeySchema = stateKeySchema;
            return self();
        }

        public Builder stateValueSchema(final StateValueSchema stateValueSchema) {
            this.stateValueSchema = stateValueSchema;
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
                    snapshotSettings,
                    retention,
                    overwrite,
                    stateKeySchema,
                    stateValueSchema);
        }
    }
}
