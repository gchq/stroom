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

package stroom.planb.impl.db.trace;

import stroom.planb.shared.ArchivalGranularity;

import java.nio.file.Path;
import java.time.Instant;

/**
 * The incremental archive lifecycle, which only trace stores have. Traces are unusual among Plan B store
 * types in that a record is assembled from many parts arriving over time, so its spans are archived
 * continuously while the record itself is kept behind as the accumulator and evicted later. Every other
 * store type writes a record once and archives it whole via {@code Db.archiveOldData}.
 *
 * <p>These deliberately do <b>not</b> live on {@link stroom.planb.impl.db.Db}: that is the generic store
 * contract, and defaults returning zero for every non-trace type would put trace vocabulary — rooted
 * spans, trace roots — in front of every implementor. Callers that need them test for this interface
 * instead, as they already do for {@code SnapshotCapable}.
 *
 * <p>Only {@link TraceDb} implements this.
 */
public interface TraceArchiveCapable {

    /**
     * Archives the spans of records that are already complete enough to query, keeping the record itself
     * as an accumulator for late arrivals. Runs every merge cycle, unlike
     * {@code Db.archiveOldData} which waits out the archival lead time.
     *
     * @return the number of spans removed from the live store.
     */
    long archiveRootedSpans(ArchivalGranularity granularity, Path archiveBaseDir);

    /**
     * Evicts records whose spans are already archived and which can no longer gain more, so the live store
     * stops holding what only existed to accumulate against. Nothing is moved — the archive already holds
     * the data.
     *
     * @param evictBefore     the normal cut-off, applied once a record has gone quiet.
     * @param hardEvictBefore the earlier backstop, which evicts a record that never goes quiet at all.
     * @return the number of entries removed.
     */
    long evictArchivedRoots(Instant evictBefore, Instant hardEvictBefore);
}
