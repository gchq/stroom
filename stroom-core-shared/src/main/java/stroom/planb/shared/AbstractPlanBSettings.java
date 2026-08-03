/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.util.shared.time.SimpleDuration;

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
    public static final long DEFAULT_MAX_STORE_SIZE = 10737418240L;

    /** See {@code rootCutOffError} — must stay clear of the pathways processor's grace period. */
    private static final long MIN_ROOT_CUT_OFF_MS = 60_000L;

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
        this.maxStoreSize = Objects.requireNonNullElse(maxStoreSize, DEFAULT_MAX_STORE_SIZE);
        this.synchroniseMerge = Objects.requireNonNullElse(synchroniseMerge, false);
        this.overwrite = Objects.requireNonNullElse(overwrite, true);
        this.retention = Objects.requireNonNullElse(retention, new RetentionSettings.Builder().build());
        this.snapshotSettings = Objects.requireNonNullElse(snapshotSettings, new SnapshotSettings());
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

    /**
     * Validates the settings that have to agree with each other, for both the client (which blocks
     * the save) and the server (which backstops the import and REST paths).
     *
     * @return the first user-facing message found, or null if the settings are valid
     */
    public static String validationError(final AbstractPlanBSettings settings) {
        if (settings == null) {
            return null;
        }
        final String checkIntervalError = RetentionSettings.checkIntervalError(settings.getRetention());
        if (checkIntervalError != null) {
            return checkIntervalError;
        }
        final String archiveAgeError = archiveAgeError(settings);
        if (archiveAgeError != null) {
            return archiveAgeError;
        }
        return rootCutOffError(settings);
    }

    /**
     * The root cut-off has to sit between two hard floors and ceilings. Below roughly a minute it can
     * evict a trace's root before the pathways processor has had its (10 second) grace period to find
     * it in the live store, silently dropping the trace from pathway analysis. Above the retention
     * period it can never be reached, because retention deletes the root first.
     */
    private static String rootCutOffError(final AbstractPlanBSettings settings) {
        final ArchivalSettings archival = settings instanceof final HasSharedFileStore s
                                          && s.getSharedFileStore() != null
                ? s.getSharedFileStore().getArchival()
                : null;
        if (archival == null) {
            return null;
        }
        final SimpleDuration cutOff = archival.getRootCutOff();
        if (cutOff == null) {
            return null;
        }
        if (cutOff.getApproxMillis() < MIN_ROOT_CUT_OFF_MS) {
            return "'Keep Trace Root For' (" + cutOff.toLongString() + ") must be at least one minute, "
                   + "otherwise a trace's root can be evicted before pathway processing has seen it.";
        }
        final RetentionSettings retention = settings.getRetention();
        if (retention != null && retention.isEnabled() && retention.getDuration() != null
                && cutOff.getApproxMillis() >= retention.getDuration().getApproxMillis()) {
            return "'Keep Trace Root For' (" + cutOff.toLongString() + ") must be shorter than "
                   + "'Retain For' (" + retention.getDuration().toLongString() + "), otherwise retention "
                   + "removes the trace root before the cut-off is ever reached.";
        }
        return null;
    }

    private static String archiveAgeError(final AbstractPlanBSettings settings) {
        final ArchivalSettings archival = settings instanceof final HasSharedFileStore s
                                          && s.getSharedFileStore() != null
                ? s.getSharedFileStore().getArchival()
                : null;
        final RetentionSettings retention = settings.getRetention();
        if (archival == null || !archival.isEnabled() || retention == null || !retention.isEnabled()) {
            return null;
        }
        final SimpleDuration archiveAge = archival.getDuration();
        final SimpleDuration retainFor = retention.getDuration();
        if (archiveAge == null || retainFor == null) {
            return null;
        }
        if (archiveAge.getApproxMillis() >= retainFor.getApproxMillis()) {
            return "'Archive Data Older Than' (" + archiveAge.toLongString() + ") must be shorter than "
                   + "'Retain For' (" + retainFor.toLongString() + "), otherwise retention deletes the "
                   + "data before it can be archived and nothing is ever written to the archive.";
        }
        return null;
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
