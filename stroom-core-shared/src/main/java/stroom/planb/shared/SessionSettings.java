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
        "keySchema"
})
@JsonInclude(Include.NON_NULL)
public final class SessionSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final DurationSetting condense;
    @JsonProperty
    private final SessionKeySchema keySchema;

    @JsonCreator
    public SessionSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                           @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                           @JsonProperty("overwrite") final Boolean overwrite,
                           @JsonProperty("retention") final RetentionSettings retention,
                           @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                           @JsonProperty("condense") final DurationSetting condense,
                           @JsonProperty("keySchema") final SessionKeySchema keySchema) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
        this.condense = NullSafe.requireNonNullElse(condense, new DurationSetting.Builder().build());
        this.keySchema = NullSafe.requireNonNullElse(keySchema, new SessionKeySchema.Builder().build());
    }

    public DurationSetting getCondense() {
        return condense;
    }

    public SessionKeySchema getKeySchema() {
        return keySchema;
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
               Objects.equals(keySchema, that.keySchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                condense,
                keySchema);
    }

    @Override
    public String toString() {
        return "SessionSettings{" +
               super.toString() +
               ", condense=" + condense +
               ", keySchema=" + keySchema +
               '}';
    }

    public static class Builder extends AbstractBuilder<SessionSettings, Builder> {

        private DurationSetting condense;
        private SessionKeySchema keySchema;

        public Builder() {
        }

        public Builder(final SessionSettings settings) {
            super(settings);
            if (settings != null) {
                this.condense = settings.condense;
                this.keySchema = settings.keySchema;
            }
        }

        public Builder condense(final DurationSetting condense) {
            this.condense = condense;
            return self();
        }

        public Builder keySchema(final SessionKeySchema keySchema) {
            this.keySchema = keySchema;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SessionSettings build() {
            return new SessionSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    overwrite,
                    retention,
                    snapshotSettings,
                    condense,
                    keySchema);
        }
    }
}
