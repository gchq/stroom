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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The seed: the draft's own rules turned into proposed event types, the draft's, for the agent to correct.
class TestCatalogueSeed {

    private static final String FW_JSON = """
            {"timestamp":"2026-10-01T09:00:12+10:00","device":"FW1","event_type":"TRAFFIC","src_ip":"192.0.2.10",\
            "dst_ip":"198.51.100.20","dst_port":443,"action":"ALLOW","username":"","message":"allowed"}
            {"timestamp":"2026-10-01T09:01:00+10:00","device":"FW1","event_type":"LOGIN","src_ip":"192.0.2.11",\
            "dst_ip":"198.51.100.1","dst_port":22,"action":"ALLOW","username":"alice","message":"Admin login"}
            """;

    @Test
    void eachRuleTheDraftTellsRecordsApartWithIsAProposedEventTypeOfTheDrafts() {
        final List<EventType> seed = CatalogueSeed.seed(Map.of("fw.jsonl", FW_JSON), null);

        assertThat(seed).extracting(EventType::title).containsExactly("traffic_permitted", "login");
        assertThat(seed).allMatch(type -> EventType.DRAFT.equals(type.basis())
                                          && EventType.PROPOSED.equals(type.status()));
        final EventType traffic = seed.getFirst();
        assertThat(traffic.eventDetail()).isEqualTo("Network/Permit");
        assertThat(traffic.recognisedBy()).hasSize(1);
        assertThat(traffic.recognisedBy().getFirst().when()).containsExactly(
                Condition.fromJson(Map.of("field", "event_type", "equals", "TRAFFIC")),
                Condition.fromJson(Map.of("field", "action", "equals", "ALLOW")));
        final EventType login = seed.get(1);
        assertThat(login.eventDetail()).isEqualTo("Authenticate");
        assertThat(login.action()).isEqualTo("Logon");
        assertThat(login.actor()).isEqualTo("username");
        // Every TypeId Verb-Object, the default where the rule writes none of its own.
        assertThat(seed).allMatch(type -> TypeIds.problem(type.typeId()) == null);
        assertThat(seed).extracting(EventType::typeId).doesNotHaveDuplicates();
        assertThat(login.typeId()).isEqualTo("Authenticate-LOGIN");
    }

    @Test
    void theRuleForTheRestIsNoEventTypeAndTypeIdsAreMadeUnique() {
        final Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("mapping", Map.of("events", List.of(
                rule("a", List.of(Map.of("field", "op", "equals", "x.Save")), "EventDetail/Update/Action", "Modify"),
                rule("b", List.of(Map.of("field", "kind", "equals", "y.Save")), "EventDetail/Update/Action", "Modify"),
                rule("c", List.of(Map.of("field", "kind", "equals", "4624")), "EventDetail/Unknown/Data", null),
                Map.of("name", "other", "fields", List.of(Map.of("path", "EventDetail/Unknown/Data", "data_name", "x",
                        "field", "x"))))));

        final List<EventType> seed = CatalogueSeed.fromDraft(draft);

        assertThat(seed).extracting(EventType::typeId).containsExactly("Update-Save", "Update-Save2",
                "Unknown-C");
        assertThat(seed.get(2).eventDetail()).isNull();
    }

    @Test
    void aDraftOfOneRuleForEveryRecordSeedsASoleEventType() {
        // stroom-ai/DESIGN.md, A sole event type: records all one event type (every record a logon, told by nothing
        // in it) are recognised by every record; seeded so, not left to the catch-all as Unknown.
        final Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("mapping", Map.of("events", List.of(Map.of("name", "logon", "fields", List.of(
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventSource/User/Id", "field", "user"))))));

        final List<EventType> seed = CatalogueSeed.fromDraft(draft);

        assertThat(seed).singleElement().satisfies(type -> {
            assertThat(type.recognisedBy()).isEmpty();
            assertThat(type.eventDetail()).isEqualTo("Authenticate");
            assertThat(type.action()).isEqualTo("Logon");
            assertThat(type.actor()).isEqualTo("user");
            assertThat(type.basis()).isEqualTo(EventType.DRAFT);
            assertThat(TypeIds.problem(type.typeId())).isNull();
        });
        // A draft whose one rule writes Unknown seeds nothing: its records are uncatalogued.
        final Map<String, Object> unknown = new LinkedHashMap<>();
        unknown.put("mapping", Map.of("events", List.of(Map.of("name", "other", "fields", List.of(
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "x", "field", "x"))))));
        assertThat(CatalogueSeed.fromDraft(unknown)).isEmpty();
    }

    private static Map<String, Object> rule(final String name,
                                            final List<Map<String, Object>> when,
                                            final String path,
                                            final String value) {
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", path);
        if (value == null) {
            field.put("data_name", "x");
            field.put("field", "x");
        } else {
            field.put("value", value);
        }
        return Map.of("name", name, "when", when, "fields", List.of(field));
    }
}
