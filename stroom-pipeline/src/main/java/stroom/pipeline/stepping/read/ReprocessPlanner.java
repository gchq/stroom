/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.stepping.read;

import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.StagePlanner.PlannerElement;
import stroom.pipeline.stepping.read.StagePlanner.StagePlan;
import stroom.pipeline.stepping.store.StepDataStore;

import java.util.List;
import java.util.Map;

/**
 * Turns the reuse/reprocess {@link StagePlan} into a concrete capture decision: either sweep the whole stream
 * from source, or re-run just the changed elements from a reusable upstream element's stored output.
 * <p>
 * Reprocess is applied only to the clean case an XSLT edit produces: a single edited element (and its
 * downstream) whose one upstream neighbour is reused. Anything else - the first sweep of a stream, a change at
 * or above the record boundary, a fork, or several independent edits - falls back to a full sweep. That
 * fallback is safe: the full sweep is the normal once-per-stream capture (O(N)), not the old per-keypress
 * engine, so reprocess is a pure optimisation on top of it and never a correctness dependency.
 */
public class ReprocessPlanner {

    private final StagePlanner stagePlanner = new StagePlanner();

    /**
     * @param elements  the steppable elements in topological order, with the record-boundary flag.
     * @param parentsOf steppable child id → its steppable upstream neighbour ids.
     * @param store     the stream's current store (holds the chunks a prior sweep captured).
     * @param current   the fingerprints for the current pipeline configuration.
     * @return whether to full-sweep, or which element to reprocess and which upstream element to feed it from.
     */
    public Decision plan(final List<PlannerElement> elements,
                         final Map<String, List<String>> parentsOf,
                         final StepDataStore store,
                         final ElementFingerprints current) {
        final StagePlan plan = stagePlanner.plan(elements, store, current);
        if (plan.fullRecapture() || plan.reuse().isEmpty() || plan.reprocess().isEmpty()) {
            // First sweep, boundary change, or nothing to reprocess - capture the whole stream from source.
            return Decision.full();
        }

        // The reprocess set must have exactly one entry into it: a single element whose one upstream neighbour
        // is reused. Its downstream reprocess elements have only reprocess parents, so they are not entries.
        String start = null;
        for (final String id : plan.reprocess()) {
            final List<String> parents = parentsOf.getOrDefault(id, List.of());
            final boolean fedByReuse = parents.stream().anyMatch(plan.reuse()::contains);
            if (fedByReuse) {
                // A fork (more than one parent) or a second entry point is not the fast path - fall back.
                if (parents.size() != 1 || start != null) {
                    return Decision.full();
                }
                start = id;
            }
        }
        if (start == null) {
            return Decision.full();
        }
        return Decision.reprocess(start, parentsOf.get(start).get(0));
    }

    /**
     * Either a full sweep, or a reprocess of {@code startElementId} fed from {@code feedElementId}'s stored
     * output. When {@link #fullSweep()} is true the element ids are null.
     */
    public record Decision(boolean fullSweep, String startElementId, String feedElementId) {

        public static Decision full() {
            return new Decision(true, null, null);
        }

        public static Decision reprocess(final String startElementId, final String feedElementId) {
            return new Decision(false, startElementId, feedElementId);
        }
    }
}
