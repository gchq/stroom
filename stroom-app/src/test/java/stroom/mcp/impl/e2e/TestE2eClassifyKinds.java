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
                .contains("### 1. A report exported", "```\ntime=2026-11-06T09:", "| Schema action | Export |")
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
                    actor, "confidence", "export".equals(value)
                            ? "medium"
                            : "high"));
            if (!"export".equals(value)) {
                proposal.put("action", "login".equals(value)
                        ? "Logon"
                        : "Logoff");
            }
            proposals.add(proposal);
        });
        json("classify_kinds", args("build", build, "proposals", proposals));
        client.answeringWith(this::answer);
        final Map<String, Object> high = json("classify_kinds", args("build", build, "confirm", true));
        assertThat((List<String>) high.get("recorded")).hasSize(2);
        // The medium one's record as the stream has it, whole: its own line, not its fields.
        assertThat(String.valueOf(high.get("table"))).as(shape)
                .contains("```\n" + ("xml-fragments".equals(shape)
                        ? "<Record><Time>2026-11-06T09:"
                        : "{\"ts\": \"2026-11-06T09:"))
                .contains("export");
        final Map<String, Object> medium = json("classify_kinds", args("build", build, "confirm", true));
        client.answeringWith(McpTestClient::agree);
        assertThat((List<String>) medium.get("recorded")).hasSize(1);

        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw, "build", build,
                "source_name", "Records app", "system_name", "Records", "environment", "Test"));
        dump("draft_translation_mapping " + shape, draft);
        final String rules = JsonUtil.writeValueAsString(map(draft, "mapping").get("events"), false);
        assertThat(rules).contains("Records app login", "Records app logout", "Records app export",
                "EventDetail/Authenticate/Action", "{\"path\":\"EventSource/User/Id\",\"field\":\"" + actor + "\"}");
    }

    // ### Chat 156: a notes doc named for the build already, then classify_kinds given the source's name. Its
    // proposals went to a second doc, named for the source, and the confirmation read the first: no form came.
    @Test
    void theBuildKeepsOneNotesDocWhateverTheSourceIsCalled() {
        final String build = "e2e-classify-notes";
        createFeed(build, "E2E-CLASSIFY-NOTES");
        final List<Long> raw = List.of(upload("E2E-CLASSIFY-NOTES", sample()));
        agreed("record_source_notes", args("build", build, "source", build, "summary", "A records app's audit log.",
                "fields", List.of(), "events", List.of()));

        json("classify_kinds", args("build", build, "source", "Records app", "stream_ids", raw));
        final Map<String, Object> shown = json("classify_kinds", args("build", build, "source", "Records app",
                "proposals", JsonUtil.readValue(PROPOSALS, List.class)));
        final String notes = String.valueOf(map(shown, "source_notes").get("uuid"));
        assertThat(map(shown, "source_notes").get("name")).isEqualTo(build + " source notes");
        client.answeringWith(this::answer);
        final int forms = client.forms().size();
        final Map<String, Object> high = json("classify_kinds", args("build", build, "confirm", true));
        client.answeringWith(McpTestClient::agree);
        assertThat(client.forms()).hasSize(forms + 1);
        assertThat(high.get("recorded")).isEqualTo(List.of("action=login", "action=logout"));
        final String folder = folderOf(notes);
        assertThat(documentsIn(folder, "Documentation", build + " source notes")).hasSize(1);
        assertThat(documentsIn(folder, "Documentation", "Records app source notes")).isEmpty();
    }

    // ### An amended set: nothing recorded; the agent sends it again as the user said, leaving out the one they
    // rejected (to ask what it is), which isn't put to them again and stays still to classify.
    @Test
    void anAmendedSetLeavesOutWhatTheUserRejected() {
        final String build = "e2e-classify-amend";
        createFeed(build, "E2E-CLASSIFY-AMEND");
        final List<Long> raw = List.of(upload("E2E-CLASSIFY-AMEND", sample()));
        json("classify_kinds", args("build", build, "source", "Records app", "stream_ids", raw));
        json("classify_kinds", args("build", build, "proposals", JsonUtil.readValue(PROPOSALS, List.class)));
        client.answeringWith(form -> new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("answer", "2 isn't a logoff: ask me what it is"), null));
        final Map<String, Object> amended = json("classify_kinds", args("build", build, "confirm", true));
        client.answeringWith(McpTestClient::agree);
        assertThat(amended.get("recorded")).isEqualTo(List.of());
        assertThat(amended.get("amendments")).isEqualTo("2 isn't a logoff: ask me what it is");

        // The agent sends the first again as the user's, leaving out the second: the next set is shown.
        final Map<String, Object> sent = json("classify_kinds", args("build", build, "proposals", List.of(
                Map.of("field", "action", "value", "login", "description", "A user logged on", "event_detail",
                        "Authenticate", "action", "Logon", "from_user", true))));
        assertThat(sent.get("recorded")).isEqualTo(List.of("action=login"));
        assertThat(String.valueOf(sent.get("table"))).startsWith("## Medium-confidence classifications (1 of 1)")
                .doesNotContain("logged off");
        client.answeringWith(TestE2eClassifyKinds::acceptingAll);
        json("classify_kinds", args("build", build, "confirm", true));
        json("classify_kinds", args("build", build, "confirm", true));
        client.answeringWith(McpTestClient::agree);
        // Only the one left out still to classify: listed again for the agent to ask about.
        final Map<String, Object> again = json("classify_kinds", args("build", build, "stream_ids", raw));
        assertThat(list(again, "to_classify")).extracting(kind -> kind.get("value")).containsExactly("logout");
    }

    // ### confirm=true sent with the proposals: no form until the user has seen the table.
    @Test
    void confirmWithTheProposalsWaitsForTheTable() {
        final String build = "e2e-classify-early";
        createFeed(build, "E2E-CLASSIFY-EARLY");
        final List<Long> raw = List.of(upload("E2E-CLASSIFY-EARLY", sample()));
        json("classify_kinds", args("build", build, "source", "Records app", "stream_ids", raw));
        final int forms = client.forms().size();
        final Map<String, Object> shown = json("classify_kinds", args("build", build, "confirm", true,
                "proposals", JsonUtil.readValue(PROPOSALS, List.class)));
        assertThat(String.valueOf(shown.get("hint"))).startsWith("No form yet: confirm=true is not taken with "
                                                                 + "proposals.");
        assertThat(String.valueOf(shown.get("table"))).startsWith("## High-confidence classifications");
        assertThat(client.forms()).hasSize(forms);
    }

    // ### Chat 160: a catch-all format (records told apart by their format alone) proposed as Unknown, so the events
    // in it were never put to the user. Refused; proposed as what it is, it shows its own line as logged.
    @Test
    void aCatchAllIsNotProposedAsUnknown() {
        final String build = "e2e-classify-catchall";
        createFeed(build, "E2E-CLASSIFY-CATCHALL");
        final List<Long> raw = List.of(upload("E2E-CLASSIFY-CATCHALL", sample() + String.join("\n", List.of(
                "Nov  6 09:30:00 web1 cache warmed after 12 seconds",
                "Nov  6 09:30:05 web2 config reloaded from disk",
                "Nov  6 09:30:09 web1 disk 80% full on the data volume",
                "Nov  6 09:30:13 web3 worker restarted by the supervisor")) + "\n"));
        final Map<String, Object> splitter = JsonUtil.readValue("""
                {"kind": "alternatives", "parts": [{"kind": "syslog", "variant": "syslog"},
                 {"kind": "key_value", "delimiter": " ", "variant": "key_value"}]}""", Map.class);
        final Map<String, Object> listed = json("classify_kinds", args("build", build, "source", "Records app",
                "stream_ids", raw, "splitter", splitter));
        dump("classify_kinds listed catch-all", listed);
        final Map<String, Object> catchAll = list(listed, "to_classify").stream()
                .filter(kind -> Boolean.TRUE.equals(kind.get("catch_all")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No catch-all listed: " + listed));
        assertThat(catchAll).containsEntry("value", "syslog");
        assertThat(String.valueOf(listed.get("hint"))).contains("refine the splitter (build_data_splitter)");

        assertThat(refusal("classify_kinds", args("build", build, "proposals", List.of(Map.of("field", "variant",
                "value", "syslog", "description", "Web host lines", "event_detail", "Unknown", "confidence",
                "medium")))))
                .contains("variant=syslog is a catch_all kind", "so not Unknown");
        final Map<String, Object> shown = json("classify_kinds", args("build", build, "proposals", List.of(
                Map.of("field", "variant", "value", "syslog", "description", "Web host status", "event_detail",
                        "Alert", "confidence", "high"))));
        // Rated medium (a catch-all), so shown in detail, its record as logged.
        assertThat(shown.get("rated_medium")).isEqualTo(List.of("variant=syslog"));
        assertThat(String.valueOf(shown.get("table"))).startsWith("## Medium-confidence classifications (1 of 1)")
                .contains("```\nNov  6 09:30:00 web1 cache warmed after 12 seconds\n```", "| Element | Value |",
                        "| Schema action | Alert |");
    }

    // ### Chat 162: a splitter reading only timestamp, host and message, so no kinds told apart: not "nothing to
    // classify" (the step done, rules then written by hand, none put to the user), but back to the agent to refine
    // the splitter; one kind only if the user confirms it.
    @Test
    void recordsNotToldApartAreNotSkipped() {
        final String build = "e2e-classify-coarse";
        createFeed(build, "E2E-CLASSIFY-COARSE");
        final List<Long> raw = List.of(upload("E2E-CLASSIFY-COARSE", String.join("\n", List.of(
                "2026-11-06T09:00:01Z jump01 sshd[101]: Failed password for alice from 10.0.0.1 port 50001 ssh2",
                "2026-11-06T09:00:02Z jump01 sudo: bob : TTY=pts/0 ; PWD=/home/bob ; USER=root ; COMMAND=/bin/ls",
                "2026-11-06T09:00:03Z web01 kernel: Out of memory: Killed process 1039 (java)",
                "2026-11-06T09:00:04Z db01 systemd-logind[88]: New session 42 of user carol.")) + "\n"));
        final Map<String, Object> coarse = Map.of("kind", "regex", "pattern", "^(\\S+) (\\S+) (.*)$", "names",
                List.of("ts", "host", "message"));
        final Map<String, Object> listed = json("classify_kinds", args("build", build, "source", "Mixed",
                "stream_ids", raw, "splitter", coarse));
        dump("classify_kinds coarse", listed);
        assertThat(String.valueOf(listed.get("hint"))).startsWith("No kinds told apart");
        assertThat(classifyState(build)).isEqualTo("to do");

        client.answeringWith(TestE2eClassifyKinds::acceptingAll);
        final int forms = client.forms().size();
        final Map<String, Object> one = json("classify_kinds", args("build", build, "source", "Mixed",
                "stream_ids", raw, "splitter", coarse, "one_kind", true));
        client.answeringWith(McpTestClient::agree);
        assertThat(client.forms()).hasSize(forms + 1);
        assertThat(String.valueOf(one.get("hint"))).startsWith("One kind of event");
        assertThat(classifyState(build)).isEqualTo("done");
    }

    // The state of the build's classify step, as build_status shows it.
    private String classifyState(final String build) {
        return list(json("build_status", args("build", build, "full", true)), "steps").stream()
                .filter(step -> "classify".equals(step.get("step")))
                .map(step -> String.valueOf(step.get("state")))
                .findFirst()
                .orElse("none");
    }

    // ### Many kinds: rounds of 18, put to the user in sets of up to ten of one confidence, each numbered from 1
    // under a heading naming its range.
    @Test
    void manyKindsArePutToTheUserInSetsOfTen() {
        final String build = "e2e-classify-many";
        createFeed(build, "E2E-CLASSIFY-MANY");
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            lines.add("time=2026-11-06T%02d:%02d:00Z event_type=kind%02d user=user%d".formatted(i / 60, i % 60,
                    i % 25, i % 4));
        }
        final List<Long> raw = List.of(upload("E2E-CLASSIFY-MANY", String.join("\n", lines) + "\n"));
        final Map<String, Object> listed = json("classify_kinds", args("build", build, "source", "Records app",
                "stream_ids", raw));
        final List<Map<String, Object>> kinds = list(listed, "to_classify");
        assertThat(kinds).hasSize(18);
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (int k = 0; k < kinds.size(); k++) {
            proposals.add(Map.of("field", kinds.get(k).get("field"), "value", kinds.get(k).get("value"), "title",
                    "Kind " + kinds.get(k).get("value"), "description", "d", "event_detail", "Alert", "confidence",
                    k < 5
                            ? "high"
                            : "medium"));
        }
        client.answeringWith(TestE2eClassifyKinds::acceptingAll);
        final List<String> headings = new ArrayList<>();
        Map<String, Object> step = json("classify_kinds", args("build", build, "proposals", proposals));
        while (step.containsKey("table")) {
            final String table = String.valueOf(step.get("table"));
            headings.add(table.lines().findFirst().orElse(""));
            // Each set numbered from 1.
            assertThat(table).containsAnyOf("| 1 | ", "### 1. ");
            step = json("classify_kinds", args("build", build, "confirm", true));
        }
        client.answeringWith(McpTestClient::agree);
        assertThat(headings).containsExactly("## High-confidence classifications (1-5 of 5)",
                "## Medium-confidence classifications (1-10 of 13)",
                "## Medium-confidence classifications (11-13 of 13)");
        assertThat(step.get("still_to_classify")).isEqualTo(7);
    }

    // Every set accepted with its button.
    @SuppressWarnings("unchecked")
    private static ElicitResult acceptingAll(final ElicitFormRequest form) {
        final Map<String, Object> question = (Map<String, Object>) ((Map<String, Object>) form.requestedSchema()
                .get("properties")).get("answer");
        return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("answer",
                ((List<?>) question.get("examples")).getFirst()), null);
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
