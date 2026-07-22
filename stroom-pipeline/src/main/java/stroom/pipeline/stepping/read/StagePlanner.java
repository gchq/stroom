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
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.util.shared.ElementId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides, for a stepping request, which elements' captured IO can be reused from the store and which must
 * be (re)processed.
 * <p>
 * Because the store is content-addressed by {@code cumulativeFingerprint} (which changes for an element
 * iff it or anything upstream changed), an element is reusable exactly when the store already holds a
 * chunk for its current cumulative fingerprint. So the plan is simply: reuse where the fingerprinted
 * chunk exists, reprocess where it does not - and reverting a pipeline edit naturally reuses the still
 * present prior-fingerprint chunks.
 * <p>
 * One exception: a change at or above the record-boundary (Source / readers / parser / split) redefines
 * what a "record" is, so record-indexed chunks are meaningless and everything must be re-captured from
 * source. When such an element is not reusable the plan is a {@code fullRecapture}.
 * <p>
 * <b>Nothing calls this yet, deliberately.</b> Editing an element currently re-sweeps the whole stream;
 * that is correct, and cheap enough, because {@link StepDataStore#putRecord} skips any element whose
 * fingerprint is unchanged, so untouched elements are never rewritten. This class is the decision logic
 * for the "stored stepping state" improvement - refreshing an edited element from its stored input rather
 * than re-running the pipeline above it - which is designed but not built. See {@code stepping-design.md}.
 * Keep it or delete it with that feature in mind; it is not dead by accident.
 */
public class StagePlanner {

    /**
     * @param elements  the steppable elements in processing (topological) order.
     * @param store     the current stream's store.
     * @param current   the fingerprints for the current pipeline configuration.
     * @return the reuse/reprocess plan.
     */
    public StagePlan plan(final List<PlannerElement> elements,
                          final StepDataStore store,
                          final ElementFingerprints current) {
        final Set<String> reuse = new LinkedHashSet<>();
        final Set<String> reprocess = new LinkedHashSet<>();
        boolean boundaryChanged = false;

        for (final PlannerElement element : elements) {
            final String fingerprint = current.getCumulativeFingerprint(element.id());
            final boolean present = fingerprint != null
                    && store.hasElement(new ElementId(element.id()), fingerprint);
            if (present) {
                reuse.add(element.id());
            } else {
                reprocess.add(element.id());
                if (element.atOrAboveRecordBoundary()) {
                    boundaryChanged = true;
                }
            }
        }

        if (boundaryChanged) {
            // Record boundaries themselves changed; nothing record-indexed can be trusted.
            final Set<String> all = new LinkedHashSet<>();
            elements.forEach(e -> all.add(e.id()));
            return new StagePlan(true, Set.of(), all);
        }

        return new StagePlan(false, reuse, reprocess);
    }

    /**
     * An element for planning: its id and whether it sits at or above the record-boundary (Source,
     * readers, parser, split) - a change to such an element forces a full re-capture.
     */
    public record PlannerElement(String id, boolean atOrAboveRecordBoundary) {
    }

    /**
     * The outcome: whether a full re-capture from source is required, and otherwise which elements to
     * reuse from the store vs (re)process.
     */
    public record StagePlan(boolean fullRecapture, Set<String> reuse, Set<String> reprocess) {
    }
}
