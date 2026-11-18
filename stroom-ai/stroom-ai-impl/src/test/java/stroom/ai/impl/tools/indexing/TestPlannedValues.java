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

package stroom.ai.impl.tools.indexing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The values an index plan's XSLT writes, read from the Events themselves.
class TestPlannedValues {

    private static final String NS = "event-logging:3";

    private static String event(final String user, final String action, final String outcome) {
        return "<Events xmlns=\"" + NS + "\"><Event><EventSource><User><Id>" + user + "</Id></User></EventSource>"
               + "<EventDetail><TypeId>" + action + "</TypeId><Authenticate><Action>" + action + "</Action>"
               + (outcome == null
                ? ""
                : "<Outcome><Success>" + outcome + "</Success></Outcome>")
               + "</Authenticate></EventDetail></Event></Events>";
    }

    private static FieldPlan plan(final PlannedField... fields) {
        return FieldPlan.of("elasticsearch", "acme", "@timestamp", Arrays.asList(fields));
    }

    @Test
    void eachEventsValuesAreReadByTheFieldsSourcePath() {
        final PlannedValues values = PlannedValues.of(plan(PlannedField.of("user.name", "keyword",
                "EventSource/User/Id")), List.of(event("alice", "Logon", null), event("bob", "Logon", null)));

        final PlannedValues.Written written = values.written("user.name");

        assertThat(written.known()).isTrue();
        assertThat(written.source()).isEqualTo("EventSource/User/Id");
        assertThat(written.perEvent()).containsExactly(List.of("alice"), List.of("bob"));
        assertThat(values.eventsPerRecord()).containsExactly(1, 1);
    }

    @Test
    void dropWhenEventsAreNotIndexed() {
        final FieldPlan plan = new FieldPlan("elasticsearch", "acme", "@timestamp", List.of(PlannedField.of(
                "user.name", "keyword", "EventSource/User/Id")), List.of("EventDetail/TypeId = 'Heartbeat'"),
                null, List.of(), List.of(), true, null, List.of(), null, Map.of());

        final PlannedValues values = PlannedValues.of(plan, List.of(event("alice", "Logon", null),
                event("sys", "Heartbeat", null)));

        assertThat(values.indexed()).isEqualTo(1);
        assertThat(values.dropped()).isEqualTo(1);
        assertThat(values.written("user.name").perEvent()).containsExactly(List.of("alice"));
    }

    @Test
    void theOutcomeTransformAndStandInsAreAppliedAsTheXsltDoes() {
        final FieldPlan plan = plan(new PlannedField("event.outcome", "keyword",
                        "EventDetail/Authenticate/Outcome/Success", "", "outcome", false),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"))
                .withStandIns(Map.of("-", Discovery.LEAVE_OUT));

        final PlannedValues values = PlannedValues.of(plan, List.of(event("-", "Logon", "false"),
                event("bob", "Logon", "true"), event("carol", "Logon", null)));

        assertThat(values.written("event.outcome").perEvent())
                .containsExactly(List.of("failure"), List.of("success"), List.of());
        // The stand-in for none is left out, as the XSLT's predicate leaves it out.
        assertThat(values.written("user.name").perEvent()).containsExactly(List.of(), List.of("bob"),
                List.of("carol"));
    }

    @Test
    void aFieldTheEventsDoNotGiveSaysWhy() {
        final PlannedValues values = PlannedValues.of(plan(PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("message", "text", "EventDetail/Description"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated")), List.of(event("a", "Logon", null)));

        assertThat(values.written("StreamId").unknown()).contains("is written by Stroom");
        assertThat(values.written("message").unknown()).contains("is analysed text");
        assertThat(values.written("@timestamp").unknown()).contains("is a date the index converts");
        assertThat(values.written("host.name").unknown()).contains("isn't a field of the plan");
        // Whether an event has a value at all is read for a date too.
        assertThat(values.present("@timestamp").known()).isTrue();
        assertThat(values.present("@timestamp").perEvent()).containsExactly(List.of());
    }

    @Test
    void severalValuesAreEachAValueAndOneValueFieldJoinsThem() {
        final String twoUsers = "<Events xmlns=\"" + NS + "\"><Event><EventSource><User><Id>a</Id></User>"
                                + "<User><Id>b</Id></User></EventSource></Event></Events>";
        final PlannedValues values = PlannedValues.of(plan(
                new PlannedField("related.user", "keyword", "EventSource/User/Id", "", null, true),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id")), List.of(twoUsers));

        assertThat(values.written("related.user").perEvent()).containsExactly(List.of("a", "b"));
        assertThat(values.written("user.name").perEvent()).containsExactly(List.of("a b"));
    }

    @Test
    void eventsInAnotherNamespaceAreReadInTheirOwn() {
        final String forked = "<Events xmlns=\"acme-events:1\"><Event><EventSource><User><Id>alice</Id></User>"
                              + "</EventSource></Event></Events>";
        final PlannedValues values = PlannedValues.of(plan(PlannedField.of("user.name", "keyword",
                "EventSource/User/Id")), List.of(forked, "not xml"));

        assertThat(values.written("user.name").perEvent()).containsExactly(List.of("alice"));
        assertThat(values.eventsPerRecord()).containsExactly(1, 0);
    }
}
