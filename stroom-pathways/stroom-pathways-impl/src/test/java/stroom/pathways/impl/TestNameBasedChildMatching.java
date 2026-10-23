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
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
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
 * How a trace's child spans line up with the model's child nodes.
 *
 * <p>They line up by name. A name that turns up four times under one parent is one thing that
 * happened four times, so the model holds one node for it and records the count as a constraint.
 * The alternative — one node per span, matched by position — makes a request that ran three queries
 * a different path from one that ran four, and the model then grows with every new count seen.
 */
class TestNameBasedChildMatching {

    /** Nothing ignored, which is the default. */
    private static final IgnoredAttributes NO_IGNORED = new IgnoredAttributes(List.of());

    private static final String OPERATION = "ProcessorTaskCreatorJob.run";
    private static final String PREPARE = "Prepare statement";
    private static final String PING = "Ping";
    private static final String COMMIT = "Commit";
    private static final String BATCH = "PathwaysProcessor.applyBatch";
    private static final String OCCURRENCES = "occurrences";
    private static final long BASE = 1_700_000_000_000_000_000L;

    @Test
    void repeatsOfOneNameAreOneChild() {
        final PathNode root = learn(null, trace(PREPARE, PREPARE, PREPARE, PREPARE));

        assertThat(root.getChildren())
                .as("four spans of one name are one child")
                .hasSize(1);
        assertThat(root.getChildren().getFirst().getName()).isEqualTo(PREPARE);
        assertThat(count(root.getChildren().getFirst()))
                .as("how many there were is a constraint on the child")
                .isEqualTo(new IntegerValue(4));
    }

    @Test
    void aDifferentCountWidensTheChildRatherThanAddingOne() {
        PathNode root = learn(null, trace(PREPARE, PREPARE, PREPARE, PREPARE));
        root = learn(root, trace(PREPARE, PREPARE, PREPARE, PREPARE, PREPARE, PREPARE));

        assertThat(root.getChildren())
                .as("running the same step more times is the same path")
                .hasSize(1);
    }

    @Test
    void aNameMissingFromOneTraceIsStillTheSameChild() {
        PathNode root = learn(null, trace(PING, PREPARE));
        root = learn(root, trace(PING));

        assertThat(root.getChildren())
                .as("a step that did not run this time does not start a second path")
                .hasSize(2);
        assertThat(root.getChildren().stream().map(PathNode::getName))
                .containsExactlyInAnyOrder(PING, PREPARE);
    }

    @Test
    void aNameNeverSeenBeforeAddsOneChild() {
        PathNode root = learn(null, trace(PING));
        root = learn(root, trace(PING, PREPARE));

        assertThat(root.getChildren()).hasSize(2);
    }

    @Test
    void theModelStopsGrowingOnceEveryNameHasBeenSeen() {
        PathNode root = learn(null, trace(PING, PREPARE));

        // The same two names over and over, in every count and order the workload throws up.
        for (int i = 1; i <= 20; i++) {
            final List<String> names = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                names.add(PREPARE);
            }
            names.add(PING);
            root = learn(root, trace(names.toArray(new String[0])));
            root = learn(root, trace(PING, PREPARE, PING));
        }

        assertThat(root.getChildren())
                .as("no name is new, so nothing is added")
                .hasSize(2);
    }

    @Test
    void childrenKeepTheOrderTheyWereFirstReachedIn() {
        PathNode root = learn(null, trace(PING));
        // Alphabetically Commit sorts first, but it was reached second.
        root = learn(root, trace(PING, COMMIT));

        assertThat(root.getChildren().stream().map(PathNode::getName))
                .as("the model holds the order the steps were first reached, not their names sorted")
                .containsExactly(PING, COMMIT);
    }

    @Test
    void theSameWorkTwiceIsTheSameShape() {
        final PathNode root = learn(null, sequential(PING, COMMIT));

        assertThat(stepsOf(root, sequential(PING, COMMIT)))
                .as("the same work twice is one way of working, not two")
                .isEqualTo(stepsOf(null, sequential(PING, COMMIT)));
    }

    @Test
    void aRunOfOneChildOverAndOverIsOneStep() {
        final PathNode root = learn(null, sequential(PING, PING, PING, COMMIT));

        assertThat(stepsOf(null, sequential(PING, PING, PING, COMMIT)))
                .as("doing the same work three times rather than once is how much there was to do, "
                    + "not a different way of working")
                .isEqualTo(PING + " > " + COMMIT);
        assertThat(count(child(root, PING)))
                .as("how many times is still counted on the child")
                .isEqualTo(new IntegerValue(3));
    }

    @Test
    void aChildComingBackAfterAnotherIsAStepOfItsOwn() {
        final PathNode root = learn(null, sequential(PING, COMMIT, PING));

        assertThat(stepsOf(null, sequential(PING, COMMIT, PING)))
                .as("the second Ping did not happen where the first one did, and a model that put it "
                    + "there would say this node pinged twice and then committed")
                .isEqualTo(PING + " > " + COMMIT + " > " + PING);
        assertThat(count(child(root, PING)))
                .isEqualTo(new IntegerValue(2));
    }

    @Test
    void goingRoundAgainIsNotTheSameAsDoingItAllAtOnce() {
        assertThat(stepsOf(null, sequential(PING, COMMIT, PING)))
                .as("both ping twice and commit once, and the counts on their children are the same, "
                    + "so the shape is the only thing that can tell a batch from a loop")
                .isNotEqualTo(stepsOf(null, sequential(PING, PING, COMMIT)));
    }

    @Test
    void countingStepsIsNotAChangeToTheModel() {
        final PathNode root = learn(null, sequential(PING, COMMIT));

        final NodeMutatorImpl mutator = mutator();
        mutator.process(sequential(PING, COMMIT), new NamePathKey(OPERATION), root, (severity, message) -> {
        }, doc());

        assertThat(mutator.isChanged())
                .as("a trace that taught nothing must not count as a change, or Times Updated climbs "
                    + "on every trace and a settled model can no longer be told from a moving one")
                .isFalse();
    }

    @Test
    void aNodeReachedTwiceKeepsBothWaysItRan() {
        final NodeMutatorImpl mutator = mutator();
        final PathNode root = process(mutator, twoBatches(PING, COMMIT, COMMIT, PING));

        assertThat(PathShapeText.of(mutator.getPathShape(), root))
                .as("a node several spans reached can run differently each time, and the shape keeps "
                    + "both ways — read in an order settled by what they are, not by which ran first, "
                    + "so nothing here may depend on that order")
                .contains(BATCH + " (" + PING + " > " + COMMIT + ")")
                .contains(BATCH + " (" + COMMIT + " > " + PING + ")");
    }

    @Test
    void runningTheSameWayTwiceInATraceIsSaidOnce() {
        final NodeMutatorImpl mutator = mutator();
        final PathNode root = process(mutator, twoBatches(PING, COMMIT, PING, COMMIT));

        assertThat(PathShapeText.of(mutator.getPathShape(), root))
                .as("the same run twice over is how much work there was to do, so it is said once")
                .isEqualTo(OPERATION + " (" + BATCH + " (" + PING + " > " + COMMIT + "))");
    }

    @Test
    void aNodeThatRanNoChildrenIsALeaf() {
        final NodeMutatorImpl mutator = mutator();
        final PathNode root = process(mutator, trace(PING));

        assertThat(PathShapeText.of(mutator.getPathShape(), root))
                .as("a node with nothing below it is a leaf, and that is the only thing it can mean")
                .isEqualTo(OPERATION + " (" + PING + ")");
    }

    @Test
    void anOptionalChildMissingChangesTheStepsItsParentTook() {
        final NodeMutatorImpl withBoth = mutator();
        final PathNode first = process(withBoth, sequential(PING, COMMIT));

        final NodeMutatorImpl withoutCommit = mutator();
        final PathNode root = process(withoutCommit, sequential(PING), first);

        assertThat(PathShapeText.of(withoutCommit.getPathShape(), root))
                .as("a child that did not run leaves its parent running differently, which is where a "
                    + "path records that the child was skipped")
                .isEqualTo(OPERATION + " (" + PING + ")");
        assertThat(PathShapeText.of(withBoth.getPathShape(), first))
                .isEqualTo(OPERATION + " (" + PING + " > " + COMMIT + ")");
    }

    @Test
    void aTraceThatReachedNoChildrenIsJustTheRoot() {
        final PathNode root = learn(null, trace(PING));

        assertThat(stepsOf(root, trace()))
                .as("doing no work below is a count of zero on the child, not an empty run")
                .isEmpty();
    }

    @Test
    void aRunOfTheSameTwoChildrenIsOneRound() {
        final PathNode root = learn(null, sequential(PING, COMMIT, PING, COMMIT, PING, COMMIT));

        assertThat(stepsOf(null, sequential(PING, COMMIT, PING, COMMIT, PING, COMMIT)))
                .as("going round the same two children three times is the amount of work there was "
                    + "to do, exactly as doing one child three times is")
                .isEqualTo(PING + " > " + COMMIT);
        assertThat(count(child(root, PING)))
                .as("how many times round is still counted on each child")
                .isEqualTo(new IntegerValue(3));
    }

    @Test
    void aRoundInsideARoundIsSaidOnce() {
        // Two children over and over, then a third, and the whole thing again — which is what
        // reading documents a page at a time and then reading the page looks like.
        assertThat(stepsOf(null, sequential(
                PREPARE, PING, PREPARE, PING, PREPARE, COMMIT,
                PREPARE, PING, PREPARE, PING, PREPARE, COMMIT)))
                .as("the long round is found first and said once, and the short one inside it is "
                    + "said once within that — neither carries a mark saying it happened again")
                .isEqualTo(PREPARE + " > " + PING + " > " + PREPARE + " > " + COMMIT);
    }

    @Test
    void aChildAfterTheRoundEndsIsStillAStep() {
        final PathNode root = learn(null, sequential(PING, COMMIT, PING, COMMIT, PREPARE));

        assertThat(stepsOf(null, sequential(PING, COMMIT, PING, COMMIT, PREPARE)))
                .as("only what repeated is said once; what ran afterwards did not repeat and is "
                    + "where this path differs from one that stopped")
                .isEqualTo(PING + " > " + COMMIT + " > " + PREPARE);
    }

    @Test
    void goingRoundMoreTimesIsTheSameShapeNotANewOne() {
        assertThat(stepsOf(null, sequential(PING, COMMIT, PING, COMMIT, PING, COMMIT, PING, COMMIT)))
                .as("a run over more documents is the same way of working, and a model that called "
                    + "it a new shape would hold another for every count it ever saw")
                .isEqualTo(stepsOf(null, sequential(PING, COMMIT, PING, COMMIT)));
    }

    @Test
    void aLongSequenceWithNothingRepeatingIsStillRecorded() {
        final String[] names = new String[60];
        for (int i = 0; i < names.length; i++) {
            names[i] = "child-" + i;
        }
        final PathNode root = learn(null, sequential(names));

        assertThat(shapeOf(sequential(names)).steps())
                .as("a path is the shape, so a node that gave none would go missing from its own "
                    + "path and two traces that went different ways would then be told apart by "
                    + "nothing")
                .hasSize(60);
    }

    @Test
    void aTraceTheModelWasBuiltFromStillMatches() {
        final PathwaysDoc doc = doc();
        final Trace learnt = trace(PING, PREPARE, PREPARE);
        final PathNode root = learn(null, learnt);

        final TracePredicate predicate = new TracePredicate(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()),
                new IgnoredSpans(List.of()),
                new PathKeyFactoryImpl(),
                Map.of(new NamePathKey(OPERATION), root));

        assertThat(predicate.test(learnt))
                .as("the matcher lines children up by name the same way the writer does")
                .isTrue();
    }

    @Test
    void aTraceCarryingAnUnknownNameDoesNotMatch() {
        final PathwaysDoc doc = doc();
        final PathNode root = learn(null, trace(PING));

        final TracePredicate predicate = new TracePredicate(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()),
                new IgnoredSpans(List.of()),
                new PathKeyFactoryImpl(),
                Map.of(new NamePathKey(OPERATION), root));

        assertThat(predicate.test(trace(PING, PREPARE))).isFalse();
    }

    // What one trace ran below the root, read off its shape: a run is recorded on the path now
    // rather than gathered onto the node it happened at.
    private static String stepsOf(final PathNode current, final Trace trace) {
        final NodeMutatorImpl mutator = mutator();
        final PathNode root = process(mutator, trace, current);
        return PathShapeText.under(mutator.getPathShape(), root, OPERATION);
    }

    private static PathShape shapeOf(final Trace trace) {
        final NodeMutatorImpl mutator = mutator();
        process(mutator, trace, null);
        return mutator.getPathShape();
    }

    private static PathNode learn(final PathNode current, final Trace trace) {
        return mutator().process(trace, new NamePathKey(OPERATION), current, (severity, message) -> {
        }, doc());
    }

    private static PathNode process(final NodeMutatorImpl mutator, final Trace trace) {
        return process(mutator, trace, null);
    }

    private static PathNode process(final NodeMutatorImpl mutator,
                                    final Trace trace,
                                    final PathNode current) {
        return mutator.process(trace, new NamePathKey(OPERATION), current, (severity, message) -> {
        }, doc());
    }

    private static PathNode child(final PathNode parent, final String name) {
        return parent.getChildren().stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static NodeMutatorImpl mutator() {
        return new NodeMutatorImpl(
                new CanonicalSpanOrder(doc().getTemporalOrderingTolerance()), NO_IGNORED,
                new IgnoredSpans(List.of()));
    }

    private static Object count(final PathNode pathNode) {
        final Constraint constraint = pathNode.getConstraints().get(OCCURRENCES);
        return constraint == null
                ? null
                : constraint.getValue();
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

    // As trace, but with a gap between children wider than the ordering tolerance, so each one is
    // its own timing group and the order given is the order the model records. Children that overlap
    // are deliberately ordered by name instead, which would make every call here give the same order.
    private static Trace sequential(final String... childNames) {
        return trace(3, childNames);
    }

    // One root span with a child per name given, each starting a millisecond after the last.
    private static Trace trace(final String... childNames) {
        return trace(1, childNames);
    }

    // A root running two spans of the same name, each with two children of its own. Reaches one node
    // twice in one trace so it can run its children a different way each time, which is what a job
    // whose drain threads interleave actually does.
    private static Trace twoBatches(final String firstA,
                                    final String firstB,
                                    final String secondA,
                                    final String secondB) {
        final Map<String, List<Span>> byParent = new HashMap<>();
        byParent.put("", List.of(span(OPERATION, "r0", "", 0)));
        byParent.put("r0", List.of(span(BATCH, "b0", "r0", 1), span(BATCH, "b1", "r0", 20)));
        byParent.put("b0", List.of(span(firstA, "b0a", "b0", 2), span(firstB, "b0b", "b0", 5)));
        byParent.put("b1", List.of(span(secondA, "b1a", "b1", 21), span(secondB, "b1b", "b1", 24)));
        return new Trace("0a0b0c0d0e0f00010203040506070809", byParent);
    }

    private static Trace trace(final int gapMillis, final String... childNames) {
        final Span root = span(OPERATION, "r0", "", 0);
        final List<Span> children = new ArrayList<>(childNames.length);
        for (int i = 0; i < childNames.length; i++) {
            children.add(span(childNames[i], "c" + i, "r0", (i * gapMillis) + 1));
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
                             final int millisIn) {
        final long start = BASE + NanoDuration.ofMillis(millisIn).getNanos();
        return Span.builder()
                .name(name)
                .spanId(spanId)
                .parentSpanId(parentSpanId)
                .kind(SpanKind.SPAN_KIND_INTERNAL)
                .startTimeUnixNano(Long.toString(start))
                .endTimeUnixNano(Long.toString(start + NanoDuration.ofMillis(1).getNanos()))
                .build();
    }
}
