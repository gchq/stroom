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

package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.PathStep;
import stroom.pathways.shared.pathway.PathUse;
import stroom.pathways.shared.pathway.Paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds one trace's walk into the paths a pathway has already seen.
 *
 * <p>A path is one shape holding the whole walk: every node the trace reached, the children it ran
 * at each, and the order it ran them in, with work that repeated said once. How many times a repeat
 * ran is not part of it — recording that makes a trace that called a routine eight times a different
 * path from one that called it seven, which is the workload rather than the code path.
 *
 * <p>Shapes are kept once each and named by position, so a subtree many paths reach costs one entry
 * however many of them reach it, and a path is one number.
 *
 * <p>Every trace produces a path, including a trace that reached no children at all, so the path
 * counts sum to the number of traces the pathway has been used for.
 */
final class PathRecorder {

    private PathRecorder() {
    }

    static Paths add(final Paths current,
                      final PathShape shape,
                      final NanoTime time,
                      final NanoTime traceTime,
                      final String traceId,
                      final String sourceUuid) {
        final List<String> nodes = new ArrayList<>(current.getNodes());
        final Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            positions.put(nodes.get(i), i);
        }

        final List<PathStep> steps = new ArrayList<>(current.getSteps());
        final Map<PathStep, Integer> shapes = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            shapes.putIfAbsent(steps.get(i), i);
        }
        // Held whether or not this trace goes on to create a path, because the changes it made are
        // numbered against the same list and are recorded even when the path it took is one the
        // pathway already had.
        final List<String> sources = new ArrayList<>(current.getSources());
        final int source = sourceIndex(sources, sourceUuid);

        // A trace always has a shape; -1 is here so a caller that has none records a path the screen
        // reads as nothing rather than failing.
        final int root = shape == null
                ? -1
                : intern(shape, nodes, positions, steps, shapes);

        final List<PathUse> paths = new ArrayList<>(current.getPaths());
        for (int i = 0; i < paths.size(); i++) {
            if (paths.get(i).getRoot() == root) {
                paths.set(i, paths.get(i).used(time, traceTime));
                return new Paths(nodes, steps, paths, sources);
            }
        }

        // Kept in the order first taken, so the oldest path stays at the top of the table however
        // the counts move.
        paths.add(new PathUse(root, 1L, time, time, traceId, traceTime, traceTime, source));
        return new Paths(nodes, steps, paths, sources);
    }

    // Where a trace store sits in the list, adding it where it is not there yet. A trace that arrived
    // without a source is -1 rather than an entry, so an unattributed row says so instead of pointing
    // at whichever store happens to be first.
    private static int sourceIndex(final List<String> sources, final String sourceUuid) {
        if (sourceUuid == null) {
            return -1;
        }
        final int found = sources.indexOf(sourceUuid);
        if (found >= 0) {
            return found;
        }
        sources.add(sourceUuid);
        return sources.size() - 1;
    }

    // Puts a shape and everything under it in the shape list, deepest first, and says where it went.
    // A shape the list already holds is not added again, so a subtree many paths reach costs one
    // entry however many of them reach it.
    private static int intern(final PathShape shape,
                              final List<String> nodes,
                              final Map<String, Integer> positions,
                              final List<PathStep> steps,
                              final Map<PathStep, Integer> shapes) {
        // Numbered on the way down, so nodes are numbered in the order the trace reached them and the
        // root takes the first position. Where the shape goes in the list has to wait for the children,
        // because it is made of where they went.
        final int node;
        if (shape.kind() != PathShape.Kind.NODE) {
            node = switch (shape.kind()) {
                case UNFINISHED -> PathStep.UNFINISHED;
                case CONCURRENT -> PathStep.CONCURRENT;
                case RUN -> PathStep.RUN;
                case NODE -> throw new IllegalStateException("handled above");
            };
        } else {
            Integer position = positions.get(shape.nodeUuid());
            if (position == null) {
                position = nodes.size();
                nodes.add(shape.nodeUuid());
                positions.put(shape.nodeUuid(), position);
            }
            node = position;
        }

        final List<Integer> children = new ArrayList<>(shape.steps().size());
        for (final PathShape child : shape.steps()) {
            children.add(intern(child, nodes, positions, steps, shapes));
        }

        final PathStep step = new PathStep(node, children);
        final Integer existing = shapes.get(step);
        if (existing != null) {
            return existing;
        }
        steps.add(step);
        shapes.put(step, steps.size() - 1);
        return steps.size() - 1;
    }
}
