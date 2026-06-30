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

import com.fasterxml.jackson.annotation.JsonCreator;
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
 * {@code archiveOldData} override in its DB class — no changes are required to
 * the core infrastructure ({@code ShardManager}, {@code ArchiveOperation}, etc.).
 */
@JsonPropertyOrder({
        "maxStoreSize",
        "synchroniseMerge",
        "overwrite",
        "retention",
        "sharedFileStore",
        "snapshotSettings"
})
@JsonInclude(Include.NON_NULL)
public final class TraceSettings extends AbstractPlanBSettings implements HasSharedFileStore {

    @JsonProperty
    private final SharedFileStoreSettings sharedFileStore;

    @JsonCreator
    public TraceSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                         @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                         @JsonProperty("overwrite") final Boolean overwrite,
                         @JsonProperty("retention") final RetentionSettings retention,
                         @JsonProperty("sharedFileStore") final SharedFileStoreSettings sharedFileStore,
                         @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
        this.sharedFileStore = sharedFileStore;
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
        return Objects.equals(sharedFileStore, that.sharedFileStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sharedFileStore);
    }

    @Override
    public String toString() {
        return "TraceSettings{" +
               super.toString() +
               ", sharedFileStore=" + sharedFileStore +
               '}';
    }

    public static class Builder extends AbstractBuilder<TraceSettings, Builder> {

        private SharedFileStoreSettings sharedFileStore;

        public Builder() {
        }

        public Builder(final TraceSettings settings) {
            super(settings);
            if (settings != null) {
                this.sharedFileStore = settings.sharedFileStore;
            }
        }

        public Builder sharedFileStore(final SharedFileStoreSettings sharedFileStore) {
            this.sharedFileStore = sharedFileStore;
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
                    snapshotSettings);
        }
    }
}
