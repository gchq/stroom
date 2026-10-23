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

import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.MutationType;
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathwayMutation;
import stroom.pathways.shared.pathway.PathwayReplay;
import stroom.planb.impl.dao.trace.CanonicalSpanOrder;
import stroom.planb.impl.dao.trace.IgnoredSpans;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Winding a learnt model back through the changes that built it, and forward again.
 *
 * <p>The model is built here the way the consumer builds it, from real traces, so what is wound back
 * is a real history rather than mutations written by hand. Undoing has to be the exact opposite of
 * applying: a scrubber moved back and forth would otherwise drift away from the truth a step at a
 * time.
 */
class TestPathwayReplay {

    private static final String OPERATION = "ProcessorTaskCreatorJob.run";
    private static final String PING = "Ping";
    private static final String PREPARE = "Prepare statement";
    private static final String COMMIT = "Commit";
    private static final long BASE = 1_700_000_000_000_000_000L;

    @Test
    void windingAllTheWayBackLeavesNothing() {
        final Learnt learnt = learn();

        assertThat(PathwayReplay.rewind(learnt.root, learnt.mutations))
                .as("before the first change there was no pathway")
                .isNull();
    }

    @Test
    void windingBackAndForwardAgainGivesTheSameModel() {
        final Learnt learnt = learn();

        // All the way back, then every change put on again in the order it was made.
        PathNode node = PathwayReplay.rewind(learnt.root, learnt.mutations);
        for (final PathwayMutation mutation : learnt.mutations) {
            node = PathwayReplay.apply(node, mutation);
        }

        assertSame(node, learnt.root);
    }

    @Test
    void anyPointInTheHistoryCanBeReached() {
        final Learnt learnt = learn();

        // Every point, so nothing in the history is left unexercised.
        for (int i = 0; i <= learnt.mutations.size(); i++) {
            final List<PathwayMutation> later = learnt.mutations.subList(i, learnt.mutations.size());
            final PathNode at = PathwayReplay.rewind(learnt.root, later);

            // The same point reached forwards from nothing must agree.
            PathNode forwards = null;
            for (final PathwayMutation mutation : learnt.mutations.subList(0, i)) {
                forwards = PathwayReplay.apply(forwards, mutation);
            }
            assertSame(at, forwards);
        }
    }

    @Test
    void aNodeKeepsItsIdentityAcrossTheReplay() {
        final Learnt learnt = learn();

        PathNode node = PathwayReplay.rewind(learnt.root, learnt.mutations);
        for (final PathwayMutation mutation : learnt.mutations) {
            node = PathwayReplay.apply(node, mutation);
        }

        assertThat(uuids(node))
                .as("a replayed node is the same node, so the view can follow it between frames")
                .isEqualTo(uuids(learnt.root));
    }

    @Test
    void aConstraintBornOptionalStaysOptional() {
        // An attribute first seen on a node that already had constraints is added as optional. The
        // flag is not something a replay can work out from the values, so the change has to carry it.
        PathNode root = null;
        final List<PathwayMutation> mutations = new ArrayList<>();
        long sequence = 0;
        for (final Trace trace : List.of(trace("GET", 20, PING), traceWithExtraAttribute())) {
            final NodeMutatorImpl mutator = mutator();
            root = mutator.process(trace, new NamePathKey(OPERATION), root, quiet(), doc());
            for (final PathwayMutation mutation : mutator.getMutations()) {
                mutations.add(mutation.withSequence(++sequence, -1));
            }
        }

        assertThat(root.getConstraints().get("attribute.http.status").isOptional())
                .as("the fixture only says something if the constraint really was born optional")
                .isTrue();

        PathNode replayed = PathwayReplay.rewind(root, mutations);
        for (final PathwayMutation mutation : mutations) {
            replayed = PathwayReplay.apply(replayed, mutation);
        }
        assertSame(replayed, root);
    }

    @Test
    void undoingAnIgnoredAttributeRemovesIt() {
        // An ignored attribute is recorded with nothing before it, so undoing it has to take the
        // constraint away rather than put back a constraint holding no value.
        final IgnoredAttributes ignored = new IgnoredAttributes(List.of("http.method"));
        final NodeMutatorImpl mutator = new NodeMutatorImpl(
                new CanonicalSpanOrder(doc().getTemporalOrderingTolerance()), ignored,
                new IgnoredSpans(List.of()));
        final PathNode root = mutator.process(trace("GET", 20, PING), new NamePathKey(OPERATION), null,
                quiet(), doc());

        final List<PathwayMutation> mutations = new ArrayList<>();
        long sequence = 0;
        for (final PathwayMutation mutation : mutator.getMutations()) {
            mutations.add(mutation.withSequence(++sequence, -1));
        }

        assertThat(root.getConstraints()).containsKey("attribute.http.method");

        // Back to just before it was recorded. Winding the whole way would end at null whether this
        // works or not, so the state either side of the one change is what has to be looked at.
        final PathwayMutation change = mutations.stream()
                .filter(m -> MutationType.CONSTRAINT_IGNORED.equals(m.getType()))
                .findFirst()
                .orElseThrow();
        final List<PathwayMutation> from = mutations.stream()
                .filter(m -> m.getSequence() >= change.getSequence())
                .toList();

        assertThat(PathwayReplay.rewind(root, from).getConstraints())
                .as("it did not exist before, so undoing must take it away rather than blank it")
                .doesNotContainKey("attribute.http.method");
    }

    // Compares the shape, the names, and every constraint, which is everything a replay has to get
    // right. Left as text so a failure says where the two differ.
    private static void assertSame(final PathNode actual, final PathNode expected) {
        assertThat(describe(actual)).isEqualTo(describe(expected));
    }

    private static String describe(final PathNode node) {
        if (node == null) {
            return "<none>";
        }
        final StringBuilder sb = new StringBuilder();
        describe(node, sb, "");
        return sb.toString();
    }

    private static void describe(final PathNode node, final StringBuilder sb, final String indent) {
        sb.append(indent).append(node.getName()).append(' ').append(node.getNodePath()).append('\n');
        final List<String> names = new ArrayList<>(node.getConstraints() == null
                ? List.of()
                : node.getConstraints().keySet());
        names.sort(String::compareTo);
        for (final String name : names) {
            final Constraint constraint = node.getConstraints().get(name);
            sb.append(indent).append("  ").append(name).append('=').append(constraint.getValue())
                    .append(constraint.isOptional()
                            ? " (optional)"
                            : "").append('\n');
        }
        final List<PathNode> children = new ArrayList<>(node.getChildren() == null
                ? List.<PathNode>of()
                : node.getChildren());
        children.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (final PathNode child : children) {
            describe(child, sb, indent + "    ");
        }
    }

    private static List<String> uuids(final PathNode node) {
        final List<String> found = new ArrayList<>();
        collect(node, found);
        found.sort(String::compareTo);
        return found;
    }

    private static void collect(final PathNode node, final List<String> found) {
        if (node != null) {
            found.add(node.getUuid());
            if (node.getChildren() != null) {
                node.getChildren().forEach(child -> collect(child, found));
            }
        }
    }

    // A history with something of every kind in it: nodes appearing, constraints added, a set growing,
    // a duration range stretching both ways, and a step that does not run every time.
    private static Learnt learn() {
        final List<PathwayMutation> mutations = new ArrayList<>();
        PathNode root = null;
        long sequence = 0;

        final List<Trace> traces = List.of(
                trace("GET", 20, PING),
                trace("GET", 20, PING, PREPARE),
                trace("POST", 5, PING, PREPARE),
                trace("PUT", 90, PING, PREPARE, COMMIT),
                trace("GET", 50, PING),
                trace("DELETE", 20, PING, PREPARE, COMMIT));

        for (final Trace trace : traces) {
            final NodeMutatorImpl mutator = mutator();
            root = mutator.process(trace, new NamePathKey(OPERATION), root, quiet(), doc());
            for (final PathwayMutation mutation : mutator.getMutations()) {
                sequence++;
                mutations.add(mutation.withSequence(sequence, -1));
            }
        }

        assertThat(mutations).as("the fixture only says something if the model really moved")
                .hasSizeGreaterThan(10);
        return new Learnt(root, mutations);
    }

    // The root carries a second attribute this time, so it is added to a node that already has
    // constraints — which is what makes it optional.
    private static Trace traceWithExtraAttribute() {
        final Span root = Span.builder()
                .name(OPERATION)
                .spanId("r0")
                .parentSpanId("")
                .kind(SpanKind.SPAN_KIND_INTERNAL)
                .startTimeUnixNano(Long.toString(BASE))
                .endTimeUnixNano(Long.toString(BASE + NanoDuration.ofMillis(20).getNanos()))
                .attributes(List.of(
                        attribute("http.method", "GET"),
                        attribute("http.status", "200")))
                .build();
        final Map<String, List<Span>> byParent = new HashMap<>();
        byParent.put("", List.of(root));
        byParent.put("r0", List.of(span(PING, "c0", "r0", 1, 1, null)));
        return new Trace("0a0b0c0d0e0f00010203040506070809", byParent);
    }

    private static KeyValue attribute(final String key, final String value) {
        return KeyValue.builder()
                .key(key)
                .value(new AnyValue(value, null, null, null, null, null, null))
                .build();
    }

    private static NodeMutatorImpl mutator() {
        return new NodeMutatorImpl(
                new CanonicalSpanOrder(doc().getTemporalOrderingTolerance()),
                new IgnoredAttributes(List.of()),
                new IgnoredSpans(List.of()));
    }

    private static MessageReceiver quiet() {
        return (severity, message) -> {
        };
    }

    private static PathwaysDoc doc() {
        return PathwaysDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test Pathways")
                .allowPathwayCreation(true)
                .allowPathwayMutation(true)
                .allowConstraintCreation(true)
                .allowConstraintMutation(true)
                .build();
    }

    private static Trace trace(final String method, final int rootMillis, final String... childNames) {
        final Span root = span(OPERATION, "r0", "", 0, rootMillis, method);
        final List<Span> children = new ArrayList<>(childNames.length);
        for (int i = 0; i < childNames.length; i++) {
            children.add(span(childNames[i], "c" + i, "r0", i + 1, 1, null));
        }

        final Map<String, List<Span>> byParent = new HashMap<>();
        byParent.put("", List.of(root));
        if (!children.isEmpty()) {
            byParent.put("r0", children);
        }
        return new Trace("0a0b0c0d0e0f00010203040506070809", byParent);
    }

    private static Span span(final String name,
                             final String spanId,
                             final String parentSpanId,
                             final int startMillis,
                             final int durationMillis,
                             final String method) {
        final long start = BASE + NanoDuration.ofMillis(startMillis).getNanos();
        final Span.Builder builder = Span.builder()
                .name(name)
                .spanId(spanId)
                .parentSpanId(parentSpanId)
                .kind(SpanKind.SPAN_KIND_INTERNAL)
                .startTimeUnixNano(Long.toString(start))
                .endTimeUnixNano(Long.toString(start + NanoDuration.ofMillis(durationMillis).getNanos()));
        if (method != null) {
            builder.attributes(List.of(KeyValue.builder()
                    .key("http.method")
                    .value(new AnyValue(method, null, null, null, null, null, null))
                    .build()));
        }
        return builder.build();
    }


    // --------------------------------------------------------------------------------


    private static final class Learnt {

        private final PathNode root;
        private final List<PathwayMutation> mutations;

        private Learnt(final PathNode root, final List<PathwayMutation> mutations) {
            this.root = root;
            this.mutations = mutations;
        }
    }
}
