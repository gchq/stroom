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

import stroom.ai.impl.tools.indexing.EcsSchema;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.mcp.impl.BundledKnowledge;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexPlans {

    private static final FieldPlan PLAN = FieldPlan.of("elasticsearch", "acme-v1", "@timestamp", List.of(
            PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
            PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
            PlannedField.of("source.ip", "ip",
                    "EventDetail/Network/*/Source/IPAddress | EventSource/Device/IPAddress")));

    @Test
    void coreFieldsMostEventsLeaveEmptyAreNamedWithTheirPaths() {
        // Seen in chat 177: event.action empty in 2,233 of 2,457 documents, and nothing said so.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("event.action", "keyword", "EventDetail/Send/Action"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("acme.ticket", "keyword", "EventDetail/Send/Data"))).withConvention("ecs", List.of());
        final Map<String, Double> populated = Map.of("EventTime/TimeCreated", 100.0, "EventDetail/Send/Action", 9.1,
                "EventSource/User/Id", 80.0, "EventDetail/Send/Data", 2.0);
        final EcsSchema ecs = EcsSchema.from(new BundledKnowledge()).orElseThrow();

        assertThat(IndexPlans.mostlyEmptyCore(plan, populated, ecs))
                .containsExactly("event.action <- EventDetail/Send/Action: in 9.1% of events read");
        // Without a convention, only the time field is core.
        assertThat(IndexPlans.mostlyEmptyCore(plan.withConvention(null, List.of()), populated, ecs)).isEmpty();
    }

    @Test
    void aCoreFieldNoEventReadPopulatesIsNamedToo() {
        // Seen in chat 178: event.action in the plan, written by no event, and nothing said so until a search on it
        // failed (Elasticsearch never mapped it).
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("event.action", "keyword", "EventDetail/*/Action"),
                PlannedField.of("acme.ticket", "keyword", "EventDetail/Send/Data"))).withConvention("ecs", List.of());
        final Map<String, Double> populated = Map.of("EventTime/TimeCreated", 100.0, "EventDetail/TypeId", 100.0);
        final EcsSchema ecs = EcsSchema.from(new BundledKnowledge()).orElseThrow();

        assertThat(IndexPlans.mostlyEmptyCore(plan, populated, ecs))
                .containsExactly("event.action <- EventDetail/*/Action: in no event read");
        // Read from the TypeId where an event has no Action: every event has it.
        final FieldPlan fallback = FieldPlan.of("elasticsearch", "acme", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("event.action", "keyword", FieldPlans.orElse("EventDetail/*/Action",
                        "EventDetail/TypeId")))).withConvention("ecs", List.of());
        assertThat(IndexPlans.mostlyEmptyCore(fallback, populated, ecs)).isEmpty();
    }

    @Test
    void aDraftedPlansFieldsAreShownToTheUserAsATable() {
        // Seen in testing: the agent asked the user to approve an index of fields it had never shown them.
        final Map<String, Object> drafted = drafted();

        final Map<String, Object> shown = IndexPlans.withShownPlan(drafted, true);

        assertThat(shown.keySet()).startsWith("status", "table");
        assertThat(shown).containsEntry("status", "needs_showing").containsEntry("plan", PLAN.toJson());
        assertThat(String.valueOf(shown.get("table")))
                .startsWith("## Index fields planned for 'acme-v1'\n\n| # | Field | Type | Source path |")
                .contains("| 2 | `user.name` | keyword | `EventSource/User/Id` |")
                // A union's pipes don't split the cell.
                .contains("| 3 | `source.ip` | ip | `EventDetail/Network/*/Source/IPAddress \\| "
                          + "EventSource/Device/IPAddress` |");
        assertThat(String.valueOf(shown.get("hint"))).startsWith("The chat shows the user the plan's fields")
                .endsWith("Review unmapped paths.");
    }

    @Test
    void anotherClientsAgentIsToldToShowTheTable() {
        final Map<String, Object> shown = IndexPlans.withShownPlan(drafted(), false);

        assertThat(shown).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(shown.get("hint"))).startsWith("Show the user the plan's fields (table) in your "
                                                                 + "reply");
    }

    @Test
    void aResultWithoutAPlanOrWithAStatusOfItsOwnIsLeftAsItIs() {
        final Map<String, Object> asking = new LinkedHashMap<>(Map.of("status", "needs_confirmation",
                "plan", PLAN.toJson()));
        assertThat(IndexPlans.withShownPlan(asking, true)).isSameAs(asking);
        final Map<String, Object> options = new LinkedHashMap<>(Map.of("drafted", false));
        assertThat(IndexPlans.withShownPlan(options, true)).isSameAs(options);
        final Map<String, Object> unreadable = new LinkedHashMap<>(Map.of("plan", Map.of("fields", "none")));
        assertThat(IndexPlans.withShownPlan(unreadable, true)).isSameAs(unreadable);
    }

    private static Map<String, Object> drafted() {
        final Map<String, Object> drafted = new LinkedHashMap<>();
        drafted.put("plan", PLAN.toJson());
        drafted.put("problems", List.of());
        drafted.put("hint", "Review unmapped paths.");
        return drafted;
    }
}
