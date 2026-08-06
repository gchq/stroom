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

import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Settings for a Traces (TraceDb / TracesDoc) shard.
 *
 * <p>Implements {@link HasSharedFileStore} to declare that trace shards support
 * horizontal sharding and time-based archival via a shared file store
 * (see {@link SharedFileStoreSettings}).
 *
 * <p>All other PlanB doc types use their own settings class and do <em>not</em>
 * implement {@link HasSharedFileStore}.  To add sharding support to a further type,
 * implement {@link HasSharedFileStore} on its settings class and provide an
 * {@code runArchival} override in its DB class — no changes are required to
 * the core infrastructure ({@code ShardManager}, {@code ArchiveOperation}, etc.).
 */
@JsonPropertyOrder({
        "maxStoreSize",
        "synchroniseMerge",
        "overwrite",
        "retention",
        "sharedFileStore",
        "snapshotSettings",
        "maxQueryTimeRange",
        "maxSpansPerTrace"
})
@JsonInclude(Include.NON_NULL)
public final class TraceSettings extends AbstractPlanBSettings implements HasSharedFileStore {

    /**
     * Spans to accept for a single trace. A trace is sharded by its trace id, so all of its spans
     * land in one shard, and a runaway trace — typically one whose OTel context leaked into pooled
     * work, so unrelated spans keep joining it — can fill that shard's fixed-size LMDB map and stop
     * it accepting any data at all. This bounds the worst case a single trace can cost.
     */
    public static final long DEFAULT_MAX_SPANS_PER_TRACE = 100_000L;

    @JsonProperty
    private final SharedFileStoreSettings sharedFileStore;

    @JsonProperty
    private final SimpleDuration maxQueryTimeRange;

    @JsonProperty
    private final Long maxSpansPerTrace;

    @JsonCreator
    public TraceSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                         @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                         @JsonProperty("overwrite") final Boolean overwrite,
                         @JsonProperty("retention") final RetentionSettings retention,
                         @JsonProperty("sharedFileStore") final SharedFileStoreSettings sharedFileStore,
                         @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                         @JsonProperty("maxQueryTimeRange") final SimpleDuration maxQueryTimeRange,
                         @JsonProperty("maxSpansPerTrace") final Long maxSpansPerTrace) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
        this.sharedFileStore = sharedFileStore;
        this.maxQueryTimeRange = maxQueryTimeRange;
        this.maxSpansPerTrace = maxSpansPerTrace;
    }

    /**
     * Returns the shared file store settings (shard count, path and optional
     * archival policy), or {@code null} if the shared file store has not been
     * configured.
     */
    @Override
    public SharedFileStoreSettings getSharedFileStore() {
        return sharedFileStore;
    }

    /**
     * Returns the maximum query time range allowed for this data source,
     * or {@code null} if no limit is configured.
     */
    public SimpleDuration getMaxQueryTimeRange() {
        return maxQueryTimeRange;
    }

    /**
     * The configured per-trace span limit, or {@code null} to use
     * {@link #DEFAULT_MAX_SPANS_PER_TRACE}. A value of zero or less means unlimited.
     */
    public Long getMaxSpansPerTrace() {
        return maxSpansPerTrace;
    }

    /** The effective per-trace span limit, or {@code 0} when unlimited. */
    @JsonIgnore
    public long getEffectiveMaxSpansPerTrace() {
        final long value = maxSpansPerTrace == null
                ? DEFAULT_MAX_SPANS_PER_TRACE
                : maxSpansPerTrace;
        return Math.max(0L, value);
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
        final TraceSettings that = (TraceSettings) o;
        return Objects.equals(sharedFileStore, that.sharedFileStore)
                && Objects.equals(maxQueryTimeRange, that.maxQueryTimeRange)
                && Objects.equals(maxSpansPerTrace, that.maxSpansPerTrace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sharedFileStore, maxQueryTimeRange, maxSpansPerTrace);
    }

    @Override
    public String toString() {
        return "TraceSettings{" +
               super.toString() +
               ", sharedFileStore=" + sharedFileStore +
               ", maxQueryTimeRange=" + maxQueryTimeRange +
               ", maxSpansPerTrace=" + maxSpansPerTrace +
               '}';
    }

    public static class Builder extends AbstractBuilder<TraceSettings, Builder> {

        private SharedFileStoreSettings sharedFileStore;
        private SimpleDuration maxQueryTimeRange;
        private Long maxSpansPerTrace;

        public Builder() {
        }

        public Builder(final TraceSettings settings) {
            super(settings);
            if (settings != null) {
                this.sharedFileStore = settings.sharedFileStore;
                this.maxQueryTimeRange = settings.maxQueryTimeRange;
                this.maxSpansPerTrace = settings.maxSpansPerTrace;
            }
        }

        public Builder sharedFileStore(final SharedFileStoreSettings sharedFileStore) {
            this.sharedFileStore = sharedFileStore;
            return self();
        }

        public Builder maxQueryTimeRange(final SimpleDuration maxQueryTimeRange) {
            this.maxQueryTimeRange = maxQueryTimeRange;
            return self();
        }

        public Builder maxSpansPerTrace(final Long maxSpansPerTrace) {
            this.maxSpansPerTrace = maxSpansPerTrace;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TraceSettings build() {
            return new TraceSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    overwrite,
                    retention,
                    sharedFileStore,
                    snapshotSettings,
                    maxQueryTimeRange,
                    maxSpansPerTrace);
        }
    }
}
