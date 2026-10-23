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

import stroom.pathways.shared.pathway.PathNode;

import java.util.HashMap;
import java.util.Map;

/**
 * A trace's shape written out, so a test can say what a trace did in one line.
 *
 * <p>Written the way the paths table writes it, so what a test asserts is what a reader sees.
 * {@code name} is a node that ran nothing and {@code name (a > b)} one that ran a and then b;
 * {@code [a > b]} is work that ran over and over and stopped part way through the last time;
 * {@code {a | b}} is runs that happened at the same time. Each bracket means one thing and nothing
 * else. Work that repeated and did finish is simply said once, so it has no mark of its own.
 */
final class PathShapeText {

    private PathShapeText() {
    }

    static String of(final PathShape shape, final PathNode root) {
        final Map<String, String> names = new HashMap<>();
        collect(root, names);
        return render(shape, names);
    }

    /**
     * What one node ran, without the node itself — for asserting on a node partway down the model
     * where naming everything above it would say the same thing every time.
     */
    static String under(final PathShape shape, final PathNode root, final String nodeName) {
        final Map<String, String> names = new HashMap<>();
        collect(root, names);
        final PathShape found = find(shape, names, nodeName);
        if (found == null) {
            return "";
        }
        final String text = render(found, names);
        final int open = text.indexOf(" (");
        return open < 0
                ? ""
                : text.substring(open + 2, text.length() - 1);
    }

    private static PathShape find(final PathShape shape,
                                   final Map<String, String> names,
                                   final String nodeName) {
        if (shape.kind() == PathShape.Kind.NODE && nodeName.equals(names.get(shape.nodeUuid()))) {
            return shape;
        }
        for (final PathShape step : shape.steps()) {
            final PathShape found = find(step, names, nodeName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collect(final PathNode node, final Map<String, String> names) {
        names.put(node.getUuid(), node.getName());
        node.getChildren().forEach(child -> collect(child, names));
    }

    // Everything this shape ran is a turn of one and the same node, so the order shown is settled by
    // what the turns are rather than by when they happened.
    private static boolean turnsOfOneNode(final PathShape shape) {
        if (shape.steps().size() < 2) {
            return false;
        }
        String node = null;
        for (final PathShape step : shape.steps()) {
            if (step.nodeUuid() == null) {
                return false;
            }
            if (node == null) {
                node = step.nodeUuid();
            } else if (!node.equals(step.nodeUuid())) {
                return false;
            }
        }
        return true;
    }

    private static String render(final PathShape shape, final Map<String, String> names) {
        final String separator = shape.kind() == PathShape.Kind.CONCURRENT
                ? " | "
                : turnsOfOneNode(shape)
                        ? ", "
                        : " > ";
        final StringBuilder sb = new StringBuilder();
        for (final PathShape step : shape.steps()) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(render(step, names));
        }
        return switch (shape.kind()) {
            case UNFINISHED -> "[" + sb + "]";
            case CONCURRENT -> "{" + sb + "}";
            case RUN -> sb.toString();
            case NODE -> shape.steps().isEmpty()
                    ? names.get(shape.nodeUuid())
                    : names.get(shape.nodeUuid()) + " (" + sb + ")";
        };
    }
}
