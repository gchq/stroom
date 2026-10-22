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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Stroom MCP server's tests of what the generator does with a draft (`tests/test_draft_reply.py`, and
/// `tests/test_record_shapes.py`'s generated XSLT): the draft as the agent receives it, small enough for the client
/// to show it whole, and read back the same. The drafts are the server's `draft_mapping`'s, kept as fixtures
/// (`drafts.json`) until it is ported.
class TestMappingDrafts extends GenerationFixtures {

    private static final Map<String, Object> DRAFTS = json(resource("drafts.json"));

    @SuppressWarnings("unchecked")
    private static Map<String, Object> draft(final String name) {
        return (Map<String, Object>) DRAFTS.get(name);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRulesDataListReadsBackAsItsDataEntries() {
        final Map<String, Object> draft = draft("fw");
        final Map<String, Object> compact = MappingCompaction.compactRules(draft);
        final List<Map<String, Object>> events = (List<Map<String, Object>>) compact.get("events");
        final Map<String, Object> permitted = events.stream()
                .filter(rule -> "traffic_permitted".equals(rule.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(permitted.get("data")).isEqualTo(List.of("severity", "rule_id", "bytes_sent", "bytes_received"));
        assertThat((List<Map<String, Object>>) permitted.get("fields"))
                .noneSatisfy(field -> assertThat((String) field.get("path")).endsWith("/Data"));
        // Unknown's Data stays as it was: data needs an action element to go under.
        final Map<String, Object> other = events.stream().filter(rule -> "other".equals(rule.get("name")))
                .findFirst().orElseThrow();
        assertThat((List<Object>) other.get("fields")).isNotEmpty();
        assertThat(other.get("data")).isNull();
        // Read back, it is the same mapping: the same XSLT, and a third smaller.
        final XsltGenerator.Generated same = XsltGenerator.generate(TranslationMapping.fromJson(compact), SCHEMA_352,
                "3.5.2");
        assertThat(same.ok()).isTrue();
        assertThat(same.xslt()).isEqualTo(XsltGenerator.generate(TranslationMapping.fromJson(draft), SCHEMA_352,
                "3.5.2").xslt());
        assertThat(PyJson.dumps(compact).length()).isLessThan((int) (0.75 * PyJson.dumps(draft).length()));
        final EventRule rule = TranslationMapping.fromJson(compact).events().getFirst();
        assertThat(rule.data()).isEmpty();
        assertThat(rule.fields()).anySatisfy(field -> {
            assertThat(field.path()).isEqualTo("EventDetail/Network/Permit/Data");
            assertThat(field.dataName()).isEqualTo("rule_id");
        });
    }

    @Test
    void dataWithoutAnActionElementIsRefusedWithWhatToDo() {
        assertThatThrownBy(() -> TranslationMapping.fromJson(json("""
                {"input": "data_splitter", "events": [{"name": "x", "data": ["rule_id"]}]}""")))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("[x] data needs the rule's action element");
    }

    @Test
    void theSameWarningForSeveralRulesIsOne() {
        assertThat(MappingCompaction.grouped(List.of(
                "[a] required ['EventTime', 'EventDetail/TypeId'] are left out when empty.",
                "[b] required ['EventTime', 'EventDetail/Update/After'] are left out when empty.",
                "[c] something else")))
                .containsExactly("[a, b] required ['EventTime', 'EventDetail/TypeId', 'EventDetail/Update/After'] are "
                                 + "left out when empty.", "[c] something else");
    }

    @Test
    void theSessionsDraftFitsTheClientInline() {
        // Copilot saved a 10.6 KB draft to a file; the agent read 100 of its 502 lines and wrote its own mapping.
        final Map<String, Object> mapping = MappingCompaction.compactRules(draft("firewall_logs_20"));
        final XsltGenerator.Generated checked = XsltGenerator.generate(TranslationMapping.fromJson(mapping),
                SCHEMA_352, "3.5.2");
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("ok", checked.ok());
        check.put("problems", MappingCompaction.grouped(checked.problems()));
        final List<String> warnings = MappingCompaction.grouped(checked.warnings());
        check.put("warnings", warnings.subList(0, Math.min(6, warnings.size())));
        final Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("mapping", mapping);
        reply.put("schema_check", check);
        // The draft's notes aren't part of the fixture: the mapping and its check are most of the reply.
        assertThat(PyJson.dumpsCompact(reply).length()).isLessThan(8000);
    }

    @Test
    void allowUnknownOnARuleThatWritesAnActionElementMeansNothing() {
        // Seen: a rule moved from Unknown to Alert kept its allow_unknown; the user was asked to keep it Unknown
        // ("none of the 20 records"), and the documentation listed it under Kept as Unknown.
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "data_splitter", "common": [
                  {"path": "EventTime/TimeCreated", "field": "timestamp"},
                  {"path": "EventSource/System/Name", "value": "FW"},
                  {"path": "EventSource/System/Environment", "value": "Prod"},
                  {"path": "EventSource/Generator", "value": "FW"},
                  {"path": "EventSource/Device/HostName", "field": "device"},
                  {"path": "EventDetail/TypeId", "field": "action"}],
                 "events": [{"name": "system_alert", "when": [{"field": "event_type", "equals": "SYSTEM"}],
                             "allow_unknown": "System events",
                             "fields": [{"path": "EventDetail/Alert/Type", "value": "Other"}]},
                            {"name": "other", "allow_unknown": "anything else",
                             "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "action",
                                         "field": "action"}]}]}"""));
        assertThat(MappingFacts.keptUnknown(mapping)).extracting(EventRule::name).containsExactly("other");
        assertThat(XsltGenerator.generate(mapping, SCHEMA_352, "3.5.2").warnings())
                .anySatisfy(w -> assertThat(w).startsWith("[system_alert] allow_unknown is ignored: the rule writes "
                                                          + "Alert"));
    }

    @Test
    void aConditionGivenWithItsInputInsideTheTestIsSpreadOut() {
        // Seen in a test environment: "matches": {"value": "Event: \[User\]", "xpath": "/Event/EventData/Data"},
        // refused with "Input should be a valid string".
        final Condition c = Condition.fromJson(json("""
                {"matches": {"value": "Event: \\\\[User\\\\]", "xpath": "/Event/EventData/Data"}}"""));
        assertThat(c.matches()).isEqualTo("Event: \\[User\\]");
        assertThat(c.xpath()).isEqualTo("/Event/EventData/Data");
        assertThat(c.field()).isNull();
        assertThat(Condition.fromJson(json("{\"equals\": {\"value\": \"LOGIN\", \"field\": \"action\"}}")).field())
                .isEqualTo("action");
        assertThat(Condition.fromJson(json("{\"one_of\": {\"values\": [\"a\", \"b\"], \"field\": \"f\"}}")).oneOf())
                .containsExactly("a", "b");
    }

    @Test
    void everyShapeIsReadAndJsonHeldInAFieldIsReadInside() {
        // test_record_shapes.py: two JSON structures in one sample, a whole JSON line written into TypeId, and the
        // second shape's events without time or kind. The draft reads the held keys; the XSLT reads them too.
        final TranslationMapping model = TranslationMapping.fromJson(draft("shapes"));
        final XsltGenerator.Generated generated = XsltGenerator.generate(model, SCHEMA_352, "3.5.2");
        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
        // Stroom's format-date isn't available here: one that passes the (ISO) time through stands in for it.
        final StringBuilder standIn = new StringBuilder();
        for (final int n : new int[]{2, 3, 4}) {
            standIn.append("<xsl:function name=\"stroom:format-date\">");
            for (int i = 0; i < n; i++) {
                standIn.append("<xsl:param name=\"p").append(i).append("\"/>");
            }
            standIn.append("<xsl:sequence select=\"string($p0)\"/></xsl:function>");
        }
        final String xslt = generated.xslt().replace("</xsl:stylesheet>", standIn + "</xsl:stylesheet>");
        // the empty one (the last record): no fatal error
        final XmlElement events = XsltRuns.transform(xslt, (String) DRAFTS.get("shapes_records"));
        final List<List<String>> got = XsltRuns.children(events, "Event").stream()
                .map(e -> java.util.Arrays.asList(XsltRuns.text(e, "EventTime/TimeCreated"),
                        XsltRuns.text(e, "EventDetail/TypeId"), XsltRuns.text(e, "EventSource/User/Id")))
                .toList();
        assertThat(got.get(0)).containsExactly("2026-10-06T09:00:00Z", "login", "alice");
        assertThat(got.get(3)).containsExactly("2026-10-06T10:00:00Z", "user_authentication_check", "carol");
        assertThat(got.get(5)).containsExactly("2026-10-06T10:02:00Z", "password_change", "erin");
        assertThat(got).hasSize(7);
    }
}
