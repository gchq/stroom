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

        // ### Proposed: a table for the chat, which renders Markdown; no form yet.
        final Map<String, Object> shown = json("classify_kinds", args("build", BUILD, "proposals",
                JsonUtil.readValue(PROPOSALS, List.class)));
        dump("classify_kinds shown", shown);
        assertThat(String.valueOf(shown.get("table"))).contains("### 1. A user logged on", "```\ntime = ")
                .doesNotContain("| Kind |");

        // ### Confirmed in a short form: the sure ones accepted together, the low one rejected, and corrected.
        client.answeringWith(this::answer);
        final Map<String, Object> answered = json("classify_kinds", args("build", BUILD, "confirm", true));
        dump("classify_kinds answered", answered);
        client.answeringWith(McpTestClient::agree);
        assertThat(answered.get("recorded")).isEqualTo(List.of("action=login", "action=logout", "action=export"));
        assertThat(answered.get("rejected")).isEqualTo(List.of("action=purge"));
        assertThat(answered.get("corrections")).isEqualTo("purge deletes the audit records: Delete, not Alert");
        final ElicitFormRequest form = client.forms().getLast();
        // Plain text only: the form's fields are shown as they are, so no table in it.
        assertThat(JsonUtil.writeValueAsString(form.requestedSchema(), false)).contains("4. Records purged (Delete)")
                .doesNotContain("|");
        assertThat(form.message()).doesNotContain("|");

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

    // The block accepted, the low one rejected, and a correction written.
    private ElicitResult answer(final ElicitFormRequest form) {
        final Map<String, Object> content = new LinkedHashMap<>();
        final Map<String, Object> fields = (Map<String, Object>) form.requestedSchema().get("properties");
        fields.forEach((name, field) -> {
            final Map<String, Object> schema = (Map<String, Object>) field;
            if ("boolean".equals(schema.get("type"))) {
                content.put(name, !String.valueOf(schema.get("title")).contains("purge"));
            }
        });
        content.put("text", "purge deletes the audit records: Delete, not Alert");
        return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
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
