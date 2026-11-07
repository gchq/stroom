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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// record_source_notes, and what `TestE2eSourceDocs` (stroom-app) expects of it.
class TestRecordSourceNotesTool {

    private static final String FIELDS = "[{\"field\": \"evt\", \"meaning\": \"Event id\"}, {\"field\": \"usr\", "
                                         + "\"meaning\": \"Account name\", \"event_logging_path\": "
                                         + "\"EventSource/User/Id\"}]";
    private static final String EVENTS = "[{\"event\": \"4624\", \"description\": \"An account logged on\", "
                                         + "\"event_detail\": \"Authenticate\", \"type_id\": \"Logon\", "
                                         + "\"field\": \"evt\", \"value\": \"4624\", \"action\": \"Logon\", "
                                         + "\"success\": true}, {\"event\": \"4625\", \"description\": \"An "
                                         + "account failed to log on\", \"event_detail\": \"Authenticate\", "
                                         + "\"type_id\": \"Logon failed\", \"field\": \"evt\", \"value\": "
                                         + "\"4625\"}]";

    private final BuildWorld world = new BuildWorld();
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final BuildPlan plan = new BuildPlan(new FakeBuildFacts(), world.guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());
    private final EventVersions eventVersions = new EventVersions(heldSchemas, new Consent(
            () -> Mockito.mock(AiAuditLog.class)), new BuildMemory(),
            world::guard, world::records, () -> Mockito.mock(EventsTargetInference.class));
    private final RecordSourceNotesTool tool = new RecordSourceNotesTool(world.guard(), world.documentAccess,
            new DocumentWrites(world.documentAccess), eventVersions, plan);

    @BeforeEach
    void setUp() {
        Mockito.when(heldSchemas.eventVersions()).thenReturn(List.of("4.1.0", "3.5.2"));
        Mockito.when(heldSchemas.eventSchema("4.1.0")).thenReturn(schema());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPathTheSchemaDoesntHaveIsLeftOutAndSaysWhatIsAllowed() {
        // Seen: EventSource/Client/Device/IpAddress taken as given into the draft, and each wrong path then cost
        // schema lookups to put right.
        final Map<String, Object> noted = json(call("{\"build\": \"acme\", \"source\": \"Acme proxy\", "
                                                    + "\"fields\": [{\"field\": \"usr\", \"meaning\": \"Account\", "
                                                    + "\"event_logging_path\": \"EventSource/User/Id\"}, {\"field\": "
                                                    + "\"cip\", \"meaning\": \"Client address\", "
                                                    + "\"event_logging_path\": "
                                                    + "\"EventSource/Client/Device/IpAddress\"}]}"));

        assertThat((List<Map<String, Object>>) noted.get("path_problems")).singleElement().satisfies(problem -> {
            assertThat(problem).containsEntry("field", "cip")
                    .containsEntry("event_logging_path", "EventSource/Client/Device/IpAddress");
            assertThat((String) problem.get("problem")).contains("Allowed here");
        });
        assertThat((String) noted.get("hint")).startsWith("1 event_logging_path(s) aren't in the event-logging "
                                                          + "schema and were left out");
        final String text = world.body(String.valueOf(noted.get("uuid")));
        assertThat(text).contains("| usr | Account |  |  | EventSource/User/Id |")
                .doesNotContain("Device/IpAddress");
    }

    @Test
    void theDocumentationAndTheNotesFromItAreKeptInTheBuild() {
        final Map<String, Object> noted = json(call("{\"build\": \"acme\", \"source\": \"Acme directory\", "
                                                    + "\"summary\": \"The directory audit log.\", \"fields\": "
                                                    + FIELDS + ", \"events\": " + EVENTS + ", \"references\": "
                                                    + "[\"https://acme.example/audit\"], \"documents\": "
                                                    + "[{\"title\": \"audit log reference\", \"text\": \"| 4625 | "
                                                    + "An account failed to log on |\\n\", \"source\": "
                                                    + "\"audit.md\"}]}"));

        final List<?> kept = (List<?>) noted.get("documents_kept");
        assertThat(kept).hasSize(1);
        final Map<?, ?> reference = (Map<?, ?>) kept.getFirst();
        assertThat(reference.get("name")).isEqualTo("Acme directory reference - audit log reference");
        // The reference kept verbatim in Stroom, its tables as the vendor wrote them.
        assertThat(world.body(String.valueOf(reference.get("uuid")))).isEqualTo("# audit log reference\n\nSource: "
                                                                                 + "audit.md\n\n| 4625 | An account "
                                                                                 + "failed to log on |\n");
        assertThat(noted).containsEntry("type", "Documentation").containsEntry("name", "Acme directory source notes")
                .containsEntry("fields", 2).containsEntry("events", 2).containsKey("next");
        assertThat(noted.get("hint")).isEqualTo("draft_translation_mapping with build= drafts from these notes.");
        final String text = world.body(String.valueOf(noted.get("uuid")));
        assertThat(text).startsWith("# Acme directory source notes\n\nThe directory audit log.\n\n## Field dictionary")
                .contains("| usr | Account name |  |  | EventSource/User/Id | documentation |")
                .contains("| 4625 | An account failed to log on | Authenticate | Logon failed | documentation |")
                .contains("## Sources\n\n- https://acme.example/audit\n- `Acme directory reference - audit log "
                          + "reference` (kept in Stroom)");
        final Map<String, Object> notes = SourceNotes.readNotes(text);
        assertThat(notes.get("source")).isEqualTo("Acme directory");
        // Each note as the tools write it: its defaults left out.
        assertThat(notes.get("fields")).isEqualTo(List.of(Map.of("field", "evt", "meaning", "Event id"),
                Map.of("field", "usr", "meaning", "Account name", "event_logging_path", "EventSource/User/Id")));
        assertThat(((List<?>) notes.get("events")).getFirst()).isEqualTo(Map.of("event", "4624", "description",
                "An account logged on", "event_detail", "Authenticate", "type_id", "Logon", "field", "evt", "value",
                "4624", "action", "Logon", "success", true));
        assertThat(notes.get("documents")).isEqualTo(List.of("Acme directory reference - audit log reference"));
        assertThat(notes.get("references")).isEqualTo(List.of("https://acme.example/audit"));
    }

    @Test
    void notesRecordedAgainForASourceAreMergedIntoItsOneDoc() {
        // Seen in e2e: a long manual sent a part at a time; the second call made the notes doc again, which the
        // build refused (one doc per name).
        json(call("{\"build\": \"acme\", \"source\": \"Acme directory\", \"summary\": \"Summary.\", \"fields\": "
                  + FIELDS + ", \"events\": " + EVENTS + ", \"documents\": [{\"title\": \"part 1\", \"text\": "
                  + "\"The manual.\"}]}"));
        final String first = world.created.toString();

        final Map<String, Object> again = json(call("{\"build\": \"acme\", \"source\": \"Acme directory\", "
                                                    + "\"summary\": \"Summary.\", \"fields\": [{\"field\": \"usr\", "
                                                    + "\"meaning\": \"The account\", \"event_logging_path\": "
                                                    + "\"EventSource/User/Id\"}], \"documents\": [{\"title\": "
                                                    + "\"part 2\", \"text\": \"More of the manual.\"}]}"));

        // The doc already there, written again: not a second one.
        assertThat(world.created.stream().filter(made -> made.startsWith("Documentation:")))
                .containsExactly("Documentation:Acme directory reference - part 1",
                        "Documentation:Acme directory source notes", "Documentation:Acme directory reference - part 2");
        assertThat(first).doesNotContain("part 2");
        final Map<String, Object> notes = SourceNotes.readNotes(world.body(String.valueOf(again.get("uuid"))));
        // usr given again: the new one.
        assertThat(((List<?>) notes.get("fields")).stream()
                .map(field -> String.valueOf(((Map<?, ?>) field).get("field"))))
                .containsExactly("evt", "usr");
        assertThat((List<?>) notes.get("fields")).anyMatch(field -> "The account".equals(((Map<?, ?>) field)
                .get("meaning")));
        assertThat(((List<?>) notes.get("events")).stream()
                .map(event -> String.valueOf(((Map<?, ?>) event).get("event"))))
                .containsExactly("4624", "4625");
        assertThat(notes.get("documents")).isEqualTo(List.of("Acme directory reference - part 1",
                "Acme directory reference - part 2"));
        assertThat(again).containsEntry("fields", 2).containsEntry("events", 2);
    }

    @Test
    void anEventWithAWrongActionElementIsLeftOutAndTheRestRecorded() {
        final ToolResult refused = call("{\"build\": \"fw\", \"source\": \"Firewall\", \"summary\": \"A "
                                        + "firewall.\", \"events\": [{\"event\": \"TRAFFIC-ALLOW\", "
                                        + "\"description\": \"allowed\", \"field\": \"event_type\", \"value\": "
                                        + "\"TRAFFIC\", \"event_detail\": \"Allow\"}, {\"event\": "
                                        + "\"TRAFFIC-DENY\", \"description\": \"blocked\", \"field\": "
                                        + "\"event_type\", \"value\": \"TRAFFIC\", \"event_detail\": "
                                        + "\"Network/Deny\"}]}");

        // Seen in testing (chat 144): 10,500 characters of notes refused whole for three such events, then sent whole
        // again. The rest is kept; the wrong one comes back to be sent again, corrected.
        assertThat(refused.isError()).isFalse();
        assertThat(refused.text()).contains("\"events\":1", "TRAFFIC-ALLOW: 'Allow' is not an EventDetail action "
                                                              + "element", "were left out");
        assertThat(world.created).isNotEmpty();
    }

    @Test
    void withoutTheSchemaTheCatalogueIsStillCheckedForTwins() {
        Mockito.when(heldSchemas.eventVersions()).thenThrow(new ToolRefusal(HeldSchemas.NO_EVENT_LOGGING));

        final ToolResult refused = call("{\"build\": \"fw\", \"source\": \"Firewall\", \"summary\": \"s\", "
                                        + "\"events\": [{\"event\": \"A\", \"description\": \"a\", \"field\": "
                                        + "\"f\", \"value\": \"1\", \"event_detail\": \"Allow\"}, {\"event\": "
                                        + "\"B\", \"description\": \"b\", \"field\": \"f\", \"value\": \"1\"}]}");

        assertThat(refused.text()).isEqualTo("Nothing recorded; the event catalogue can't be drafted from as it "
                                             + "is: A and B are both f=1: a record can't show which it is. Give "
                                             + "each the field and value that tell them apart (e.g. action=ALLOW "
                                             + "and action=DENY), or record them as one event. Correct the events "
                                             + "and call again.");
    }

    @Test
    void theCatalogueIsCheckedAgainstTheVersionChosenForTheBuild() {
        // As the translation tools read it: the build's chosen event-logging version, not the newest held.
        world.guard().buildFolder("fw");
        world.records().rememberEventLoggingVersion("fw", "3.5.2", EventVersions.GIVEN);
        Mockito.when(heldSchemas.eventSchema("3.5.2")).thenReturn(schema());

        final ToolResult refused = call("{\"build\": \"fw\", \"source\": \"Firewall\", \"summary\": \"s\", "
                                        + "\"events\": [{\"event\": \"A\", \"description\": \"a\", "
                                        + "\"event_detail\": \"Allow\"}]}");

        assertThat(refused.text()).contains("A: 'Allow' is not an EventDetail action element");
        Mockito.verify(heldSchemas, Mockito.atLeastOnce()).eventSchema("3.5.2");
        Mockito.verify(heldSchemas, Mockito.never()).eventSchema("4.1.0");
    }

    @Test
    void aDocumentAlreadyInStroomIsNamedWhereItIs() {
        final DocRef table = world.add(DocumentationDoc.TYPE, "status codes", world.folder("System/Docs").uuid,
                null).docRef();

        final Map<String, Object> noted = json(call("{\"build\": \"acme\", \"source\": \"Acme directory\", "
                                                    + "\"summary\": \"s\", \"documents\": [{\"title\": \"status "
                                                    + "codes\", \"uuid\": \"" + table.getUuid() + "\"}]}"));

        assertThat(noted.get("documents_kept")).isEqualTo(List.of(Map.of("uuid", table.getUuid(), "name",
                "status codes", "already_in_stroom", true)));
        assertThat(world.created).doesNotContain("Documentation:Acme directory reference - status codes");
        assertThat(call("{\"build\": \"acme\", \"source\": \"A\", \"summary\": \"s\", \"documents\": [{\"title\": "
                        + "\"t\", \"uuid\": \"nope\"}]}").text())
                .startsWith("No Documentation doc 'nope' in this Stroom.");
    }

    @Test
    void eventsThatCantBecomeRulesAreSaidSo() {
        final Map<String, Object> noted = json(call("{\"build\": \"acme\", \"source\": \"Acme\", \"summary\": "
                                                    + "\"s\", \"events\": [{\"event\": \"login\", \"description\": "
                                                    + "\"A login\", \"event_detail\": \"Authenticate\"}]}"));

        assertThat(noted.get("hint")).isEqualTo("draft_translation_mapping with build= drafts from these notes; "
                                                + "events without a field and value (1) cannot become rules: give "
                                                + "them where the documentation says how a record shows the event.");
    }

    @Test
    void theAgentsOwnKnowledgeIsKeptAsSuchAndItsLeastSureNotesPutToTheUserFirst() {
        // Asked for by the user (work package E7): what the agent knows of a source, said as such, beside the user's
        // documentation; the notes it is least sure of reviewed first.
        final Map<String, Object> noted = json(call("{\"build\": \"acme\", \"source\": \"Acme directory\", "
                                                    + "\"summary\": \"The directory audit log.\", \"events\": ["
                                                    + "{\"event\": \"4624\", \"description\": "
                                                    + "\"An account logged on\", \"event_detail\": "
                                                    + "\"Authenticate\", \"basis\": \"agent_knowledge\", "
                                                    + "\"confidence\": \"high\"}, "
                                                    + "{\"event\": \"4662\", \"description\": "
                                                    + "\"An operation on an object\", "
                                                    + "\"basis\": \"agent_knowledge\", \"confidence\": \"low\"}, "
                                                    + "{\"event\": \"4625\", \"description\": \"A logon failed\"}]}"));
        assertThat(noted.get("to_review")).isEqualTo(List.of("4662"));
        final String text = world.body(String.valueOf(noted.get("uuid")));
        assertThat(text).contains("| 4624 | An account logged on | Authenticate |  | agent knowledge (high) |")
                .contains("| 4662 | An operation on an object |  |  | agent knowledge (low) |")
                .contains("| 4625 | A logon failed |  |  | documentation |");
        // The documentation's notes carry no basis: it is the default.
        assertThat(((List<?>) SourceNotes.readNotes(text).get("events")).get(2))
                .isEqualTo(Map.of("event", "4625", "description", "A logon failed"));
    }

    @Test
    void aNoteFromTheAgentsKnowledgeSaysHowSureItIs() {
        final String start = "{\"build\": \"acme\", \"source\": \"A\", \"summary\": \"s\", \"fields\": [{\"field\": "
                             + "\"x\", \"meaning\": \"m\", ";
        assertThat(call(start + "\"basis\": \"agent_knowledge\"}]}").text())
                .isEqualTo("fields[0]: a note from your own knowledge needs confidence 'high', 'medium' or 'low'");
        assertThat(call(start + "\"basis\": \"guess\"}]}").text())
                .isEqualTo("fields[0].basis is 'documentation' or 'agent_knowledge', not 'guess'");
        assertThat(world.created).isEmpty();
    }

    @Test
    void whatCantBeKeptIsRefusedSayingWhy() {
        assertThat(call("{\"build\": \"acme\", \"source\": \"A\", \"summary\": \"s\", \"documents\": [{\"title\": "
                        + "\"t\", \"text\": \"x\", \"uuid\": \"u\"}]}").text())
                .isEqualTo("documents[0]: give the document's text, or the uuid of the Documentation doc holding "
                           + "it, not both");
        assertThat(call("{\"build\": \"acme\", \"source\": \"A\", \"summary\": \"s\", \"documents\": [{\"title\": "
                        + "\"t\"}]}").text()).startsWith("documents[0]: give the document's text");
        assertThat(call("{\"build\": \"acme\", \"source\": \"A\", \"summary\": \"s\", \"fields\": [{\"field\": "
                        + "\"x\"}]}").text()).isEqualTo("fields[0].meaning is required");
        assertThat(call("{\"build\": \"acme\", \"summary\": \"s\"}").text()).isEqualTo("Give source");
        assertThat(world.created).isEmpty();
    }

    @Test
    void itIsAWriteThatStaysInItsBuild() {
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.changesOnlyItsBuild()).isTrue();
        assertThat(tool.category().name()).isEqualTo("CONTENT");
    }

    private ToolResult call(final String arguments) {
        return tool.execute(BuildToolCalls.call(RecordSourceNotesTool.NAME, arguments), BuildToolCalls.context());
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }

    private static EventSchema schema() {
        try (final InputStream in = TestRecordSourceNotesTool.class.getResourceAsStream("event-logging-v4.1.0.xsd")) {
            return EventSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
