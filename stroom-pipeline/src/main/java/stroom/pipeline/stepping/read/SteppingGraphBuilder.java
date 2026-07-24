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

import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineLink;
import stroom.pipeline.stepping.read.StagePlanner.PlannerElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the <b>steppable</b> element graph a reprocess decision needs, from the merged {@link PipelineData}
 * plus the set of elements that actually have captured IO (from the store). Only steppable elements are
 * captured, so that set <em>is</em> the steppable set - deriving it from the store avoids re-deriving it from
 * element roles, and is exact.
 * <p>
 * Non-steppable intermediates are skipped: a steppable element's parents are its <em>nearest steppable
 * ancestors</em>, matching how {@code PipelineFactory} links a stepping pipeline (it recurses through
 * non-steppable elements). A steppable element with no steppable parent is the record boundary - the parser or
 * reader, whose only upstream is the non-steppable {@code Source}.
 */
public final class SteppingGraphBuilder {

    private SteppingGraphBuilder() {
    }

    /**
     * @param data         the merged pipeline data (elements + links).
     * @param steppableIds the element ids that have captured IO (the steppable set).
     * @return the steppable elements (with boundary flags) and the nearest-steppable-ancestor map.
     */
    public static Graph build(final PipelineData data, final Set<String> steppableIds) {
        // Direct upstream adjacency (to -> [from]), only between real elements - a dangling link must not
        // inject a phantom id (same rule as ElementFingerprinter).
        final Set<String> realIds = new HashSet<>();
        for (final PipelineElement element : data.getAddedElements()) {
            realIds.add(element.getId());
        }
        final Map<String, List<String>> directUpstream = new HashMap<>();
        for (final PipelineLink link : data.getAddedLinks()) {
            if (realIds.contains(link.getFrom()) && realIds.contains(link.getTo())) {
                directUpstream.computeIfAbsent(link.getTo(), k -> new ArrayList<>()).add(link.getFrom());
            }
        }

        final Map<String, List<String>> parentsOf = new HashMap<>();
        final List<PlannerElement> elements = new ArrayList<>();
        for (final String id : steppableIds) {
            final List<String> parents = new ArrayList<>();
            collectSteppableParents(id, directUpstream, steppableIds, parents, new HashSet<>());
            parentsOf.put(id, parents);
            elements.add(new PlannerElement(id, parents.isEmpty()));
        }
        return new Graph(elements, parentsOf);
    }

    private static void collectSteppableParents(final String id,
                                                final Map<String, List<String>> directUpstream,
                                                final Set<String> steppableIds,
                                                final List<String> out,
                                                final Set<String> visited) {
        for (final String up : directUpstream.getOrDefault(id, List.of())) {
            if (!visited.add(up)) {
                continue;
            }
            if (steppableIds.contains(up)) {
                if (!out.contains(up)) {
                    out.add(up);
                }
            } else {
                // Skip a non-steppable intermediate and look above it.
                collectSteppableParents(up, directUpstream, steppableIds, out, visited);
            }
        }
    }

    /**
     * The steppable elements (with record-boundary flags) and each steppable element's nearest steppable
     * ancestors, ready to hand to {@link ReprocessPlanner}.
     */
    public record Graph(List<PlannerElement> elements, Map<String, List<String>> parentsOf) {
    }
}
