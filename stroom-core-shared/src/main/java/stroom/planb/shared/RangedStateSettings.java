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
        "overwrite",
        "rangeType",
        "stateValueSchema"
})
@JsonInclude(Include.NON_NULL)
public class RangedStateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final Boolean overwrite;
    @JsonProperty
    private final RangeType rangeType;
    @JsonProperty
    private final StateValueSchema stateValueSchema;

    @JsonCreator
    public RangedStateSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                               @JsonProperty("synchroniseMerge") final boolean synchroniseMerge,
                               @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                               @JsonProperty("overwrite") final Boolean overwrite,
                               @JsonProperty("rangeType") final RangeType rangeType,
                               @JsonProperty("stateValueSchema") final StateValueSchema stateValueSchema) {
        super(maxStoreSize, synchroniseMerge, snapshotSettings);
        this.overwrite = overwrite;
        this.rangeType = rangeType;
        this.stateValueSchema = stateValueSchema;
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
        final RangedStateSettings that = (RangedStateSettings) o;
        return Objects.equals(overwrite, that.overwrite) &&
               rangeType == that.rangeType &&
               Objects.equals(stateValueSchema, that.stateValueSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), overwrite, rangeType, stateValueSchema);
    }

    @Override
    public String toString() {
        return "RangedStateSettings{" +
               "overwrite=" + overwrite +
               ", rangeType=" + rangeType +
               ", stateValueSchema=" + stateValueSchema +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<RangedStateSettings, Builder> {

        private Boolean overwrite;
        private RangeType rangeType;
        private StateValueSchema stateValueSchema;

        public Builder() {
        }

        public Builder(final RangedStateSettings settings) {
            super(settings);
            this.overwrite = settings.overwrite;
            this.rangeType = settings.rangeType;
            this.stateValueSchema = settings.stateValueSchema;
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

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public RangedStateSettings build() {
            return new RangedStateSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    snapshotSettings,
                    overwrite,
                    rangeType,
                    stateValueSchema);
        }
    }
}
