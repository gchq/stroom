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

import stroom.pipeline.factory.PipelineFactory.MidPipelineScope;
import stroom.pipeline.stepping.read.SteppingGraphBuilder.Graph;
import stroom.pipeline.stepping.read.StagePlanner.PlannerElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out how a pipeline can be cut into independently runnable <b>stages</b>: runs of elements that can be
 * started, and restarted, on their own because each is fed from the previous one's captured records rather
 * than by re-running the pipeline above it.
 * <p>
 * A cut is only possible where the upstream element's captured output can be <em>replayed</em>. Stepping
 * stores XML elements as SAX events, which replay exactly; readers, writers and appenders store text, which
 * does not drive a downstream element. So the split points are the elements whose parent produces events, and
 * everything else travels with its parent. In the usual shape - {@code readers -> parser -> xslt -> schema ->
 * writer -> appenders} - that gives a head stage of {@code readers + parser} (run from the raw stream), a
 * stage each for {@code xslt} and {@code schema}, and a final stage of {@code writer + appenders}, the last
 * carrying its text-fed descendants because they cannot be cut off from it.
 * <p>
 * <b>Only simple chains are decomposed.</b> A fork means an element's output feeds two stages, and a stage
 * that has to run some descendants but stop before others needs a build mode that does not exist (the two
 * {@link MidPipelineScope} modes are "just this element" and "this element and everything below it"). Rather
 * than half-support that, a pipeline that is not a chain plans as a single stage - which is exactly today's
 * whole-pipeline sweep, so the fallback costs nothing that is not already being paid.
 */
public final class StageGraphPlanner {

    private StageGraphPlanner() {
    }

    /**
     * One independently runnable run of elements.
     *
     * @param startElementId  the element the stage is built from.
     * @param feedElementId   the upstream element whose captured output feeds it, or null for the head stage,
     *                        which reads the raw stream instead.
     * @param scope           for a mid-pipeline stage, the {@link MidPipelineScope} to build it with. For the
     *                        head stage - built from {@code Source} instead - it says whether the build runs
     *                        on past this stage's elements ({@code ELEMENT_AND_DESCENDANTS}, i.e. the whole
     *                        pipeline) or stops after them ({@code ELEMENT_ONLY}).
     * @param elementIds      every element this stage runs and captures, in order.
     */
    public record Stage(String startElementId,
                        String feedElementId,
                        MidPipelineScope scope,
                        List<String> elementIds) {

        public boolean isHead() {
            return feedElementId == null;
        }
    }

    /**
     * @param stages     the stages, upstream first. Always at least one.
     * @param decomposed false if the pipeline could not be cut up and {@code stages} is the single
     *                   whole-pipeline stage.
     */
    public record StageGraph(List<Stage> stages, boolean decomposed) {
    }

    /**
     * @param graph             the steppable elements and their nearest-steppable-parent map.
     * @param replayableOutputs the elements whose stored output can feed a downstream stage - in practice the
     *                          ones that capture SAX events rather than text.
     * @return the stage decomposition, or a single whole-pipeline stage if the pipeline is not a simple chain.
     */
    public static StageGraph plan(final Graph graph, final Set<String> replayableOutputs) {
        final List<String> chain = chainOf(graph);
        if (chain == null || chain.isEmpty()) {
            return single(graph);
        }

        // Cut before every element whose parent can be replayed from the store.
        final List<List<String>> runs = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            final String id = chain.get(i);
            final boolean cutHere = i > 0 && replayableOutputs.contains(chain.get(i - 1));
            if (cutHere && !current.isEmpty()) {
                runs.add(current);
                current = new ArrayList<>();
            }
            current.add(id);
        }
        runs.add(current);

        if (runs.size() < 2) {
            // Nothing could be cut - one stage is the whole pipeline, which is just today's sweep.
            return single(graph);
        }

        final List<Stage> stages = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            final List<String> run = runs.get(i);
            final boolean last = i == runs.size() - 1;
            final boolean head = i == 0;
            // A mid-pipeline stage that is followed by another must stop at its own element, or it would run
            // - and capture - the elements the next stage owns. Only the final stage may take its
            // descendants, which is how the text tail (writer + appenders) stays together. The head is
            // exempt: it is built from Source rather than with createFrom, and stopping after its last
            // element is the same one build mode either way, so readers + parser travel together happily.
            if (!last && !head && run.size() > 1) {
                // Would need a "descendants, but stop before the next stage" build mode. Not supported.
                return single(graph);
            }
            final MidPipelineScope scope = last
                    ? MidPipelineScope.ELEMENT_AND_DESCENDANTS
                    : MidPipelineScope.ELEMENT_ONLY;
            final String feed = i == 0 ? null : runs.get(i - 1).getLast();
            stages.add(new Stage(run.getFirst(), feed, scope, List.copyOf(run)));
        }
        return new StageGraph(List.copyOf(stages), true);
    }

    /**
     * @return the elements in order from the single head down, or null if the steppable graph is not a simple
     * chain (no head, several heads, or any element with more than one parent or child).
     */
    private static List<String> chainOf(final Graph graph) {
        final List<String> ids = graph.elements().stream().map(PlannerElement::id).toList();
        if (ids.isEmpty()) {
            return null;
        }
        final Set<String> known = new HashSet<>(ids);

        final Map<String, List<String>> childrenOf = new HashMap<>();
        final List<String> heads = new ArrayList<>();
        for (final String id : ids) {
            final List<String> parents = graph.parentsOf().getOrDefault(id, List.of()).stream()
                    .filter(known::contains)
                    .toList();
            if (parents.isEmpty()) {
                heads.add(id);
            } else if (parents.size() > 1) {
                // Joined from two upstreams: not a chain.
                return null;
            } else {
                childrenOf.computeIfAbsent(parents.getFirst(), k -> new ArrayList<>()).add(id);
            }
        }
        if (heads.size() != 1) {
            return null;
        }

        final List<String> chain = new ArrayList<>();
        String id = heads.getFirst();
        while (id != null) {
            chain.add(id);
            final List<String> children = childrenOf.getOrDefault(id, List.of());
            if (children.size() > 1) {
                return null;
            }
            id = children.isEmpty() ? null : children.getFirst();
        }
        // Every steppable element must be on the chain; anything off it is a branch we have not modelled.
        return chain.size() == ids.size() ? chain : null;
    }

    private static StageGraph single(final Graph graph) {
        final List<String> all = graph.elements().stream().map(PlannerElement::id).toList();
        final String start = all.isEmpty() ? null : all.getFirst();
        return new StageGraph(
                List.of(new Stage(start, null, MidPipelineScope.ELEMENT_AND_DESCENDANTS, all)),
                false);
    }
}
