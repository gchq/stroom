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
     * Merges {@code queueDirs} for one shard and publishes the result.
     *
     * @return what is safe to delete, and what failed. Anything left out is offered again on a
     *         later cycle.
     */
    MergeResult mergeShard(MergeContext ctx, List<Path> queueDirs) throws IOException;

    /**
     * Whether this shard has work falling due that no waiting item would reveal.
     *
     * <p>Most of a store type's work arrives: something writes an item and the processor finds it.
     * Some of it simply becomes due — data aged out of its retention, a model gone too wide for its
     * window — and nothing on disk says so except a marker recording when it was last done. A shard
     * is opened and locked for that as readily as for an item waiting.
     *
     * <p>How often, and against what, is the strategy's own business: an interval since the last run
     * and a calendar boundary are both reasonable and they are not interchangeable. What is said here
     * reaches {@link #mergeShard} as {@link MergeContext#maintenanceDue()}, which also does the work
     * and records that it was done.
     *
     * <p>Called before the lock is taken, so this must only read, and what it finds is worth asking
     * again inside the lock: on a cluster every node decides this for itself, and by the time one has
     * the shard another may have done the work.
     */
    default boolean maintenanceDue(final PlanBDocument doc, final int shardIndex) {
        return false;
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
