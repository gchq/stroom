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
 * Telling a trace that taught the model something from one that merely took a path it already knew.
 *
 * <p>This is what separates the two timestamps on a pathway: last used moves for every trace, while
 * updated moves only when the model itself did. A pathway that is busy but settled shows the first
 * advancing and the second standing still, which is the whole point of keeping both.
 */
class TestModelChangeDetection {

    private static final String OPERATION = "ProcessorTaskCreatorJob.run";
    private static final String PING = "Ping";
    private static final String PREPARE = "Prepare statement";
    private static final long BASE = 1_700_000_000_000_000_000L;

    @Test
    void theFirstTraceTeachesEverything() {
        final NodeMutatorImpl mutator = mutator();
        mutator.process(trace("GET", 5, PING), key(), null, quiet(), doc());

        assertThat(mutator.isChanged())
                .as("nothing was known, so all of it is new")
                .isTrue();
    }

    @Test
    void thatSameTraceAgainTeachesNothing() {
        final PathNode root = apply(null, trace("GET", 5, PING));

        final NodeMutatorImpl mutator = mutator();
        mutator.process(trace("GET", 5, PING), key(), root, quiet(), doc());

        assertThat(mutator.isChanged())
                .as("a path the model already knows, taken in a way it already allows")
                .isFalse();
    }

    @Test
    void aStepNeverSeenBeforeIsAChange() {
        final PathNode root = apply(null, trace("GET", 5, PING));

        final NodeMutatorImpl mutator = mutator();
        mutator.process(trace("GET", 5, PING, PREPARE), key(), root, quiet(), doc());

        assertThat(mutator.isChanged()).isTrue();
    }

    @Test
    void anAttributeValueNeverSeenBeforeIsAChange() {
        final PathNode root = apply(null, trace("GET", 5, PING));

        final NodeMutatorImpl mutator = mutator();
        mutator.process(trace("POST", 5, PING), key(), root, quiet(), doc());

        assertThat(mutator.isChanged())
                .as("the method constraint had to widen to hold both")
                .isTrue();
    }

    @Test
    void aDurationOutsideWhatWasSeenIsAChange() {
        final PathNode root = apply(null, trace("GET", 5, PING));

        final NodeMutatorImpl mutator = mutator();
        mutator.process(trace("GET", 40, PING), key(), root, quiet(), doc());

        assertThat(mutator.isChanged())
                .as("the duration range had to stretch")
                .isTrue();
    }

    @Test
    void aDurationInsideWhatWasSeenIsNot() {
        PathNode root = apply(null, trace("GET", 5, PING));
        root = apply(root, trace("GET", 40, PING));

        final NodeMutatorImpl mutator = mutator();
        mutator.process(trace("GET", 20, PING), key(), root, quiet(), doc());

        assertThat(mutator.isChanged())
                .as("already inside the range the model holds, so nothing moved")
                .isFalse();
    }

    @Test
    void anIgnoredAttributeSettlesAfterTheFirstSight() {
        final IgnoredAttributes ignored = new IgnoredAttributes(List.of("http.method"));
        final PathNode root = new NodeMutatorImpl(spanOrder(), ignored, new IgnoredSpans(List.of()))
                .process(trace("GET", 5, PING), key(), null, quiet(), doc());

        final NodeMutatorImpl mutator = new NodeMutatorImpl(spanOrder(), ignored, new IgnoredSpans(List.of()));
        mutator.process(trace("a-value-never-seen", 5, PING), key(), root, quiet(), doc());

        assertThat(mutator.isChanged())
                .as("an ignored attribute must not keep the model looking like it is still moving")
                .isFalse();
    }

    private static PathNode apply(final PathNode current, final Trace trace) {
        return mutator().process(trace, key(), current, quiet(), doc());
    }

    private static NodeMutatorImpl mutator() {
        return new NodeMutatorImpl(spanOrder(), new IgnoredAttributes(List.of()),
                new IgnoredSpans(List.of()));
    }

    private static CanonicalSpanOrder spanOrder() {
        return new CanonicalSpanOrder(doc().getTemporalOrderingTolerance());
    }

    private static NamePathKey key() {
        return new NamePathKey(OPERATION);
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
}
