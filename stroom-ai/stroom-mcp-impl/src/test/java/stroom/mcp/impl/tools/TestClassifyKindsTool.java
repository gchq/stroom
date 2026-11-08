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
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final String PROPOSALS = "[{\"field\": \"action\", \"value\": \"login\", \"title\": \"User logon\", "
                                            + "\"product\": \"Acme app\", \"description\": \"A user "
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
        assertThat(listed.get("record_fields")).isEqualTo(List.of("time", "action", "user", "src"));
        assertThat(listed.get("user_fields")).isEqualTo(List.of("user"));
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
    void eachConfidenceIsASetAcceptedTogetherInTurn() {
        // Asked for by the user: the high-confidence classifications in one table, then the medium and the low ones
        // in detail, each set numbered from 1 and accepted all together or amended in a short form.
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "Accept it"));
        final Map<String, Object> shown = call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}",
                user.context());
        assertThat(String.valueOf(shown.get("table"))).isEqualTo("""
                ## High-confidence classifications (1 of 1)

                | # | Rule | Kind | Mapping |
                | --- | --- | --- | --- |
                | 1 | Acme app | User logon | Authenticate: Logon |""");
        assertThat(String.valueOf(shown.get("hint"))).contains("confirm=true", "high-confidence",
                "Show only this table");
        assertThat(user.shown()).isEmpty();

        // The high-confidence one accepted; then the medium one in detail, numbered from 1, and only it.
        final Map<String, Object> high = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        assertThat(high.get("recorded")).isEqualTo(List.of("action=login"));
        assertThat(user.shown().getLast()).isEqualTo("Review the high-confidence classifications shown in the chat.");
        final String medium = String.valueOf(high.get("table"));
        // The record as logged, whole (not its fields cut short); then what the proposal says, each under a
        // subheading, no table.
        assertThat(medium).isEqualTo("""
                ## Medium-confidence classifications (1 of 1)

                ### 1. A user logged off

                ```
                time=2026-11-06T09:00:05Z action=logout user=bob src=10.0.0.2
                ```

                #### Action element

                Authenticate

                #### Action

                Logoff""");
        assertThat(String.valueOf(high.get("hint"))).contains("medium-confidence");

        // The medium one accepted the same way; then the low one, numbered from 1 again.
        final Map<String, Object> mediumDone = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        assertThat(mediumDone.get("recorded")).isEqualTo(List.of("action=logout"));
        assertThat(user.shown().getLast()).isEqualTo("Review the medium-confidence classifications shown in the "
                                                     + "chat.");
        assertThat(String.valueOf(mediumDone.get("table"))).startsWith("## Low-confidence classifications (1 of 1)"
                                                                       + "\n\n### 1. Records purged");

        // The low one accepted: every kind classified.
        final Map<String, Object> done = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        assertThat(done.get("recorded")).isEqualTo(List.of("action=purge"));
        assertThat(done).doesNotContainKey("table");
        assertThat(String.valueOf(done.get("hint"))).startsWith("Every kind is classified");
        assertThat(user.shown()).hasSize(3);
        assertThat(user.shown()).allSatisfy(form -> assertThat(form).doesNotContain("|").hasSizeLessThan(200));
        assertThat(pending()).isEmpty();
        final Map<String, Object> login = list(notes().get("events")).getFirst();
        assertThat(login).containsEntry("event", "login").containsEntry("event_detail", "Authenticate")
                .containsEntry("actor", "user").containsEntry("basis", "user_confirmed")
                .doesNotContainKey("confidence").doesNotContainKey("number");
        assertThat(world.body(String.valueOf(map(done.get("source_notes")).get("uuid"))))
                .contains("| login | A user logged on | Authenticate |  | agent's proposal, confirmed by the user |");
    }

    @Test
    void inStroomAiTheChatShowsTheTableNotTheAgent() {
        // Seen in testing: the agent retyped a set's table, shortened and restyled, and once never showed it. In
        // Stroom AI's chat the chat shows a needs_showing result's table itself; elsewhere the agent shows it.
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "Accept it"));
        final Map<String, Object> inChat = call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}",
                user.chatContext());
        assertThat(inChat).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(inChat.get("table"))).startsWith("## High-confidence classifications");
        assertThat(String.valueOf(inChat.get("hint"))).startsWith("The chat shows the user this table itself")
                .contains("don't repeat", "confirm=true");
        final Map<String, Object> next = call("{\"build\": \"acme\", \"confirm\": true}", user.chatContext());
        assertThat(next).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(next.get("hint"))).startsWith("The chat shows the user this table itself");

        // From another client, the agent shows it.
        final Map<String, Object> elsewhere = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        assertThat(elsewhere).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(elsewhere.get("hint"))).startsWith("Show the table to the user in your reply");
    }

    @Test
    void aSetOfOneConfidenceIsAmendedNotAccepted() {
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}", BuildToolCalls.context());
        call("{\"build\": \"acme\", \"confirm\": true}", BuildToolCalls.user(Map.of("answer", "Accept it"))
                .context());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "1 is a timeout, not a logoff"));
        final Map<String, Object> amended = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        // Nothing recorded until the agent sends it as the user said; the low one still to come.
        assertThat(amended.get("recorded")).isEqualTo(List.of());
        assertThat(amended.get("amendments")).isEqualTo("1 is a timeout, not a logoff");
        assertThat(String.valueOf(amended.get("hint"))).contains("medium-confidence", "send it again",
                "from_user: true");
        // The amended set leaves the proposals, still to classify until the agent sends it as the user said.
        assertThat(JsonValues.maps(notes().get(RecordSourceNotesTool.PROPOSED)))
                .extracting(entry -> entry.get("kind"))
                .containsExactly("action=purge");
        assertThat(pending()).containsExactly("action=logout", "action=purge");

        // The agent leaves out the one the user rejected (to ask what it is): it isn't put to the user again, the
        // next set is.
        final Map<String, Object> next = call("{\"build\": \"acme\", \"confirm\": true}",
                BuildToolCalls.user(Map.of("answer", "Accept it")).context());
        assertThat(next.get("recorded")).isEqualTo(List.of("action=purge"));
        assertThat(pending()).containsExactly("action=logout");
    }

    @Test
    void manyKindsAreClassifiedInRounds() {
        // Asked for by the user: a source of many kinds in rounds a person can read.
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            lines.add("time=2026-11-06T09:00:%02dZ event_type=kind%02d user=user%d".formatted(i, i % 25, i % 3));
        }
        samples(9L, String.join("\n", lines) + "\n");
        final Map<String, Object> first = call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [9]}",
                BuildToolCalls.context());
        final List<Map<String, Object>> round = list(first.get("to_classify"));
        assertThat(round).hasSize(ClassifyKindsTool.BATCH);
        assertThat(String.valueOf(first.get("round"))).startsWith("18 of the 25 kinds");
        assertThat(pending()).hasSize(25);
        final List<Map<String, Object>> said = new ArrayList<>();
        for (final Map<String, Object> kind : round) {
            said.add(Map.of("field", kind.get("field"), "value", kind.get("value"), "description", "d",
                    "event_detail", "Alert", "from_user", true));
        }
        call("{\"build\": \"acme\", \"proposals\": " + JsonUtil.writeValueAsString(said, false) + "}",
                BuildToolCalls.context());
        final Map<String, Object> next = call("{\"build\": \"acme\", \"stream_ids\": [9]}", BuildToolCalls.context());
        assertThat(list(next.get("to_classify"))).hasSize(7);
        assertThat(next).doesNotContainKey("round");
    }

    @Test
    void aHundredKindsAreClassifiedInRoundsAndSetsOfTen() {
        // Asked for by the user: a source of about a hundred event types, in rounds of 18 kinds; each round put to
        // the user a set at a time, of one confidence, at most ten, each numbered from 1. Some rounds all of one
        // confidence (two sets of it: ten, then eight), one mixed.
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            lines.add("time=2026-11-06T09:%02d:%02dZ event_id=E%03d user=user%d".formatted(i / 60, i % 60, i % 100,
                    i % 4));
        }
        samples(10L, String.join("\n", lines) + "\n");
        final List<String> rounds = List.of("high", "medium", "low", "mixed", "high", "high");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "yes"));
        int classified = 0;
        for (int round = 0; round < rounds.size(); round++) {
            final Map<String, Object> listed = call("{\"build\": \"acme\", \"source\": \"Acme app\", "
                                                    + "\"stream_ids\": [10]}", BuildToolCalls.context());
            final List<Map<String, Object>> kinds = list(listed.get("to_classify"));
            assertThat(kinds).as("round %s", round + 1).hasSize(round < 5
                    ? ClassifyKindsTool.BATCH
                    : 100 - 5 * ClassifyKindsTool.BATCH);
            final List<Map<String, Object>> proposals = new ArrayList<>();
            for (int k = 0; k < kinds.size(); k++) {
                final String confidence = "mixed".equals(rounds.get(round))
                        ? List.of("high", "medium", "low").get(k % 3)
                        : rounds.get(round);
                proposals.add(Map.of("field", kinds.get(k).get("field"), "value", kinds.get(k).get("value"),
                        "title", "Event " + kinds.get(k).get("value"), "product", "Acme app", "description", "d",
                        "event_detail", "Alert", "confidence", confidence));
            }
            final List<String> sets = new ArrayList<>();
            Map<String, Object> step = call("{\"build\": \"acme\", \"proposals\": "
                                            + JsonUtil.writeValueAsString(proposals, false) + "}", user.context());
            while (step.containsKey("table")) {
                final String table = String.valueOf(step.get("table"));
                final boolean high = table.startsWith("## High-confidence classifications");
                final String heading = table.substring(3, table.indexOf('-'));
                // One confidence a set, at most ten, numbered from 1.
                assertThat(table.split("-confidence classifications", -1)).hasSize(2);
                final int shown = (int) table.lines()
                        .filter(line -> high
                                ? line.matches("\\| \\d+ \\| .*")
                                : line.matches("### \\d+\\. .*"))
                        .count();
                assertThat(shown).as("%s", table).isBetween(1, ClassifyKindsTool.SET_SIZE);
                assertThat(table).contains(high
                        ? "| 1 | Acme app | Event "
                        : "### 1. [Acme app] Event ").doesNotContain(high
                        ? "| " + (shown + 1) + " | Acme app"
                        : "### " + (shown + 1) + ". ");
                sets.add(heading + " " + shown + " " + table.substring(table.indexOf('('), table.indexOf(')') + 1));
                step = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
                classified += JsonValues.strings(step.get("recorded")).size();
            }
            assertThat(sets).as("round %s", round + 1).isEqualTo(switch (rounds.get(round)) {
                case "mixed" -> List.of("High 6 (1-6 of 6)", "Medium 6 (1-6 of 6)", "Low 6 (1-6 of 6)");
                default -> round < 5
                        ? List.of(heading(rounds.get(round)) + " 10 (1-10 of 18)",
                        heading(rounds.get(round)) + " 8 (11-18 of 18)")
                        : List.of(heading(rounds.get(round)) + " 10 (1-10 of 10)");
            });
        }
        assertThat(classified).isEqualTo(100);
        assertThat(pending()).isEmpty();
        final Map<String, Object> after = call("{\"build\": \"acme\", \"stream_ids\": [10]}",
                BuildToolCalls.context());
        assertThat(list(after.get("to_classify"))).isEmpty();
    }

    private static String heading(final String confidence) {
        return Character.toUpperCase(confidence.charAt(0)) + confidence.substring(1);
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
        assertThat(String.valueOf(done.get("hint"))).contains("for the next round of the 2 kinds still to classify");
    }

    @Test
    void withoutFormsTheUserIsAskedInTheChatAndNothingRecorded() {
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        final Map<String, Object> shown = call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}",
                BuildToolCalls.context());
        assertThat(String.valueOf(shown.get("table"))).contains("## High-confidence classifications");
        final Map<String, Object> asked = call("{\"build\": \"acme\", \"confirm\": true}", BuildToolCalls.context());
        assertThat(asked).containsEntry("asked", false);
        assertThat(String.valueOf(asked.get("hint"))).contains("from_user: true");
        assertThat(pending()).hasSize(3);
    }

    @Test
    void whatCantBeClassifiedIsRefusedSayingWhy() {
        assertThat(text("{\"build\": \"acme\", \"source\": \"Acme app\", \"proposals\": " + PROPOSALS + "}"))
                .isEqualTo("Call classify_kinds without proposals first: it lists the kinds to classify");
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        assertThat(text("{\"build\": \"acme\", \"confirm\": true}"))
                .startsWith("Nothing to confirm: send the proposals first");
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", \"value\": \"reboot\", "
                        + "\"description\": \"d\", \"event_detail\": \"Alert\", \"confidence\": \"high\"}]}"))
                .startsWith("Nothing recorded: proposals[0]: action=reboot isn't a kind still to classify");
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", \"value\": \"login\", "
                        + "\"description\": \"d\", \"event_detail\": \"Authenticate\"}]}"))
                .isEqualTo("Nothing recorded: proposals[0]: confidence is high, medium or low");
        // A label from the text is no field of the records.
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", \"value\": \"login\", "
                        + "\"description\": \"d\", \"event_detail\": \"Authenticate\", \"actor\": \"By User\", "
                        + "\"confidence\": \"high\"}]}"))
                .isEqualTo("Nothing recorded: proposals[0]: actor and affected name fields of the records, as listed "
                           + "(time, action, user, src), or are left out");
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", \"value\": \"login\", "
                        + "\"description\": \"d\", \"event_detail\": \"Login\", \"confidence\": \"high\"}]}"))
                .startsWith("Nothing recorded:");
    }

    @Test
    void theNotesDocNamedForTheBuildIsTheOneUsedWhenTheSourceIsNamedOtherwise() {
        // Seen in testing: a notes doc named for the build (an earlier call gave no source), then classify_kinds
        // called with the source's name, wrote its proposals to a second doc and read the first: confirm=true found
        // nothing to confirm, and no form came.
        notesTool.recordClassified(BuildToolCalls.context(), "acme", "acme", List.of(), List.of(), Map.of());
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "Accept it"));
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"proposals\": " + PROPOSALS + "}",
                user.context());
        final Map<String, Object> confirmed = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        assertThat(confirmed.get("recorded")).as("%s", confirmed).isEqualTo(List.of("action=login"));
        assertThat(user.shown()).hasSize(1);
        assertThat(world.guard().folderContents("acme"))
                .filteredOn(doc -> doc.name().endsWith(" source notes"))
                .extracting(WriteGuard.BuildDoc::name)
                .containsExactly("acme source notes");
    }

    @Test
    void confirmSentWithTheProposalsWaitsForTheTableToBeShown() {
        // Seen in testing: confirm=true sent with the proposals, and the agent took the table for the end of it.
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "Accept it"));
        final Map<String, Object> shown = call("{\"build\": \"acme\", \"confirm\": true, \"proposals\": "
                                               + PROPOSALS + "}", user.context());
        assertThat(String.valueOf(shown.get("hint"))).startsWith("No form yet: confirm=true is not taken with "
                                                                 + "proposals.");
        assertThat(user.shown()).isEmpty();
        assertThat(call("{\"build\": \"acme\", \"confirm\": true}", user.context()).get("recorded"))
                .isEqualTo(List.of("action=login"));
    }

    @Test
    void aCorrectionFromTheUserBetweenTheRoundsKeepsTheOthersToConfirm() {
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}", BuildToolCalls.context());
        final Map<String, Object> corrected = call("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", "
                                                   + "\"value\": \"purge\", \"description\": \"Audit records "
                                                   + "deleted\", \"event_detail\": \"Delete\", \"from_user\": "
                                                   + "true}]}", BuildToolCalls.context());
        assertThat(corrected.get("recorded")).isEqualTo(List.of("action=purge"));
        assertThat(corrected.get("still_to_confirm")).isEqualTo(List.of("action=login", "action=logout"));
        assertThat(String.valueOf(corrected.get("hint"))).contains("confirm=true");
        assertThat(JsonValues.maps(notes().get(RecordSourceNotesTool.PROPOSED)))
                .extracting(entry -> entry.get("kind"))
                .containsExactly("action=login", "action=logout");
    }

    @Test
    void theHighConfidenceOnesAreAcceptedOrAmendedNotBoth() {
        // Asked for by the user: a yes/no box beside a text box let them both accept and amend; now one question,
        // answered with the button or in their own words.
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}", BuildToolCalls.context());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "1 is Authenticate: Logon "
                                                                                  + "by the src host"));
        final Map<String, Object> amended = call("{\"build\": \"acme\", \"confirm\": true}", user.context());
        // Nothing recorded until the agent sends them as the user said; the others still to confirm.
        assertThat(amended.get("recorded")).isEqualTo(List.of());
        assertThat(amended.get("amendments")).isEqualTo("1 is Authenticate: Logon by the src host");
        assertThat(String.valueOf(amended.get("hint"))).contains("send it again with the amendments applied",
                "from_user: true");
        assertThat(JsonValues.maps(notes().get(RecordSourceNotesTool.PROPOSED))).hasSize(2);

        // Sent as the user said: recorded, and the others shown in detail for the next form.
        final Map<String, Object> sent = call("{\"build\": \"acme\", \"proposals\": [{\"field\": \"action\", "
                                              + "\"value\": \"login\", \"description\": \"A user logged on\", "
                                              + "\"event_detail\": \"Authenticate\", \"action\": \"Logon\", "
                                              + "\"from_user\": true}]}", BuildToolCalls.context());
        assertThat(sent.get("recorded")).isEqualTo(List.of("action=login"));
        assertThat(String.valueOf(sent.get("table"))).startsWith("## Medium-confidence classifications");
        assertThat(String.valueOf(sent.get("hint"))).contains("confirm=true");
    }

    @Test
    void theTableOfHighConfidenceOnesShowsEachOutcome() {
        // Seen in chat 158: a successful and a failed logon both shown as "Authenticate: Logon" in the table
        // accepted together.
        final String table = ClassifyKindsTool.summary(List.of(
                Map.of("number", 1, "product", "Gateway", "title", "Login", "event_detail", "Authenticate",
                        "action", "Logon", "success", true),
                Map.of("number", 2, "product", "Gateway", "title", "Login failed", "event_detail", "Authenticate",
                        "action", "Logon", "success", false),
                Map.of("number", 3, "product", "AppFirewall", "title", "SQL injection", "event_detail", "Alert")));
        assertThat(table).contains("| 1 | Gateway | Login | Authenticate: Logon (Success) |",
                "| 2 | Gateway | Login failed | Authenticate: Logon (Failure) |",
                // No outcome given, none shown.
                "| 3 | AppFirewall | SQL injection | Alert |");
    }

    @Test
    void aFormatReadIntoTheFieldsOfOneEventIsAKindNotACatchAll() {
        // Seen in chat 161: the agent's splitter gave each sshd message form a variant of its own (failed password,
        // accepted), each one kind of event, and they were rated medium as catch-alls. A format whose records say
        // what happened in free text still is one.
        final String sshd = String.join("\n", List.of(
                "Nov  6 09:00:01 jump01 sshd[101]: Failed password for alice from 10.0.0.1 port 50001 ssh2",
                "Nov  6 09:00:02 jump01 sshd[102]: Accepted password for bob from 10.0.0.2 port 50002 ssh2",
                "Nov  6 09:00:03 jump01 sshd[103]: Failed password for carol from 10.0.0.3 port 50003 ssh2",
                "Nov  6 09:00:04 jump01 sshd[104]: Accepted password for dave from 10.0.0.4 port 50004 ssh2",
                "Nov  6 09:00:05 jump01 kernel: Out of memory: Killed process 1039 (java)",
                "Nov  6 09:00:06 jump01 kernel: usb 1-1: new high-speed USB device number 4 using xhci_hcd",
                "Nov  6 09:00:07 jump01 kernel: eth0: link up at 1000 Mbps full duplex")) + "\n";
        samples(13L, sshd);
        final String head = "^(\\S+ +\\d+ \\S+) (\\S+) ";
        final String sshdTail = " password for (\\S+) from (\\S+) port (\\d+) ssh2$";
        final List<String> sshdNames = List.of("time", "host", "pid", "user", "src", "port");
        final String splitter = JsonUtil.writeValueAsString(Map.of("kind", "alternatives", "parts", List.of(
                Map.of("kind", "regex", "variant", "sshd_failed", "names", sshdNames,
                        "pattern", head + "sshd\\[(\\d+)\\]: Failed" + sshdTail),
                Map.of("kind", "regex", "variant", "sshd_accepted", "names", sshdNames,
                        "pattern", head + "sshd\\[(\\d+)\\]: Accepted" + sshdTail),
                Map.of("kind", "regex", "variant", "kernel", "names", List.of("time", "host", "message"),
                        "pattern", head + "kernel: (.*)$"))), false);
        final Map<String, Object> listed = call("{\"build\": \"acme\", \"source\": \"Hosts\", \"stream_ids\": [13], "
                                                + "\"splitter\": " + splitter + "}", BuildToolCalls.context());
        final Map<String, Boolean> catchAll = new LinkedHashMap<>();
        list(listed.get("to_classify")).forEach(kind -> catchAll.put(String.valueOf(kind.get("value")),
                Boolean.TRUE.equals(kind.get("catch_all"))));
        assertThat(catchAll).as("%s", listed)
                .containsEntry("sshd_failed", false)
                .containsEntry("sshd_accepted", false)
                .containsEntry("kernel", true);
    }

    @Test
    void aCatchAllIsNotProposedAsUnknown() {
        // Seen in chat 160: two catch-all formats proposed as Unknown, the gateway's logons and the hosts' sudo
        // commands in them left for the mapping, never put to the user.
        final String mixed = THREE_KINDS + String.join("\n", List.of(
                "Nov  6 09:02:00 web1 cache warmed after 12 seconds",
                "Nov  6 09:02:05 web2 config reloaded from disk",
                "Nov  6 09:02:09 web1 disk 80% full on the data volume",
                "Nov  6 09:02:13 web3 worker restarted by the supervisor")) + "\n";
        samples(10L, mixed);
        final String splitter = JsonUtil.writeValueAsString(SplitterDryRun.inferSpec(mixed).spec().toMap(), false);
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [10], \"splitter\": " + splitter + "}",
                BuildToolCalls.context());
        assertThat(text("{\"build\": \"acme\", \"proposals\": [{\"field\": \"variant\", \"value\": \"syslog\", "
                        + "\"description\": \"Web host lines\", \"event_detail\": \"Unknown\", "
                        + "\"confidence\": \"medium\"}]}"))
                .startsWith("Nothing recorded: proposals[0]: variant=syslog is a catch_all kind")
                .contains("refine the splitter (build_data_splitter)");
        // Its sample is its own line as logged, though its value (the format's name) isn't in it.
        final Map<String, Object> shown = call("{\"build\": \"acme\", \"proposals\": [{\"field\": \"variant\", "
                                               + "\"value\": \"syslog\", \"description\": \"Web host lines\", "
                                               + "\"event_detail\": \"Alert\", \"confidence\": \"medium\"}]}",
                BuildToolCalls.context());
        assertThat(String.valueOf(shown.get("table"))).contains("```\nNov  6 09:02:00 web1 cache warmed after 12 "
                                                               + "seconds\n```");
    }

    @Test
    void anAnswerInWordsThatAcceptsIsTakenAsAccepting() {
        call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [7]}", BuildToolCalls.context());
        call("{\"build\": \"acme\", \"proposals\": " + PROPOSALS + "}", BuildToolCalls.context());
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("answer", "accept"));
        assertThat(call("{\"build\": \"acme\", \"confirm\": true}", user.context()).get("recorded"))
                .isEqualTo(List.of("action=login"));
    }

    @Test
    void aCatchAllKindIsAtMostMediumAndShownOnItsOwn() {
        // Asked for by the user: a format whose events the draft can't tell apart is one kind, a catch-all; the
        // agent rated those high in testing, so the user never saw them on their own.
        final String mixed = THREE_KINDS + String.join("\n", List.of(
                "Nov  6 09:02:00 web1 cache warmed after 12 seconds",
                "Nov  6 09:02:05 web2 config reloaded from disk",
                "Nov  6 09:02:09 web1 disk 80% full on the data volume",
                "Nov  6 09:02:13 web3 worker restarted by the supervisor")) + "\n";
        samples(10L, mixed);
        final SplitterDryRun.Inferred inferred = SplitterDryRun.inferSpec(mixed);
        assertThat(inferred.spec()).as("%s", inferred).isNotNull();
        final String splitter = JsonUtil.writeValueAsString(inferred.spec().toMap(), false);
        final Map<String, Object> listed = call("{\"build\": \"acme\", \"source\": \"Acme app\", \"stream_ids\": [10], "
                                                + "\"splitter\": " + splitter + "}", BuildToolCalls.context());
        final List<Map<String, Object>> catchAll = list(listed.get("to_classify")).stream()
                .filter(kind -> Boolean.TRUE.equals(kind.get("catch_all")))
                .toList();
        // The plain-text lines, told apart by nothing but their format; not the key-value lines' 'other' rule,
        // which leaves none of the sample's records.
        assertThat(catchAll).as("%s", listed).hasSize(1);
        assertThat(catchAll.getFirst()).containsEntry("value", "syslog").containsEntry("records", 4);
        assertThat(list(listed.get("to_classify"))).filteredOn(kind -> "login".equals(kind.get("value")))
                .allMatch(kind -> !kind.containsKey("catch_all"));
        assertThat(String.valueOf(listed.get("hint"))).contains("catch_all", "at most medium",
                "refine the splitter (build_data_splitter)");

        final String field = String.valueOf(catchAll.getFirst().get("field"));
        final String value = String.valueOf(catchAll.getFirst().get("value"));
        final String proposals = PROPOSALS.substring(0, PROPOSALS.length() - 1) + ", {\"field\": \"" + field
                                 + "\", \"value\": \"" + value + "\", \"title\": \"Web host message\", "
                                 + "\"description\": \"A web host's status message\", \"event_detail\": \"Alert\", "
                                 + "\"confidence\": \"high\"}]";
        final Map<String, Object> shown = call("{\"build\": \"acme\", \"proposals\": " + proposals + "}",
                BuildToolCalls.context());
        assertThat(shown.get("rated_medium")).as("%s", shown).isEqualTo(List.of(field + "=" + value));
        // Not in the high-confidence table: in the medium ones, shown on its own after.
        assertThat(String.valueOf(shown.get("table"))).contains("User logon").doesNotContain("Web host message");
        assertThat(JsonValues.maps(notes().get(RecordSourceNotesTool.PROPOSED)))
                .filteredOn(entry -> (field + "=" + value).equals(entry.get("kind")))
                .extracting(entry -> entry.get("confidence"))
                .containsExactly("medium");
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
        return notesTool.notesOf(BuildToolCalls.context(), "acme", null);
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
