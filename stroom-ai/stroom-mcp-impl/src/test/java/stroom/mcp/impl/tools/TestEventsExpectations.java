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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.Fetched;
import stroom.ai.impl.tools.StreamReader.Records;
import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.mcp.impl.BundledKnowledge;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

/// What verify_index's searches must find, counted from the Events streams rather than asserted.
class TestEventsExpectations {

    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final EventsExpectations expectations = new EventsExpectations(streamReader);

    private static String event(final String user, final String action) {
        return "<Events xmlns=\"event-logging:3\"><Event><EventTime><TimeCreated>2026-11-10T00:00:00.000Z"
               + "</TimeCreated></EventTime><EventSource><User><Id>" + user + "</Id></User></EventSource>"
               + "<EventDetail><Send>" + (action == null
                ? ""
                : "<Action>" + action + "</Action>") + "</Send></EventDetail></Event></Events>";
    }

    private static FieldPlan plan() {
        return FieldPlan.of("elasticsearch", "acme", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("event.action", "keyword", "EventDetail/*/Action"),
                PlannedField.of("bytes", "long", "EventDetail/*/Bytes"))).withConvention("ecs", List.of());
    }

    private void stream(final long streamId, final long total, final List<String> records) {
        Mockito.when(streamReader.readRecords(eq(streamId), anyLong(), anyInt(), Mockito.isNull(), anyInt()))
                .thenReturn(new Records(records, new Fetched("ACME", "Events", records.getFirst(), true, total,
                        List.of(), List.of())));
    }

    @Test
    void anExactCountIsTheEventsOwnNotTheAgents() {
        // Seen in chat 177: the agent's asserted counts were wrong four times, then it took every expected count
        // from the index itself.
        stream(6L, 3L, List.of(event("alice", "Send"), event("bob", "Send"), event("alice", null)));

        final EventsExpectations.Sample sample = expectations.read(plan(), List.of(6L));

        assertThat(sample.indexed()).isEqualTo(3);
        assertThat(sample.expect("user.name", "EQUALS", "alice").count()).isEqualTo(2L);
        assertThat(sample.expect("user.name", "EQUALS", "al*").count()).isEqualTo(2L);
        // Elasticsearch's keyword compares by case.
        assertThat(sample.expect("user.name", "EQUALS", "ALICE").count()).isEqualTo(0L);
        assertThat(sample.expect("user.name", "NOT_EQUALS", "alice").count()).isEqualTo(1L);
        assertThat(sample.expect("user.name", "IN", "alice, bob").count()).isEqualTo(3L);
        assertThat(sample.expect("user.name", "EQUALS", "alice").source()).isEqualTo("EventSource/User/Id");
        assertThat(sample.said()).isEqualTo("stream 6 (3 events indexed)");
    }

    @Test
    void aFieldTheEventsCannotCountSaysWhy() {
        stream(6L, 1L, List.of(event("alice", "Send")));

        final EventsExpectations.Sample sample = expectations.read(plan(), List.of(6L));

        assertThat(sample.expect("@timestamp", "EQUALS", "x").why()).contains("is a date the index converts");
        assertThat(sample.expect("user.name", "CONTAINS", "ali").why()).contains("CONTAINS isn't counted");
        assertThat(sample.expect("user.name", "GREATER_THAN", "a").why()).contains("on a keyword field");
        assertThat(sample.expect("message", "EQUALS", "x").count()).isNull();
    }

    @Test
    void aLongStreamIsCountedByItsFirstEventsAndSearchedByTheirEventIds() {
        final List<String> first = new ArrayList<>();
        for (int i = 0; i < EventsExpectations.PER_STREAM; i++) {
            first.add(event("u" + i, "Send"));
        }
        stream(7L, 50_000L, first);
        stream(8L, 2L, List.of(event("a", "Send"), event("b", "Send")));

        final EventsExpectations.Sample sample = expectations.read(plan(), List.of(7L, 8L));

        assertThat(sample.said()).isEqualTo("stream 7's events 1 to 1,000 and stream 8 (1,002 events indexed)");
        assertThat(sample.scope().toString()).contains("StreamId = 7").contains("EventId <= 1000")
                .contains("StreamId = 8");
    }

    @Test
    void numbersAreComparedAsNumbers() {
        assertThat(EventsExpectations.matches("EQUALS", "10", List.of("10.0"), "long", true)).isTrue();
        assertThat(EventsExpectations.matches("BETWEEN", "5,10", List.of("7"), "long", true)).isTrue();
        assertThat(EventsExpectations.matches("GREATER_THAN", "10", List.of("7"), "double", true)).isFalse();
        assertThat(EventsExpectations.matches("EQUALS", "1*", List.of("10"), "long", true)).isNull();
        // The Lucene fields the tools make don't compare by case.
        assertThat(EventsExpectations.matches("EQUALS", "ALICE", List.of("alice"), "keyword", false)).isTrue();
        assertThat(EventsExpectations.matches("EQUALS", "*", List.of(), "keyword", true)).isFalse();
    }

    @Test
    void coreFieldsMostEventsLeaveEmptyAreFound() {
        // Seen in chat 177: event.action empty in 2,233 of 2,457 documents, and nothing said so.
        stream(6L, 4L, List.of(event("a", "Send"), event("b", null), event("c", null), event("d", null)));
        final EcsSchema ecs = EcsSchema.from(new BundledKnowledge()).orElseThrow();

        final List<Map<String, Object>> empty = expectations.read(plan(), List.of(6L)).mostlyEmpty(ecs);

        assertThat(empty).containsExactly(Map.of("field", "event.action", "source", "EventDetail/*/Action",
                "empty_in", "3 of 4 events read"));
    }

    @Test
    void anEmptyTimeFieldIsRequired() {
        stream(6L, 2L, List.of("<Events xmlns=\"event-logging:3\"><Event><EventSource><User><Id>a</Id></User>"
                               + "</EventSource><EventDetail><Send><Action>Send</Action></Send></EventDetail>"
                               + "</Event></Events>", event("b", "Send").replace("<EventTime><TimeCreated>"
                                                                                 + "2026-11-10T00:00:00.000Z"
                                                                                 + "</TimeCreated></EventTime>",
                "")));

        final List<Map<String, Object>> empty = expectations.read(plan(), List.of(6L)).mostlyEmpty(null);

        assertThat(empty).singleElement().satisfies(finding -> {
            assertThat(finding.get("field")).isEqualTo("@timestamp");
            assertThat(finding.get("required")).isEqualTo(true);
        });
    }
}
