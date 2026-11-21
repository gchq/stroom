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
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/// `catalogue_event_types` (stroom-ai/DESIGN.md, Event type catalogue, Contracts): the evidence listed, proposals
/// applied by TypeId and checked against every record, the catalogue kept in the build's source notes, and the user
/// confirming it a set at a time in short forms, the catch-all last.
class TestCatalogueEventTypesTool {

    private static final String BUILD = "acme";
    // The source notes doc, linked as the chat opens it (no section: the doc opens at its top).
    private static final String NOTES_LINK = "[acme source notes](?action=open-doc&docType=Documentation&docUuid=";
    private static final String TABLE_HEADER = "| Event type | Recognised by | Records | Translated as |";

    private final BuildWorld world = new BuildWorld();
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final SampleStreamTexts sampleStreamTexts = SampleStreamTextsMocks.mock();
    private final Consent consent = new Consent(() -> Mockito.mock(AiAuditLog.class));
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, new BuildMemory());
    private final EventVersions eventVersions = new EventVersions(heldSchemas, consent, new BuildMemory(),
            world::guard, world::records, () -> Mockito.mock(EventsTargetInference.class));
    private final RecordSourceNotesTool notesTool = new RecordSourceNotesTool(world.guard(), world.documentAccess,
            new DocumentWrites(world.documentAccess), eventVersions, plan);
    private final TranslationBuilds builds = Mockito.mock(TranslationBuilds.class);
    private final AtomicLong now = new AtomicLong(1_800_000_000_000L);
    private final CatalogueEventTypesTool tool = new CatalogueEventTypesTool(notesTool, sampleStreamTexts, consent,
            plan, builds, now::get);

    @BeforeEach
    void setUp() {
        Mockito.when(heldSchemas.eventVersions()).thenReturn(List.of("4.1.0"));
        Mockito.when(heldSchemas.eventSchema("4.1.0")).thenReturn(schema());
        Mockito.when(sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", sample()), List.of()));
    }

    @Test
    void theEvidenceIsListedAndNothingEntersTheCatalogueByItself() {
        final Map<String, Object> listed = call(Map.of("build", BUILD, "stream_ids", List.of(7)), plain());

        assertThat(listed).containsEntry("status", "listed");
        final Map<String, Object> evidence = map(listed.get("evidence"));
        assertThat(list(evidence.get("fields"))).extracting(field -> field.get("field")).contains("action")
                .doesNotContain("host", "level");
        assertThat(list(evidence.get("fields"))).filteredOn(field -> "action".equals(field.get("field")))
                .singleElement().satisfies(field -> assertThat(map(field.get("values"))).containsEntry("login", 8)
                        .containsEntry("purge", 1));
        assertThat(list(evidence.get("shapes"))).isNotEmpty()
                .allSatisfy(shape -> assertThat(shape).containsKeys("signature", "records"));
        assertThat(String.valueOf(evidence.get("examples"))).as("the rare record among the examples")
                .contains("action=purge");
        assertThat(list(evidence.get("seed"))).isNotEmpty()
                .allSatisfy(seeded -> assertThat(seeded).containsEntry("basis", "draft"));
        // The seed is evidence only: no catalogue yet, so the draft keeps its own rules.
        assertThat(SourceNotes.catalogue(notes())).isNull();
        assertThat(map(listed.get("catalogue")).get("event_types")).isEqualTo(List.of());
    }

    @Test
    void aBuildCataloguesOnceItsSamplesAreSurveyed() {
        facts.ownRaw = List.of(7L);
        facts.docs.add(FakeBuildFacts.doc("Feed", "f", "ACME-OWN"));

        assertThat(text(Map.of("build", BUILD, "stream_ids", List.of(7)), plain()))
                .contains("Survey first: survey_feed feed=ACME-OWN build=acme stream_ids=[7].");

        // The survey refused, nothing in the feed readable: catalogued from the samples, saying so.
        facts.unreadableSurveys.put("ACME-OWN", "stream 7 part 0: Unterminated string");
        assertThat((String) call(Map.of("build", BUILD, "stream_ids", List.of(7)), plain()).get("survey"))
                .startsWith("survey_feed couldn't read the feed ('ACME-OWN': stream 7 part 0: Unterminated string)");

        Mockito.when(builds.surveyStatesInBuild(BUILD)).thenReturn(List.of(Map.of("feed", "ACME-OWN")));
        assertThat(call(Map.of("build", BUILD, "stream_ids", List.of(7)), plain())).doesNotContainKey("survey")
                .containsEntry("status", "listed");
    }

    @Test
    void proposalsAreCheckedAgainstEveryRecordAndKeptInTheSourceNotes() {
        final Map<String, Object> checked = propose(List.of(
                withKeys(type("Authenticate-Logon", "Authenticate", "high", "login"), "action", "Logon",
                        "actor", "user"),
                withKeys(type("Authenticate-Logoff", "Authenticate", "high", "logout"), "action", "Logoff"),
                type("Export-Report", "Export", "medium", "export"),
                type("Delete-Records", "Delete", "low", "purge")));

        assertThat(checked).containsEntry("status", "checked").containsEntry("recorded", true);
        final Map<String, Object> check = map(checked.get("check"));
        assertThat(map(check.get("counts"))).containsEntry("Authenticate-Logon", Map.of("records", 8, "by_way",
                List.of(8))).containsEntry("Delete-Records", Map.of("records", 1, "by_way", List.of(1)));
        assertThat(list(check.get("overlaps"))).isEmpty();
        assertThat(map(check.get("uncatalogued"))).containsEntry("records", 0);
        // The set the user confirms first, as the chat will show it: the high-confidence ones summarised.
        assertThat(String.valueOf(checked.get("table"))).startsWith("**2 high-confidence event types**")
                .contains("- Authenticate: 2 event types, 16 records; Authenticate-Logon (8), Authenticate-Logoff (8)");
        assertThat(map(checked.get("catalogue")).get("event_types")).isEqualTo(List.of(
                Map.of("type_id", "Authenticate-Logon", "title", "Authenticate Logon", "status", "proposed"),
                Map.of("type_id", "Authenticate-Logoff", "title", "Authenticate Logoff", "status", "proposed"),
                Map.of("type_id", "Export-Report", "title", "Export Report", "status", "proposed"),
                Map.of("type_id", "Delete-Records", "title", "Delete Records", "status", "proposed")));

        // The source notes hold the catalogue (its JSON block) and its readable table, the catch-all's row last.
        final Catalogue kept = SourceNotes.catalogue(notes());
        assertThat(kept.eventTypes()).extracting(type -> type.typeId()).containsExactly("Authenticate-Logon",
                "Authenticate-Logoff", "Export-Report", "Delete-Records");
        assertThat(kept.catchAll().confirmed()).isFalse();
        final String doc = notesText();
        // Every row, how each is recognised in words and its records: the full table the summary refers to, each
        // type's placements typed (decided by the user 2026-11-21: recorded row by row in the source notes).
        assertThat(doc).contains("## Event types", "### Authenticate", "### Delete", CatalogueTables.NOTES_HEADER,
                "| `Authenticate-Logon` Authenticate Logon | Test: Authenticate-Logon | action is login | 8 | "
                + "Authenticate Logon, who acted: user; typed: 'Logon' -> Action; user -> User/Id | proposed; high; "
                + "from the agent's knowledge |",
                "| `Delete-Records` Delete Records | Test: Delete-Records | action is purge | 1 | Delete; typed: user "
                + "-> Object/Id | proposed; low; from the agent's knowledge |");
        final List<String> rows = doc.lines().filter(line -> line.startsWith("| ")).toList();
        assertThat(rows.getLast()).startsWith("| `Unknown` Uncatalogued records | - | records no event type "
                                              + "recognises | 0 | Unknown events, their fields kept as Data |");
    }

    @Test
    void placementsAreCheckedWithTheProposalsAndKeptInTheSourceNotesRowByRow() {
        // Decided by the user 2026-11-21 (chat 182: affected given as a field the records spell otherwise, accepted
        // unchecked).
        final Map<String, Object> misspelt = propose(List.of(withKeys(type("Delete-Records", "Delete", "low",
                "purge"), "placements", List.of(Map.of("path", "Document/Title", "field", "objet")))));

        assertThat(misspelt).containsEntry("recorded", false);
        assertThat(String.valueOf(misspelt.get("hint"))).contains("Nothing recorded",
                "no record has the field objet (the records have object)");

        final Map<String, Object> wrongPath = propose(List.of(withKeys(type("Delete-Records", "Delete", "low",
                "purge"), "placements", List.of(Map.of("path", "Document/Heading", "field", "object")))));
        assertThat(wrongPath).containsEntry("recorded", false);
        assertThat(String.valueOf(wrongPath.get("hint"))).contains("has no child 'Heading'");

        final Map<String, Object> placed = propose(List.of(withKeys(type("Delete-Records", "Delete", "low",
                "purge"), "placements", List.of(Map.of("path", "Document/Title", "field", "object")))));
        assertThat(placed).containsEntry("recorded", true);
        assertThat(map(map(map(placed.get("check")).get("typing")).get("Delete-Records")).get("fill"))
                .isEqualTo(Map.of("object", "1 of 1"));
        assertThat(SourceNotes.catalogue(notes()).eventType("Delete-Records").toJson().get("placements"))
                .isEqualTo(List.of(Map.of("path", "Document/Title", "field", "object")));
        assertThat(notesText()).contains("| Delete; typed: object -> Document/Title |");
    }

    @Test
    void anEventTypeKeepingMostOfItsFieldsAsDataTellsTheAgentWhatItCouldTypeAndRefusesNothing() {
        // Haiku eval run 1, case 56: only what the schema requires placed, and nothing told the agent what more it
        // could type (added 2026-11-22).
        Mockito.when(sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", sample().replace("object=rec-99", "object=rec-99 table=orders reason=retention "
                                                                     + "rows=40 owner=al days=90")), List.of()));
        final Map<String, Object> checked = propose(List.of(type("Delete-Records", "Delete", "low", "purge")));

        assertThat(checked).containsEntry("recorded", true);
        final Map<String, Object> typing = map(map(map(checked.get("check")).get("typing")).get("Delete-Records"));
        assertThat(typing).containsKeys("worth_a_look", "could_type");
        final Map<String, Object> couldType = map(typing.get("could_type"));
        assertThat(String.valueOf(couldType.get("data"))).contains("table (1/1, e.g. 'orders')");
        assertThat(String.valueOf(couldType.get("schema"))).startsWith("Object: ").contains("Outcome: ");
        // Worded strongly, with an example of the call for the event type named (asked for by the user 2026-11-22:
        // Haiku went on past the softer wording in eval case 56).
        assertThat(String.valueOf(checked.get("hint"))).contains("Most fields of Delete-Records are kept as Data",
                "check.typing.<TypeId>.could_type", "These fields should be typed",
                "belongs in a placement, not in Data", "only what has no home stays as Data",
                "E.g. catalogue_event_types proposals=[{\"op\": \"change\", \"type_id\": \"Delete-Records\", "
                + "\"placements\": [{\"path\": ");
        // The set summary the user is shown is unchanged.
        assertThat(checked).containsKey("table");
        assertThat(String.valueOf(checked.get("table"))).doesNotContain("could_type", "orders");
    }

    @Test
    void aSetLeavingAnElementTheSchemaRequiresEmptyIsNotPutToTheUserUntilPlaced() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                withKeys(type("Delete-Records", "Delete", "low", "purge"), "placements", List.of())));
        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);

        // The high set, whose event types place what the schema requires, is put to the user as before.
        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context()))
                .containsEntry("status", "needs_showing");
        final Map<String, Object> afterHigh = call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(afterHigh).containsEntry("status", "needs_placements")
                .containsEntry("confirmed_now", List.of("Authenticate-Logon"));
        assertThat(String.valueOf(afterHigh.get("unplaced"))).contains("EventDetail/Delete needs one of");

        // The low set waits: refused naming the element, and nothing shown to the user.
        final int shown = user.shown().size();
        assertThat(text(Map.of("build", BUILD, "confirm", true), user.context()))
                .startsWith("Not put to the user").contains("event type Delete-Records: EventDetail/Delete needs "
                                                             + "one of", "give a placement");
        assertThat(user.shown()).hasSize(shown);

        propose(List.of(Map.of("op", "change", "type_id", "Delete-Records", "placements",
                List.of(Map.of("path", "Document/Id", "field", "object")))));
        confirmAll(user);
        assertThat(SourceNotes.catalogue(notes()).eventType("Delete-Records").confirmed()).isTrue();
    }

    @Test
    void proposalsAddChangeAndRemoveByTypeIdAndAChangeIsTheAgentsAgain() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Export-Report", "Export", "high", "export")));
        confirmAll();

        final Map<String, Object> changed = propose(List.of(
                Map.of("op", "change", "type_id", "Export-Report", "description", "A report exported"),
                Map.of("op", "remove", "type_id", "Authenticate-Logon")));

        assertThat(changed).containsEntry("recorded", true);
        final Catalogue kept = SourceNotes.catalogue(notes());
        assertThat(kept.eventTypes()).singleElement().satisfies(type -> {
            assertThat(type.typeId()).isEqualTo("Export-Report");
            assertThat(type.description()).isEqualTo("A report exported");
            // Kept as it was otherwise; changed, the user confirms it again.
            assertThat(type.eventDetail()).isEqualTo("Export");
            assertThat(type.confirmed()).isFalse();
        });
        // What the catch-all takes has changed: it is put to the user again.
        assertThat(kept.catchAll().confirmed()).isFalse();
    }

    @Test
    void proposalsThatCantBeAppliedAreRefusedSayingWhy() {
        propose(List.of(type("Authenticate-Logoff", "Authenticate", "high", "logout")));

        final Map<String, Object> refused = propose(List.of(
                type("Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "export"),
                Map.of("op", "change", "type_id", "Update-Nothing", "title", "x"),
                withKeys(type("Export-Report", "Export", "high", "export"), "basis", "user_confirmed"),
                Map.of("op", "rename", "type_id", "Authenticate-Logoff")));

        assertThat(refused).containsEntry("recorded", false);
        assertThat(list(refused.get("refused"))).extracting(item -> String.valueOf(item.get("why")))
                .satisfiesExactly(
                        why -> assertThat(why).contains("Verb-Object"),
                        why -> assertThat(why).contains("already in the catalogue"),
                        why -> assertThat(why).contains("no event type 'Update-Nothing'"),
                        why -> assertThat(why).contains("basis user_confirmed is the user's"),
                        why -> assertThat(why).contains("op is one of add, change, remove"));
        assertThat(map(map(refused.get("check")).get("counts"))).containsOnlyKeys("Authenticate-Logoff");

        // Given no TypeId: the default, the event detail element and the first equals value, its first letter a
        // capital.
        assertThat(map(map(propose(List.of(type(null, "Authenticate", "high", "login"))).get("check"))
                .get("counts"))).containsKey("Authenticate-Login");
    }

    @Test
    void problemsAProposalBringsAreRefusedAndNothingIsRecorded() {
        propose(List.of(soleType()));

        // A second event type beside one recognised by every record: the catalogue's problem, nothing recorded.
        final String reply = JsonUtil.writeValueAsString(propose(List.of(
                type("Authenticate-Logoff", "Authenticate", "high", "logout"))), false);
        assertThat(reply).contains("Nothing recorded", "every record");
        // A field no record has: refused naming it.
        assertThat(JsonUtil.writeValueAsString(propose(List.of(withKeys(type("Export-Report", "Export", "high",
                "export"), "recognised_by", List.of(Map.of("when", List.of(Map.of("field", "acton", "equals",
                "export")), "names", List.of()))))), false)).contains("no record has the field acton");
        assertThat(SourceNotes.catalogue(notes()).eventTypes()).singleElement()
                .satisfies(type -> assertThat(type.recognisedBy()).isEmpty());
    }

    @Test
    void aFieldTheGivenReadingExtractsIsOneTheConditionsMayTest() {
        // Seen in the reference eval (cases 11, 16, 19 and others): the records read only as a fresh draft reads
        // them, a field the source's extraction gives was refused as "no record has the field".
        final List<Map<String, Object>> proposals = List.of(numbered());
        assertThat(JsonUtil.writeValueAsString(propose(proposals), false))
                .contains("no record has the field record_number");

        final Map<String, Object> checked = call(Map.of("build", BUILD, "stream_ids", List.of(7), "proposals",
                proposals, "reading", Map.of("extract", List.of(recordNumber()))), plain());

        assertThat(checked).containsEntry("recorded", true);
        assertThat(JsonUtil.writeValueAsString(checked, false)).doesNotContain("no record has the field");
        // Kept for the calls after it (the confirmation's), which don't give it again.
        final Map<String, Object> listed = call(Map.of("build", BUILD, "stream_ids", List.of(7)), plain());
        assertThat(listed).containsKey("check");
        assertThat(JsonUtil.writeValueAsString(listed.get("check"), false)).doesNotContain("no record has the field");
    }

    @Test
    void theBuildsLatestMappingReadsTheRecordsWhenNoReadingIsGiven() {
        Mockito.when(builds.latestMapping(Mockito.any(), Mockito.eq(BUILD))).thenReturn(Optional.of(Map.of(
                "input", "data_splitter", "extract", List.of(recordNumber()), "events", List.of())));

        final Map<String, Object> checked = propose(List.of(numbered()));

        assertThat(checked).containsEntry("recorded", true);
        assertThat(JsonUtil.writeValueAsString(checked, false)).doesNotContain("no record has the field");
    }

    @Test
    void theReadingAnEarlierCallGaveWinsOverTheBuildsLatestMapping() {
        // Seen in eval case 69: the catalogue told to read each item of a batch (reading for_each), a draft made
        // before it reading whole batches; the calls after it read as the draft did, and the reading given was lost.
        final List<Map<String, Object>> proposals = List.of(numbered());
        assertThat(call(Map.of("build", BUILD, "stream_ids", List.of(7), "proposals", proposals, "reading",
                Map.of("extract", List.of(recordNumber()))), plain())).containsEntry("recorded", true);
        Mockito.when(builds.latestMapping(Mockito.any(), Mockito.eq(BUILD))).thenReturn(Optional.of(Map.of(
                "input", "data_splitter", "events", List.of())));

        final Map<String, Object> listed = call(Map.of("build", BUILD, "stream_ids", List.of(7)), plain());

        assertThat(listed).containsKey("check");
        assertThat(JsonUtil.writeValueAsString(listed.get("check"), false)).doesNotContain("no record has the field");
    }

    @Test
    void theBuildsLatestMappingIsReadOnlyWhenNoCallGaveAReading() {
        // No reading given by any call: the latest mapping's, over the draft's own.
        Mockito.when(builds.latestMapping(Mockito.any(), Mockito.eq(BUILD))).thenReturn(Optional.of(Map.of(
                "input", "data_splitter", "extract", List.of(recordNumber()), "events", List.of())));

        assertThat(JsonUtil.writeValueAsString(propose(List.of(numbered())), false))
                .doesNotContain("no record has the field");
    }

    @Test
    void aReadingIsOnlyHowRecordsAreRead() {
        assertThat(text(Map.of("build", BUILD, "stream_ids", List.of(7), "reading", Map.of("events", List.of(),
                "json_fields", List.of("msg"))), plain()))
                .contains("reading takes only how a record is read (input, ", "not events");
        assertThat(text(Map.of("build", BUILD, "stream_ids", List.of(7), "reading", Map.of("extract", List.of(
                Map.of("field", "object")))), plain())).contains("reading can't be read");
    }

    @Test
    void anOverlapIsRecordedButRefusesConfirmation() {
        final Map<String, Object> checked = propose(List.of(type("Authenticate-Logon", "Authenticate", "high",
                "login"), withKeys(type("View-Records", "View", "medium", null), "recognised_by", List.of(
                Map.of("when", List.of(Map.of("field", "user", "equals", "user1")), "names", List.of())))));

        assertThat(checked).containsEntry("recorded", true);
        assertThat(list(map(checked.get("check")).get("overlaps"))).isNotEmpty();
        assertThat(String.valueOf(checked.get("hint"))).contains("overlap");
        assertThat(text(Map.of("build", BUILD, "confirm", true), user(McpAnswers.ACCEPT).context()))
                .contains("can't be confirmed", "overlap", "Authenticate-Logon", "View-Records");
    }

    @Test
    void aSeededEventTypeThatMayBeSeveralIsNotPutToTheUserUntilSaidToBeOne() {
        Mockito.when(sampleStreamTexts.read(List.of(8L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 8", loggers()), List.of()));
        final Map<String, Object> checked = call(Map.of("build", BUILD, "stream_ids", List.of(8), "proposals",
                List.of(Map.of("op", "add", "type_id", "Process-SecurityLogger", "title", "Security logger",
                        "event_detail", "Process", "basis", "draft", "confidence", "low", "placements",
                        List.of(Map.of("path", "Action", "value", "Execute"), Map.of("path", "Type", "value",
                                "Application"), Map.of("path", "Command", "field", "logger")), "recognised_by",
                        List.of(Map.of("when", List.of(Map.of("field", "logger", "equals",
                                "com.acme.SecurityLogger")), "names", List.of()))))), plain());
        assertThat(list(map(checked.get("check")).get("several"))).singleElement()
                .satisfies(several -> assertThat(several).containsEntry("blocking", true));

        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);
        assertThat(text(Map.of("build", BUILD, "confirm", true), user.context()))
                .contains("Process-SecurityLogger", "one_type_because");
        assertThat(user.shown()).isEmpty();

        call(Map.of("build", BUILD, "proposals", List.of(Map.of("op", "change", "type_id",
                "Process-SecurityLogger", "one_type_because", "every security event is translated alike"))),
                plain());
        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context()))
                .containsEntry("status", "needs_showing");
    }

    @Test
    void theUserConfirmsTheHighThenTheMediumThenTheLowOnesAFormEachThenTheCatchAll() {
        // A record no event type recognises, for the catch-all's form.
        withAnUncataloguedRecord();
        final List<Map<String, Object>> proposals = new ArrayList<>(List.of(
                type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout")));
        proposals.add(type("Export-Report", "Export", "medium", "export"));
        proposals.add(withKeys(type("Delete-Records", "Delete", "low", "purge"), "basis", "draft"));
        propose(proposals);
        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);

        // The table first, shown in the chat as it is; then its form, short and plain.
        final Map<String, Object> shown = call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(shown).containsEntry("status", "needs_showing");
        // The high-confidence ones summarised, whatever their number; every row in the source notes.
        assertThat(String.valueOf(shown.get("table"))).startsWith("**2 high-confidence event types**")
                .contains("Authenticate-Logon", "Authenticate-Logoff", NOTES_LINK)
                .doesNotContain("Export-Report", TABLE_HEADER);
        assertThat(map(map(shown.get("next")).get("call"))).containsEntry("tool", "catalogue_event_types");
        assertThat(user.shown()).isEmpty();

        final Map<String, Object> high = call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(user.shown()).singleElement().satisfies(form -> assertThat(form)
                .startsWith("Accept all 2 event types, or type your amendments, or ask about any of them")
                .doesNotContain("|")
                .contains("summarised in the chat", "every row: [acme source notes]("));
        assertThat(high).containsEntry("status", "needs_showing").containsEntry("confirmed_now",
                List.of("Authenticate-Logon", "Authenticate-Logoff"));
        // The medium ones as a summary too, each worth a look (decided by the user 2026-11-20).
        assertThat(String.valueOf(high.get("table"))).startsWith("**1 medium-confidence event type**")
                .contains("| `Export-Report` Export Report | action is export | 8 | Export | medium confidence |")
                .doesNotContain("Delete-Records", TABLE_HEADER + "\n");

        call(Map.of("build", BUILD, "confirm", true), user.context());
        // The catch-all last: the uncatalogued records.
        final Map<String, Object> catchAll = call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(catchAll).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(catchAll.get("table"))).contains("Uncatalogued records");
        final Map<String, Object> done = call(Map.of("build", BUILD, "confirm", true, "catch_all",
                Map.of("allow_unknown", "The user keeps the rest as Unknown")), user.context());

        assertThat(done).containsEntry("status", "confirmed");
        assertThat(user.shown()).hasSize(4);
        assertThat(user.shown().getLast()).contains("uncatalogued records");
        final Catalogue kept = SourceNotes.catalogue(notes());
        assertThat(kept.eventTypes()).allSatisfy(type -> assertThat(type.confirmed()).isTrue());
        // The seed's, confirmed by the user, is the user's; the agent's keeps its basis.
        assertThat(kept.eventType("Delete-Records").basis()).isEqualTo("user_confirmed");
        assertThat(kept.eventType("Export-Report").basis()).isEqualTo("agent_knowledge");
        assertThat(kept.catchAll()).isEqualTo(new Catalogue.CatchAll("confirmed",
                "The user keeps the rest as Unknown", null));
    }

    @Test
    void theSetsFormOffersItsAcceptanceAsAnAnswerToChooseNotAFilledBox() {
        // Seen in chat 182: a box filled with "Accept all 6 event types", no way to accept but to send it as typed.
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout")));
        final BuildToolCalls.FormUser user = user(Map.of("amendments", "Accept all 2 event types"));
        call(Map.of("build", BUILD, "confirm", true), user.context());

        final Map<String, Object> chosen = call(Map.of("build", BUILD, "confirm", true), user.context());

        final Map<String, Object> field = map(map(user.schemas().getFirst().get("properties")).get("amendments"));
        assertThat(field).containsEntry("type", "string")
                .containsEntry("examples", List.of("Accept all 2 event types"))
                .doesNotContainKey("default");
        // Choosing it confirms the set, as accepting the form with the box left empty does.
        assertThat(chosen).containsEntry("confirmed_now", List.of("Authenticate-Logon", "Authenticate-Logoff"));
        assertThat(SourceNotes.catalogue(notes()).eventTypes()).allSatisfy(type -> assertThat(type.confirmed())
                .isTrue());
    }

    @Test
    void withEveryRecordCataloguedTheCatchAllStandsWithNoForm() {
        // Seen in chat 182: a form asking the user to keep 0 uncatalogued records as Unknown.
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout"),
                type("Export-Report", "Export", "high", "export"),
                type("Delete-Records", "Delete", "high", "purge")));
        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);
        call(Map.of("build", BUILD, "confirm", true), user.context());

        final Map<String, Object> done = call(Map.of("build", BUILD, "confirm", true), user.context());

        assertThat(done).containsEntry("status", "confirmed").containsEntry("confirmed_now", List.of(
                "Authenticate-Logon", "Authenticate-Logoff", "Export-Report", "Delete-Records"));
        assertThat(String.valueOf(done.get("hint"))).contains("No sampled record is uncatalogued",
                "the catch-all stands as proposed", "don't ask the user about it");
        assertThat(user.shown()).singleElement().satisfies(form -> assertThat(form).startsWith("Accept all 4"));
        assertThat(SourceNotes.catalogue(notes()).catchAll().confirmed()).isTrue();
        // Asked again, nothing more is put to the user.
        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context())).containsEntry("status",
                "confirmed");
        assertThat(user.shown()).hasSize(1);
    }

    @Test
    void withRecordsUncataloguedTheCatchAllIsStillAskedInItsForm() {
        // The purge record left to the catch-all.
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout"),
                type("Export-Report", "Export", "high", "export")));
        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);
        call(Map.of("build", BUILD, "confirm", true), user.context());

        final Map<String, Object> catchAll = call(Map.of("build", BUILD, "confirm", true), user.context());

        assertThat(catchAll).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(catchAll.get("table"))).contains("Uncatalogued records");
        assertThat(SourceNotes.catalogue(notes()).catchAll().confirmed()).isFalse();
        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context())).containsEntry("status",
                "confirmed");
        assertThat(user.shown()).hasSize(2);
        assertThat(user.shown().getLast()).contains("uncatalogued records");
        assertThat(SourceNotes.catalogue(notes()).catchAll().confirmed()).isTrue();
    }

    @Test
    void allTheMediumConfidenceOnesArePutToTheUserInOneForm() {
        final StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            lines.append("time=2026-11-06T09:%02d:00Z code=c%02d user=user%d\n".formatted(i, i % 25, i % 4));
        }
        Mockito.when(sampleStreamTexts.read(List.of(9L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 9", lines.toString()), List.of()));
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (int k = 0; k < 25; k++) {
            proposals.add(withKeys(type("Process-Code%02d".formatted(k), "Process", "medium", null), "recognised_by",
                    List.of(Map.of("when", List.of(Map.of("field", "code", "equals", "c%02d".formatted(k))),
                            "names", List.of()))));
        }
        assertThat(call(Map.of("build", BUILD, "stream_ids", List.of(9), "proposals", proposals), plain()))
                .as("recorded").containsEntry("recorded", true);

        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);
        confirmAll(user);

        // Seen: 304 kinds would have been some 30 forms in sets of ten.
        assertThat(user.shown().stream().filter(form -> form.startsWith("Accept all")).toList())
                .extracting(form -> form.substring(0, form.indexOf(','))).containsExactly(
                        "Accept all 25 event types");
    }

    @Test
    void aConfidenceWithNoEventTypesIsPassedOver() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Delete-Records", "Delete", "low", "purge")));
        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);

        confirmAll(user);

        assertThat(user.shown()).hasSize(3);
        assertThat(user.shown().get(0)).startsWith("Accept all 1 event type,").contains("1 high-confidence");
        assertThat(user.shown().get(1)).startsWith("Accept all 1 event type,").contains("1 low-confidence");
        assertThat(user.shown().get(2)).contains("uncatalogued records");
    }

    @Test
    void amendmentsTypedInTheFormComeBackForTheAgentAndTheSetIsPutAgain() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout")));
        final String amendment = "logout is a session timeout";
        final Answering user = new Answering(form -> form.contains("event types")
                ? Map.of("amendments", amendment)
                : Map.of("value", true));

        call(Map.of("build", BUILD, "confirm", true), user.context());
        final Map<String, Object> amended = call(Map.of("build", BUILD, "confirm", true), user.context());

        assertThat(amended).containsEntry("status", "needs_confirmation").containsEntry("amendments", amendment);
        assertThat(map(map(amended.get("next")).get("call")).get("arguments")).isEqualTo(Map.of("build", BUILD,
                "proposals", "<the user's amendments>"));
        assertThat(SourceNotes.catalogue(notes()).eventTypes()).noneSatisfy(type -> assertThat(type.confirmed())
                .isTrue());
        // Not put to them again until the amendments are proposed.
        assertThat(text(Map.of("build", BUILD, "confirm", true), user.context()))
                .contains("The user wrote in this set's form", amendment);

        propose(List.of(Map.of("op", "change", "type_id", "Authenticate-Logoff", "title", "Session timeout")));
        final Map<String, Object> again = call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(again).containsEntry("status", "needs_showing");
        // The amended event type is worth a look, saying what changed, so the user can check it landed; every row
        // is in the source notes, which the summary refers to.
        assertThat(String.valueOf(again.get("table"))).contains(NOTES_LINK,
                "| `Authenticate-Logoff` Session timeout | action is logout | 8 | Authenticate | changed since shown: "
                + "title |");
        assertThat(notesText()).contains("| `Authenticate-Logoff` Session timeout |");
    }

    @Test
    void anAmendmentTheProposalsCannotMakeLeavesTheSetToBePutAgain() {
        // Seen in eval case 61 on Haiku (2026-11-20): the user amended a rule to a condition the check refuses; the
        // agent asked them in the chat, they chose the rule as it was, and the agent proposed it. The set unchanged,
        // every confirm=true replayed the amendment, about ten times, and the build stopped.
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout")));
        final String amendment = "logout only where the source is the portal";
        final Answering user = new Answering(form -> form.contains("event types")
                ? Map.of("amendments", amendment)
                : Map.of("value", true));
        call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context())).containsEntry("amendments",
                amendment);

        // The user's answer in the chat: the rule as it was.
        final Map<String, Object> proposed = propose(List.of(Map.of("op", "change", "type_id",
                "Authenticate-Logoff", "recognised_by", List.of(Map.of("when", List.of(Map.of("field", "action",
                        "equals", "logout")), "names", List.of())))));
        assertThat(proposed).containsEntry("recorded", false);
        assertThat(String.valueOf(proposed.get("hint"))).contains("puts the set to the user again");

        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context())).containsEntry("status",
                "needs_showing");
    }

    @Test
    void anEventTypeIsExplainedWithExamplesAndNothingIsChanged() {
        propose(List.of(withKeys(type("Authenticate-Logon", "Authenticate", "high", "login"), "action", "Logon",
                        "actor", "user"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout"),
                withKeys(type("Delete-Records", "Delete", "low", "purge"), "doubt", "seen once")));
        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);
        call(Map.of("build", BUILD, "confirm", true), user.context());
        final String notesBefore = notesText();

        final Map<String, Object> explained = call(Map.of("build", BUILD, "explain", List.of("authenticate-logon",
                "Delete-Records")), user.context());

        assertThat(explained).containsEntry("status", "explained");
        assertThat(list(explained.get("event_types"))).hasSize(2).first().satisfies(one -> {
            assertThat(one).containsEntry("type_id", "Authenticate-Logon")
                    .containsEntry("title", "Authenticate Logon")
                    .containsEntry("description", "Test: Authenticate-Logon")
                    .containsEntry("recognised_by", "action is login")
                    .containsEntry("translated_as", "Authenticate Logon, who acted: user; typed: 'Logon' -> Action; "
                                                    + "user -> User/Id")
                    .containsEntry("records", 8)
                    .containsEntry("status", "proposed, in the set put to the user");
            assertThat(list(one.get("examples"))).hasSize(CatalogueEventTypesTool.EXAMPLES_GIVEN)
                    .allSatisfy(example -> {
                        assertThat(String.valueOf(example.get("text"))).contains("\"action\": \"login\"");
                        assertThat(map(example.get("values"))).containsEntry("action", "login");
                    });
        });
        assertThat(list(explained.get("event_types")).getLast()).containsEntry("doubt", "seen once")
                .containsEntry("records", 1);
        assertThat(String.valueOf(explained.get("hint"))).contains("Nothing was changed", "confirm=true");
        // Where every row is, linked for the answer.
        assertThat(String.valueOf(explained.get("every_row"))).contains(NOTES_LINK);
        assertThat(map(map(explained.get("next")).get("call")).get("arguments")).isEqualTo(Map.of("build", BUILD,
                "confirm", true));
        // No side effect: the notes (catalogue, the set shown and its marks) as they were, and the form follows.
        assertThat(notesText()).isEqualTo(notesBefore);
        call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(user.shown()).singleElement().satisfies(form -> assertThat(form).startsWith("Accept all 2"));
    }

    @Test
    void aGroupOfTheSummaryIsExplainedByItsActionElementWithItsMembers() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout"),
                type("Export-Report", "Export", "high", "export")));

        final Map<String, Object> explained = call(Map.of("build", BUILD, "explain", "authenticate"), plain());

        assertThat(explained).doesNotContainKey("event_types");
        assertThat(list(explained.get("groups"))).singleElement().satisfies(group -> {
            assertThat(group).containsEntry("group", "Authenticate").containsEntry("event_types", 2)
                    .containsEntry("records", 16);
            assertThat(list(group.get("members"))).extracting(member -> member.get("type_id"))
                    .containsExactly("Authenticate-Logon", "Authenticate-Logoff");
            assertThat(list(group.get("members")).getFirst()).containsEntry("recognised_by", "action is login");
        });
        // The uncatalogued records too, by the catch-all's name.
        assertThat(map(call(Map.of("build", BUILD, "explain", "Unknown"), plain()).get("uncatalogued_records")))
                .containsEntry("records", 1).containsEntry("translated_as", "Unknown events, their fields kept as "
                                                                            + "Data");
    }

    @Test
    void aNameExplainDoesNotKnowIsRefusedWithTheNearestAndNothingIsChanged() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login"),
                type("Authenticate-Logoff", "Authenticate", "high", "logout")));
        final String notesBefore = notesText();

        assertThat(text(Map.of("build", BUILD, "explain", List.of("Authenticate-Logout")), plain()))
                .contains("No event type or group 'Authenticate-Logout'",
                        "nearest: Authenticate-Logon, Authenticate-Logoff",
                        "Nothing was changed");
        assertThat(text(Map.of("build", BUILD, "explain", "Authenticate-Logon", "confirm", true), plain()))
                .contains("given alone");
        assertThat(notesText()).isEqualTo(notesBefore);
    }

    @Test
    void aMessageTypedInsteadOfTheFormIsTheAgentsToReadAndNeverAnAcceptance() {
        // Decided by the user 2026-11-20: a question is answered in the chat and the set put again; a change is
        // proposed; acceptance only through the form.
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));
        final String question = "What do the logons look like?";
        final PuttingAside user = new PuttingAside(question);
        call(Map.of("build", BUILD, "confirm", true), user.context());

        final String told = text(Map.of("build", BUILD, "confirm", true), user.context());

        assertThat(told).contains("sent a message instead", question, "explain=", "confirm=true puts the set to "
                                  + "them again", "ask them", "never take their message as acceptance");
        assertThat(SourceNotes.catalogue(notes()).eventTypes()).noneSatisfy(type -> assertThat(type.confirmed())
                .isTrue());
        assertThat(call(Map.of("build", BUILD, "explain", "Authenticate-Logon"), user.context()))
                .containsEntry("status", "explained");

        // The set put to them again: now they accept, in the form.
        final Map<String, Object> again = call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(again).containsEntry("confirmed_now", List.of("Authenticate-Logon"));
        assertThat(user.asked()).isEqualTo(2);
    }

    @Test
    void aQuestionWrittenInTheFormIsAnsweredAndTheSetPutAgainAsItIs() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));
        final String question = "how is a logon recognised?";
        final List<String> answers = new ArrayList<>(List.of(question));
        final Answering user = new Answering(form -> form.contains("event types") && !answers.isEmpty()
                ? Map.of("amendments", answers.removeFirst())
                : Map.of("value", true));
        call(Map.of("build", BUILD, "confirm", true), user.context());
        final Map<String, Object> written = call(Map.of("build", BUILD, "confirm", true), user.context());
        assertThat(String.valueOf(written.get("hint"))).contains(question, "A question about any of them",
                "answered=true");

        assertThat(text(Map.of("build", BUILD, "confirm", true), user.context())).contains("If it is a question",
                "answered=true");
        final Map<String, Object> explained = call(Map.of("build", BUILD, "explain", "Authenticate-Logon"),
                user.context());
        assertThat(map(map(explained.get("next")).get("call")).get("arguments")).isEqualTo(Map.of("build", BUILD,
                "confirm", true, "answered", true));

        // answered=true puts the set to them again as it is (shown first), never accepting it for them.
        final Map<String, Object> again = call(Map.of("build", BUILD, "confirm", true, "answered", true),
                user.context());
        assertThat(again).containsEntry("status", "needs_showing");
        assertThat(SourceNotes.catalogue(notes()).eventTypes().getFirst().confirmed()).isFalse();
        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context())).containsEntry("confirmed_now",
                List.of("Authenticate-Logon"));
    }

    @Test
    void aFormShownLongAfterItsTableShowsTheTableAgainFirst() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));
        final BuildToolCalls.FormUser user = user(McpAnswers.ACCEPT);
        call(Map.of("build", BUILD, "confirm", true), user.context());

        now.addAndGet(5 * 60_000L);

        assertThat(call(Map.of("build", BUILD, "confirm", true), user.context())).containsEntry("status",
                "needs_showing");
        assertThat(user.shown()).isEmpty();
    }

    @Test
    void aClientWithoutFormsConfirmsThroughNeedsReviewAndItsReviewId() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));

        final Map<String, Object> review = call(Map.of("build", BUILD, "confirm", true), plain());
        assertThat(review).containsEntry("status", "needs_review").containsKey("review_id");
        // The same summary as a client with forms is shown.
        assertThat(String.valueOf(review.get("table"))).startsWith("**1 high-confidence event type**");
        // Not its id: shown again.
        assertThat(call(Map.of("build", BUILD, "confirm", true, "reviewed", "true"), plain()))
                .containsEntry("status", "needs_review");

        final Map<String, Object> catchAll = call(Map.of("build", BUILD, "confirm", true, "reviewed",
                review.get("review_id")), plain());
        assertThat(catchAll).containsEntry("status", "needs_review");
        assertThat(String.valueOf(catchAll.get("table"))).contains("Uncatalogued records");
        assertThat(call(Map.of("build", BUILD, "confirm", true, "reviewed", catchAll.get("review_id")), plain()))
                .containsEntry("status", "confirmed");
        assertThat(SourceNotes.catalogue(notes()).catchAll().confirmed()).isTrue();
    }

    @Test
    void aChatWhoseUserIsAwayConfirmsNothing() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));
        final ToolCallContext away = ToolCallContext.builder(ToolSession.chat(1))
                .userQuestions(UserQuestions.NONE)
                .build();

        assertThat(text(Map.of("build", BUILD, "confirm", true), away)).contains("away from the chat");
        assertThat(SourceNotes.catalogue(notes()).eventTypes().getFirst().confirmed()).isFalse();
    }

    @Test
    void aDeclinedFormConfirmsNothing() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));
        final BuildToolCalls.FormUser declining = BuildToolCalls.declining();
        call(Map.of("build", BUILD, "confirm", true), declining.context());

        assertThat(text(Map.of("build", BUILD, "confirm", true), declining.context())).contains("did not agree");
        assertThat(SourceNotes.catalogue(notes()).eventTypes().getFirst().confirmed()).isFalse();
    }

    @Test
    void aDeclinedCatchAllTellsTheAgentToAskTheUserAndTheirAnswerIsPutToThemInItsForm() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));
        final BuildToolCalls.FormUser accepting = user(McpAnswers.ACCEPT);
        call(Map.of("build", BUILD, "confirm", true), accepting.context());
        final Map<String, Object> catchAll = call(Map.of("build", BUILD, "confirm", true), accepting.context());
        assertThat(String.valueOf(catchAll.get("table"))).contains("Uncatalogued records", "Unknown events");

        final String declined = text(Map.of("build", BUILD, "confirm", true), BuildToolCalls.declining().context());

        assertThat(declined).contains("The user declined the catch-all", "Ask the user in the chat what those "
                                      + "records should become", "op: add", "catch_all={translation:",
                "the only place it is confirmed");
        assertThat(SourceNotes.catalogue(notes()).catchAll().confirmed()).isFalse();

        // Their answer in the chat: another event for those records, put to them in the catch-all's form.
        final Map<String, Object> answer = Map.of("build", BUILD, "confirm", true, "catch_all", Map.of(
                "translation", Map.of("type_id", "Process-Records", "event_detail", "Process", "description",
                        "Other records processed")));
        final Map<String, Object> shown = call(answer, accepting.context());
        assertThat(shown).containsEntry("status", "needs_showing");
        assertThat(String.valueOf(shown.get("table"))).contains("Process-Records events (Process)");
        final Map<String, Object> done = call(answer, accepting.context());

        assertThat(done).containsEntry("status", "confirmed");
        assertThat(accepting.shown().getLast()).contains("Process-Records events (Process)");
        assertThat(SourceNotes.catalogue(notes()).catchAll()).isEqualTo(new Catalogue.CatchAll("confirmed", null,
                new Catalogue.CatchAll.Translation("Process-Records", "Process", null, "Other records processed")));
    }

    @Test
    void aCatchAllTranslationThatCantBeWrittenIsRefused() {
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));

        assertThat(text(Map.of("build", BUILD, "confirm", true, "catch_all", Map.of("translation",
                Map.of("type_id", "Records"))), user(McpAnswers.ACCEPT).context()))
                .contains("The catch-all's translation can't be written", "type_id", "event_detail")
                .contains("Nothing was changed");
    }

    @Test
    void whatAnEventTypeActsOnIsWrittenOutToTheLeafItsValueGoesIn() {
        // As record_source_notes wrote it for its events: Document becomes Document/Id, as the translation writes it.
        propose(List.of(withKeys(type("Delete-Records", "Delete", "low", "purge"), "affected", "object",
                "affected_as", "Document")));

        assertThat(SourceNotes.catalogue(notes()).eventType("Delete-Records").affectedAs()).isEqualTo("Document/Id");
    }

    @Test
    void anEventTypeNotInTheSampleIsKeptAndSaidToBe() {
        final Map<String, Object> checked = propose(List.of(type("Authenticate-Logon", "Authenticate", "high",
                "login"), withKeys(type("Unlock-Account", "Update", "high", "unlock"), "basis", "documentation")));

        assertThat(String.valueOf(checked.get("not_in_sample"))).contains("Unlock-Account", "not in the sample");
        assertThat(SourceNotes.catalogue(notes()).eventType("Unlock-Account")).isNotNull();
    }

    @Test
    void aSoleEventTypeIsRecognisedByEveryRecordAndTheCatchAllStillConfirmed() {
        final Map<String, Object> sole = propose(List.of(soleType()));
        assertThat(map(map(sole.get("check")).get("counts"))).containsOnlyKeys("Authenticate-Logon");
        assertThat(list(map(sole.get("check")).get("problems"))).isEmpty();

        confirmAll();

        final Catalogue kept = SourceNotes.catalogue(notes());
        assertThat(kept.catchAll().confirmed()).isTrue();
        assertThat(kept.eventTypes()).singleElement().satisfies(type -> assertThat(type.confirmed()).isTrue());
    }

    @Test
    void theNotesKeepOneDocWhateverTheSourceIsCalledAndRecordSourceNotesKeepsTheCatalogue() {
        notesTool.execute(BuildToolCalls.call(RecordSourceNotesTool.NAME, JsonUtil.writeValueAsString(Map.of(
                "build", BUILD, "source", BUILD, "summary", "A records app's audit log."), false)), plain());
        propose(List.of(type("Authenticate-Logon", "Authenticate", "high", "login")));
        // Notes recorded again afterwards keep the catalogue.
        notesTool.execute(BuildToolCalls.call(RecordSourceNotesTool.NAME, JsonUtil.writeValueAsString(Map.of(
                "build", BUILD, "source", BUILD, "summary", "The audit log.", "fields", List.of(Map.of("field",
                        "user", "meaning", "Who acted"))), false)), plain());

        final Map<String, Object> notes = notes();
        assertThat(notes).containsEntry("summary", "The audit log.");
        assertThat(SourceNotes.catalogue(notes).eventType("Authenticate-Logon")).isNotNull();
        assertThat(world.guard().folderContents(BUILD).stream()
                .filter(doc -> doc.name().endsWith(SourceNotes.NOTES_SUFFIX))).hasSize(1);
    }


    // --------------------------------------------------------------------------------


    // Every set confirmed by a user who accepts each form, the catch-all with a reason.
    private void confirmAll() {
        confirmAll(user(McpAnswers.ACCEPT));
    }

    private void confirmAll(final BuildToolCalls.FormUser user) {
        for (int round = 0; round < 40; round++) {
            final Map<String, Object> step = call(Map.of("build", BUILD, "confirm", true, "catch_all",
                    Map.of("allow_unknown", "Kept as Unknown")), user.context());
            if ("confirmed".equals(step.get("status"))) {
                return;
            }
        }
        throw new AssertionError("The catalogue was never confirmed");
    }

    // The sample with one more record, whose action no event type the tests propose recognises.
    private void withAnUncataloguedRecord() {
        Mockito.when(sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", sample() + "time=2026-11-06T09:31:00Z action=archive user=user1 host=web2 "
                                   + "level=INFO src=10.0.0.2 object=rec-98\n"), List.of()));
    }

    private Map<String, Object> propose(final List<Map<String, Object>> proposals) {
        return call(Map.of("build", BUILD, "stream_ids", List.of(7), "proposals", proposals), plain());
    }

    // An event type as the agent proposes it, recognised by its action's value (none: no way given).
    private static Map<String, Object> type(final String typeId,
                                            final String eventDetail,
                                            final String confidence,
                                            final String action) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("op", "add");
        if (typeId != null) {
            out.put("type_id", typeId);
        }
        out.put("title", typeId == null
                ? eventDetail
                : typeId.replace('-', ' '));
        out.put("description", "Test: " + typeId);
        out.put("event_detail", eventDetail);
        out.put("basis", "agent_knowledge");
        out.put("confidence", confidence);
        // What the schema requires of the action element, placed (decided by the user 2026-11-21: a set isn't put to
        // the user while an event type leaves one empty).
        required(eventDetail, out);
        if (action != null) {
            out.put("recognised_by", List.of(Map.of("when", List.of(Map.of("field", "action", "equals", action)),
                    "names", List.of())));
        }
        return out;
    }

    // The placements (and Action) the schema requires of an action element, for the tests whose subject is
    // something else: the user field every sample has as the object.
    private static void required(final String eventDetail, final Map<String, Object> type) {
        switch (String.valueOf(eventDetail)) {
            case "Authenticate" -> type.put("placements", List.of(Map.of("path", "Action", "value", "Logon"),
                    Map.of("path", "User/Id", "field", "user")));
            case "View", "Create", "Delete" -> type.put("placements", List.of(Map.of("path", "Object/Id", "field",
                    "user")));
            case "Update" -> type.put("placements", List.of(Map.of("path", "After/Object/Id", "field", "user")));
            case "Process" -> type.put("placements", List.of(Map.of("path", "Action", "value", "Execute"),
                    Map.of("path", "Type", "value", "Application"), Map.of("path", "Command", "value", "code")));
            default -> {
                // Nothing required.
            }
        }
    }

    // An event type recognised by the number an extraction reads from the record's object (by its start: an equals
    // on a number each record has its own is refused).
    private static Map<String, Object> numbered() {
        return withKeys(type("Delete-Records", "Delete", "low", null), "recognised_by", List.of(Map.of("when",
                List.of(Map.of("field", "record_number", "starts_with", "99")), "names", List.of())));
    }

    // The extraction reading the record's number from its object (rec-99).
    private static Map<String, Object> recordNumber() {
        return Map.of("field", "object", "regex", "^rec-(\\d+)$", "names", List.of("record_number"));
    }

    // The only event type, recognised by every record.
    private static Map<String, Object> soleType() {
        return withKeys(type("Authenticate-Logon", "Authenticate", "high", null), "recognised_by", List.of());
    }

    private static Map<String, Object> withKeys(final Map<String, Object> proposal, final Object... pairs) {
        final Map<String, Object> out = new LinkedHashMap<>(proposal);
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private Map<String, Object> call(final Map<String, Object> arguments, final ToolCallContext context) {
        final String text = text(arguments, context);
        // A refusal is text, not JSON: said as the failure.
        assertThat(text).as("a JSON result").startsWith("{");
        return BuildToolCalls.json(text);
    }

    private String text(final Map<String, Object> arguments, final ToolCallContext context) {
        return tool.execute(BuildToolCalls.call(CatalogueEventTypesTool.NAME, JsonUtil.writeValueAsString(arguments,
                false)), context).text();
    }

    private static ToolCallContext plain() {
        return BuildToolCalls.context();
    }

    private static BuildToolCalls.FormUser user(final Map<String, Object> answer) {
        return BuildToolCalls.user(answer);
    }

    private Map<String, Object> notes() {
        return notesTool.notesOf(BuildToolCalls.context(), BUILD, null);
    }

    private String notesText() {
        return world.guard().folderContents(BUILD).stream()
                .filter(doc -> doc.name().endsWith(SourceNotes.NOTES_SUFFIX))
                .map(doc -> world.body(doc.docRef().getUuid()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(final Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return (Map<String, Object>) value;
    }

    // 25 records: login, logout and export eight each, purge once (rare); hosts and levels that tell nothing.
    private static String sample() {
        final List<String> lines = new ArrayList<>();
        final List<String> actions = List.of("login", "logout", "export");
        for (int i = 0; i < 24; i++) {
            lines.add(("time=2026-11-06T09:%02d:00Z action=%s user=user%d host=web%d level=%s src=10.0.0.%d "
                       + "object=rec-%d").formatted(i, actions.get(i % actions.size()), i % 5, i % 3, i % 2 == 0
                    ? "INFO"
                    : "WARN", 1 + i % 9, i));
        }
        lines.add("time=2026-11-06T09:30:00Z action=purge user=user0 host=web1 level=INFO src=10.0.0.1 "
                  + "object=rec-99");
        return String.join("\n", lines) + "\n";
    }

    // A security logger writing three event types told apart by op (four records each), and a request logger.
    private static String loggers() {
        final List<String> lines = new ArrayList<>();
        final List<String> ops = List.of("password_change", "group_change", "ruleset_unlock");
        for (int i = 0; i < 12; i++) {
            lines.add("time=2026-11-06T09:%02d:00Z logger=com.acme.SecurityLogger op=%s user=user%d".formatted(i,
                    ops.get(i % ops.size()), i % 3));
            lines.add("time=2026-11-06T10:%02d:00Z logger=com.acme.RequestLogger path=/p%d user=user%d".formatted(i,
                    i % 4, i % 3));
        }
        return String.join("\n", lines) + "\n";
    }

    private static EventSchema schema() {
        try (final InputStream in = TestCatalogueEventTypesTool.class.getResourceAsStream(
                "event-logging-v4.1.0.xsd")) {
            return EventSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }


    // --------------------------------------------------------------------------------


    // The answers the tests' users give.
    private static final class McpAnswers {

        // A form accepted as it is: a yes, an editable value left as proposed.
        private static final Map<String, Object> ACCEPT = Map.of("value", true);

        private McpAnswers() {
            // Constants only.
        }
    }


    // --------------------------------------------------------------------------------


    // A user of a client that shows forms, answering each form by its message.
    private static final class Answering implements UserQuestions {

        private final Function<String, Map<String, Object>> answers;

        private Answering(final Function<String, Map<String, Object>> answers) {
            this.answers = answers;
        }

        private ToolCallContext context() {
            return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-2",
                            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                    .userQuestions(this)
                    .build();
        }

        @Override
        public boolean canAsk() {
            return true;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            return new ElicitResult(ElicitResult.Action.ACCEPT, answers.apply(intro));
        }
    }


    // --------------------------------------------------------------------------------


    // A user of Stroom AI's chat who types a message instead of answering the first catalogue form, then answers
    // every form.
    private static final class PuttingAside implements UserQuestions {

        private final String typed;
        private int asked;

        private PuttingAside(final String typed) {
            this.typed = typed;
        }

        private ToolCallContext context() {
            return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-3",
                            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                    .userQuestions(this)
                    .build();
        }

        private int asked() {
            return asked;
        }

        @Override
        public boolean canAsk() {
            return true;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            asked++;
            return asked == 1
                    ? new ElicitResult(ElicitResult.Action.CANCEL, null, Map.of(McpElicitations.SENT_INSTEAD, typed))
                    : new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true));
        }
    }
}
