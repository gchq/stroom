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
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.StringValue;
import stroom.planb.impl.dao.trace.CanonicalSpanOrder;
import stroom.planb.impl.dao.trace.IgnoredSpans;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Attributes the configuration says to leave alone.
 *
 * <p>Some attributes carry a different value on every span — a thread name, say. Learning them fills
 * the model with values that say nothing about the path and never settle. Naming them in
 * {@link PathwaysDoc#getIgnoredAttributes()} records each one once as accepting anything, so it
 * still shows against the node but stops driving the model.
 *
 * <p>The same names the built-in constraints a span is measured by — {@code duration}, {@code kind},
 * {@code flags} — which are taken from the span itself rather than carried as attributes. A duration
 * is the one that never settles: it widens on every outlier until it admits everything anyway.
 */
class TestIgnoredAttributes {

    private static final String OPERATION = "ProcessorTaskCreatorJob.run";
    private static final long BASE = 1_700_000_000_000_000_000L;

    @Test
    void wildcardsMatchTheWholeName() {
        final IgnoredAttributes ignored = new IgnoredAttributes(List.of("thread.*", "db.operation"));

        assertThat(ignored.test("thread.name")).isTrue();
        assertThat(ignored.test("thread.id")).isTrue();
        assertThat(ignored.test("db.operation")).isTrue();
        assertThat(ignored.test("db.operation.name"))
                .as("the pattern covers the whole name, so it is not a prefix match")
                .isFalse();
        assertThat(ignored.test("http.method")).isFalse();
        assertThat(ignored.test("my.thread.name"))
                .as("thread.* anchors at the start")
                .isFalse();
    }

    @Test
    void nothingIsIgnoredByDefault() {
        assertThat(new IgnoredAttributes(PathwaysDoc.builder().uuid("test").name("test").build().getIgnoredAttributes())
                .test("thread.name")).isFalse();
    }

    @Test
    void anIgnoredAttributeIsRecordedAsAcceptingAnything() {
        final PathNode root = learn(null, List.of("thread.name"), trace("pool-1-thread-4", "GET"));

        assertThat(root.getConstraints().get("attribute.thread.name").getValue())
                .as("it stays visible against the node, constraining nothing")
                .isInstanceOf(AnyTypeValue.class);
        assertThat(root.getConstraints().get("attribute.http.method").getValue())
                .as("an attribute not named is learnt as usual")
                .isEqualTo(new StringValue("GET"));
    }

    @Test
    void aDifferentValueDoesNotMoveIt() {
        PathNode root = learn(null, List.of("thread.*"), trace("pool-1-thread-4", "GET"));
        root = learn(root, List.of("thread.*"), trace("pool-1-thread-9", "GET"));
        root = learn(root, List.of("thread.*"), trace("pool-2-thread-1", "GET"));

        assertThat(root.getConstraints().get("attribute.thread.name").getValue())
                .as("three thread names, and the model has not moved")
                .isInstanceOf(AnyTypeValue.class);
    }

    @Test
    void aTraceStillMatchesWhateverTheIgnoredValueIs() {
        final PathwaysDoc doc = doc();
        final PathNode root = learn(null, List.of("thread.*"), trace("pool-1-thread-4", "GET"));

        final TracePredicate predicate = new TracePredicate(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()),
                new IgnoredSpans(List.of()),
                new PathKeyFactoryImpl(),
                Map.of(new NamePathKey(OPERATION), root));

        assertThat(predicate.test(trace("a-thread-never-seen-before", "GET")))
                .as("an ignored attribute must not decide whether a trace took this path")
                .isTrue();
    }

    @Test
    void aBuiltInCanBeIgnoredToo() {
        PathNode root = learn(null, List.of("duration"), trace("t", "GET", NanoDuration.ofMillis(5)));
        root = learn(root, List.of("duration"), trace("t", "GET", NanoDuration.ofMillis(500)));

        assertThat(root.getConstraints().get("duration").getValue())
                .as("duration is taken from the span rather than carried as an attribute, and can be"
                    + " named just the same")
                .isInstanceOf(AnyTypeValue.class);
        assertThat(root.getConstraints().get("kind").getValue())
                .as("a built-in not named is learnt as usual")
                .isEqualTo(new StringValue("SPAN_KIND_INTERNAL"));
    }

    @Test
    void aDurationNotNamedStillWidens() {
        PathNode root = learn(null, List.of(), trace("t", "GET", NanoDuration.ofMillis(5)));
        root = learn(root, List.of(), trace("t", "GET", NanoDuration.ofMillis(500)));

        assertThat(root.getConstraints().get("duration").getValue())
                .as("naming nothing leaves the range to widen as it always has")
                .isInstanceOf(NanoTimeRange.class);
    }

    @Test
    void namingOneEndLeavesItOpen() {
        PathNode root = learn(null, List.of("duration.max"), trace("t", "GET", NanoDuration.ofMillis(5)));
        root = learn(root, List.of("duration.max"), trace("t", "GET", NanoDuration.ofMillis(500)));
        root = learn(root, List.of("duration.max"), trace("t", "GET", NanoDuration.ofMillis(1)));

        final NanoTimeRange duration = (NanoTimeRange) root.getConstraints().get("duration").getValue();
        assertThat(duration.getMax())
                .as("the slowest run is an outlier the model was told not to learn")
                .isNull();
        assertThat(duration.getMin())
                .as("the fastest run still narrows the bottom")
                .isEqualTo(NanoTime.ofNanos(NanoDuration.ofMillis(1).getNanos()));
    }

    @Test
    void namingTheOtherEndLeavesThatOneOpen() {
        PathNode root = learn(null, List.of("duration.min"), trace("t", "GET", NanoDuration.ofMillis(5)));
        root = learn(root, List.of("duration.min"), trace("t", "GET", NanoDuration.ofMillis(500)));

        final NanoTimeRange duration = (NanoTimeRange) root.getConstraints().get("duration").getValue();
        assertThat(duration.getMin()).isNull();
        assertThat(duration.getMax()).isEqualTo(NanoTime.ofNanos(NanoDuration.ofMillis(500).getNanos()));
    }

    @Test
    void namingBothEndsIsTheSameAsNamingTheConstraint() {
        final PathNode root = learn(null, List.of("duration.min", "duration.max"),
                trace("t", "GET", NanoDuration.ofMillis(5)));

        assertThat(root.getConstraints().get("duration").getValue())
                .as("neither end learnt asserts nothing, which is what ignoring it outright means")
                .isInstanceOf(AnyTypeValue.class);
    }

    @Test
    void anOpenEndedDurationMatchesAnything() {
        final PathwaysDoc doc = doc();
        final PathNode root = learn(null, List.of("duration.max"), trace("t", "GET", NanoDuration.ofMillis(5)));

        final TracePredicate predicate = new TracePredicate(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()),
                new IgnoredSpans(List.of()),
                new PathKeyFactoryImpl(),
                Map.of(new NamePathKey(OPERATION), root));

        assertThat(predicate.test(trace("t", "GET", NanoDuration.ofSeconds(30))))
                .as("nothing is asserted above the bottom, however slow the span was")
                .isTrue();
        assertThat(predicate.test(trace("t", "GET", NanoDuration.ofMillis(1))))
                .as("the bottom is still asserted")
                .isFalse();
    }

    private static PathNode learn(final PathNode current,
                                  final List<String> ignored,
                                  final Trace trace) {
        final PathwaysDoc doc = doc();
        return new NodeMutatorImpl(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()),
                new IgnoredAttributes(ignored),
                new IgnoredSpans(List.of()))
                .process(trace, new NamePathKey(OPERATION), current, (severity, message) -> {
                }, doc);
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

    private static Trace trace(final String threadName, final String method) {
        return trace(threadName, method, NanoDuration.ofMillis(5));
    }

    private static Trace trace(final String threadName, final String method, final NanoDuration duration) {
        final Span root = Span.builder()
                .name(OPERATION)
                .spanId("r0")
                .parentSpanId("")
                .kind(SpanKind.SPAN_KIND_INTERNAL)
                .startTimeUnixNano(Long.toString(BASE))
                .endTimeUnixNano(Long.toString(BASE + duration.getNanos()))
                .attributes(List.of(
                        attribute("thread.name", threadName),
                        attribute("http.method", method)))
                .build();
        final Map<String, List<Span>> byParent = new HashMap<>();
        byParent.put("", List.of(root));
        return new Trace("0a0b0c0d0e0f00010203040506070809", byParent);
    }

    private static KeyValue attribute(final String key, final String value) {
        return KeyValue.builder()
                .key(key)
                .value(new AnyValue(value, null, null, null, null, null, null))
                .build();
    }
}
