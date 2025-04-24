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
        "overwrite"
})
@JsonInclude(Include.NON_NULL)
public class StateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final Boolean overwrite;

    @JsonCreator
    public StateSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                         @JsonProperty("synchroniseMerge") final boolean synchroniseMerge,
                         @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                         @JsonProperty("overwrite") final Boolean overwrite) {
        super(maxStoreSize, synchroniseMerge, snapshotSettings);
        this.overwrite = overwrite;
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
        final StateSettings that = (StateSettings) o;
        return Objects.equals(overwrite, that.overwrite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), overwrite);
    }

    @Override
    public String toString() {
        return "StateSettings{" +
               "overwrite=" + overwrite +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<StateSettings, Builder> {

        protected Boolean overwrite;

        public Builder() {
        }

        public Builder(final StateSettings settings) {
            super(settings);
            this.overwrite = settings.overwrite;
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
        public StateSettings build() {
            return new StateSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    snapshotSettings,
                    overwrite);
        }
    }
}
