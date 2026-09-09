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

package stroom.pathways.impl;

import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TraceSpanRow;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.query.api.GroupSelection;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translation between a span tree walk and the paged shape the client asks for: which spans are open,
 * the opaque resume cursor, and the cache-key suffix for a given expand/collapse state.
 *
 * <p>Depends on nothing but its arguments, so the cursor round trip and the key's stability can be
 * exercised without a store.
 */
final class SpanPaging {

    private SpanPaging() {
    }

    // Expand/collapse: the client sends a GroupSelection only when the view actually prunes something
    // (a collapsed span or a reduced expand-level); null ⇒ fully expanded ⇒ the unfiltered walk, which
    // keeps the fast on-disk-checkpoint path. The "group key" is the span's spanId (hex).
    static TraceDb.SpanOpenTest openTest(final GroupSelection groupSelection) {
        return groupSelection == null
                ? TraceDb.SpanOpenTest.ALL
                : (spanId, depth) -> groupSelection.isGroupOpen(HexStringUtil.encode(spanId), depth);
    }

    static TraceSpanPage toSpanPage(final TraceDb.SpanPage page,
                                    final boolean sequential,
                                    final Integer totalSpans) {
        final List<TraceSpanRow> rows = new ArrayList<>();
        boolean more = false;
        List<byte[]> next = null;
        if (page != null) {
            if (page.rows() != null) {
                for (final TraceDb.SpanRow row : page.rows()) {
                    rows.add(new TraceSpanRow(row.span(), row.depth(), row.hasChildren()));
                }
            }
            more = page.more();
            next = page.nextCursor();
        }
        // A page with more rows to come exposes a resume cursor, so next/prev stay cheap after any page,
        // including one reached by offset — archiveSpanPage passes sequential true on every path.
        final String nextCursor = (sequential && more) ? encodeCursor(next) : null;
        return new TraceSpanPage(rows, more, nextCursor, totalSpans);
    }

    private static String encodeCursor(final List<byte[]> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(TraceDb.encodePath(path));
    }

    static List<byte[]> decodeCursor(final String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        return TraceDb.decodePath(Base64.getDecoder().decode(cursor));
    }

    // Deterministic cache-key suffix for a GroupSelection (empty when fully expanded / null). Sorted so it
    // is stable regardless of set iteration order.
    static String groupSelectionKey(final GroupSelection groupSelection) {
        if (groupSelection == null) {
            return "";
        }
        final String open = groupSelection.getOpenGroups().stream().sorted().collect(Collectors.joining(","));
        final String closed = groupSelection.getClosedGroups().stream().sorted()
                .collect(Collectors.joining(","));
        return "|gs=" + groupSelection.getExpandedDepth() + ";o=" + open + ";c=" + closed;
    }
}
