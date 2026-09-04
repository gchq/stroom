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

package stroom.planb.impl.dao.trace;

import stroom.pathways.shared.otel.trace.TraceRoot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one publish pass will act on, decided once by {@code TraceDb.selectRoots} and then read by the
 * staging and purge phases so the two cannot disagree about which traces are in play.
 *
 * <p>The two maps are independent: a trace past the cut-off that was staged in an earlier pass retires in
 * this one without being staged again.
 *
 * @param labels   traceIdHex -&gt; bucket label, for every trace being staged
 * @param retiring traceIdHex -&gt; the STORED root, for the traces being retired. The stored value is kept
 *                 because a root's index entries are value-addressed, so deleting them has to be computed
 *                 from the old value.
 */
record PublishSelection(Map<String, String> labels, Map<String, TraceRoot> retiring) {

    boolean isEmpty() {
        return labels.isEmpty() && retiring.isEmpty();
    }

    boolean isStaged(final String traceIdHex) {
        return labels.containsKey(traceIdHex);
    }

    boolean isRetiring(final String traceIdHex) {
        return retiring.containsKey(traceIdHex);
    }

    /**
     * The traces to stage, grouped by the bucket they go to. Each group is in ascending trace-id order,
     * which is also the order of the span keys they prefix, so a delta's writes stay sequential.
     */
    Map<String, List<String>> tracesByLabel() {
        final Map<String, List<String>> byLabel = new LinkedHashMap<>();
        labels.keySet().stream().sorted().forEach(traceIdHex ->
                byLabel.computeIfAbsent(labels.get(traceIdHex), k -> new ArrayList<>()).add(traceIdHex));
        return byLabel;
    }
}
