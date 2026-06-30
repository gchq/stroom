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
 * Configuration for the shared file store used by a {@link HasSharedFileStore} PlanB store.
 *
 * <p>Combines three related concerns that all require the shared file store to be
 * active:
 * <ul>
 *   <li><b>shardCount</b> — how many LMDB shards the store is split across.</li>
 *   <li><b>sharedPath</b> — path to the shared filesystem used for multi-node
 *       replication and archiving. {@code null} or blank means the shared file
 *       store is disabled.</li>
 *   <li><b>archival</b> — optional time-based archival policy; {@code null} means
 *       archival is not configured.</li>
 * </ul>
 *
 * <p>Archival is nested here (rather than being a sibling field on the enclosing
 * settings class) because it is only meaningful when the shared file store is
 * active — archival data is written to the shared path.
 */
@JsonPropertyOrder({"shardCount", "sharedPath", "archival"})
@JsonInclude(Include.NON_NULL)
public final class SharedFileStoreSettings {

    @JsonProperty("shardCount")
    private final int shardCount;

    @JsonProperty("sharedPath")
    private final String sharedPath;

    @JsonProperty("archival")
    private final ArchivalSettings archival;

    @JsonCreator
    public SharedFileStoreSettings(
            @JsonProperty("shardCount") final int shardCount,
            @JsonProperty("sharedPath") final String sharedPath,
            @JsonProperty("archival") final ArchivalSettings archival) {
        this.shardCount = shardCount;
        this.sharedPath = sharedPath;
        this.archival = archival;
    }

    /**
     * Convenience constructor for cases where no archival policy is needed
     * (e.g. tests, or stores that have not yet configured archival).
     */
    public SharedFileStoreSettings(final int shardCount, final String sharedPath) {
        this(shardCount, sharedPath, null);
    }

    public int getShardCount() {
        return shardCount;
    }

    public String getSharedPath() {
        return sharedPath;
    }

    /**
     * Returns the archival policy for this store, or {@code null} if archival
     * has not been configured.
     */
    public ArchivalSettings getArchival() {
        return archival;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final SharedFileStoreSettings other)) {
            return false;
        }
        return shardCount == other.shardCount &&
               Objects.equals(sharedPath, other.sharedPath) &&
               Objects.equals(archival, other.archival);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shardCount, sharedPath, archival);
    }

    @Override
    public String toString() {
        return "SharedFileStoreSettings[shardCount=" + shardCount +
               ", sharedPath=" + sharedPath +
               ", archival=" + archival + "]";
    }
}
