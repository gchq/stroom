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
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// classify_kinds (work package E8): the kinds the documentation doesn't cover listed, proposed by the agent,
/// confirmed by the user in one form, and recorded in the source notes the draft follows.
class TestClassifyKindsTool {

    private static final String THREE_KINDS = String.join("\n", List.of(
            "time=2026-11-06T09:00:00Z action=login user=alice src=10.0.0.1",
            "time=2026-11-06T09:00:05Z action=logout user=bob src=10.0.0.2",
            "time=2026-11-06T09:00:09Z action=purge user=carol src=10.0.0.3",
            "time=2026-11-06T09:00:12Z action=login user=dave src=10.0.0.4",
            "time=2026-11-06T09:00:15Z action=logout user=erin src=10.0.0.5",
            "time=2026-11-06T09:00:19Z action=purge user=alice src=10.0.0.1",
            "time=2026-11-06T09:00:22Z action=login user=bob src=10.0.0.2",
            "time=2026-11-06T09:00:25Z action=logout user=carol src=10.0.0.3")) + "\n";
    private static final String PROPOSALS = "[{\"field\": \"action\", \"value\": \"login\", \"description\": \"A user "
                                            + "logged on\", \"event_detail\": \"Authenticate\", \"action\": "
                                            + "\"Logon\", \"actor\": \"user\", \"confidence\": \"high\"}, "
                                            + "{\"field\": \"action\", \"value\": \"logout\", \"description\": \"A "
                                            + "user logged off\", \"event_detail\": \"Authenticate\", \"action\": "
                                            + "\"Logoff\", \"confidence\": \"medium\"}, "
                                            + "{\"field\": \"action\", \"value\": \"purge\", \"description\": "
                                            + "\"Records purged\", \"event_detail\": \"Delete\", \"confidence\": "
                                            + "\"low\"}]";

    private final BuildWorld world = new BuildWorld();
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final SampleStreamTexts sampleStreamTexts = Mockito.mock(SampleStreamTexts.class);
    private final Consent consent = new Consent(() -> Mockito.mock(AiAuditLog.class));
    private final BuildPlan plan = new BuildPlan(new FakeBuildFacts(), world.guard(), consent, new BuildMemory());
    private final EventVersions eventVersions = new EventVersions(heldSchemas, consent, new BuildMemory(),
            world::guard, world::records, () -> Mockito.mock(EventsTargetInference.class));
    private final RecordSourceNotesTool notesTool = new RecordSourceNotesTool(world.guard(), world.documentAccess,
            new DocumentWrites(world.documentAccess), eventVersions, plan);
    private final ClassifyKindsTool tool = new ClassifyKindsTool(notesTool, sampleStreamTexts, consent, plan);

    @BeforeEach
    void setUp() {
        Mockito.when(heldSchemas.eventVersions()).thenReturn(List.of("4.1.0"));
        Mockito.when(heldSchemas.eventSchema("4.1.0")).thenReturn(schema());
        samples(7L, THREE_KINDS);
    }

    @Test
    void theKindsAreListedWithTheirRecordsAndKeptAsStillToClassify() {
        final Map<String, Object> listed = call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}",
                BuildToolCalls.context());
        final List<Map<String, Object>> kinds = list(listed.get("to_classify"));
        assertThat(kinds).as("%s", listed).extracting(kind -> kind.get("value")).containsExactly("login", "logout",
                "purge");
        assertThat(kinds.getFirst()).containsEntry("field", "action").containsEntry("records", 3);
        assertThat(list(kinds.getFirst().get("examples"))).hasSize(3);
        assertThat(pending()).containsExactly("action=login", "action=logout", "action=purge");
    }

    @Test
    void aKindInSeveralRulesIsListedOnce() {
        // Seen in an e2e test: a kind the draft writes a rule for in each of two formats was listed twice, and its
        // two proposals refused as a pair a record can't tell apart.
        samples(9L, THREE_KINDS + "<13>Nov  6 09:01:00 host7 app: action=login user=frank src=10.0.0.6\n"
                    + "<13>Nov  6 09:01:05 host7 app: action=logout user=grace src=10.0.0.7\n");
        final Map<String, Object> listed = call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [9]}",
                BuildToolCalls.context());
        final List<String> keys = list(listed.get("to_classify")).stream()
                .map(kind -> kind.get("field") + "=" + kind.get("value"))
                .toList();
        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void oneKindLeavesNothingToClassify() {
        samples(8L, THREE_KINDS.replace("action=logout", "action=login").replace("action=purge", "action=login"));
        final Map<String, Object> listed = call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [8]}",
                BuildToolCalls.context());
        assertThat(list(listed.get("to_classify"))).isEmpty();
        assertThat(pending()).isEmpty();
    }

    @Test
    void theUserConfirmsTheSureOnesTogetherAndTheOthersOneByOne() {
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        // The sure ones accepted together (i1), the low one rejected (i2), and a correction.
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("i1", true, "i2", false, "text",
                "purge is Delete of the audit records"));
        final Map<String, Object> done = call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}",
                user.context());
        assertThat(done.get("recorded")).isEqualTo(List.of("action=login", "action=logout"));
        assertThat(done.get("rejected")).isEqualTo(List.of("action=purge"));
        assertThat(done.get("corrections")).isEqualTo("purge is Delete of the audit records");
        assertThat(String.valueOf(done.get("hint"))).contains("from_user: true");
        assertThat(user.shown()).hasSize(1);
        assertThat(pending()).isEmpty();
        final Map<String, Object> login = list(notes().get("events")).getFirst();
        assertThat(login).containsEntry("event", "login").containsEntry("event_detail", "Authenticate")
                .containsEntry("actor", "user").containsEntry("basis", "user_confirmed")
                .doesNotContainKey("confidence");
        assertThat(world.body(String.valueOf(map(done.get("source_notes")).get("uuid"))))
                .contains("| login | A user logged on | Authenticate |  | agent's proposal, confirmed by the user |");
    }

    @Test
    void whatTheUserSaidIsRecordedWithoutAnotherForm() {
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        final Map<String, Object> done = call("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", "
                                              + "\"value\": \"purge\", \"description\": \"Audit records deleted\", "
                                              + "\"event_detail\": \"Delete\", \"from_user\": true}]}",
                BuildToolCalls.context());
        assertThat(done.get("recorded")).isEqualTo(List.of("action=purge"));
        assertThat(pending()).containsExactly("action=login", "action=logout");
        assertThat(String.valueOf(done.get("hint"))).contains("Propose the kinds still to classify");
    }

    @Test
    void withoutFormsTheTableIsForTheChatAndNothingRecorded() {
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        final Map<String, Object> asked = call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}",
                BuildToolCalls.context());
        assertThat(asked).containsEntry("asked", false);
        assertThat(String.valueOf(asked.get("table"))).contains("| action=purge: Records purged | Delete |");
        assertThat(pending()).hasSize(3);
    }

    @Test
    void whatCantBeClassifiedIsRefusedSayingWhy() {
        assertThat(text("{\"build\": \"acme\", \"source\": \"Acme app\", \"proposals\": " + PROPOSALS + "}"))
                .isEqualTo("Call classify_kinds without proposals first: it lists the kinds to classify");
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", \"value\": \"reboot\", "
                        + "\"description\": \"d\", \"event_detail\": \"Alert\", \"confidence\": \"high\"}]}"))
                .startsWith("Nothing recorded: proposals[0]: action=reboot isn't a kind still to classify");
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", \"value\": \"login\", "
                        + "\"description\": \"d\", \"event_detail\": \"Authenticate\"}]}"))
                .isEqualTo("Nothing recorded: proposals[0]: confidence is high, medium or low");
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", \"value\": \"login\", "
                        + "\"description\": \"d\", \"event_detail\": \"Login\", \"confidence\": \"high\"}]}"))
                .startsWith("Nothing recorded:");
    }

    private void samples(final long stream, final String text) {
        Mockito.when(sampleStreamTexts.read(List.of(stream))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream " + stream, text), List.of()));
    }

    private Map<String, Object> call(final String arguments, final ToolCallContext context) {
        return BuildToolCalls.json(tool.execute(BuildToolCalls.call(ClassifyKindsTool.NAME, arguments), context)
                .text());
    }

    private String text(final String arguments) {
        return tool.execute(BuildToolCalls.call(ClassifyKindsTool.NAME, arguments), BuildToolCalls.context()).text();
    }

    private Map<String, Object> notes() {
        return notesTool.notesOf(BuildToolCalls.context(), "acme");
    }

    private List<String> pending() {
        @SuppressWarnings("unchecked") final List<String> left = (List<String>) notes().get(
                RecordSourceNotesTool.PENDING);
        return left;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(final Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return (Map<String, Object>) value;
    }

    private static EventSchema schema() {
        try (final InputStream in = TestClassifyKindsTool.class.getResourceAsStream("event-logging-v4.1.0.xsd")) {
            return EventSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
