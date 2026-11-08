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
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// classify_kinds on records a JSON or XML parser reads rather than a Data Splitter, and on records whose event is
/// inside an inner message (key/value, JSON or XML text held in a field): the kinds are told apart, each with its
/// records and a sample, the inner fields are record fields an actor may name, and what the user accepts is
/// recorded.
class TestClassifyKindsToolFormats {

    private static final List<String> ACTIONS = List.of("login", "logout", "purge");
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin");
    private static final int RECORDS = 9;

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
    }

    static Stream<Shape> shapes() {
        return Stream.of(
                // ### Read by a JSON parser.
                new Shape("JSON lines", lines((i, action, user) -> "{\"ts\": \"" + time(i) + "\", \"action\": \""
                                                                   + action + "\", \"user\": \"" + user + "\"}")),
                new Shape("JSON lines, nested", lines((i, action, user) -> "{\"ts\": \"" + time(i)
                                                                           + "\", \"event\": {\"type\": \"" + action
                                                                           + "\"}, \"actor\": {\"user\": \"" + user
                                                                           + "\"}}")),
                new Shape("JSON array", "[" + String.join(",\n", records((i, action, user) -> "{\"ts\": \""
                                                                                              + time(i)
                                                                                              + "\", \"action\": \""
                                                                                              + action
                                                                                              + "\", \"user\": \""
                                                                                              + user + "\"}"))
                                        + "]\n"),
                new Shape("JSON object wrapping its records", "{\"Records\": [" + String.join(", ",
                        records((i, action, user) -> "{\"eventTime\": \"" + time(i) + "\", \"eventName\": \""
                                                     + action + "\", \"userName\": \"" + user + "\"}")) + "]}\n"),
                // ### Read by an XML parser.
                new Shape("XML fragments", lines((i, action, user) -> "<Record><Time>" + time(i) + "</Time><Action>"
                                                                      + action + "</Action><User>" + user
                                                                      + "</User></Record>")),
                new Shape("XML document", "<?xml version=\"1.0\"?>\n<Records>\n" + lines((i, action, user) ->
                        "  <Record><Time>" + time(i) + "</Time><Action>" + action + "</Action><User>" + user
                        + "</User></Record>") + "</Records>\n"),
                // ### The event inside an inner message.
                new Shape("JSON, key/value message", lines((i, action, user) -> "{\"ts\": \"" + time(i)
                                                                                + "\", \"host\": \"app1\", "
                                                                                + "\"message\": \"action=" + action
                                                                                + " user=" + user
                                                                                + " src=10.0.0.1\"}")),
                new Shape("JSON, JSON message", lines((i, action, user) -> "{\"ts\": \"" + time(i)
                                                                           + "\", \"host\": \"app1\", \"message\": "
                                                                           + "\"{\\\"action\\\": \\\"" + action
                                                                           + "\\\", \\\"user\\\": \\\"" + user
                                                                           + "\\\"}\"}")),
                new Shape("JSON, XML message", lines((i, action, user) -> "{\"ts\": \"" + time(i)
                                                                          + "\", \"host\": \"app1\", \"message\": "
                                                                          + "\"<Event><Action>" + action
                                                                          + "</Action><User>" + user
                                                                          + "</User></Event>\"}")),
                new Shape("XML, key/value message", lines((i, action, user) -> "<Record><Time>" + time(i)
                                                                               + "</Time><Message>action=" + action
                                                                               + " user=" + user
                                                                               + " src=10.0.0.1</Message>"
                                                                               + "</Record>")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void theKindsAreListedProposedAcceptedAndRecorded(final Shape shape) {
        Mockito.when(sampleStreamTexts.read(List.of(5L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 5", shape.text()), List.of()));

        // ### Listed: a kind an action, each with its records and a sample.
        final Map<String, Object> listed = call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [5]}",
                BuildToolCalls.context());
        final List<Map<String, Object>> kinds = list(listed.get("to_classify"));
        assertThat(kinds).as("%s", listed).extracting(kind -> kind.get("value"))
                .containsExactlyInAnyOrderElementsOf(ACTIONS);
        assertThat(kinds).as("%s", listed)
                .allSatisfy(kind -> assertThat(kind.get("records")).isEqualTo(RECORDS / ACTIONS.size()))
                .allSatisfy(kind -> assertThat(list(kind.get("examples"))).isNotEmpty())
                .allSatisfy(kind -> assertThat(kind).doesNotContainKey("catch_all"));
        // The user field, inner or not, is one the records have.
        final List<String> users = JsonValues.strings(listed.get("user_fields"));
        assertThat(users).as("%s", listed).hasSize(1);
        assertThat(JsonValues.strings(listed.get("record_fields"))).contains(users.getFirst());
        final String actor = users.getFirst();

        // ### Proposed, the user's field as each one's actor: one table of them.
        final List<Map<String, Object>> proposals = new ArrayList<>();
        kinds.forEach(kind -> proposals.add(Map.of("field", kind.get("field"), "value", kind.get("value"),
                "title", "Event " + kind.get("value"), "description", "The " + kind.get("value") + " event",
                "event_detail", "Alert", "actor", actor, "confidence", "high")));
        final Map<String, Object> shown = call("{\"build\": \"acme\", \"proposals\": "
                                               + JsonUtil.writeValueAsString(proposals, false) + "}",
                BuildToolCalls.context());
        assertThat(String.valueOf(shown.get("table"))).as("%s", shown)
                .startsWith("## High-confidence classifications")
                .contains("| 3 |");

        // ### Accepted: recorded in the source notes, with the actor, and nothing left to classify.
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "Accept all 3"));
        final Map<String, Object> accepted = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        assertThat(JsonValues.strings(accepted.get("recorded"))).as("%s", accepted).hasSize(3);
        final Map<String, Object> notes = notesTool.notesOf(BuildToolCalls.context(), "acme", null);
        assertThat(JsonValues.strings(notes.get(RecordSourceNotesTool.PENDING))).isEmpty();
        assertThat(JsonValues.maps(notes.get("events")))
                .extracting(event -> event.get("event"))
                .containsExactlyInAnyOrderElementsOf(ACTIONS);
        assertThat(JsonValues.maps(notes.get("events"))).allSatisfy(event -> assertThat(event)
                .containsEntry("actor", actor).containsEntry("basis", "user_confirmed"));
    }

    private Map<String, Object> call(final String arguments, final ToolCallContext context) {
        return BuildToolCalls.json(tool.execute(BuildToolCalls.call(ClassifyKindsTool.NAME, arguments), context)
                .text());
    }

    private static String lines(final Line line) {
        return String.join("\n", records(line)) + "\n";
    }

    private static List<String> records(final Line line) {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < RECORDS; i++) {
            out.add(line.of(i, ACTIONS.get(i % ACTIONS.size()), USERS.get(i % USERS.size())));
        }
        return out;
    }

    private static String time(final int i) {
        return "2026-11-06T09:00:%02dZ".formatted(i);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(final Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static EventSchema schema() {
        try (final InputStream in = TestClassifyKindsToolFormats.class.getResourceAsStream(
                "event-logging-v4.1.0.xsd")) {
            return EventSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }


    // --------------------------------------------------------------------------------


    /// A record of the sample, from its number, action and user.
    @FunctionalInterface
    private interface Line {

        String of(int i, String action, String user);
    }

    /// A shape of sample: its name, shown as the test's, and its text.
    record Shape(String name, String text) {

        @Override
        public String toString() {
            return name;
        }
    }
}
