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
 * Where a {@link HasSharedFileStore} store's data lives on the shared filesystem, and how many ways
 * it is split.
 *
 * <p>Only these two, because they are all that every shared file store store type has in common.
 * Whether the data is bucketed at all, what a bucket means, and whether writes pass through a
 * holding shard first are decided by the store type, so those settings live on its own settings
 * class — see {@link HasHoldingAreaSettings}.
 *
 * <ul>
 *   <li><b>shardCount</b> — how many LMDB shards the store is split across, by key hash. 1 means
 *       unsharded.</li>
 *   <li><b>sharedPath</b> — path to the shared filesystem used for multi-node replication. Blank or
 *       {@code null} means the shared file store is not configured.</li>
 * </ul>
 */
@JsonPropertyOrder({"shardCount", "sharedPath"})
@JsonInclude(Include.NON_NULL)
public final class SharedFileStoreSettings {

    @JsonProperty("shardCount")
    private final int shardCount;

    @JsonProperty("sharedPath")
    private final String sharedPath;

    @JsonCreator
    public SharedFileStoreSettings(
            @JsonProperty("shardCount") final int shardCount,
            @JsonProperty("sharedPath") final String sharedPath) {
        this.shardCount = shardCount;
        this.sharedPath = sharedPath;
    }

    public int getShardCount() {
        return shardCount;
    }

    public String getSharedPath() {
        return sharedPath;
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
               Objects.equals(sharedPath, other.sharedPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shardCount, sharedPath);
    }

    @Override
    public String toString() {
        return "SharedFileStoreSettings[shardCount=" + shardCount +
               ", sharedPath=" + sharedPath + "]";
    }
}
