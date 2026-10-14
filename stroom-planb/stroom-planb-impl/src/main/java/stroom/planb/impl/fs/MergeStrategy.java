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

import stroom.planb.impl.PlanBConstants;
import stroom.planb.shared.PlanBDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * How one store type gets the work waiting for a shard into where its readers look.
 *
 * <p>Called inside the per-shard cluster lock, once the processor has decided there is work to do.
 * Everything the store type does with the shard lives here — opening it, merging into it, and
 * publishing it — because store types differ in whether they keep a holding shard at all.
 *
 * <p>Bound per {@link stroom.planb.shared.StateType}. A store type with no strategy is not merged.
 */
public interface MergeStrategy {

    /**
     * Merges {@code batchDirs} for one shard and publishes the result.
     *
     * @return which batches are safe to mark merged, and how many failed. Batches left out are
     *         retried on a later cycle.
     */
    MergeResult mergeShard(MergeContext ctx, List<Path> batchDirs) throws IOException;

    /**
     * Shards this strategy wants worked that no waiting batch would reveal.
     *
     * <p>A store type whose work arrives as batches needs none of this: the processor finds those by
     * looking, and it is the only kind of work most types have. A type whose work arrives some other
     * way — a queue of its own, or something simply falling due — says so here, and its shards are
     * locked and passed to {@link #mergeShard} on the same terms, with no batches.
     *
     * <p>Only which shards, not what to do with them: what to do is {@link #mergeShard}, for every
     * store type and both kinds of work. Said before any lock is taken, so this must only read, and
     * what it finds is worth checking again inside the lock.
     *
     * @return shard indexes, beyond those with batches waiting. Empty for every strategy that only
     * merges batches.
     */
    default Set<Integer> shardsWithOwnWork(final PlanBDocument doc) {
        return Set.of();
    }

    /**
     * The cluster lock held while one of this document's shards is worked.
     *
     * <p>Named by the store type rather than fixed here, because the merge is not always the only
     * thing that writes to a shard: where something else does — a screen that edits what the shard
     * holds, say — the two have to ask for the same lock by the same name, and that name belongs to
     * whoever owns the shard.
     */
    default String lockName(final PlanBDocument doc, final int shardIndex) {
        return PlanBConstants.getMergeLockName(doc.getUuid(), shardIndex);
    }
}
