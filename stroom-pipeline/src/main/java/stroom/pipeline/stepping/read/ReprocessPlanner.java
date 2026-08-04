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
import stroom.pipeline.stepping.store.RecordRange;
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
        return plan(elements, parentsOf, store, current, null);
    }

    /**
     * As above, judging reuse against the records the step is about rather than the whole stream: with a
     * span, an upstream captured <i>up to a frontier</i> can feed a replay of the records behind it, complete
     * or not. Null keeps the whole-stream requirement.
     */
    public Decision plan(final List<PlannerElement> elements,
                         final Map<String, List<String>> parentsOf,
                         final StepDataStore store,
                         final ElementFingerprints current,
                         final RecordRange span) {
        final StagePlan plan = stagePlanner.plan(elements, store, current, span);
        if (plan.fullRecapture() || plan.reuse().isEmpty()) {
            // First sweep or boundary change - capture the whole stream from source.
            return Decision.full();
        }
        if (plan.reprocess().isEmpty()) {
            // Everything covers what was asked. For a whole-stream plan that means a re-sweep adds nothing
            // but is also all we can offer (the old behaviour); for a span it means the demand is ALREADY
            // SATISFIED - the records were materialised by an earlier step - and launching anything, least
            // of all a full sweep, would be pure waste. This distinction matters because a resolver loop
            // re-plans after its materialisation lands, and that second plan always finds nothing left to
            // do.
            return span != null ? Decision.alreadySatisfied() : Decision.full();
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
     * One of three answers: a full sweep, a reprocess of {@code startElementId} fed from
     * {@code upstreamElementId}'s stored output, or already {@link #satisfied()} - everything the step demands
     * is in the store and nothing need run. The element ids are null unless a reprocess was planned.
     */
    public record Decision(boolean fullSweep, boolean satisfied, String startElementId, String upstreamElementId) {

        public static Decision full() {
            return new Decision(true, false, null, null);
        }

        /**
         * The demanded records are already in the store under every element's current fingerprint - nothing
         * to launch at all.
         */
        public static Decision alreadySatisfied() {
            return new Decision(false, true, null, null);
        }

        public static Decision reprocess(final String startElementId, final String upstreamElementId) {
            return new Decision(false, false, startElementId, upstreamElementId);
        }
    }
}
