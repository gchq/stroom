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
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.planb.impl.dao.trace.CanonicalSpanOrder;
import stroom.planb.impl.dao.trace.IgnoredSpans;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A span that carries the same attribute key twice.
 *
 * <p>OTLP's {@code Span.attributes} is a repeated field and nothing on the way in deduplicates it —
 * {@code SpanHandler} appends each key/value to a plain list — so a producer that emits a key twice
 * gets it stored twice. Both places that turn those attributes into a map have to survive that: one
 * builds the learnt model, the other answers a pathway-filtered search, and a throw from either loses
 * far more than the one span it came from.
 */
class TestRepeatedSpanAttributes {

    /** Nothing ignored, which is the default. */
    private static final IgnoredAttributes NO_IGNORED = new IgnoredAttributes(List.of());

    private static final String OPERATION = "GET /orders";
    private static final long BASE = 1_700_000_000_000_000_000L;

    @Test
    void theModelTakesTheLastValueRatherThanThrowing() {
        final PathwaysDoc doc = doc();
        final NodeMutatorImpl mutator = new NodeMutatorImpl(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()), NO_IGNORED,
                new IgnoredSpans(List.of()));
        final PathKey pathKey = new NamePathKey(OPERATION);

        final PathNode[] root = new PathNode[1];
        assertThatCode(() -> root[0] = mutator.process(
                traceWithRepeatedAttribute(), pathKey, null, (severity, message) -> {
                }, doc))
                .as("a repeated key is legal on the wire, so it must not cost the whole trace")
                .doesNotThrowAnyException();

        assertThat(root[0].getConstraints())
                .as("the key is kept once")
                .containsKey("attribute.http.method");
    }

    @Test
    void aPathwaySearchSurvivesIt() {
        final PathwaysDoc doc = doc();
        final PathNode root = new NodeMutatorImpl(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()), NO_IGNORED,
                new IgnoredSpans(List.of()))
                .process(traceWithRepeatedAttribute(), new NamePathKey(OPERATION), null,
                        (severity, message) -> {
                        }, doc);

        final TracePredicate predicate = new TracePredicate(
                new CanonicalSpanOrder(doc.getTemporalOrderingTolerance()),
                new IgnoredSpans(List.of()),
                new PathKeyFactoryImpl(),
                Map.of(new NamePathKey(OPERATION), root));

        assertThatCode(() -> predicate.test(traceWithRepeatedAttribute()))
                .as("one malformed span must not fail a search across every trace")
                .doesNotThrowAnyException();
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

    // One root span carrying http.method twice, which the OTel SDKs resolve as last-one-wins.
    private static Trace traceWithRepeatedAttribute() {
        final Span root = Span.builder()
                .name(OPERATION)
                .spanId("r0")
                .parentSpanId("")
                .kind(SpanKind.SPAN_KIND_SERVER)
                .startTimeUnixNano(Long.toString(BASE))
                .endTimeUnixNano(Long.toString(BASE + NanoDuration.ofMillis(5).getNanos()))
                .attributes(List.of(
                        attribute("http.method", "GET"),
                        attribute("http.method", "POST")))
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
