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

import stroom.pathways.shared.otel.trace.NanoTime;

/**
 * Per-trace aggregate accumulator maintained incrementally as spans arrive, so the merge-cycle
 * finalize ({@code mergeComplete}) is O(1) per trace instead of re-scanning every span.
 *
 * <ul>
 *   <li>{@code spanCount} / {@code serviceCount} — cumulative (total ever ingested); monotonic,
 *       not decremented when spans age out under retention.</li>
 *   <li>{@code maxEnd} / {@code lastActivityMs} — running max span end time and max span insert
 *       (receipt) time.</li>
 *   <li>{@code depth} — last computed longest-path depth; recomputed by the bounded DFS only when
 *       never computed or {@code spanCount} has grown to {@code >= 2 * spanCountAtLastDepth}
 *       (depth is stable, so it stays off the per-cycle hot path).</li>
 *   <li>{@code hasError} — monotonic OR flag: set once any span reports an error status; never
 *       cleared (a trace that ever errored stays flagged even if that span later ages out).</li>
 *   <li>{@code truncated} — monotonic OR flag: set once a span has been rejected because the trace
 *       reached the store's per-trace span limit.</li>
 * </ul>
 */
public record TraceStats(long spanCount,
                         int serviceCount,
                         NanoTime maxEnd,
                         long lastActivityMs,
                         int depth,
                         long spanCountAtLastDepth,
                         boolean hasError,
                         boolean truncated) {

    public static final TraceStats EMPTY =
            new TraceStats(0L, 0, NanoTime.ZERO, 0L, 0, 0L, false, false);
}
