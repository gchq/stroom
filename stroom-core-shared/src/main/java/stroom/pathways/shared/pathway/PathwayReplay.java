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

package stroom.pathways.shared.pathway;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Winds a learnt model backwards and forwards through the changes made to it.
 *
 * <p>Every mutation carries what the constraint held before as well as after, so each one can be taken
 * off as readily as put on. The model at any point is therefore the current model with every later
 * change undone — no snapshot of it has to be kept, and nothing has to be replayed from the beginning.
 *
 * <p>This lives beside the model rather than with the code that writes it because both sides need it:
 * the browser scrubs through the history without asking the server for each step, and the server can
 * answer for a point in time with the same code the browser runs.
 *
 * <p>{@link #apply} and {@link #undo} are exact opposites. Winding back and forward again must give
 * the model it started with, or scrubbing would drift.
 */
public final class PathwayReplay {

    private PathwayReplay() {
    }

    /**
     * The model as it stood after the given change, given the model as it stands now and the changes
     * made since. Changes are taken off newest first, so each sees the model its own change produced.
     *
     * @param later the changes made after the wanted point, in any order.
     * @return the earlier model, or null where the pathway did not exist at that point.
     */
    public static PathNode rewind(final PathNode current, final List<PathwayMutation> later) {
        final List<PathwayMutation> newestFirst = new ArrayList<>(NullSafe.list(later));
        newestFirst.sort((a, b) -> Long.compare(b.getSequence(), a.getSequence()));

        PathNode node = current;
        for (final PathwayMutation mutation : newestFirst) {
            if (node == null) {
                return null;
            }
            node = undo(node, mutation);
        }
        return node;
    }

    /**
     * Takes one change off the model.
     *
     * @return the model without it, or null where the change was the pathway coming into being.
     */
    public static PathNode undo(final PathNode root, final PathwayMutation mutation) {
        if (root == null || mutation == null) {
            return root;
        }

        final MutationType type = mutation.getType();
        if (MutationType.PATHWAY_ADDED.equals(type)) {
            // Before this there was no pathway at all.
            return null;
        }
        if (MutationType.NODE_ADDED.equals(type)) {
            return removeNode(root, mutation.getNodePath());
        }
        if (MutationType.NODE_RETIRED.equals(type)) {
            return change(root, mutation.getNodePath(), node -> node.copy().retiredTime(null).build());
        }
        if (MutationType.NODE_REVIVED.equals(type)) {
            // Put back as retired. When it was retired is not kept on the change, and the drawing only
            // asks whether it is, so the time this undo stands at says it as well as any other.
            return change(root, mutation.getNodePath(),
                    node -> node.copy().retiredTime(mutation.getTime()).build());
        }
        if (MutationType.PATH_DROPPED.equals(type)) {
            // Paths are not part of the node tree a replay winds back, so there is nothing here to put
            // back. The row says a path was dropped and when, which is what a reader wants from it.
            return root;
        }
        return change(root, mutation.getNodePath(), node -> undoConstraint(node, mutation));
    }

    /**
     * Puts one change back on the model, so a replay can run forwards as well as backwards.
     */
    public static PathNode apply(final PathNode root, final PathwayMutation mutation) {
        if (mutation == null) {
            return root;
        }

        final MutationType type = mutation.getType();
        if (MutationType.PATHWAY_ADDED.equals(type)) {
            final List<String> path = NullSafe.list(mutation.getNodePath());
            return new PathNode(mutation.getNodeUuid(), name(path, null), path,
                    new ArrayList<>(), new HashMap<>());
        }
        if (root == null) {
            return null;
        }
        if (MutationType.NODE_ADDED.equals(type)) {
            return addNode(root, mutation);
        }
        if (MutationType.NODE_RETIRED.equals(type)) {
            return change(root, mutation.getNodePath(),
                    node -> node.copy().retiredTime(mutation.getTime()).build());
        }
        if (MutationType.NODE_REVIVED.equals(type)) {
            return change(root, mutation.getNodePath(), node -> node.copy().retiredTime(null).build());
        }
        if (MutationType.PATH_DROPPED.equals(type)) {
            // Nothing in the node tree changed, so there is nothing to put back on it. Named by the
            // pathway rather than by a node, and paths are not what a replay winds through.
            return root;
        }
        return change(root, mutation.getNodePath(), node -> applyConstraint(node, mutation));
    }

    private static PathNode undoConstraint(final PathNode node, final PathwayMutation mutation) {
        final Map<String, Constraint> constraints = new HashMap<>(NullSafe.map(node.getConstraints()));
        final String name = mutation.getConstraint();
        final Constraint existing = constraints.get(name);

        if (mutation.getOldValue() == null) {
            // Nothing was there before, whether this added the constraint or recorded one the
            // configuration says to leave alone. Putting the null back would leave a constraint that
            // never existed, holding no value.
            constraints.remove(name);
        } else if (MutationType.CONSTRAINT_OPTIONAL.equals(mutation.getType())) {
            // Only the flag moved, and only one way.
            constraints.put(name, rebuild(existing, name, mutation.getOldValue(), false));
        } else {
            // A value changing never moves the flag, so the node keeps the one it has.
            constraints.put(name, rebuild(existing, name, mutation.getOldValue(),
                    existing != null && existing.isOptional()));
        }
        return node.copy().constraints(constraints).build();
    }

    private static PathNode applyConstraint(final PathNode node, final PathwayMutation mutation) {
        final Map<String, Constraint> constraints = new HashMap<>(NullSafe.map(node.getConstraints()));
        // The change says what the flag is once it has been made, which is the only way to know that a
        // constraint was born optional rather than made so later.
        constraints.put(mutation.getConstraint(), rebuild(constraints.get(mutation.getConstraint()),
                mutation.getConstraint(), mutation.getNewValue(), mutation.isOptional()));
        return node.copy().constraints(constraints).build();
    }

    // How often a constraint has been used, and when it last was, are what the model holds now rather
    // than something the changes describe. Winding the model back says what it allowed at the time, so
    // those counts carry across untouched rather than being wound back with the value.
    private static Constraint rebuild(final Constraint existing,
                                      final String name,
                                      final ConstraintValue value,
                                      final boolean optional) {
        return Constraint.builder()
                .name(name)
                .value(value)
                .optional(optional)
                .timesUsed(existing == null
                        ? 0L
                        : existing.getTimesUsed())
                .lastUsedTime(existing == null
                        ? null
                        : existing.getLastUsedTime())
                .build();
    }

    // The node the change happened to, found by name from the root down. Unambiguous because children
    // are held one per name.
    private static PathNode change(final PathNode node,
                                   final List<String> path,
                                   final NodeChange nodeChange) {
        if (node == null) {
            return null;
        }
        if (NullSafe.list(path).size() <= 1) {
            return nodeChange.apply(node);
        }

        final String childName = path.get(1);
        final List<PathNode> children = new ArrayList<>(NullSafe.list(node.getChildren()));
        for (int i = 0; i < children.size(); i++) {
            if (childName.equals(children.get(i).getName())) {
                children.set(i, change(children.get(i), path.subList(1, path.size()), nodeChange));
                return node.copy().children(children).build();
            }
        }
        // Nothing there to change, which is what a model that never saw it looks like.
        return node;
    }

    private static PathNode removeNode(final PathNode root, final List<String> path) {
        if (NullSafe.list(path).size() < 2) {
            return root;
        }
        final String name = path.get(path.size() - 1);
        return change(root, path.subList(0, path.size() - 1), parent -> {
            final List<PathNode> children = new ArrayList<>(NullSafe.list(parent.getChildren()));
            children.removeIf(child -> name.equals(child.getName()));
            return parent.copy().children(children).build();
        });
    }

    private static PathNode addNode(final PathNode root, final PathwayMutation mutation) {
        final List<String> path = NullSafe.list(mutation.getNodePath());
        if (path.size() < 2) {
            return root;
        }
        final String name = path.get(path.size() - 1);
        return change(root, path.subList(0, path.size() - 1), parent -> {
            final List<PathNode> children = new ArrayList<>(NullSafe.list(parent.getChildren()));
            children.removeIf(child -> name.equals(child.getName()));
            children.add(new PathNode(mutation.getNodeUuid(), name, path, new ArrayList<>(),
                    new HashMap<>()));
            return parent.copy().children(children).build();
        });
    }

    private static String name(final List<String> path, final String fallback) {
        final List<String> names = NullSafe.list(path);
        return names.isEmpty()
                ? fallback
                : names.get(names.size() - 1);
    }


    // --------------------------------------------------------------------------------


    private interface NodeChange {

        PathNode apply(PathNode node);
    }
}
