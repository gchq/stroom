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

package stroom.planb.impl.fs;

import stroom.planb.shared.PlanBDocument;

/**
 * What a {@link MergeStrategy} needs to merge one shard, gathered by the processor before it takes
 * the cluster lock.
 *
 * <p>Carries no paths: where a store type keeps its data on the shared file store is its own
 * business. {@code retentionDue} is decided once by the processor rather than by each user of it,
 * because retention over the holding shard and over the archive buckets share one schedule and one
 * {@code .retention.last} marker.
 */
public record MergeContext(PlanBDocument doc,
                           int shardIndex,
                           String lockName,
                           boolean retentionDue) {
}
