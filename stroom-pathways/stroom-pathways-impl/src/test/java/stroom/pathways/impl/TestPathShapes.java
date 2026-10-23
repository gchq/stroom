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
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.PathNode;
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
 * What one trace's shape says about work that repeated.
 *
 * <p>Written as the paths table writes it: {@code name} for a node that ran nothing,
 * {@code name (a > b)} for one that ran a and then b, and {@code [a > b]} for work that ran over and
 * over and stopped part way through the last time. Work that repeated and did finish is said once,
 * with nothing to mark that it happened again.
 */
class TestPathShapes {

    private static final IgnoredAttributes NO_IGNORED = new IgnoredAttributes(List.of());
    private static final String OPERATION = "job.run";
    private static final String TURN = "filter.run";
    private static final long BASE = 1_700_000_000_000_000_000L;

    @Test
    void workThatDidNotRepeatIsKeptAsItRan() {
        assertThat(shapeOf("a", "b", "c")).isEqualTo("job.run (a > b > c)");
    }

    @Test
    void aChildRunOverAndOverIsSaidOnce() {
        assertThat(shapeOf("a", "a", "a", "a")).isEqualTo("job.run (a)");
    }

    @Test
    void howManyTimesAGroupRanIsNotRecorded() {
        // Three turns and five turns are the same path: how much work there was to do is the
        // workload rather than the path through the code.
        assertThat(shapeOf("a", "b", "a", "b", "a", "b"))
                .isEqualTo(shapeOf("a", "b", "a", "b", "a", "b", "a", "b", "a", "b"));
    }

    @Test
    void aRunWhoseLastTurnStoppedEarlyIsNotACleanRun() {
        assertThat(shapeOf("a", "b", "a", "b", "a", "b")).isEqualTo("job.run (a > b)");
        assertThat(shapeOf("a", "b", "a", "b", "a")).isEqualTo("job.run ([a > b])");
    }

    @Test
    void aRunWhoseMiddleTurnStoppedEarlyIsNotACleanRunEither() {
        // The case the old collapse could not see: an unfinished turn anywhere but the end came out
        // reading exactly like a run where every turn finished.
        assertThat(shapeOf("a", "b", "a", "b", "a", "a", "b")).isEqualTo("job.run ([a > b])");
        assertThat(shapeOf("a", "b", "a", "b", "a", "b", "a", "b")).isEqualTo("job.run (a > b)");
    }

    @Test
    void aLongerUnitWithAnUnfinishedTurnInTheMiddleIsSeenToo() {
        // The shape the GitRepoPush trace actually has, with one turn missing its last step. Matching
        // as far as the names go would run past the end of the short turn and miss it.
        assertThat(shapeOf("p", "d", "p", "e", "p", "d", "p", "e", "p", "d", "p", "d", "p", "e"))
                .isEqualTo("job.run ([p > d > p > e])");
    }

    @Test
    void aStepThatMerelyStartsLikeTheGroupIsItsOwnStep() {
        // "a" here is followed by "c", so the work did not pick the group up again and this is not an
        // unfinished turn of it.
        assertThat(shapeOf("a", "b", "a", "b", "a", "c")).isEqualTo("job.run (a > b > a > c)");
    }

    @Test
    void aRepeatInsideARepeatIsStillSaidOnce() {
        // The case the old collapse got wrong the other way: folding the shortest run first ate the
        // doubled "a" and left "a b", losing that the pair ran twice.
        assertThat(shapeOf("a", "a", "b", "a", "a", "b")).isEqualTo("job.run (a > b)");
    }

    @Test
    void whichOfANodesOwnTurnsRanFirstIsNotPartOfTheShape() {
        // Both traces ran "a" twice, once doing b and once doing c. Which turn did which is decided by
        // which thread read the clock first, or by what was queued, so it is not the path through the
        // code — and recording it would make almost every trace a path of its own.
        final NodeMutatorImpl first = mutator();
        final PathNode root = first.process(twoTurns(nested("a", "b"), nested("a", "c")),
                new NamePathKey(OPERATION), null, (severity, message) -> {
                }, doc());

        // Folded into the same model, so both traces are talking about the same nodes.
        final NodeMutatorImpl second = mutator();
        second.process(twoTurns(nested("a", "c"), nested("a", "b")),
                new NamePathKey(OPERATION), root, (severity, message) -> {
                }, doc());

        assertThat(PathShapeText.of(second.getPathShape(), root))
                .isEqualTo(PathShapeText.of(first.getPathShape(), root));
    }

    @Test
    void aNodeComingBackAfterAnotherOneKeepsItsPlace() {
        // Not the same thing: these are different nodes either side, so running a then b then a is
        // not running a twice and then b, and nothing here may be reordered.
        assertThat(shapeOf("a", "b", "a")).isEqualTo("job.run (a > b > a)");
    }

    @Test
    void runningOnceAndRunningOverAndOverAreTheSameShape() {
        // How much work there was to do is the workload, not the path through the code. The model
        // already refuses to tell three turns from fifty; telling one from three was the only count
        // that survived, and there is no reason it should be the one that does.
        assertThat(shapeOf("a", "b")).isEqualTo(shapeOf("a", "b", "a", "b", "a", "b"));
        assertThat(shapeOf("a")).isEqualTo(shapeOf("a", "a", "a", "a", "a"));
    }

    @Test
    void workOnTwoThreadsAtOnceIsNotReadAsASequence() {
        // Which run reads first is settled by what the runs are, and nothing here may depend on it.
        assertThat(concurrentShape(new String[]{"a", "b"}, new String[]{"c", "d"}))
                .as("the two threads overlapped, so what each did is held side by side rather than "
                    + "one after the other")
                .startsWith("job.run ({")
                .endsWith("})")
                .contains("a > b")
                .contains("c > d")
                .contains(" | ");
    }

    @Test
    void whichThreadGotThereFirstIsNotPartOfTheShape() {
        // The defect this was built for: the runs were laid out in the order each thread got its
        // first span in, so the same work came out as a different path depending on the scheduler.
        // Both traces are folded into one model, because the order the runs settle into is fixed for
        // a pathway but says nothing across two built from nothing.
        final NodeMutatorImpl first = mutator();
        final PathNode root = first.process(concurrent(new String[]{"a", "b"}, new String[]{"c", "d"}),
                new NamePathKey(OPERATION), null, (severity, message) -> {
                }, doc());

        final NodeMutatorImpl second = mutator();
        second.process(concurrent(new String[]{"c", "d"}, new String[]{"a", "b"}),
                new NamePathKey(OPERATION), root, (severity, message) -> {
                }, doc());

        assertThat(PathShapeText.of(second.getPathShape(), root))
                .isEqualTo(PathShapeText.of(first.getPathShape(), root));
    }

    @Test
    void twoThreadsDoingTheSameWorkAreSaidOnce() {
        assertThat(concurrentShape(new String[]{"a", "b"}, new String[]{"a", "b"}))
                .as("how many threads there were is how much work there was to do, not the path "
                    + "through the code")
                .isEqualTo("job.run ({a > b})");
    }

    @Test
    void aNodeThatRanNothingIsALeaf() {
        assertThat(shapeOf("a")).isEqualTo("job.run (a)");
    }

    @Test
    void aTraceThatRanNothingAtAllIsALeafLikeAnyOther() {
        // The whole point of holding a shape rather than rebuilding one: nothing ran under it, and
        // that is the only thing this can mean.
        assertThat(shapeOf()).isEqualTo("job.run");
    }

    @Test
    void turnsOfOneNodeTakenBySeveralThreadsAreSaidBetweenThem() {
        // Threads taking turns of one node off a shared queue. Which thread took the odd turn out is
        // decided by what it reached first, so the turns are said between them rather than as a run
        // per thread.
        assertThat(turnsShape(new String[]{"p", "q"}, new String[]{"p", "q", "r"}))
                .as("the turns the threads took between them, not who took which")
                .startsWith("job.run ({")
                .endsWith("})")
                .contains(TURN + " (p)")
                .contains(TURN + " (q)")
                .contains(TURN + " (r)")
                .doesNotContain(" | ");
    }

    @Test
    void howTheTurnsFellAcrossTheThreadsIsNotPartOfTheShape() {
        // The defect this was built for. Both traces took the same five turns; they differ only in
        // how many each thread happened to get off the queue, which is decided by how long the turns
        // before them took. Recording the split made almost every run of the job a path of its own.
        //
        // Both traces are folded into one model, because the order the turns settle into is fixed for
        // a pathway but says nothing across two built from nothing.
        final NodeMutatorImpl first = mutator();
        final PathNode root = first.process(
                turnsOfOneNode(new String[]{"p", "q"}, new String[]{"p", "q", "r"}),
                new NamePathKey(OPERATION), null, (severity, message) -> {
                }, doc());

        final NodeMutatorImpl second = mutator();
        second.process(turnsOfOneNode(new String[]{"p"}, new String[]{"q", "p", "q", "r"}),
                new NamePathKey(OPERATION), root, (severity, message) -> {
                }, doc());

        assertThat(PathShapeText.of(second.getPathShape(), root))
                .as("the same turns, so the same path however they fell across the threads")
                .isEqualTo(PathShapeText.of(first.getPathShape(), root));
    }

    @Test
    void threadsDoingDifferentWorkKeepTheirRuns() {
        // Uneven runs of different nodes, which is not the same thing at all: what each thread did is
        // what it did, and holding them apart is the point of the runs.
        assertThat(concurrentShape(new String[]{"a", "b"}, new String[]{"a", "b", "c"}))
                .as("different work, so the runs stay apart")
                .contains(" | ");
    }

    // ---------------------------------------------------------------------------------------------

    private static String[] nested(final String parent, final String child) {
        return new String[]{parent, child};
    }

    private static Trace twoTurns(final String[] first, final String[] second) {
        final Map<String, List<Span>> byParent = new HashMap<>();
        byParent.put("", List.of(span(OPERATION, "r0", "", 0)));
        byParent.put("r0", List.of(span(first[0], "t0", "r0", 1), span(second[0], "t1", "r0", 20)));
        byParent.put("t0", List.of(span(first[1], "u0", "t0", 2)));
        byParent.put("t1", List.of(span(second[1], "u1", "t1", 21)));
        return new Trace("0a0b0c0d0e0f00010203040506070809", byParent);
    }

    private static String concurrentShape(final String[] first, final String[] second) {
        final NodeMutatorImpl mutator = mutator();
        final PathNode node = mutator.process(concurrent(first, second),
                new NamePathKey(OPERATION), null, (severity, message) -> {
                }, doc());
        return PathShapeText.of(mutator.getPathShape(), node);
    }

    // Two threads whose spans overlap, each running the names given, in the order given.
    private static Trace concurrent(final String[] first, final String[] second) {
        final Span root = span(OPERATION, "r0", "", 0);
        final Map<String, List<Span>> byParent = new HashMap<>();
        final List<Span> children = new ArrayList<>();
        addRun(children, first, "A", 1);
        addRun(children, second, "B", 2);
        byParent.put("", List.of(root));
        byParent.put("r0", children);
        return new Trace("0a0b0c0d0e0f00010203040506070809", byParent);
    }

    // Spans of one thread, each starting before the one before it has finished so the two threads'
    // runs overlap and are seen to have happened at the same time.
    private static void addRun(final List<Span> into,
                               final String[] names,
                               final String thread,
                               final int offset) {
        for (int i = 0; i < names.length; i++) {
            into.add(span(names[i], thread + i, "r0", offset + (i * 2), thread));
        }
    }

    private static String turnsShape(final String[] first, final String[] second) {
        final NodeMutatorImpl mutator = mutator();
        final PathNode node = mutator.process(turnsOfOneNode(first, second),
                new NamePathKey(OPERATION), null, (severity, message) -> {
                }, doc());
        return PathShapeText.of(mutator.getPathShape(), node);
    }

    // Two threads each taking turns of one node, each turn running the one child named for it, with
    // the threads' spans overlapping so the turns are seen to have happened at the same time.
    private static Trace turnsOfOneNode(final String[] first, final String[] second) {
        final Map<String, List<Span>> byParent = new HashMap<>();
        final List<Span> children = new ArrayList<>();
        addTurns(byParent, children, first, "A", 1);
        addTurns(byParent, children, second, "B", 2);
        byParent.put("", List.of(span(OPERATION, "r0", "", 0)));
        byParent.put("r0", children);
        return new Trace("0a0b0c0d0e0f00010203040506070809", byParent);
    }

    private static void addTurns(final Map<String, List<Span>> byParent,
                                 final List<Span> into,
                                 final String[] ran,
                                 final String thread,
                                 final int offset) {
        for (int i = 0; i < ran.length; i++) {
            final String id = thread + i;
            final int millisIn = offset + (i * 2);
            into.add(span(TURN, id, "r0", millisIn, thread));
            byParent.put(id, List.of(span(ran[i], id + "c", id, millisIn, thread)));
        }
    }

    private static NodeMutatorImpl mutator() {
        return new NodeMutatorImpl(
                new CanonicalSpanOrder(doc().getTemporalOrderingTolerance()), NO_IGNORED,
                new IgnoredSpans(List.of()));
    }

    private static String shapeOf(final String... childNames) {
        final NodeMutatorImpl mutator = mutator();
        final PathNode root = mutator.process(
                sequential(childNames), new NamePathKey(OPERATION), null, (severity, message) -> {
                }, doc());

        return PathShapeText.of(mutator.getPathShape(), root);
    }

    // Children spaced wider than the ordering tolerance, so the sequence given is the sequence the
    // shape records rather than one settled by name.
    private static Trace sequential(final String... childNames) {
        final Span root = span(OPERATION, "r0", "", 0);
        final List<Span> children = new ArrayList<>(childNames.length);
        for (int i = 0; i < childNames.length; i++) {
            children.add(span(childNames[i], "c" + i, "r0", (i * 3) + 1));
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
                             final int millisIn,
                             final String thread) {
        return span(name, spanId, parentSpanId, millisIn).copy()
                .attributes(List.of(new KeyValue("thread.name", AnyValue.stringValue(thread))))
                .build();
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
}
