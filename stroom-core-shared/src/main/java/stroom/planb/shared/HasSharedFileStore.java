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

/**
 * Implemented by the settings class of a store type whose data lives on a shared filesystem rather
 * than only on the local node, so that it can be split across shards and read by every node.
 *
 * <p>This says nothing about how the data is laid out under the shared path. Bucketing, and whether
 * writes pass through a holding shard, are the store type's own business — see
 * {@link HasHoldingAreaSettings}.
 *
 * <p>How a store type's batches actually reach the place its queries read is decided by the
 * {@code MergeStrategy} bound for its {@link StateType}; a store type with no strategy is not
 * merged.
 */
public interface HasSharedFileStore {

    /**
     * The shared store path and shard count, or {@code null} when the shared file store has not been
     * configured.
     */
    SharedFileStoreSettings getSharedFileStore();
}
