package stroom.planb.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "condense",
        "retain",
        "maxStoreSize",
        "overwrite"
})
@JsonInclude(Include.NON_NULL)
public class SessionSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final DurationSetting condense;
    @JsonProperty
    private final DurationSetting retain;
    @JsonProperty
    private final Boolean overwrite;

    @JsonCreator
    public SessionSettings(@JsonProperty("condense") final DurationSetting condense,
                           @JsonProperty("retain") final DurationSetting retain,
                           @JsonProperty("maxStoreSize") final Long maxStoreSize,
                           @JsonProperty("overwrite") final Boolean overwrite) {
        super(maxStoreSize);
        this.condense = condense;
        this.retain = retain;
        this.overwrite = overwrite;
    }

    public DurationSetting getCondense() {
        return condense;
    }

    public DurationSetting getRetain() {
        return retain;
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
        final SessionSettings that = (SessionSettings) o;
        return Objects.equals(condense, that.condense) &&
               Objects.equals(retain, that.retain) &&
               Objects.equals(overwrite, that.overwrite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), condense, retain, overwrite);
    }

    @Override
    public String toString() {
        return "SessionSettings{" +
               "condense=" + condense +
               ", retain=" + retain +
               ", overwrite=" + overwrite +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<SessionSettings, Builder> {

        protected DurationSetting condense;
        protected DurationSetting retain;
        protected Boolean overwrite;

        public Builder() {
        }

        public Builder(final SessionSettings settings) {
            super(settings);
            this.condense = settings.condense;
            this.retain = settings.retain;
            this.overwrite = settings.overwrite;
        }

        public Builder condense(final DurationSetting condense) {
            this.condense = condense;
            return self();
        }

        public Builder retain(final DurationSetting retain) {
            this.retain = retain;
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
        public SessionSettings build() {
            return new SessionSettings(
                    condense,
                    retain,
                    maxStoreSize,
                    overwrite);
        }
    }
}
