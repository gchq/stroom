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

package stroom.mcp.impl.e2e;

import stroom.mcp.impl.McpTestClient;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// classify_kinds in Stroom (work package E8): the plan asks for it before the draft, the agent's proposals are put
/// to the user in one form, what the user confirms and corrects goes into the source notes, and the draft follows
/// them, a rule a kind.
@SuppressWarnings("unchecked")
class TestE2eClassifyKinds extends AbstractE2eTest {

    private static final String BUILD = "e2e-classify";
    private static final String FEED = "E2E-CLASSIFY";
    private static final String PROPOSALS = """
            [{"field": "action", "value": "login", "description": "A user logged on", "event_detail": "Authenticate",
              "action": "Logon", "actor": "user", "confidence": "high"},
             {"field": "action", "value": "logout", "description": "A user logged off",
              "event_detail": "Authenticate", "action": "Logoff", "actor": "user", "confidence": "high"},
             {"field": "action", "value": "export", "description": "A report exported", "event_detail": "Export",
              "confidence": "medium"},
             {"field": "action", "value": "purge", "description": "Records purged", "event_detail": "Delete",
              "confidence": "low"}]""";

    @Test
    void theKindsAreClassifiedConfirmedAndFollowedByTheDraft() {
        createFeed(BUILD, FEED);
        final long stream = upload(FEED, sample());
        final List<Long> raw = List.of(stream);

        // ### Listed: four kinds, kept as still to classify, and the plan's next step until they are.
        final Map<String, Object> listed = json("classify_kinds", args("build", BUILD, "source", "Records app",
                "stream_ids", raw));
        dump("classify_kinds listed", listed);
        assertThat(list(listed, "to_classify")).extracting(kind -> kind.get("value"))
                .containsExactlyInAnyOrder("login", "logout", "export", "purge");
        assertThat(String.valueOf(map(json("build_status", args("build", BUILD)), "next").get("step")))
                .isNotEqualTo("translation");

        // ### Proposed: the high-confidence ones as one table for the chat, which renders Markdown; no form yet.
        final Map<String, Object> shown = json("classify_kinds", args("build", BUILD, "proposals",
                JsonUtil.readValue(PROPOSALS, List.class)));
        dump("classify_kinds shown", shown);
        assertThat(String.valueOf(shown.get("table"))).startsWith("## High-confidence classifications")
                .contains("| 1 |  | A user logged on | Authenticate: Logon |");

        // ### Accepted together in a short form; then the medium-confidence one in detail, numbered from 1.
        client.answeringWith(this::answer);
        final Map<String, Object> high = json("classify_kinds", args("build", BUILD, "confirm", true));
        dump("classify_kinds high", high);
        assertThat(high.get("recorded")).isEqualTo(List.of("action=login", "action=logout"));
        assertThat(String.valueOf(high.get("table"))).startsWith("## Medium-confidence classifications")
                .contains("### 1. A report exported", "```\ntime=2026-11-06T09:", "#### Action element\n\nExport")
                .doesNotContain("Low-confidence");

        // ### The medium one accepted the same way; then the low one, numbered from 1 again.
        final Map<String, Object> medium = json("classify_kinds", args("build", BUILD, "confirm", true));
        dump("classify_kinds medium", medium);
        assertThat(medium.get("recorded")).isEqualTo(List.of("action=export"));
        assertThat(String.valueOf(medium.get("table"))).startsWith("## Low-confidence classifications")
                .contains("### 1. Records purged");

        // ### The low one amended: nothing recorded, the amendment back for the agent.
        final Map<String, Object> answered = json("classify_kinds", args("build", BUILD, "confirm", true));
        dump("classify_kinds answered", answered);
        client.answeringWith(McpTestClient::agree);
        assertThat(answered.get("recorded")).isEqualTo(List.of());
        assertThat(answered.get("amendments")).isEqualTo("purge deletes the audit records: Delete, not Alert");
        final ElicitFormRequest form = client.forms().getLast();
        // Plain text only: the form's fields are shown as they are, so no table in it.
        assertThat(form.message()).isEqualTo("Review the low-confidence classifications shown in the chat.");
        assertThat(JsonUtil.writeValueAsString(form.requestedSchema(), false)).doesNotContain("|");

        // ### The correction sent as the user's: recorded without another form.
        final int forms = client.forms().size();
        final Map<String, Object> corrected = json("classify_kinds", args("build", BUILD, "proposals", List.of(
                Map.of("field", "action", "value", "purge", "description", "Audit records deleted",
                        "event_detail", "Delete", "actor", "user", "affected", "object", "affected_as", "Document",
                        "from_user", true))));
        dump("classify_kinds corrected", corrected);
        assertThat(corrected.get("recorded")).isEqualTo(List.of("action=purge"));
        assertThat(client.forms()).hasSize(forms);
        final Map<String, Object> status = json("build_status", args("build", BUILD, "full", true));
        assertThat(String.valueOf(status.get("steps"))).contains("classify");
        assertThat(String.valueOf(map(status, "next").get("step"))).isNotEqualTo("classify");

        // ### The draft follows them: a rule a kind, with its action element.
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw, "build", BUILD,
                "source_name", "Records app", "system_name", "Records", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        final String rules = JsonUtil.writeValueAsString(map(draft, "mapping").get("events"), false);
        assertThat(rules).contains("EventDetail/Authenticate/Action", "Logon", "Logoff", "A report exported",
                "Audit records deleted");
        // What the purge acts on, where the user said: the Delete's Document, its Id from the object field.
        assertThat(rules).contains("{\"path\":\"EventDetail/Delete/Document/Id\",\"field\":\"object\"}");
        assertThat(strings(draft, "notes")).anyMatch(note -> note.startsWith("Drafted from the source "
                                                                             + "documentation"));
    }

    // ### Records a JSON or XML parser reads, and events inside an inner message: classified, accepted, followed.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"json-nested", "xml-fragments", "json-kv-message", "json-xml-message"})
    void kindsInJsonXmlAndInnerMessagesAreClassifiedAndFollowedByTheDraft(final String shape) {
        final String build = "e2e-classify-" + shape;
        final String feed = "E2E-CLASSIFY-" + shape.toUpperCase();
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, shaped(shape)));

        final Map<String, Object> listed = json("classify_kinds", args("build", build, "source", "Records app",
                "stream_ids", raw));
        dump("classify_kinds listed " + shape, listed);
        final List<Map<String, Object>> kinds = list(listed, "to_classify");
        assertThat(kinds).extracting(kind -> kind.get("value"))
                .containsExactlyInAnyOrder("login", "logout", "export");
        assertThat(kinds).allSatisfy(kind -> assertThat(kind.get("records")).isEqualTo(4));
        final List<String> users = (List<String>) listed.get("user_fields");
        assertThat(users).hasSize(1);
        final String actor = users.getFirst();

        final List<Map<String, Object>> proposals = new ArrayList<>();
        final Map<String, String> details = Map.of("login", "Authenticate", "logout", "Authenticate", "export",
                "Export");
        kinds.forEach(kind -> {
            final String value = String.valueOf(kind.get("value"));
            final Map<String, Object> proposal = new LinkedHashMap<>(Map.of("field", kind.get("field"), "value",
                    value, "description", "Records app " + value, "event_detail", details.get(value), "actor",
                    actor, "confidence", "high"));
            if (!"export".equals(value)) {
                proposal.put("action", "login".equals(value)
                        ? "Logon"
                        : "Logoff");
            }
            proposals.add(proposal);
        });
        json("classify_kinds", args("build", build, "proposals", proposals));
        client.answeringWith(this::answer);
        final Map<String, Object> accepted = json("classify_kinds", args("build", build, "confirm", true));
        client.answeringWith(McpTestClient::agree);
        assertThat((List<String>) accepted.get("recorded")).hasSize(3);

        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw, "build", build,
                "source_name", "Records app", "system_name", "Records", "environment", "Test"));
        dump("draft_translation_mapping " + shape, draft);
        final String rules = JsonUtil.writeValueAsString(map(draft, "mapping").get("events"), false);
        assertThat(rules).contains("Records app login", "Records app logout", "Records app export",
                "EventDetail/Authenticate/Action", "{\"path\":\"EventSource/User/Id\",\"field\":\"" + actor + "\"}");
    }

    // Twelve records, four of each kind, in the shape named.
    private static String shaped(final String shape) {
        final List<String> actions = List.of("login", "logout", "export");
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final String time = "2026-11-06T09:%02d:00Z".formatted(i);
            final String action = actions.get(i % actions.size());
            final String user = "user" + (i % 5);
            lines.add(switch (shape) {
                case "json-nested" -> "{\"ts\": \"" + time + "\", \"event\": {\"type\": \"" + action
                                      + "\"}, \"actor\": {\"user\": \"" + user + "\"}}";
                case "xml-fragments" -> "<Record><Time>" + time + "</Time><Action>" + action + "</Action><User>"
                                        + user + "</User></Record>";
                case "json-kv-message" -> "{\"ts\": \"" + time + "\", \"host\": \"app1\", \"message\": \"action="
                                          + action + " user=" + user + " src=10.0.0.1\"}";
                case "json-xml-message" -> "{\"ts\": \"" + time + "\", \"host\": \"app1\", \"message\": "
                                           + "\"<Event><Action>" + action + "</Action><User>" + user
                                           + "</User></Event>\"}";
                default -> throw new IllegalArgumentException(shape);
            });
        }
        return String.join("\n", lines) + "\n";
    }

    // Each set's one question (accept or amend, not both): the high- and medium-confidence ones accepted with the
    // button, the low one amended in words.
    private ElicitResult answer(final ElicitFormRequest form) {
        final Map<String, Object> fields = (Map<String, Object>) form.requestedSchema().get("properties");
        assertThat(fields).containsOnlyKeys("answer");
        final Map<String, Object> question = (Map<String, Object>) fields.get("answer");
        return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("answer", form.message().contains("low-")
                ? "purge deletes the audit records: Delete, not Alert"
                : ((List<?>) question.get("examples")).getFirst()), null);
    }

    private static String sample() {
        final List<String> lines = new ArrayList<>();
        final List<String> actions = List.of("login", "logout", "export", "purge");
        for (int i = 0; i < 24; i++) {
            lines.add("time=2026-11-06T09:%02d:00Z action=%s user=user%d src=10.0.0.%d object=rec-%d".formatted(i,
                    actions.get(i % actions.size()), i % 5, 1 + i % 9, i));
        }
        return String.join("\n", lines) + "\n";
    }
}
