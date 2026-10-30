/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.stepping.client;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/// The path through a pipeline that the stepping screen's pipeline bar shows: one element from
/// each level, from the pipeline's root (its source) to an element with no children.
///
/// Where an element has more than one child (a fork) the path follows one of them:
///
/// 1. the child whose branch holds the selected element, else
/// 1. the child last chosen at that fork, else
/// 1. the child whose branch has the worst severity (warnings or worse), else
/// 1. the first child.
///
/// Each element after a fork knows its alternatives (its siblings and itself) and the worst
/// severity and any active filters in the branches that the path doesn't show, so these can be
/// shown on the bar.
public final class PipelinePath {

    private final PipelineElement root;
    private final Function<PipelineElement, List<PipelineElement>> childrenFunction;
    private final Function<PipelineElement, Severity> severityFunction;
    private final Predicate<PipelineElement> filtersPredicate;

    /// @param root             The pipeline's root element, i.e. its source.
    /// @param childrenFunction Gets an element's children, in order. May return null for none.
    /// @param severityFunction Gets an element's worst severity from the last step, or null for
    ///                         none.
    /// @param filtersPredicate Whether an element has active stepping filters.
    public PipelinePath(final PipelineElement root,
                        final Function<PipelineElement, List<PipelineElement>> childrenFunction,
                        final Function<PipelineElement, Severity> severityFunction,
                        final Predicate<PipelineElement> filtersPredicate) {
        this.root = Objects.requireNonNull(root);
        this.childrenFunction = Objects.requireNonNull(childrenFunction);
        this.severityFunction = Objects.requireNonNull(severityFunction);
        this.filtersPredicate = Objects.requireNonNull(filtersPredicate);
    }

    /// Records the choice made at each fork on the way to an element, so that the path keeps
    /// following it when the element is no longer selected.
    ///
    /// @param element The element, e.g. the selected one. If null or not in the pipeline, nothing
    ///                is recorded.
    /// @param choices The choices to record in, keyed by the forking element's ID, each the ID of
    ///                the child chosen.
    public void rememberPathTo(final PipelineElement element, final Map<String, String> choices) {
        final List<PipelineElement> ancestry = findAncestry(element);
        for (int i = 0; i < ancestry.size() - 1; i++) {
            final PipelineElement parent = ancestry.get(i);
            if (children(parent).size() > 1) {
                choices.put(parent.getId(), ancestry.get(i + 1).getId());
            }
        }
    }

    /// Follows the path from the root.
    ///
    /// @param selected The selected element, which is always on the path if it is in the
    ///                 pipeline. Can be null.
    /// @param choices  The choices made at forks, keyed by the forking element's ID, each the ID
    ///                 of the child chosen. Choices of children that no longer exist are ignored.
    /// @return The path's steps, from the root.
    public List<Step> follow(final PipelineElement selected, final Map<String, String> choices) {
        final List<PipelineElement> ancestry = findAncestry(selected);
        final List<Step> steps = new ArrayList<>();
        steps.add(new Step(root, Collections.emptyList(), null, false));

        PipelineElement current = root;
        int depth = 0;
        List<PipelineElement> children = children(current);
        while (!children.isEmpty()) {
            depth++;
            final PipelineElement next = choose(current, children, ancestry, depth, choices);
            if (children.size() == 1) {
                steps.add(new Step(next, Collections.emptyList(), null, false));
            } else {
                Severity hiddenSeverity = null;
                boolean hiddenFilters = false;
                for (final PipelineElement child : children) {
                    if (!child.equals(next)) {
                        hiddenSeverity = worst(hiddenSeverity, getBranchSeverity(child));
                        hiddenFilters = hiddenFilters || hasBranchFilters(child);
                    }
                }
                steps.add(new Step(next, List.copyOf(children), hiddenSeverity, hiddenFilters));
            }
            current = next;
            children = children(current);
        }
        return steps;
    }

    /// Gets the worst severity of an element and all the elements after it.
    ///
    /// @param element The first element of the branch.
    /// @return The worst severity, or null if none of them have one.
    public Severity getBranchSeverity(final PipelineElement element) {
        Severity worst = severityFunction.apply(element);
        for (final PipelineElement child : children(element)) {
            worst = worst(worst, getBranchSeverity(child));
        }
        return worst;
    }

    /// Gets whether an element or any of the elements after it have active stepping filters.
    ///
    /// @param element The first element of the branch.
    /// @return Whether any of them have active filters.
    public boolean hasBranchFilters(final PipelineElement element) {
        if (filtersPredicate.test(element)) {
            return true;
        }
        for (final PipelineElement child : children(element)) {
            if (hasBranchFilters(child)) {
                return true;
            }
        }
        return false;
    }

    private PipelineElement choose(final PipelineElement parent,
                                   final List<PipelineElement> children,
                                   final List<PipelineElement> ancestry,
                                   final int depth,
                                   final Map<String, String> choices) {
        if (children.size() == 1) {
            return children.get(0);
        }
        // The branch holding the selected element
        if (depth < ancestry.size() && children.contains(ancestry.get(depth))) {
            return ancestry.get(depth);
        }
        // The branch chosen last time
        final String chosenId = NullSafe.get(choices, map -> map.get(parent.getId()));
        if (chosenId != null) {
            for (final PipelineElement child : children) {
                if (chosenId.equals(child.getId())) {
                    return child;
                }
            }
        }
        // The branch with the worst problems
        PipelineElement worstChild = null;
        Severity worstSeverity = null;
        for (final PipelineElement child : children) {
            final Severity severity = getBranchSeverity(child);
            if (severity != null
                && severity.greaterThanOrEqual(Severity.WARNING)
                && (worstSeverity == null || severity.greaterThan(worstSeverity))) {
                worstChild = child;
                worstSeverity = severity;
            }
        }
        return worstChild != null
                ? worstChild
                : children.get(0);
    }

    // The elements from the root to the element, both included, or empty if it isn't in the
    // pipeline
    private List<PipelineElement> findAncestry(final PipelineElement element) {
        final List<PipelineElement> ancestry = new ArrayList<>();
        if (element != null && findAncestry(root, element, ancestry)) {
            return ancestry;
        }
        return Collections.emptyList();
    }

    private boolean findAncestry(final PipelineElement current,
                                 final PipelineElement element,
                                 final List<PipelineElement> ancestry) {
        ancestry.add(current);
        if (current.equals(element)) {
            return true;
        }
        for (final PipelineElement child : children(current)) {
            if (findAncestry(child, element, ancestry)) {
                return true;
            }
        }
        ancestry.remove(ancestry.size() - 1);
        return false;
    }

    private List<PipelineElement> children(final PipelineElement element) {
        return NullSafe.list(childrenFunction.apply(element));
    }

    private static Severity worst(final Severity severity1, final Severity severity2) {
        return Severity.getMaxSeverity(severity1, severity2).orElse(null);
    }


    // --------------------------------------------------------------------------------


    /// A step of the path.
    ///
    /// @param element        The element.
    /// @param alternatives   If the element is after a fork, the elements the path could follow
    ///                       there (its siblings and itself), in order, else empty.
    /// @param hiddenSeverity The worst severity in the branches of the other alternatives, or
    ///                       null for none.
    /// @param hiddenFilters  Whether any element in the branches of the other alternatives has
    ///                       active stepping filters.
    public record Step(PipelineElement element,
                       List<PipelineElement> alternatives,
                       Severity hiddenSeverity,
                       boolean hiddenFilters) {

        /// @return Whether the element is after a fork, so the path could follow another element.
        public boolean isAfterFork() {
            return alternatives.size() > 1;
        }
    }
}
