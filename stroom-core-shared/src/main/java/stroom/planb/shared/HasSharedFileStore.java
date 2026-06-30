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
 * Marker interface for {@link AbstractPlanBSettings} subclasses that support
 * sharding and archiving via a shared file store.
 *
 * <p>A settings class implementing this interface declares that the corresponding
 * PlanB doc type can be:
 * <ul>
 *   <li><b>Horizontally sharded</b> — entries are distributed across multiple
 *       LMDB environments according to {@link SharedFileStoreSettings#getShardCount()}.</li>
 *   <li><b>Archived</b> — entries older than the configured lead time are moved
 *       to dated archive shards on the shared file store, as configured by
 *       {@link SharedFileStoreSettings#getArchival()}.</li>
 * </ul>
 *
 * <p>Currently only {@link TraceSettings} implements this interface. When a
 * further PlanB doc type gains sharding and/or archiving support, its settings
 * class should implement {@code HasSharedFileStore} and provide a concrete value
 * for {@link #getSharedFileStore()}.  No changes are required to the core
 * infrastructure classes ({@code ShardManager}, {@code ArchiveOperation}, etc.).
 */
public interface HasSharedFileStore {

    /**
     * Returns the shared file store settings — shard count, shared store path,
     * and optional archival policy.  Returns {@code null} when the shared file
     * store has not been configured.
     */
    SharedFileStoreSettings getSharedFileStore();
}
