/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.planb.shared;

import stroom.docs.shared.Description;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StateSettings.class, name = "state"),
        @JsonSubTypes.Type(value = TemporalStateSettings.class, name = "temporalState"),
        @JsonSubTypes.Type(value = RangeStateSettings.class, name = "rangeState"),
        @JsonSubTypes.Type(value = TemporalRangeStateSettings.class, name = "temporalRangeState"),
        @JsonSubTypes.Type(value = SessionSettings.class, name = "session"),
        @JsonSubTypes.Type(value = HistogramSettings.class, name = "histogram"),
        @JsonSubTypes.Type(value = MetricSettings.class, name = "metric"),
        @JsonSubTypes.Type(value = TraceSettings.class, name = "trace")
})
@Description("Defines settings for Plan B")
@JsonPropertyOrder({
        "maxStoreSize",
        "synchroniseMerge",
        "overwrite",
        "retention",
        "snapshotSettings"
})
@JsonInclude(Include.NON_NULL)
public abstract sealed class AbstractPlanBSettings permits
        StateSettings,
        TemporalStateSettings,
        RangeStateSettings,
        TemporalRangeStateSettings,
        SessionSettings,
        HistogramSettings,
        MetricSettings,
        TraceSettings {

    // 10 GiB
    public static final Long DEFAULT_MAX_STORE_SIZE = 10737418240L;

    @JsonProperty
    private final Long maxStoreSize;
    @JsonProperty
    private final Boolean synchroniseMerge;
    @JsonProperty
    private final Boolean overwrite;
    @JsonProperty
    private final RetentionSettings retention;
    @JsonProperty
    private final SnapshotSettings snapshotSettings;

    public AbstractPlanBSettings(final Long maxStoreSize,
                                 final Boolean synchroniseMerge,
                                 final Boolean overwrite,
                                 final RetentionSettings retention,
                                 final SnapshotSettings snapshotSettings) {
        this.maxStoreSize = NullSafe.requireNonNullElse(maxStoreSize, DEFAULT_MAX_STORE_SIZE);
        this.synchroniseMerge = NullSafe.requireNonNullElse(synchroniseMerge, false);
        this.overwrite = NullSafe.requireNonNullElse(overwrite, true);
        this.retention = NullSafe.requireNonNullElse(retention, new RetentionSettings.Builder().build());
        this.snapshotSettings = NullSafe.requireNonNullElse(snapshotSettings, new SnapshotSettings());
    }

    public Long getMaxStoreSize() {
        return maxStoreSize;
    }

    public Boolean getSynchroniseMerge() {
        return synchroniseMerge;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public boolean overwrite() {
        return overwrite == null || overwrite;
    }

    public RetentionSettings getRetention() {
        return retention;
    }

    public SnapshotSettings getSnapshotSettings() {
        return snapshotSettings;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractPlanBSettings settings = (AbstractPlanBSettings) o;
        return Objects.equals(maxStoreSize, settings.maxStoreSize) &&
               Objects.equals(synchroniseMerge, settings.synchroniseMerge) &&
               Objects.equals(overwrite, settings.overwrite) &&
               Objects.equals(retention, settings.retention) &&
               Objects.equals(snapshotSettings, settings.snapshotSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
    }

    @Override
    public String toString() {
        return "maxStoreSize=" + maxStoreSize +
               ", synchroniseMerge=" + synchroniseMerge +
               ", overwrite=" + overwrite +
               ", retention=" + retention +
               ", snapshotSettings=" + snapshotSettings;
    }

    public abstract static class AbstractBuilder<T extends AbstractPlanBSettings, B extends AbstractBuilder<T, ?>> {

        protected Long maxStoreSize;
        protected Boolean synchroniseMerge;
        protected Boolean overwrite;
        protected RetentionSettings retention;
        protected SnapshotSettings snapshotSettings;

        public AbstractBuilder() {
        }

        public AbstractBuilder(final AbstractPlanBSettings settings) {
            if (settings != null) {
                this.maxStoreSize = settings.maxStoreSize;
                this.synchroniseMerge = settings.synchroniseMerge;
                this.overwrite = settings.overwrite;
                this.retention = settings.retention;
                this.snapshotSettings = settings.snapshotSettings;
            }
        }

        public B maxStoreSize(final Long maxStoreSize) {
            this.maxStoreSize = maxStoreSize;
            return self();
        }

        public B synchroniseMerge(final Boolean synchroniseMerge) {
            this.synchroniseMerge = synchroniseMerge;
            return self();
        }

        public B overwrite(final Boolean overwrite) {
            this.overwrite = overwrite;
            return self();
        }

        public B retention(final RetentionSettings retention) {
            this.retention = retention;
            return self();
        }

        public B snapshotSettings(final SnapshotSettings snapshotSettings) {
            this.snapshotSettings = snapshotSettings;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
