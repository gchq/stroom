package stroom.planb.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "condense",
        "retention",
        "maxStoreSize",
        "synchroniseMerge",
        "snapshotSettings",
        "overwrite"
})
@JsonInclude(Include.NON_NULL)
public class TemporalStateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final DurationSetting condense;
    @JsonProperty
    private final DurationSetting retention;
    @JsonProperty
    private final Boolean overwrite;

    @JsonCreator
    public TemporalStateSettings(@JsonProperty("condense") final DurationSetting condense,
                                 @JsonProperty("retention") final DurationSetting retention,
                                 @JsonProperty("maxStoreSize") final Long maxStoreSize,
                                 @JsonProperty("synchroniseMerge") final boolean synchroniseMerge,
                                 @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                                 @JsonProperty("overwrite") final Boolean overwrite) {
        super(maxStoreSize, synchroniseMerge, snapshotSettings);
        this.condense = condense;
        this.retention = retention;
        this.overwrite = overwrite;
    }

    public DurationSetting getCondense() {
        return condense;
    }

    public DurationSetting getRetention() {
        return retention;
    }

    public Boolean getOverwrite() {
        return overwrite;
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
               Objects.equals(retention, that.retention) &&
               Objects.equals(overwrite, that.overwrite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), condense, retention, overwrite);
    }

    @Override
    public String toString() {
        return "TemporalStateSettings{" +
               "condense=" + condense +
               ", retention=" + retention +
               ", overwrite=" + overwrite +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<TemporalStateSettings, Builder> {

        protected DurationSetting condense;
        protected DurationSetting retention;
        protected Boolean overwrite;

        public Builder() {
        }

        public Builder(final TemporalStateSettings settings) {
            super(settings);
            this.condense = settings.condense;
            this.retention = settings.retention;
            this.overwrite = settings.overwrite;
        }

        public Builder condense(final DurationSetting condense) {
            this.condense = condense;
            return self();
        }

        public Builder retention(final DurationSetting retention) {
            this.retention = retention;
            return self();
        }

        public Builder overwrite(final Boolean overwrite) {
            this.overwrite = overwrite;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TemporalStateSettings build() {
            return new TemporalStateSettings(
                    condense,
                    retention,
                    maxStoreSize,
                    synchroniseMerge,
                    snapshotSettings,
                    overwrite);
        }
    }
}
