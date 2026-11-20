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

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// `catalogue_event_types` in Stroom (replacing classify_kinds; stroom-ai/DESIGN.md, Event type catalogue): the plan
/// asks for it after the survey; it gives the agent the evidence (the fields that tell records apart, their shapes,
/// example records, the draft's seed); the agent's proposals add, change and remove event types by TypeId and each
/// is checked against every sampled record (counts, overlaps refused, uncatalogued records by shape, may be several,
/// the regex guard, TypeId); the user confirms them in sets, with the catch-all last; and the draft follows the
/// catalogue, a rule a way of recognising an event type.
///
/// Written against the contracts before the tool exists: until it does, every test fails on the missing tool.
@SuppressWarnings("unchecked")
class TestE2eCatalogueEventTypes extends AbstractE2eTest {

    private static final String SPLITTER_NAME = "E2E-CAT-SAVED";
    // A typed amendment, as the user writes it in a catalogue form.
    private static final String AMENDMENT = "purge deletes the audit records: Delete, acting on the record";
    // The catalogue's table, as the chat shows it.
    private static final String TABLE_HEADER = "| Event type | Recognised by | Records | Translated as |";

    @Test
    void theCatalogueIsListedCheckedAndConfirmedInSetsAndTheDraftFollowsIt() {
        final String build = "e2e-cat";
        final String feed = "E2E-CAT";
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, sample()));

        // ### Not before the survey, which alone finds an aggregated feed's source type field (evals 58 and 59).
        assertThat(refusal(CATALOGUE, args("build", build, "stream_ids", raw)))
                .contains("Survey first: survey_feed feed=" + feed + " build=" + build + " stream_ids=" + raw);
        surveyed(build, feed, raw);

        // ### The evidence: the fields that tell records apart (not the hosts or severities), the records' shapes,
        // examples (the rare record among them), and the draft's seed, marked as the draft's.
        final Map<String, Object> listed = json(CATALOGUE, args("build", build, "stream_ids", raw));
        dump("listed", listed);
        assertThat(listed).containsEntry("status", "listed");
        final Map<String, Object> evidence = map(listed, "evidence");
        assertThat(list(evidence, "fields")).extracting(field -> field.get("field"))
                .contains("action")
                .doesNotContain("host", "level");
        assertThat(list(evidence, "shapes")).isNotEmpty()
                .allSatisfy(shape -> assertThat(shape).containsKeys("signature", "records"));
        assertThat(jsonOf(evidence.get("examples"))).as("the rare record, seen once, among the examples")
                .contains("action=purge");
        assertThat(list(evidence, "seed")).isNotEmpty()
                .allSatisfy(seeded -> assertThat(seeded).containsEntry("basis", "draft"));
        assertThat(nextStep(json("build_status", args("build", build)))).isNotEqualTo("translation");

        // ### Proposed: checked against every record, each counted; nothing overlaps, nothing left uncatalogued;
        // the high-confidence ones summarised for the chat by what happened, every row in the source notes.
        final Map<String, Object> checked = proposed(build, raw, List.of(
                withKeys(eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login")),
                        "action", "Logon", "actor", "user"),
                withKeys(eventType("Authenticate-Logoff", "Authenticate", "high", when("action", "equals", "logout")),
                        "action", "Logoff", "actor", "user"),
                eventType("Export-Report", "Export", "medium", when("action", "equals", "export")),
                eventType("Delete-Records", "Delete", "low", when("action", "equals", "purge"))));
        assertThat(checked).containsEntry("status", "checked");
        assertThat(countsOf(checked)).containsEntry("Authenticate-Logon", 8L).containsEntry("Authenticate-Logoff", 8L)
                .containsEntry("Export-Report", 8L).containsEntry("Delete-Records", 1L);
        assertThat(list(checkOf(checked), "overlaps")).isEmpty();
        assertThat(map(checkOf(checked), "uncatalogued")).containsEntry("records", 0);
        assertThat(String.valueOf(checked.get("table"))).startsWith("**2 high-confidence event types**")
                .contains("Authenticate: 2 event types, 16 records").doesNotContain(TABLE_HEADER);
        assertThat(notesDocText(build)).contains("### Authenticate", "| `Authenticate-Logon` Authenticate Logon | "
                                                 + "E2E: Authenticate-Logon | action is login | 8 |");

        // ### Confirmed: the high-confidence ones in one form, then the medium and the low a set each; the form's
        // text plain, referring to the table in the chat. The low one amended in words: nothing confirmed of it,
        // the amendment back for the agent.
        final int formsBefore = client.forms().size();
        client.answeringWith(amendingCatalogueForm(3, AMENDMENT));
        final Map<String, Object> first = json(CATALOGUE, args("build", build, "confirm", true));
        dump("confirm high", first);
        Map<String, Object> step = first;
        for (int round = 0; round < 4 && !jsonOf(step).contains(AMENDMENT); round++) {
            step = json(CATALOGUE, args("build", build, "confirm", true));
            dump("confirm " + round, step);
        }
        assertThat(jsonOf(step)).as("the user's amendment, back for the agent").contains(AMENDMENT);
        assertThat(eventTypesPutInEachForm(formsSince(formsBefore))).containsExactly(2, 1, 1);
        assertThat(formsSince(formsBefore)).allSatisfy(form -> assertThat(formsText(List.of(form)))
                .as("a form's text is plain").doesNotContain("|"));
        final Map<String, Map<String, Object>> types = eventTypesOf(build);
        assertThat(types.get("Authenticate-Logon")).containsEntry("status", "confirmed");
        assertThat(types.get("Export-Report")).containsEntry("status", "confirmed");
        assertThat(types.get("Delete-Records")).containsEntry("status", "proposed");

        // ### The amendment sent as a change; the set confirmed; the catch-all confirmed last, with the
        // uncatalogued records' count.
        client.answeringWith(McpTestClient::agree);
        proposed(build, raw, List.of(args("op", "change", "type_id", "Delete-Records", "description",
                "Audit records deleted", "actor", "user", "affected", "object", "affected_as", "Document")));
        final int beforeCatchAll = client.forms().size();
        final Map<String, Object> confirmed = confirmedCatalogue(build);
        assertThat(formsSince(beforeCatchAll).getLast().message()).as("the catch-all, confirmed last")
                .contains("uncatalogued records");
        assertThat(map(catalogueOf(build), "catch_all")).containsEntry("status", "confirmed");
        assertThat(eventTypesOf(build).values()).allSatisfy(type -> assertThat(type)
                .containsEntry("status", "confirmed"));
        // The plan goes on past the catalogue (to the sample's converter here, then the draft).
        assertThat(map(confirmed, "next").get("step")).isNotEqualTo("catalogue");
        assertThat(nextTool(confirmed)).isIn("draft_translation_mapping", "build_data_splitter");

        // ### The draft follows the catalogue: a rule a way of recognising, each its TypeId and action element, the
        // vendor's code written as Data, the catch-all last.
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw, "build", build,
                "source_name", "Records app", "system_name", "Records", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        final List<Map<String, Object>> rules = list(map(draft, "mapping"), "events");
        assertThat(jsonOf(rules)).contains("Authenticate-Logon", "Authenticate-Logoff", "Export-Report",
                        "Delete-Records", "EventDetail/Authenticate/Action", "Audit records deleted")
                .contains("{\"path\":\"EventDetail/Delete/Document/Id\",\"field\":\"object\"}")
                .contains("\"data_name\":\"action\"");
        assertThat(rules.getLast()).containsEntry("catch_all", true);
        assertThat(rules).hasSize(5);
    }

    // ### A seeded event type whose records may be several (one per logger, the derivation the design warns of): not
    // confirmed until the agent splits it or says why it is one. This sample's draft derives its rules by op, not
    // the logger, so the per-logger seed is sent as the seed's (basis draft), as a seed of a logger would be.
    @Test
    void aSeededEventTypeThatMayBeSeveralIsNotConfirmedUntilSplitOrSaidToBeOne() {
        final String build = "e2e-cat-several";
        final String feed = "E2E-CAT-SEVERAL";
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, loggers()));
        surveyed(build, feed, raw);
        json(CATALOGUE, args("build", build, "stream_ids", raw));
        final List<Map<String, Object>> seed = new ArrayList<>();
        for (final String logger : List.of("com.acme.audit.SecurityLogger", "com.acme.web.RequestLogger")) {
            seed.add(withKeys(eventType(null, "Process", "low", when("logger", "equals", logger)), "basis",
                    "draft", "title", logger));
        }
        final Map<String, Object> seeded = proposed(build, raw, seed);
        final List<Map<String, Object>> several = severalOf(seeded);
        assertThat(several).as("the security logger's type may be several, told by op").anySatisfy(type ->
                assertThat(map(type, "by_field")).containsEntry("field", "op"));
        final String security = String.valueOf(several.stream()
                .filter(type -> "op".equals(map(type, "by_field").get("field")))
                .findFirst()
                .orElseThrow()
                .get("type_id"));

        // Not put to the user while it may be several.
        final int formsBefore = client.forms().size();
        assertThat(replyText(CATALOGUE, args("build", build, "confirm", true)))
                .contains(security, "one_type_because");
        assertThat(eventTypesPutInEachForm(formsSince(formsBefore))).isEmpty();

        // Split by op: three event types, each told by the logger and its op; any other said to be one.
        final String logger = "com.acme.audit.SecurityLogger";
        final Map<String, Object> split = proposed(build, raw, List.of(
                args("op", "remove", "type_id", security),
                eventType("Update-Password", "Update", "high", when("logger", "equals", logger, "op", "equals",
                        "password_change")),
                eventType("Update-Group", "Update", "high", when("logger", "equals", logger, "op", "equals",
                        "group_change")),
                eventType("Unlock-Ruleset", "Update", "high", when("logger", "equals", logger, "op", "equals",
                        "ruleset_unlock"))));
        assertThat(countsOf(split)).containsEntry("Update-Password", 4L).containsEntry("Update-Group", 4L)
                .containsEntry("Unlock-Ruleset", 4L).doesNotContainKey(security);
        final List<Map<String, Object>> because = new ArrayList<>();
        severalOf(split).forEach(type -> because.add(args("op", "change", "type_id", type.get("type_id"),
                "one_type_because", "Every request is one event type: its path and status are its details")));
        if (!because.isEmpty()) {
            assertThat(severalOf(proposed(build, raw, because))).isEmpty();
        }
        confirmedCatalogue(build);
        assertThat(eventTypesOf(build)).doesNotContainKey(security).containsKeys("Update-Password",
                "Update-Group", "Unlock-Ruleset");
        because.forEach(change -> assertThat(eventTypesOf(build).get((String) change.get("type_id")))
                .containsEntry("one_type_because", change.get("one_type_because")));
    }

    // ### A record two event types recognise: refused, naming both and the fields whose values tell their other
    // records apart.
    @Test
    void anOverlapIsRefusedNamingTheFieldsThatTellTheTypesApart() {
        final String build = "e2e-cat-overlap";
        final List<Long> raw = surveyedSample(build, "E2E-CAT-OVERLAP", sample());
        final String reply = replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login")),
                eventType("Access-Records", "Access", "medium", when("user", "equals", "user1")))));
        dump("overlap", reply);
        assertThat(reply).containsIgnoringCase("overlap")
                .contains("Authenticate-Logon", "Access-Records", "action");
        assertThat(replyText(CATALOGUE, args("build", build, "confirm", true))).containsIgnoringCase("overlap");
    }

    // ### Records no event type recognises: not refused, counted and grouped by shape with examples.
    @Test
    void uncataloguedRecordsAreCountedByShape() {
        final String build = "e2e-cat-uncatalogued";
        final List<Long> raw = surveyedSample(build, "E2E-CAT-UNCATALOGUED", sample());
        final Map<String, Object> checked = proposed(build, raw, List.of(
                eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login"))));
        assertThat(checked).containsEntry("status", "checked");
        final Map<String, Object> uncatalogued = map(checkOf(checked), "uncatalogued");
        assertThat(uncatalogued).containsEntry("records", 17);
        final List<Map<String, Object>> shapes = list(uncatalogued, "shapes");
        assertThat(shapes).isNotEmpty().allSatisfy(shape -> assertThat(list(shape, "examples")).isNotEmpty());
        assertThat(shapes.stream().mapToLong(shape -> number(shape.get("records"))).sum()).isEqualTo(17);
    }

    // ### The regex guard: a lookaround (not XPath), a regex past the length cap, and a regex over a value past
    // 10,000 characters where starts_with or contains would do, each refused, saying why.
    @Test
    void regexesXPathCannotRunOrTooCostlyToRunAreRefused() {
        final String build = "e2e-cat-regex";
        final List<Long> raw = surveyedSample(build, "E2E-CAT-REGEX", withLongBodies());
        assertThat(replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Authenticate-Logon", "Authenticate", "high", when("action", "matches",
                        "^(?=log)login$"))))))
                .contains("Authenticate-Logon", "lookaround");
        assertThat(replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Authenticate-Logon", "Authenticate", "high", when("action", "matches",
                        "^(" + "login|".repeat(400) + "logon)$"))))))
                .contains("Authenticate-Logon").containsIgnoringCase("long");
        assertThat(replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Import-Bulk", "Import", "high", when("body", "matches", "^L+$"))))))
                .contains("Import-Bulk", "starts_with", "contains");
    }

    // ### An event type recognised by a message holding its record's own id ("Object rec-3 has been modified"):
    // refused, suggesting starts_with or a regex with a named group; the rare purge, on a field of few values, is
    // not. A field whose whole value is an id (object=rec-3) can't be told from a code (AUTH-001) and is left alone.
    @Test
    void anEventTypeRecognisedByAValueUniqueToItsRecordIsRefused() {
        final String build = "e2e-cat-unique";
        final List<Long> raw = surveyedSample(build, "E2E-CAT-UNIQUE", sample().lines()
                .map(line -> line + " msg=\"Object " + line.replaceAll(".*object=", "") + " has been modified\"")
                .collect(Collectors.joining("\n", "", "\n")));
        final String reply = replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Update-Record", "Update", "high", when("msg", "equals",
                        "Object rec-3 has been modified")))));
        dump("unique", reply);
        assertThat(reply).contains("Update-Record", "differ only in a part that varies", "starts_with",
                "named group");
        final Map<String, Object> purge = proposed(build, raw, List.of(
                eventType("Delete-Records", "Delete", "high", when("action", "equals", "purge"))));
        assertThat(list(checkOf(purge), "problems")).isEmpty();
        assertThat(countsOf(purge)).containsEntry("Delete-Records", 1L);
    }

    // ### The user declines the catch-all: they want something else for the uncatalogued records. The agent is told
    // to ask them in the chat and call again with their answer; a translation is not saved while the catch-all is
    // unconfirmed (seen in a Haiku run: drafted without build= and saved); their answer, another event for those
    // records, is put to them in the catch-all's form, and the draft writes it.
    @Test
    void aDeclinedCatchAllIsAskedAboutInTheChatAndNoTranslationIsSavedUntilItIsConfirmed() {
        final String build = "e2e-cat-declined";
        final String feed = "E2E-CAT-DECLINED";
        final List<Long> raw = surveyedSample(build, feed, sample());
        final Map<String, Object> bypass = json("draft_translation_mapping", args("stream_ids", raw, "source_name",
                "Records app", "system_name", "Records", "environment", "Test"));
        proposed(build, raw, List.of(
                withKeys(eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login")),
                        "action", "Logon", "actor", "user"),
                withKeys(eventType("Authenticate-Logoff", "Authenticate", "high", when("action", "equals", "logout")),
                        "action", "Logoff", "actor", "user"),
                eventType("Export-Report", "Export", "high", when("action", "equals", "export"))));
        client.answeringWith(form -> form.message().contains("Keep the uncatalogued records")
                ? McpTestClient.decline(form)
                : McpTestClient.agree(form));
        String declined = "";
        for (int round = 0; round < 4 && !declined.contains("declined the catch-all"); round++) {
            declined = replyText(CATALOGUE, args("build", build, "confirm", true));
        }
        dump("catch-all declined", declined);
        assertThat(declined).contains("declined the catch-all", "Ask the user in the chat", "op: add",
                "catch_all={translation:");
        assertThat(map(catalogueOf(build), "catch_all")).containsEntry("status", "proposed");

        // ### Not saved while the catch-all is unconfirmed, the draft made without build= or not.
        assertThat(refusal("build_translation_xslt", args("mapping", timeChosen(map(bypass, "mapping")),
                "stream_ids", raw, "build", build, "name", feed + "-Events", "schema_version", VERSION)))
                .contains("catch-all", "catalogue_event_types build=" + build + " confirm=true");

        // ### Their answer in the chat: the purges are deletions. Put to them in the catch-all's form, confirmed.
        client.answeringWith(McpTestClient::agree);
        final int formsBefore = client.forms().size();
        Map<String, Object> step = Map.of();
        for (int round = 0; round < 4 && !"confirmed".equals(step.get("status")); round++) {
            step = json(CATALOGUE, args("build", build, "confirm", true, "catch_all", Map.of("translation",
                    Map.of("type_id", "Delete-Records", "event_detail", "Delete", "description",
                            "Audit records deleted"))));
        }
        assertThat(step).containsEntry("status", "confirmed");
        assertThat(formsSince(formsBefore).getLast().message()).contains("Delete-Records events");
        assertThat(map(catalogueOf(build), "catch_all")).containsEntry("status", "confirmed");
        assertThat(jsonOf(map(catalogueOf(build), "catch_all"))).contains("Delete-Records");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw, "build", build,
                "source_name", "Records app", "system_name", "Records", "environment", "Test"));
        final List<Map<String, Object>> rules = list(map(draft, "mapping"), "events");
        assertThat(rules.getLast()).containsEntry("catch_all", true);
        assertThat(jsonOf(rules.getLast())).contains("Delete-Records", "Audit records deleted");
    }

    // ### TypeId: Verb-Object or refused; unique or refused; a default suggested when none is given.
    @Test
    void typeIdsAreVerbObjectUniqueAndDefaulted() {
        final String build = "e2e-cat-typeid";
        final List<Long> raw = surveyedSample(build, "E2E-CAT-TYPEID", sample());
        assertThat(replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Logon", "Authenticate", "high", when("action", "equals", "login"))))))
                .contains("Logon", "Verb-Object");
        // Given none: the event detail element as the verb, the first equals value as the object, its first letter a
        // capital (Verb-Object: TypeIds.VERB_OBJECT).
        final Map<String, Object> defaulted = proposed(build, raw, List.of(
                eventType(null, "Authenticate", "high", when("action", "equals", "login"))));
        assertThat(countsOf(defaulted)).containsKey("Authenticate-Login");
        proposed(build, raw, List.of(eventType("Authenticate-Logoff", "Authenticate", "high",
                when("action", "equals", "logout"))));
        assertThat(replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Authenticate-Logoff", "Authenticate", "high", when("action", "equals", "export"))))))
                .contains("Authenticate-Logoff").containsAnyOf("unique", "already", "duplicate", "twice");
        assertThat(countsOf(json(CATALOGUE, args("build", build, "stream_ids", raw))))
                .containsEntry("Authenticate-Logoff", 8L);
    }

    // ### A client that can't show forms confirms nothing: every change in Stroom needs the user's agreement in a
    // form (FormsRequired, decided 2026-11-18: no id is given to pass back instead, as the model could approve its
    // own call with it), so its calls are refused and the catalogue stays as it was; a client with forms confirms it.
    @Test
    void aClientWithoutFormsConfirmsNothing() {
        final String build = "e2e-cat-review";
        final List<Long> raw = surveyedSample(build, "E2E-CAT-REVIEW", sample());
        proposed(build, raw, List.of(
                eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login")),
                eventType("Authenticate-Logoff", "Authenticate", "high", when("action", "equals", "logout"))));
        final int formsBefore = client.forms().size();
        assertThat(McpTestClient.text(plainClient.call(CATALOGUE, args("build", build, "confirm", true))))
                .contains("needs the user's agreement in a form");
        assertThat(client.forms()).hasSize(formsBefore);
        assertThat(eventTypesOf(build).values()).allSatisfy(type -> assertThat(type)
                .containsEntry("status", "proposed"));
        confirmedCatalogue(build);
        assertThat(eventTypesOf(build).values()).allSatisfy(type -> assertThat(type)
                .containsEntry("status", "confirmed"));
    }

    // ### A sole event type, recognised by every record (no condition): counted for them all; another beside it
    // refused; the catch-all still confirmed, and catching nothing.
    @Test
    void aSoleEventTypeIsRecognisedByEveryRecord() {
        final String build = "e2e-cat-sole";
        final StringBuilder logons = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            logons.append("time=2026-11-06T09:%02d:00Z user=user%d src=10.0.0.%d result=ok\n".formatted(i, i % 4,
                    i));
        }
        final List<Long> raw = surveyedSample(build, "E2E-CAT-SOLE", logons.toString());
        final Map<String, Object> sole = proposed(build, raw, List.of(
                withKeys(eventType("Authenticate-Logon", "Authenticate", "high"), "action", "Logon", "actor",
                        "user")));
        assertThat(countsOf(sole)).containsExactly(Map.entry("Authenticate-Logon", 12L));
        assertThat(list(checkOf(sole), "problems")).isEmpty();
        assertThat(replyText(CATALOGUE, args("build", build, "stream_ids", raw, "proposals", List.of(
                eventType("Authenticate-Logoff", "Authenticate", "high", when("result", "equals", "fail"))))))
                .contains("every record");
        confirmedCatalogue(build);
        assertThat(map(catalogueOf(build), "catch_all")).containsEntry("status", "confirmed");
        assertThat(list(catalogueOf(build), "event_types")).singleElement()
                .satisfies(type -> assertThat(list(type, "recognised_by")).isEmpty());
    }

    // ### Chat 156: a notes doc named for the build already, then the catalogue: kept in that one doc.
    @Test
    void theBuildKeepsOneNotesDocWhateverTheSourceIsCalled() {
        final String build = "e2e-cat-notes";
        final List<Long> raw = surveyedSample(build, "E2E-CAT-NOTES", sample());
        agreed("record_source_notes", args("build", build, "source", build, "summary", "A records app's audit log.",
                "fields", List.of()));
        proposed(build, raw, List.of(
                eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login"))));
        // catalogueOf finds exactly one notes doc in the build's folder.
        assertThat(eventTypesOf(build)).containsKey("Authenticate-Logon");
        assertThat(String.valueOf(catalogueOf(build).get("summary"))).isEqualTo("A records app's audit log.");
    }

    // ### Chat 163: without a splitter, the build's saved Data Splitter reads the records (its own field names).
    @Test
    void theBuildsSavedDataSplitterIsUsedWhenNoSplitterIsGiven() {
        final String build = "e2e-cat-saved";
        final List<Long> raw = surveyedSample(build, SPLITTER_NAME, sample());
        final Map<String, Object> saved = json("build_data_splitter", args("stream_ids", raw, "save_as",
                SPLITTER_NAME, "build", build, "spec", Map.of("kind", "regex", "pattern",
                        "^time=(\\S+) action=(\\S+) user=(\\S+) (.*)$", "names", List.of("ts", "verb", "who",
                                "rest"))));
        assertThat(saved.get("saved")).as("saved: %s", saved.get("not_saved")).isNotNull();
        final Map<String, Object> listed = json(CATALOGUE, args("build", build, "stream_ids", raw));
        assertThat(list(map(listed, "evidence"), "fields")).extracting(field -> field.get("field"))
                .contains("verb")
                .doesNotContain("action");
    }

    // ### Many event types: the high-confidence ones in one form, all the medium ones in another (decided by the user
    // 2026-11-20: at most three sets, never many forms).
    @Test
    void manyEventTypesArePutToTheUserAConfidenceAForm() {
        final String build = "e2e-cat-many";
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            lines.add("time=2026-11-06T%02d:%02d:00Z event_type=code%02d user=user%d".formatted(i / 60, i % 60,
                    i % 25, i % 4));
        }
        final List<Long> raw = surveyedSample(build, "E2E-CAT-MANY", String.join("\n", lines) + "\n");
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (int k = 0; k < 25; k++) {
            proposals.add(eventType("Process-Code%02d".formatted(k), "Process", k < 5
                    ? "high"
                    : "medium", when("event_type", "equals", "code%02d".formatted(k))));
        }
        proposed(build, raw, proposals);
        final int formsBefore = client.forms().size();
        confirmedCatalogue(build);
        assertThat(eventTypesPutInEachForm(formsSince(formsBefore))).containsExactly(5, 20);
        assertThat(eventTypesOf(build)).hasSize(25);
    }

    // A feed of the build with one stream of the text, surveyed.
    private List<Long> surveyedSample(final String build, final String feed, final String text) {
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, text));
        surveyed(build, feed, raw);
        return raw;
    }

    // The user: the given catalogue form (counted from 1) amended in words, every other form agreed.
    private static Function<ElicitFormRequest, ElicitResult> amendingCatalogueForm(final int which,
                                                                                  final String amendment) {
        final int[] seen = {0};
        return form -> {
            if (ACCEPT_ALL.matcher(form.message()).find() && ++seen[0] == which) {
                return typed(form, amendment);
            }
            return McpTestClient.agree(form);
        };
    }

    // A form answered in words: the text typed in its first text field.
    private static ElicitResult typed(final ElicitFormRequest form, final String text) {
        final Map<String, Object> fields = (Map<String, Object>) form.requestedSchema().get("properties");
        final String name = fields.entrySet().stream()
                .filter(field -> "string".equals(((Map<String, Object>) field.getValue()).get("type")))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No text field in " + form.requestedSchema()));
        return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of(name, text), null);
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

    // Two loggers: one writing three event types told apart by op (four records each), one writing requests.
    private static String loggers() {
        final List<String> lines = new ArrayList<>();
        final List<String> ops = List.of("password_change", "group_change", "ruleset_unlock");
        for (int i = 0; i < 12; i++) {
            lines.add("time=2026-11-06T09:%02d:00Z logger=com.acme.audit.SecurityLogger op=%s user=user%d target=t%d"
                    .formatted(i, ops.get(i % ops.size()), i % 3, i));
            lines.add("time=2026-11-06T10:%02d:00Z logger=com.acme.web.RequestLogger path=/p%d status=%d user=user%d"
                    .formatted(i, i % 4, i % 2 == 0
                            ? 200
                            : 404, i % 3));
        }
        return String.join("\n", lines) + "\n";
    }

    // JSON lines of logons, half with a body past 10,000 characters.
    private static String withLongBodies() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            out.append("{\"ts\": \"2026-11-06T09:%02d:00Z\", \"action\": \"login\", \"body\": \"%s\"}\n".formatted(i,
                    i % 2 == 0
                            ? "L".repeat(AwkwardValues.LONG_LENGTH)
                            : "short"));
        }
        return out.toString();
    }
}
