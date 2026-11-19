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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/// The translation drafted from the catalogue of event types: a rule per (event type, way of recognising it), in the
/// catalogue's order, then the catch-all; the vendor's code as Data, TypeId fixed, Description in the design's order.
class TestCatalogueTranslation extends DraftFixtures {

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
    private static final Function<String, String> CHECK = detail -> SourceNotes.detailProblem(SCHEMA, detail);
    // Codes that mean nothing without the vendor's documentation.
    private static final String CODES = """
            ts,host,evt,usr,src,res
            2026-10-01T08:00:00Z,dc01,4624,alice,10.0.0.1,0x0
            2026-10-01T08:05:00Z,dc01,4625,bob,10.0.0.2,0xC000006A
            2026-10-01T08:10:00Z,dc01,4634,alice,10.0.0.1,0x0
            2026-10-01T08:11:00Z,dc01,4634,carol,10.0.0.3,0x0
            2026-10-01T08:12:00Z,dc01,9999,carol,10.0.0.3,0x0
            2026-10-01T08:13:00Z,dc01,1102,carol,10.0.0.3,0x0
            """;

    @Test
    void aRulePerWayOfRecognisingEachEventTypeInTheCataloguesOrderThenTheCatchAll() {
        final Map<String, Object> draft = draft(CODES, notes(List.of(
                type("Authenticate-Logon", "Logon", "Authenticate", "Logon", "true", way(eq("evt", "4624"))),
                type("Authenticate-LogonFailed", "Logon failed", "Authenticate", "Logon", "false",
                        way(eq("evt", "4625"), eq("res", "0xC000006A"))),
                type("Authenticate-Logoff", "Logoff", "Authenticate", "Logoff", null, way(eq("evt", "4634")),
                        way(eq("evt", "4647"))),
                dropped("Clear-Log", way(eq("evt", "9999")))), true));

        final List<Map<String, Object>> rules = JsonValues.maps(mappingOf(draft).get("events"));
        // More specific first (more conditions), ties in the catalogue's order; each way its own rule.
        assertThat(rules).extracting(rule -> rule.get("name")).containsExactly("authenticate_logonfailed",
                "authenticate_logon", "authenticate_logoff", "authenticate_logoff_2", "uncatalogued");
        assertThat(rules.get(3).get("when")).isEqualTo(List.of(Map.of("field", "evt", "equals", "4647")));
        // TypeId fixed, the event type's; none read from a field for every record.
        assertThat(fields(rules.get(1)).get("EventDetail/TypeId")).isEqualTo(map("path", "EventDetail/TypeId",
                "value", "Authenticate-Logon"));
        assertThat(common(draft)).doesNotContainKey("EventDetail/TypeId");
        assertThat(fields(rules.get(0)).get("EventDetail/Authenticate/Outcome/Success").get("value"))
                .isEqualTo("false");
        assertThat(fields(rules.get(2)).get("EventDetail/Authenticate/Action").get("value")).isEqualTo("Logoff");
        // The vendor's code as Data named after its field, every equals field of the way.
        assertThat(JsonValues.maps(rules.get(0).get("fields")))
                .contains(map("path", "EventDetail/Authenticate/Data", "data_name", "evt", "field", "evt"))
                .contains(map("path", "EventDetail/Authenticate/Data", "data_name", "res", "field", "res"));
        // The catch-all: Unknown, the records' fields as Data, agreed with the catalogue.
        final Map<String, Object> catchAll = rules.getLast();
        assertThat(catchAll).containsEntry("catch_all", true)
                .containsEntry("allow_unknown", CatalogueTranslation.CATCH_ALL_AGREED);
        assertThat(fields(catchAll).get("EventDetail/TypeId").get("value")).isEqualTo("Unknown");
        assertThat(JsonValues.maps(catchAll.get("fields")))
                .contains(map("path", "EventDetail/Unknown/Data", "data_name", "evt", "field", "evt"));
        // The dropped event type: a drop rule, no translating rule.
        assertThat(JsonValues.maps(mappingOf(draft).get("drop_when"))).containsExactly(map(
                "reason", "the user said to leave these untranslated: Clear-Log",
                "when", List.of(Map.of("field", "evt", "equals", "9999"))));
        // Said: the uncatalogued record (1102); every event type has records in the sample.
        assertThat(notes(draft)).anyMatch(note -> note.startsWith("1 sampled record(s) no event type recognises"))
                .noneMatch(note -> note.startsWith("Event types no sampled record"));
        assertThat(generate(mappingOf(draft)).problems()).as("generates").isEmpty();
    }

    @Test
    void anUnconfirmedCatchAllIsNotAgreedForTheUser() {
        final Map<String, Object> draft = draft(CODES, notes(List.of(
                type("Authenticate-Logon", "Logon", "Authenticate", "Logon", "true", way(eq("evt", "4624")))),
                false));

        final Map<String, Object> catchAll = JsonValues.maps(mappingOf(draft).get("events")).getLast();
        assertThat(catchAll).containsEntry("catch_all", true).doesNotContainKey("allow_unknown");
    }

    @Test
    void aCatchAllWithItsOwnTranslationWritesThatEventNotUnknown() {
        // Read back by rebuild_mapping from a hand-written last rule taking every remaining record.
        final Map<String, Object> notes = notes(List.of(
                type("Authenticate-LogonFailed", "Logon failed", "Authenticate", "Logon", "false",
                        way(eq("evt", "4625")))), true);
        notes.put("catch_all", Map.of("status", "confirmed", "translation", Map.of("type_id",
                "Authenticate-Logon", "event_detail", "Authenticate", "action", "Logon")));

        final Map<String, Object> catchAll = JsonValues.maps(mappingOf(draft(CODES, notes)).get("events"))
                .getLast();

        assertThat(catchAll).containsEntry("catch_all", true).doesNotContainKey("allow_unknown");
        assertThat(fields(catchAll).get("EventDetail/TypeId").get("value")).isEqualTo("Authenticate-Logon");
        assertThat(fields(catchAll).get("EventDetail/Authenticate/Action").get("value")).isEqualTo("Logon");
        assertThat(JsonValues.maps(catchAll.get("fields")))
                .contains(map("path", "EventDetail/Authenticate/Data", "data_name", "evt", "field", "evt"))
                .noneMatch(field -> String.valueOf(field.get("path")).contains("Unknown"));
    }

    @Test
    void aTypeIdNotGivenIsTheDefault() {
        final Map<String, Object> type = type(null, "Explicit logon", "Authenticate", "Logon", null,
                way(Map.of("field", "evt", "one_of", List.of("4648"))));

        final Map<String, Object> draft = draft(CODES, notes(List.of(type), true));

        assertThat(fields(JsonValues.maps(mappingOf(draft).get("events")).getFirst()).get("EventDetail/TypeId"))
                .containsEntry("value", "Authenticate-Explicit");
    }

    @Test
    void theDescriptionIsTheRecordsMessageElseTheEventTypesOwnText() {
        // (2)/(3): no message in the records: the event type's description, fixed.
        final Map<String, Object> logon = type("Authenticate-Logon", "Logon", "Authenticate", "Logon", "true",
                way(eq("evt", "4624")));
        logon.put("description", "An account logged on");
        final Map<String, Object> fixed = JsonValues.maps(mappingOf(draft(CODES, notes(List.of(logon), true)))
                .get("events")).getFirst();
        assertThat(fields(fixed).get("EventDetail/Description")).isEqualTo(map("path", "EventDetail/Description",
                "value", "An account logged on"));

        // (1): the records' message, alone.
        final String messages = """
                {"time":"2026-10-01T08:00:00Z","host":"a","op":"login","user":"alice","message":"alice logged in"}
                {"time":"2026-10-01T08:01:00Z","host":"a","op":"logout","user":"alice","message":"alice left"}
                """;
        final Map<String, Object> withMessage = type("Authenticate-Logon", "Logon", "Authenticate", "Logon", null,
                way(eq("op", "login")));
        withMessage.put("description", "An account logged on");
        final Map<String, Object> draft = draft(messages, notes(List.of(withMessage), true));
        assertThat(common(draft).get("EventDetail/Description")).containsEntry("field", "message");
        assertThat(fields(JsonValues.maps(mappingOf(draft).get("events")).getFirst()))
                .doesNotContainKey("EventDetail/Description");

        // (1): a field of free text a person wrote about the event.
        final String tickets = """
                time,host,op,user,comment
                2026-10-01T08:00:00Z,a,close,alice,fixed the printer
                2026-10-01T08:01:00Z,a,open,bob,printer broken
                """;
        final Map<String, Object> closed = type("Update-Ticket", "Ticket closed", "Update", "Modify", null,
                way(eq("op", "close")));
        closed.put("description", "A ticket is closed");
        final Map<String, Object> ticket = JsonValues.maps(mappingOf(draft(tickets, notes(List.of(closed), true)))
                .get("events")).getFirst();
        assertThat(fields(ticket).get("EventDetail/Description")).containsEntry("field", "comment")
                .doesNotContainKey("value");
    }

    @Test
    void aSoleEventTypeTakesEveryRecordAndHasNoCatchAllAfterIt() {
        final Map<String, Object> draft = draft(CODES, notes(List.of(
                type("Authenticate-Logon", "Logon", "Authenticate", "Logon", null)), true));

        final List<Map<String, Object>> rules = JsonValues.maps(mappingOf(draft).get("events"));
        assertThat(rules).singleElement().satisfies(rule -> assertThat(rule).doesNotContainKey("when")
                .doesNotContainKey("catch_all"));
        assertThat(generate(mappingOf(draft)).problems()).isEmpty();
    }

    @Test
    void theSourceTypeFieldIsNoVendorCode() {
        final Map<String, Object> notes = notes(List.of(
                type("Authenticate-Logon", "Logon", "Authenticate", "Logon", null, way(eq("host", "dc01"),
                        eq("evt", "4624")))), true);
        notes.put("scope", Map.of("source_type_field", "host", "source_types", List.of("dc01")));

        final Map<String, Object> rule = JsonValues.maps(mappingOf(draft(CODES, notes)).get("events")).getFirst();

        assertThat(JsonValues.maps(rule.get("fields"))).noneMatch(field -> "host".equals(field.get("data_name")))
                .anyMatch(field -> "evt".equals(field.get("data_name")));
    }

    @Test
    void aFieldAnotherEventTypeTestsIsStillDataOfThisOnesRecords() {
        final String sample = """
                ts,host,a,b,usr
                2026-10-01T08:00:00Z,dc01,login,7,alice
                2026-10-01T08:01:00Z,dc01,save,7,alice
                """;
        final Map<String, Object> draft = draft(sample, notes(List.of(
                type("Authenticate-Logon", "Logon", "Authenticate", "Logon", null, way(eq("a", "login"))),
                type("Update-Code", "Code saved", "Update", "Modify", null, way(eq("b", "7"), eq("a", "save")))),
                true));

        final Map<String, Object> logon = JsonValues.maps(mappingOf(draft).get("events")).stream()
                .filter(rule -> "authenticate_logon".equals(rule.get("name")))
                .findFirst()
                .orElseThrow();
        assertThat(JsonValues.maps(logon.get("fields")))
                .contains(map("path", "EventDetail/Authenticate/Data", "data_name", "b", "field", "b"));
    }

    @Test
    void theVendorCodesDataNameIsItsFieldsLastSegment() {
        assertThat(CatalogueTranslation.vendorCodeName("System/EventID", false)).isEqualTo("EventID");
        assertThat(CatalogueTranslation.vendorCodeName("event.logger_name", true)).isEqualTo("logger_name");
        assertThat(CatalogueTranslation.vendorCodeName("event.logger_name", false)).isEqualTo("event.logger_name");
        assertThat(CatalogueTranslation.vendorCodeName("['log.level']", true)).isEqualTo("log.level");
        assertThat(CatalogueTranslation.vendorCodeName("labels['app.kubernetes.io/name']", true))
                .isEqualTo("app.kubernetes.io/name");
        assertThat(CatalogueTranslation.vendorCodeName("Header/@id", false)).isEqualTo("id");
        assertThat(CatalogueTranslation.vendorCodeName("evt", false)).isEqualTo("evt");
    }

    @Test
    void withoutACatalogueTheDraftsOwnRulesStayADraftToBeCatalogued() {
        final Map<String, Object> draft = DraftMapping.draft(List.of(CODES), "Acme", "Acme", "Test",
                Map.of("source", "Acme", "fields", List.of()), CHECK, null);

        assertThat(notes(draft)).anyMatch(note -> note.startsWith("No catalogue of event types yet"));
        // Its own rule for the rest flagged as the catalogue's catch-all is (Unknown, the records' fields as Data), so
        // processing logs Caught by from the flag; no other rule is.
        final List<Map<String, Object>> rules = JsonValues.maps(mappingOf(draft).get("events"));
        assertThat(rules.getLast()).containsEntry("catch_all", true);
        assertThat(rules.subList(0, rules.size() - 1)).noneMatch(rule -> rule.containsKey("catch_all"));
    }

    @Test
    void aRuleStartsFromTheDraftsOwnRuleForItsRecords() {
        // The draft's own rule for a firewall's allowed traffic reads the addresses into Network/Permit: the event
        // type's rule for those records keeps what it read.
        final Map<String, Object> draft = draft(TestDraftMapping.FW_JSON, notes(List.of(
                type("Network-Permit", "Traffic allowed", "Network/Permit", null, null, way(eq("event_type",
                        "TRAFFIC"), eq("action", "ALLOW")))), true));

        final Map<String, Object> rule = JsonValues.maps(mappingOf(draft).get("events")).getFirst();
        assertThat(JsonValues.maps(rule.get("fields")))
                .contains(map("path", "EventDetail/Network/Permit/Destination/Port", "field", "dst_port"))
                .contains(map("path", "EventDetail/Network/Permit/Data", "data_name", "event_type", "field",
                        "event_type"));
    }

    @Test
    void aRepeatedGroupsItemsAreRecognisedByTheirOwnFieldsAndTheRecordsWithScopeRecord() {
        // One object wrapping the records (for_each): the item's fields are the conditions' own, the record's
        // with scope record.
        final String wrapped = """
                {"source": "audit", "Records": [
                 {"eventTime": "2026-10-01T08:00:00Z", "eventName": "ConsoleLogin", "user": "alice",
                  "host": "h1"},
                 {"eventTime": "2026-10-01T08:01:00Z", "eventName": "DeleteBucket", "user": "bob",
                  "host": "h1"}]}
                """;
        final Map<String, Object> login = type("Authenticate-ConsoleLogin", "Console login", "Authenticate", "Logon",
                null, way(eq("eventName", "ConsoleLogin"), Map.of("field", "source", "equals", "audit", "scope",
                        "record")));
        login.put("actor", "user");
        final Map<String, Object> draft = draft(wrapped, notes(List.of(login), true));

        assertThat(mappingOf(draft).get("for_each")).isEqualTo("Records");
        final Map<String, Object> rule = JsonValues.maps(mappingOf(draft).get("events")).getFirst();
        assertThat(rule.get("when")).isEqualTo(List.of(Map.of("field", "eventName", "equals", "ConsoleLogin"),
                Map.of("field", "source", "equals", "audit", "scope", "record")));
        // Its own records counted: the delete is uncatalogued.
        assertThat(notes(draft)).anyMatch(note -> note.startsWith("1 sampled record(s) no event type recognises"));
        assertThat(generate(mappingOf(draft)).problems()).isEmpty();
    }

    private static Map<String, Object> draft(final String sample, final Map<String, Object> notes) {
        return DraftMapping.draft(List.of(sample), "Acme", "Acme", "Test", notes, CHECK, (SplitterSpec) null);
    }

    static XsltGenerator.Generated generate(final Map<String, Object> mapping) {
        return TestDraftMapping.generate(mapping, SCHEMA, "4.1.0");
    }

    @SafeVarargs
    static Map<String, Object> type(final String typeId,
                                    final String title,
                                    final String detail,
                                    final String action,
                                    final String success,
                                    final Map<String, Object>... ways) {
        final Map<String, Object> type = new LinkedHashMap<>();
        type.put("type_id", typeId);
        type.put("title", title);
        type.put("event_detail", detail);
        type.put("action", action);
        type.put("success", success);
        type.put("actor", "usr");
        type.put("recognised_by", List.of(ways));
        type.put("basis", "documentation");
        type.put("confidence", "high");
        type.put("status", "confirmed");
        return type;
    }

    @SafeVarargs
    static Map<String, Object> dropped(final String typeId, final Map<String, Object>... ways) {
        final Map<String, Object> type = type(typeId, typeId, null, null, null, ways);
        type.put("drop", true);
        return type;
    }

    @SafeVarargs
    static Map<String, Object> way(final Map<String, Object>... when) {
        return Map.of("when", List.of(when), "names", List.of());
    }

    static Map<String, Object> eq(final String field, final String value) {
        return Map.of("field", field, "equals", value);
    }

    static Map<String, Object> notes(final List<Map<String, Object>> types, final boolean catchAllConfirmed) {
        final Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("source", "Acme");
        notes.put("fields", new ArrayList<>());
        notes.put("event_types", types);
        notes.put("catch_all", Map.of("status", catchAllConfirmed
                ? "confirmed"
                : "proposed"));
        return notes;
    }
}
