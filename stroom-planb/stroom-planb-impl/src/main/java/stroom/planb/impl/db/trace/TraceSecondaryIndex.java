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
import stroom.pathways.shared.otel.trace.TraceRoot;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Single source of truth for the trace-root secondary sort indexes.
 *
 * <p>Each constant bundles the four things that must stay in lock-step for a
 * sortable trace-root column:
 * <ul>
 *   <li>the {@link TraceRootField} id carried by the query criteria sort,</li>
 *   <li>the LMDB DBI name that backs the index,</li>
 *   <li>the {@code key(root, traceId)} byte-layout used to write/scan the index, and</li>
 *   <li>the {@link Comparator} used to merge results across shards.</li>
 * </ul>
 *
 * <p>Adding a new sortable field is therefore a single new enum constant — no edits
 * to the index write/delete/update paths in {@link TraceDb} (which iterate
 * {@link #values()}) nor to the query/merge dispatch (which look up via
 * {@link #forField(String)}).
 *
 * <p>Key format for every index is {@code (sortField_bytes ∥ traceId[16])} with all
 * integers big-endian, so LMDB's byte-lexicographic order equals the natural
 * numeric/chronological order for ascending scans. {@link TraceRootField#TRACE_ID}
 * is deliberately absent: it is served by the primary {@code trace-roots} DBI whose
 * key already IS the traceId, so it needs no secondary index.
 */
public enum TraceSecondaryIndex {

    START_TIME(TraceRootField.TRACE_START, "trace-roots-start-time",
            (root, traceId) -> startTimeKey(root.getStartTime(), traceId),
            Comparator.comparing(TraceRoot::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))),

    DURATION(TraceRootField.DURATION, "trace-roots-duration",
            (root, traceId) -> durationKey(root.getStartTime(), root.getRootEndTime(), traceId),
            Comparator.comparingLong(TraceSecondaryIndex::durationNanos)),

    TOTAL_DURATION(TraceRootField.TOTAL_DURATION, "trace-roots-total-duration",
            (root, traceId) -> durationKey(root.getStartTime(), root.getEndTime(), traceId),
            Comparator.comparingLong(TraceSecondaryIndex::totalDurationNanos)),

    OPERATION(TraceRootField.OPERATION, "trace-roots-operation",
            (root, traceId) -> operationKey(root.getName(), traceId),
            Comparator.comparing(TraceRoot::getName, Comparator.nullsLast(Comparator.naturalOrder()))),

    SERVICES(TraceRootField.SERVICES, "trace-roots-services",
            (root, traceId) -> intKey(root.getServices(), traceId),
            Comparator.comparingInt(TraceRoot::getServices)),

    DEPTH(TraceRootField.DEPTH, "trace-roots-depth",
            (root, traceId) -> intKey(root.getDepth(), traceId),
            Comparator.comparingInt(TraceRoot::getDepth)),

    TOTAL_SPANS(TraceRootField.TOTAL_SPANS, "trace-roots-total-spans",
            (root, traceId) -> intKey(root.getTotalSpans(), traceId),
            Comparator.comparingInt(TraceRoot::getTotalSpans));

    /** Number of bytes of the traceId suffix appended to every secondary-index key. */
    private static final int TRACE_ID_BYTES = 16;

    private static final Map<String, TraceSecondaryIndex> BY_FIELD = new HashMap<>();

    static {
        for (final TraceSecondaryIndex index : values()) {
            BY_FIELD.put(index.fieldId, index);
        }
    }

    private final String fieldId;
    private final String dbiName;
    private final BiFunction<TraceRoot, byte[], byte[]> keyFunction;
    private final Comparator<TraceRoot> comparator;

    TraceSecondaryIndex(final String fieldId,
                        final String dbiName,
                        final BiFunction<TraceRoot, byte[], byte[]> keyFunction,
                        final Comparator<TraceRoot> comparator) {
        this.fieldId = fieldId;
        this.dbiName = dbiName;
        this.keyFunction = keyFunction;
        this.comparator = comparator;
    }

    /** The {@link TraceRootField} id this index sorts by. */
    public String fieldId() {
        return fieldId;
    }

    /** The LMDB DBI name backing this index. */
    public String dbiName() {
        return dbiName;
    }

    /** Builds the index key {@code (sortField_bytes ∥ traceId)} for the given root. */
    public byte[] key(final TraceRoot root, final byte[] traceId) {
        return keyFunction.apply(root, traceId);
    }

    /** Comparator matching this index's order, for merging results across shards. */
    public Comparator<TraceRoot> comparator() {
        return comparator;
    }

    /**
     * Returns the secondary index for the given sort-field id, or {@code null} if
     * the field has no secondary index (e.g. {@link TraceRootField#TRACE_ID}, or an
     * unknown field — callers should default to {@link #START_TIME}).
     */
    public static TraceSecondaryIndex forField(final String fieldId) {
        return BY_FIELD.get(fieldId);
    }

    // -----------------------------------------------------------------------
    // Key builders (big-endian sort-field bytes ∥ traceId[16]).
    // -----------------------------------------------------------------------

    /** Key: (startSecs[8] ∥ startNanos[4] ∥ traceId[16]) = 28 bytes. */
    private static byte[] startTimeKey(final NanoTime t, final byte[] traceId) {
        final byte[] key = new byte[Long.BYTES + Integer.BYTES + TRACE_ID_BYTES];
        ByteBuffer.wrap(key)
                .putLong(t != null ? t.getSeconds() : 0L)
                .putInt(t != null ? t.getNanos() : 0)
                .put(traceId);
        return key;
    }

    // Key: durationNanos[8] ∥ traceId[16]. Duration is the root span's own duration
    // (rootEndTime - startTime), not endTime — matches the UI Duration, not inflated by trailing spans.
    private static byte[] durationKey(final NanoTime start, final NanoTime rootEnd, final byte[] traceId) {
        final byte[] key = new byte[Long.BYTES + TRACE_ID_BYTES];
        ByteBuffer.wrap(key).putLong(durationNanos(start, rootEnd)).put(traceId);
        return key;
    }

    /**
     * Key: (nameUtf8 ∥ 0x00 ∥ traceId[16]).
     * The null-byte separator ensures the variable-length name does not bleed
     * into the fixed-length traceId suffix during comparison.
     */
    private static byte[] operationKey(final String name, final byte[] traceId) {
        final byte[] nameBytes = name != null
                ? name.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        final byte[] key = new byte[nameBytes.length + 1 + TRACE_ID_BYTES];
        final ByteBuffer buf = ByteBuffer.wrap(key);
        buf.put(nameBytes);
        buf.put((byte) 0x00); // separator
        buf.put(traceId);
        return key;
    }

    /** Key: (value[4] ∥ traceId[16]) = 20 bytes. Shared by services, depth, totalSpans. */
    private static byte[] intKey(final int value, final byte[] traceId) {
        final byte[] key = new byte[Integer.BYTES + TRACE_ID_BYTES];
        ByteBuffer.wrap(key).putInt(value).put(traceId);
        return key;
    }

    private static long durationNanos(final NanoTime start, final NanoTime rootEnd) {
        // Null when there is no root duration (e.g. an orphan-only trace) -> sort as zero duration.
        if (start == null || rootEnd == null) {
            return 0L;
        }
        final long nanos = (rootEnd.getSeconds() - start.getSeconds()) * 1_000_000_000L
                + (rootEnd.getNanos() - start.getNanos());
        // Clamp to zero. An orphan-only trace has no root end: it is null on the freshly-built root
        // (index write) but persisted/read back as ZERO (index delete), which would otherwise read
        // as a negative duration — making the write-time and delete-time keys disagree and stranding
        // stale duration-index entries (duplicate rows when sorting by duration). A real root always
        // ends at/after its start, so this only ever clamps the orphan sentinel.
        return Math.max(0L, nanos);
    }

    private static long durationNanos(final TraceRoot root) {
        return durationNanos(root.getStartTime(), root.getRootEndTime());
    }

    // Total activity span: start to the last span's end (endTime - startTime).
    private static long totalDurationNanos(final TraceRoot root) {
        return durationNanos(root.getStartTime(), root.getEndTime());
    }
}
