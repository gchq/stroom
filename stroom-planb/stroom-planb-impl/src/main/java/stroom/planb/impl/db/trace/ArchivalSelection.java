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

import stroom.pathways.shared.otel.trace.TraceRoot;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one archival pass will act on, decided once by {@code TraceDb.selectRoots} and then read by the
 * staging and purge phases so the two cannot disagree about which traces are in play.
 *
 * <p>{@code retiring} is always a subset of {@code labels}, which is what stops a trace being retired
 * without having been staged in the same pass.
 *
 * @param labels   traceIdHex -&gt; bucket label, for every trace being staged
 * @param retiring traceIdHex -&gt; the STORED root, for the traces being retired. The stored value is kept
 *                 because a root's index entries are value-addressed, so deleting them has to be computed
 *                 from the old value.
 */
record ArchivalSelection(Map<String, String> labels, Map<String, TraceRoot> retiring) {

    boolean isEmpty() {
        return labels.isEmpty();
    }

    String labelOf(final String traceIdHex) {
        return labels.get(traceIdHex);
    }

    boolean isStaged(final String traceIdHex) {
        return labels.containsKey(traceIdHex);
    }

    boolean isRetiring(final String traceIdHex) {
        return retiring.containsKey(traceIdHex);
    }

    Set<String> distinctLabels() {
        return new LinkedHashSet<>(labels.values());
    }
}
