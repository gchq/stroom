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
        "condense",
        "retention",
        "useStateTimeForRetention",
        "overwrite",
        "rangeType",
        "stateValueSchema",
        "timePrecision"
})
@JsonInclude(Include.NON_NULL)
public class TemporalRangedStateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final DurationSetting condense;
    @JsonProperty
    private final DurationSetting retention;
    @JsonProperty
    private final Boolean useStateTimeForRetention;
    @JsonProperty
    private final Boolean overwrite;
    @JsonProperty
    private final RangeType rangeType;
    @JsonProperty
    private final StateValueSchema stateValueSchema;
    @JsonProperty
    private final TimePrecision timePrecision;

    @JsonCreator
    public TemporalRangedStateSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                                       @JsonProperty("synchroniseMerge") final boolean synchroniseMerge,
                                       @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                                       @JsonProperty("condense") final DurationSetting condense,
                                       @JsonProperty("retention") final DurationSetting retention,
                                       @JsonProperty("useStateTimeForRetention") final Boolean useStateTimeForRetention,
                                       @JsonProperty("overwrite") final Boolean overwrite,
                                       @JsonProperty("rangeType") final RangeType rangeType,
                                       @JsonProperty("stateValueSchema") final StateValueSchema stateValueSchema,
                                       @JsonProperty("timePrecision") final TimePrecision timePrecision) {
        super(maxStoreSize, synchroniseMerge, snapshotSettings);
        this.condense = condense;
        this.retention = retention;
        this.useStateTimeForRetention = useStateTimeForRetention;
        this.overwrite = overwrite;
        this.rangeType = rangeType;
        this.stateValueSchema = stateValueSchema;
        this.timePrecision = timePrecision;
    }

    public DurationSetting getCondense() {
        return condense;
    }

    public DurationSetting getRetention() {
        return retention;
    }

    public Boolean getUseStateTimeForRetention() {
        return useStateTimeForRetention;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public RangeType getRangeType() {
        return rangeType;
    }

    public StateValueSchema getStateValueSchema() {
        return stateValueSchema;
    }

    public TimePrecision getTimePrecision() {
        return timePrecision;
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
        final TemporalRangedStateSettings that = (TemporalRangedStateSettings) o;
        return Objects.equals(condense, that.condense) &&
               Objects.equals(retention, that.retention) &&
               Objects.equals(useStateTimeForRetention, that.useStateTimeForRetention) &&
               Objects.equals(overwrite, that.overwrite) &&
               rangeType == that.rangeType &&
               Objects.equals(stateValueSchema, that.stateValueSchema) &&
               timePrecision == that.timePrecision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                condense,
                retention,
                useStateTimeForRetention,
                overwrite,
                rangeType,
                stateValueSchema,
                timePrecision);
    }

    @Override
    public String toString() {
        return "TemporalRangedStateSettings{" +
               "condense=" + condense +
               ", retention=" + retention +
               ", useStateTimeForRetention=" + useStateTimeForRetention +
               ", overwrite=" + overwrite +
               ", rangeType=" + rangeType +
               ", stateValueSchema=" + stateValueSchema +
               ", timePrecision=" + timePrecision +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<TemporalRangedStateSettings, Builder> {

        private DurationSetting condense;
        private DurationSetting retention;
        private Boolean useStateTimeForRetention;
        private Boolean overwrite;
        private RangeType rangeType;
        private StateValueSchema stateValueSchema;
        private TimePrecision timePrecision;

        public Builder() {
        }

        public Builder(final TemporalRangedStateSettings settings) {
            super(settings);
            this.condense = settings.condense;
            this.retention = settings.retention;
            this.useStateTimeForRetention = settings.useStateTimeForRetention;
            this.overwrite = settings.overwrite;
            this.rangeType = settings.rangeType;
            this.stateValueSchema = settings.stateValueSchema;
            this.timePrecision = settings.timePrecision;
        }

        public Builder condense(final DurationSetting condense) {
            this.condense = condense;
            return self();
        }

        public Builder retention(final DurationSetting retention) {
            this.retention = retention;
            return self();
        }

        public Builder useStateTimeForRetention(final Boolean useStateTimeForRetention) {
            this.useStateTimeForRetention = useStateTimeForRetention;
            return self();
        }

        public Builder overwrite(final Boolean overwrite) {
            this.overwrite = overwrite;
            return self();
        }

        public Builder rangeType(final RangeType rangeType) {
            this.rangeType = rangeType;
            return self();
        }

        public Builder stateValueSchema(final StateValueSchema stateValueSchema) {
            this.stateValueSchema = stateValueSchema;
            return self();
        }

        public Builder timePrecision(final TimePrecision timePrecision) {
            this.timePrecision = timePrecision;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TemporalRangedStateSettings build() {
            return new TemporalRangedStateSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    snapshotSettings,
                    condense,
                    retention,
                    useStateTimeForRetention,
                    overwrite,
                    rangeType,
                    stateValueSchema,
                    timePrecision);
        }
    }
}
