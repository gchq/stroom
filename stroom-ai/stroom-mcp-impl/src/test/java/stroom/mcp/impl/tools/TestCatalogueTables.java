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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheck;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.CatalogueSample;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The source notes' "Event types" table, which holds every row.
class TestCatalogueTables {

    private static final String DOC = "'acme source notes'";

    @Test
    void theNotesHoldEveryEventTypeWithItsRecordsASectionPerActionElement() {
        final List<EventType> set = types(300);
        final Catalogue catalogue = new Catalogue(set, Catalogue.CatchAll.PROPOSED, Catalogue.Scope.NONE);

        final List<String> table = CatalogueTables.notesTable(catalogue, CatalogueTables.recordsOf(check(set)));

        assertThat(table).startsWith("## Event types").contains("### Process", CatalogueTables.NOTES_HEADER,
                "### Uncatalogued records");
        assertThat(table.stream().filter(line -> line.startsWith("| `Process-Code"))).hasSize(300)
                .allSatisfy(line -> assertThat(line).contains(" | 2 | Process | "));
        // The catch-all last, in a section of its own.
        assertThat(table.stream().filter(line -> line.startsWith("| ")).toList().getLast())
                .startsWith("| `Unknown` Uncatalogued records | - | records no event type recognises | 0 | Unknown "
                            + "events, their fields kept as Data | proposed |");
        // Unchecked (a hand edit read back): the records left blank.
        assertThat(CatalogueTables.notesTable(catalogue, Map.of())).contains("| `Process-Code000` Process Code000 | "
                                                                             + "Test | every record |  | Process | "
                                                                             + "proposed; medium; from the agent's "
                                                                             + "knowledge |");
    }

    @Test
    void theNotesGroupEventTypesAsTheSummariesDoInTheirOrderWithStatusInShortWords() {
        final EventType logon = type("Authenticate-Logon", "Authenticate", "high", EventType.DOCUMENTATION)
                .confirmedByUser();
        final EventType export = type("Export-Report", "Export", "low", EventType.DRAFT);
        final EventType logoff = withoutDescription(type("Authenticate-Logoff", "Authenticate", "medium",
                EventType.AGENT_KNOWLEDGE));
        final Catalogue catalogue = new Catalogue(List.of(logon, export, logoff), new Catalogue.CatchAll(
                EventType.CONFIRMED, "the rest kept", null), Catalogue.Scope.NONE);

        final List<String> table = CatalogueTables.notesTable(catalogue, Map.of("Authenticate-Logon", 4,
                "Unknown", 3));

        assertThat(table.stream().filter(line -> line.startsWith("### ")).toList()).containsExactly(
                "### Authenticate", "### Export", "### Uncatalogued records");
        assertThat(table).contains("| `Authenticate-Logon` Authenticate Logon | Test | every record | 4 | Authenticate "
                                   + "| confirmed; high; from the documentation |",
                "| `Authenticate-Logoff` Authenticate Logoff | - | every record |  | Authenticate | proposed; medium; "
                + "from the agent's knowledge |",
                "| `Export-Report` Export Report | Test | every record |  | Export | proposed; low; from the draft's "
                + "seed |",
                "| `Unknown` Uncatalogued records | - | records no event type recognises | 3 | Unknown events, their "
                + "fields kept as Data | confirmed by the user: the rest kept |");
        assertThat(table.indexOf("### Export")).isGreaterThan(table.indexOf(
                "| `Authenticate-Logoff` Authenticate Logoff | - | every record |  | Authenticate | proposed; medium; "
                + "from the agent's knowledge |"));
    }

    // Seen in testing: a drop proposed as an event type ('Drop-PEGAOperational'); the user read it as a rule.
    @Test
    void aDropRuleIsSaidAsOneInTheNotesAndTheSetsTable() {
        final EventType logon = type("Authenticate-Logon", "Authenticate", "high", EventType.DOCUMENTATION);
        final EventType operational = new EventType("Operational logging", "Operational logging", "Test", null,
                null, null, null, null, null, List.of(), true, EventType.USER_CONFIRMED, "high", null,
                EventType.PROPOSED);
        final Catalogue catalogue = new Catalogue(List.of(logon, operational), Catalogue.CatchAll.PROPOSED,
                Catalogue.Scope.NONE);

        final List<String> table = CatalogueTables.notesTable(catalogue, Map.of("Operational logging", 9));

        assertThat(table.stream().filter(line -> line.startsWith("### ")).toList()).containsExactly(
                "### Authenticate", "### Drop rules (not translated)", "### Uncatalogued records");
        assertThat(table).anySatisfy(line -> assertThat(line).startsWith("| `Operational logging` Operational "
                                                                         + "logging | Test | every record | 9 | drop "
                                                                         + "rule (not translated) |"));
        assertThat(CatalogueTables.translatedAs(operational)).isEqualTo("drop rule (not translated)");
    }

    // Found in review (2026-11-27): the notes' "Uncatalogued records" row said how many become "Unknown events"
    // counting every uncatalogued record, those the build's mapping drops among them; the catch-all form, from the
    // same check, said only the rest.
    @Test
    void theNotesUncataloguedRowCountsWhatTheCatchAllFormSaysBecomesUnknown() {
        final List<Map<String, Object>> records = codes("4624", "1102", "1102", "1102", "7777");
        final Catalogue catalogue = SourceNotes.catalogue(notes(List.of(coded("Authenticate-Logon", "4624"))));
        final Map<String, Object> mapping = Map.of("drop_when", List.of(Map.of("reason", "heartbeats", "when",
                List.of(eq("evt", "1102")))));
        final CatalogueCheckResult check = CatalogueCheck.check(catalogue, sample(records), null,
                CatalogueCheck.Drafted.of(mapping));

        final String form = CatalogueCatchAll.catchAllShown(check, catalogue);
        final String row = CatalogueTables.notesTable(catalogue, CatalogueTables.recordsOf(check)).stream()
                .filter(line -> line.startsWith("| `Unknown` Uncatalogued records"))
                .findFirst()
                .orElseThrow();

        assertThat(form).contains("1 translated as Unknown events").contains("3 dropped");
        assertThat(row).as("notes' Uncatalogued records row")
                .contains("| " + check.uncatalogued().unknown() + " (3 more dropped by the build's mapping's drop "
                          + "rules) |");
    }

    // Found in review (2026-11-27): a drop rule labelled "Unknown" had its count kept under the same key as the
    // uncatalogued records', and overwritten by theirs. The label is refused now (it is what the uncatalogued
    // records go by), and the uncatalogued records are kept under a key of their own either way.
    @Test
    void aDropRuleLabelledUnknownIsRefusedAndKeepsItsOwnCountInTheNotes() {
        final List<Map<String, Object>> records = codes("4624", "1102", "1102", "1102", "7777");
        final Map<String, Object> drop = coded("Unknown", "1102");
        drop.put("drop", true);
        drop.put("basis", EventType.USER_CONFIRMED);
        final Catalogue catalogue = SourceNotes.catalogue(notes(List.of(coded("Authenticate-Logon", "4624"),
                drop)));
        final CatalogueCheckResult check = CatalogueCheck.check(catalogue, sample(records), null);

        // Adapted from the audit's expectation (no problem): the label is refused, as agreed for the fix.
        assertThat(check.problems()).anySatisfy(problem -> assertThat(problem.problem())
                .contains("is what the uncatalogued records go by"));
        assertThat(check.counts().get("Unknown").records()).isEqualTo(3);
        assertThat(check.uncatalogued().records()).isEqualTo(1);
        assertThat(CatalogueTables.recordsOf(check).get("Unknown"))
                .as("the drop rule's kept count")
                .isEqualTo(3);
        assertThat(CatalogueTables.recordsOf(check).get(CatalogueTables.UNCATALOGUED_KEY)).isEqualTo(1);
    }

    // Found in review (2026-11-27): an event type recognised by a condition the check can't evaluate (a
    // dictionary) was counted 0, and the set's table said "0 (not in the sample)" (and not_in_sample listed it)
    // though three sampled records have its code.
    @Test
    void anEventTypeWithAConditionNotCheckedIsNotSaidToBeOutOfTheSample() {
        final List<Map<String, Object>> records = codes("4624", "4624", "4624", "7777");
        final Map<String, Object> logon = coded("Authenticate-Logon", "4624");
        final Map<String, Object> way = new LinkedHashMap<>();
        way.put("when", List.of(eq("evt", "4624"), Map.of("field", "usr", "in_dictionary", "Admins")));
        way.put("names", List.of());
        logon.put("recognised_by", List.of(way));
        final Catalogue catalogue = SourceNotes.catalogue(notes(List.of(logon)));
        final CatalogueCheckResult check = CatalogueCheck.check(catalogue, sample(records), null);

        assertThat(check.notChecked()).isNotEmpty();
        assertThat(CatalogueTables.records(catalogue.eventTypes().getFirst(), check))
                .as("the set table's Records cell")
                .doesNotContain("not in the sample")
                .isEqualTo("3 may be (a condition on usr can't be checked here)");
        assertThat(CatalogueTables.notInSample(check)).isEmpty();
        assertThat(CatalogueSummary.unusual(catalogue.eventTypes(), check))
                .allSatisfy(one -> assertThat(one.why()).doesNotContain("no record of it in the sample"));
    }

    private static CatalogueSample sample(final List<Map<String, Object>> records) {
        final List<String> lines = new ArrayList<>();
        records.forEach(record -> lines.add(ToolJson.dumps(record)));
        return CatalogueSample.read(CatalogueSample.reading("json", null, null, null, null, null, null),
                String.join("\n", lines), null, LocalCheck.JSON_PARSER_CUT, null);
    }

    private static List<Map<String, Object>> codes(final String... codes) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String code : codes) {
            out.add(new LinkedHashMap<>(Map.of("evt", code, "usr", "alice")));
        }
        return out;
    }

    private static Map<String, Object> coded(final String typeId, final String code) {
        final Map<String, Object> type = new LinkedHashMap<>();
        type.put("type_id", typeId);
        type.put("title", typeId + " title");
        type.put("event_detail", "Authenticate");
        type.put("action", "Logon");
        type.put("recognised_by", List.of(Map.of("when", List.of(eq("evt", code)), "names", List.of())));
        type.put("basis", "documentation");
        type.put("confidence", "high");
        type.put("status", "confirmed");
        return type;
    }

    private static Map<String, Object> eq(final String field, final String value) {
        return Map.of("field", field, "equals", value);
    }

    private static Map<String, Object> notes(final List<Map<String, Object>> types) {
        final Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("source", "Acme");
        notes.put("fields", new ArrayList<>());
        notes.put("event_types", types);
        notes.put("catch_all", Map.of("status", "proposed"));
        return notes;
    }

    private static EventType type(final String typeId,
                                  final String eventDetail,
                                  final String confidence,
                                  final String basis) {
        return new EventType(typeId, typeId.replace('-', ' '), "Test", eventDetail, null, null, null, null, null,
                List.of(), false, basis, confidence, null, EventType.PROPOSED);
    }

    private static EventType withoutDescription(final EventType type) {
        return new EventType(type.typeId(), type.title(), null, type.eventDetail(), null, null, null, null, null,
                List.of(), false, type.basis(), type.confidence(), null, type.status());
    }

    private static List<EventType> types(final int count) {
        final List<EventType> out = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            final String typeId = "Process-Code%03d".formatted(k);
            out.add(new EventType(typeId, typeId.replace('-', ' '), "Test", "Process", null, null, null, null, null,
                    List.of(), false, EventType.AGENT_KNOWLEDGE, "medium", null, EventType.PROPOSED));
        }
        return out;
    }

    private static CatalogueCheckResult check(final List<EventType> set) {
        final Map<String, CatalogueCheckResult.Count> counts = new LinkedHashMap<>();
        set.forEach(type -> counts.put(type.typeId(), new CatalogueCheckResult.Count(2, List.of(2))));
        return new CatalogueCheckResult(counts, List.of(), CatalogueCheckResult.Uncatalogued.NONE,
                CatalogueCheckResult.Unparsed.NONE, List.of(), List.of(), List.of(),
                set.stream().map(EventType::typeId).toList());
    }
}
