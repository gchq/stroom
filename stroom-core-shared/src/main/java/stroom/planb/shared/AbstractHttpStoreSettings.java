/*
 * Copyright 2016-2026 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Settings for a store whose parts are transferred between nodes over HTTP, whose reads can be served
 * from snapshots, and whose writes put a whole value at a key.
 *
 * <p>None of this is meaningful to a trace store: snapshots are only created and consulted by
 * {@code RestStoreShard} and {@code PlanBQueryService}; {@code synchroniseMerge} only reaches
 * {@code RestPartDestination}, never {@code SharedFileStorePartDestination}; and {@code TraceDb}
 * merges into an existing trace root with {@code MDB_NOOVERWRITE} regardless. {@link TraceSettings}
 * therefore extends {@link AbstractPlanBSettings} directly and cannot express any of them.
 */
@JsonInclude(Include.NON_NULL)
public abstract sealed class AbstractHttpStoreSettings extends AbstractPlanBSettings permits
        StateSettings,
        TemporalStateSettings,
        RangeStateSettings,
        TemporalRangeStateSettings,
        SessionSettings,
        HistogramSettings,
        MetricSettings {

    @JsonProperty
    private final Boolean synchroniseMerge;
    @JsonProperty
    private final Boolean overwrite;
    @JsonProperty
    private final SnapshotSettings snapshotSettings;

    public AbstractHttpStoreSettings(final Long maxStoreSize,
                                     final Boolean synchroniseMerge,
                                     final Boolean overwrite,
                                     final RetentionSettings retention,
                                     final SnapshotSettings snapshotSettings) {
        super(maxStoreSize, retention);
        this.synchroniseMerge = Objects.requireNonNullElse(synchroniseMerge, false);
        this.overwrite = Objects.requireNonNullElse(overwrite, true);
        this.snapshotSettings = Objects.requireNonNullElse(snapshotSettings, new SnapshotSettings());
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

    public SnapshotSettings getSnapshotSettings() {
        return snapshotSettings;
    }

    /**
     * The snapshot settings for the given store, or all-disabled defaults if the store is not served
     * over HTTP.
     */
    public static SnapshotSettings snapshotSettings(final AbstractPlanBSettings settings) {
        return settings instanceof final AbstractHttpStoreSettings httpStoreSettings
                ? httpStoreSettings.getSnapshotSettings()
                : new SnapshotSettings();
    }

    /**
     * Whether the receiving node should merge a transferred part synchronously. Always false for a
     * store that is not served over HTTP, as there is no part transfer to synchronise with.
     */
    public static boolean synchroniseMerge(final AbstractPlanBSettings settings) {
        return settings instanceof final AbstractHttpStoreSettings httpStoreSettings
               && Boolean.TRUE.equals(httpStoreSettings.getSynchroniseMerge());
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
        final AbstractHttpStoreSettings that = (AbstractHttpStoreSettings) o;
        return Objects.equals(synchroniseMerge, that.synchroniseMerge) &&
               Objects.equals(overwrite, that.overwrite) &&
               Objects.equals(snapshotSettings, that.snapshotSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), synchroniseMerge, overwrite, snapshotSettings);
    }

    @Override
    public String toString() {
        return super.toString() +
               ", synchroniseMerge=" + synchroniseMerge +
               ", overwrite=" + overwrite +
               ", snapshotSettings=" + snapshotSettings;
    }

    public abstract static class AbstractHttpBuilder<
            T extends AbstractHttpStoreSettings,
            B extends AbstractHttpBuilder<T, ?>>
            extends AbstractBuilder<T, B> {

        protected Boolean synchroniseMerge;
        protected Boolean overwrite;
        protected SnapshotSettings snapshotSettings;

        public AbstractHttpBuilder() {
        }

        public AbstractHttpBuilder(final AbstractHttpStoreSettings settings) {
            super(settings);
            if (settings != null) {
                this.synchroniseMerge = settings.synchroniseMerge;
                this.overwrite = settings.overwrite;
                this.snapshotSettings = settings.snapshotSettings;
            }
        }

        public B synchroniseMerge(final Boolean synchroniseMerge) {
            this.synchroniseMerge = synchroniseMerge;
            return self();
        }

        public B overwrite(final Boolean overwrite) {
            this.overwrite = overwrite;
            return self();
        }

        public B snapshotSettings(final SnapshotSettings snapshotSettings) {
            this.snapshotSettings = snapshotSettings;
            return self();
        }
    }
}
