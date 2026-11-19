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
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Source notes from the user's documentation, kept readable by the tools and followed by the draft.
class TestSourceNotes {

    private static final EventSchema SCHEMA = schema();
    private static final Function<String, String> CHECK = detail -> SourceNotes.detailProblem(SCHEMA, detail);

    // The fields and values of a sample whose codes mean nothing without the vendor's documentation.
    private static final List<String> NAMES = List.of("ts", "host", "evt", "usr", "src", "res");
    private static final Map<String, List<String>> VALUES = Map.of(
            "ts", List.of("2026-10-01T08:00:00Z"), "host", List.of("dc01"),
            "evt", List.of("4624", "4625", "4634", "9999"), "usr", List.of("alice", "bob", "alice", "carol"),
            "src", List.of("10.0.0.1"), "res", List.of("0x0", "0xC000006A"));
    private static final String NOTES = """
            {"source": "Acme directory",
             "fields": [
              {"field": "evt", "meaning": "Event id"},
              {"field": "usr", "meaning": "Account name", "event_logging_path": "EventSource/User/Id"},
              {"field": "src", "meaning": "Workstation address", "event_logging_path": "EventSource/Client/IPAddress"},
              {"field": "res", "meaning": "Status code",
               "values": {"0x0": "success", "0xC000006A": "failed: bad password"}}],
             "events": [
              {"event": "4624", "description": "An account logged on", "event_detail": "Authenticate",
               "type_id": "Logon", "field": "evt", "value": "4624", "action": "Logon", "success": true},
              {"event": "4625", "description": "An account failed to log on", "event_detail": "Authenticate",
               "type_id": "Logon failed", "field": "evt", "value": "4625", "action": "Logon", "success": false},
              {"event": "4634", "description": "An account logged off", "event_detail": "Authenticate",
               "type_id": "Logoff", "field": "evt", "value": "4634", "action": "Logoff"},
              {"event": "4648", "description": "A logon with explicit credentials", "event_detail": "Authenticate",
               "type_id": "Explicit logon", "field": "evt", "value": "4648", "action": "Logon"}]}
            """;
    // The catalogue an agent recorded from a firewall sample alone (no documentation), as it was given.
    private static final String GUESSED = """
            [{"event": "TRAFFIC-ALLOW", "description": "Network traffic allowed", "field": "event_type",
              "value": "TRAFFIC", "event_detail": "Allow"},
             {"event": "TRAFFIC-DENY", "description": "Network traffic blocked", "field": "event_type",
              "value": "TRAFFIC", "event_detail": "Deny"},
             {"event": "LOGIN-SUCCESS", "description": "Administrator logged in", "field": "action",
              "value": "LOGIN_SUCCESS", "event_detail": "Authenticate", "success": true},
             {"event": "LOGIN-FAILED", "description": "Administrator failed to log in", "field": "action",
              "value": "LOGIN_FAILED", "event_detail": "Authenticate", "success": false},
             {"event": "CONFIG-CHANGE", "description": "Configuration modified", "field": "action",
              "value": "CONFIG_CHANGE", "event_detail": "Update"},
             {"event": "VPN-DOWN", "description": "VPN tunnel disconnected", "field": "action",
              "value": "VPN_TUNNEL_DOWN", "event_detail": "Unknown"}]
            """;

    @Test
    void theNotesAreKeptInTheDocAndReadBack() {
        final Map<String, Object> notes = notes();
        final String text = "# Acme directory source notes\n\nSummary.\n\n" + SourceNotes.block(notes) + "\n";

        assertThat(SourceNotes.readNotes(text)).isEqualTo(notes);
        assertThat(SourceNotes.readNotes("no notes here")).isNull();
        assertThat(SourceNotes.readNotes(null)).isNull();
        assertThat(SourceNotes.readNotes("<!-- stroom-mcp source notes\n[1, 2]\n-->")).isNull();
        // Indented by one, the form the notes are kept in.
        assertThat(SourceNotes.block(Map.of("source", "A"))).isEqualTo("<!-- stroom-mcp source notes\n{\n "
                                                                       + "\"source\": \"A\"\n}\n-->");
    }

    @Test
    void codesBecomeOutcomesOnlyWhenEveryMeaningSaysOne() {
        assertThat(SourceNotes.outcomeMap(Map.of("0x0", "success", "0xC000006A", "failed: bad password")))
                .isEqualTo(Map.of("0x0", "true", "0xC000006A", "false"));
        assertThat(SourceNotes.outcomeMap(Map.of("0x0", "success", "0x1", "pending"))).isNull();
    }

    @Test
    void aCatalogueNamingNoActionElementOrTwinEventsIsRefused() {
        final List<String> problems = SourceNotes.catalogueProblems(guessed(), CHECK);

        assertThat(problems).anyMatch(problem -> problem.startsWith("TRAFFIC-ALLOW: 'Allow' is not an EventDetail "
                                                                    + "action element")
                                                 && problem.contains("Network/Permit"));
        assertThat(problems).anyMatch(problem -> problem.startsWith("TRAFFIC-ALLOW and TRAFFIC-DENY are both "
                                                                    + "event_type=TRAFFIC"));
        // Authenticate, Update and Unknown are elements.
        assertThat(problems).hasSize(3);
        assertThat(CHECK.apply("Network")).contains("'Network' needs its action below it: ")
                .contains("Network/Permit");
        assertThat(CHECK.apply("Network/Permit")).isNull();
        assertThat(CHECK.apply("Authenticate")).isNull();
        assertThat(CHECK.apply("TypeId")).contains("'TypeId' is not an EventDetail action element");
        assertThat(CHECK.apply("")).isNull();
        // Without the schema, the events are still checked for twins.
        assertThat(SourceNotes.catalogueProblems(guessed(), null)).hasSize(1);
    }

    @Test
    void theDraftPlacesTheFieldsTheDocumentationPlaces() {
        final List<Map<String, Object>> events = new ArrayList<>(List.of(other()));
        final Map<String, Object> mapping = mapping(new ArrayList<>(List.of(entry("path", "EventSource/User/Id",
                "field", "user"))), events);

        final Map<String, Object> applied = SourceNotes.applyToDraft(mapping, NAMES, VALUES, notes(), CHECK);

        final Map<Object, Map<String, Object>> common = byKey(list(mapping.get("common")), "path");
        assertThat(common.get("EventSource/User/Id").get("field")).isEqualTo("usr");
        assertThat(common.get("EventSource/Client/IPAddress").get("field")).isEqualTo("src");
        assertThat(applied.get("fields")).isEqualTo(List.of("usr -> EventSource/User/Id",
                "src -> EventSource/Client/IPAddress"));
        // The old notes' events are no catalogue: the draft's own rules stay.
        assertThat(applied.get(SourceNotes.CATALOGUED)).isEqualTo(false);
        assertThat(mapping.get("events")).isSameAs(events);
    }

    @Test
    void theCatalogueIsReadFromTheNotesJsonBlock() {
        final Map<String, Object> notes = TestCatalogueTranslation.notes(List.of(TestCatalogueTranslation.type(
                "Authenticate-Logon", "Logon", "Authenticate", "Logon", "true",
                TestCatalogueTranslation.way(TestCatalogueTranslation.eq("evt", "4624")))), true);
        notes.put("scope", Map.of("source_type_field", "event>log_type", "source_types", List.of("Integration")));

        assertThat(SourceNotes.catalogue(notes)).satisfies(catalogue -> {
            assertThat(catalogue.eventTypes()).extracting(type -> type.typeId()).containsExactly("Authenticate-Logon");
            assertThat(catalogue.catchAll().confirmed()).isTrue();
            assertThat(catalogue.scope().sourceTypes()).containsExactly("Integration");
        });
        assertThat(SourceNotes.catchAllReason(notes, SourceNotes.catalogue(notes)))
                .isEqualTo(CatalogueTranslation.CATCH_ALL_AGREED);
        notes.put("catch_all", Map.of("status", "confirmed", "allow_unknown", "the user's reason"));
        assertThat(SourceNotes.catchAllReason(notes, SourceNotes.catalogue(notes))).isEqualTo("the user's reason");
        assertThat(SourceNotes.catalogue(notes())).as("no event_types").isNull();
        assertThat(SourceNotes.catalogue(null)).isNull();
        // Several docs: the first catalogue.
        assertThat(SourceNotes.merged(List.of(notes(), notes)).get("event_types")).isEqualTo(notes.get("event_types"));
    }

    @Test
    void severalNotesDocsAreReadAsOneTheFirstFieldOfANameKept() {
        final Map<String, Object> merged = SourceNotes.merged(List.of(notes(), entry("fields",
                List.of(entry("field", "evt", "meaning", "other")), "events", List.of())));

        assertThat(list(merged.get("fields")).getFirst().get("meaning")).isEqualTo("Event id");
        assertThat(list(merged.get("events"))).hasSize(list(notes().get("events")).size());
    }

    @Test
    void aMappingThatContradictsTheCatalogueIsReported() {
        final List<Map<String, Object>> events = new ArrayList<>();
        for (final Map<String, Object> event : list(notes().get("events"))) {
            events.add(sampleRule(String.valueOf(event.get("value")), List.of(entry("field", "evt", "equals",
                            event.get("value"))), entry("path", "EventDetail/TypeId", "value", event.get("type_id")),
                    entry("path", "EventDetail/Authenticate/Action", "value", event.get("action"))));
        }
        events.add(other());
        final Map<String, Object> mapping = mapping(new ArrayList<>(), events);
        assertThat(SourceNotes.checkMapping(mapping, notes())).isEmpty();

        for (final Map<String, Object> rule : list(mapping.get("events"))) {
            if ("4625".equals(rule.get("name"))) {
                rule.put("fields", List.of(entry("path", "EventDetail/TypeId", "value", "Logon"),
                        entry("path", "EventDetail/Unknown/Data", "data_name", "evt", "field", "evt")));
            }
        }
        final List<String> problems = SourceNotes.checkMapping(mapping, notes());
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("4625 (evt=4625): the documentation says "
                                                                    + "Authenticate; rule '4625' writes Unknown"));
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("TypeId is 'Logon failed'; rule '4625' writes "
                                                                    + "'Logon'"));

        mapping.put("events", list(mapping.get("events")).stream()
                .filter(rule -> !"4634".equals(rule.get("name")) && rule.get("when") instanceof final List<?> when
                                                                    && !when.isEmpty())
                .toList());
        assertThat(SourceNotes.checkMapping(mapping, notes())).anySatisfy(p -> assertThat(p).contains(
                "4634 (evt=4634): no rule takes it"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFieldsTimeFormatIsKeptWhenANoteMapsItAndNotTakenFromAnotherField() {
        // Seen: the note for an epoch field replaced the drafted TimeCreated entry and its time_format with it, and
        // the agent spent turns finding the syntax.
        final List<Map<String, Object>> common = new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("path", "EventTime/TimeCreated", "field", "ts", "time_format", "epoch_s",
                        "timezone", "UTC")),
                new LinkedHashMap<>(Map.of("path", "EventSource/Device/HostName", "field", "host",
                        "time_format", "not a time"))));
        final Map<String, Object> mapping = mapping(common, new ArrayList<>(List.of(other())));
        final Map<String, Object> notes = new LinkedHashMap<>(Map.of("fields", List.of(
                Map.of("field", "ts", "meaning", "When", "event_logging_path", "EventTime/TimeCreated"),
                Map.of("field", "usr", "meaning", "Who", "event_logging_path", "EventSource/Device/HostName"))));

        SourceNotes.applyToDraft(mapping, NAMES, VALUES, notes, CHECK);

        final List<Map<String, Object>> after = (List<Map<String, Object>>) mapping.get("common");
        assertThat(after).filteredOn(entry -> "EventTime/TimeCreated".equals(entry.get("path"))).singleElement()
                .satisfies(entry -> assertThat(entry).containsEntry("field", "ts")
                        .containsEntry("time_format", "epoch_s").containsEntry("timezone", "UTC"));
        assertThat(after).filteredOn(entry -> "EventSource/Device/HostName".equals(entry.get("path")))
                .singleElement()
                .as("another field's format isn't taken")
                .satisfies(entry -> assertThat(entry).containsEntry("field", "usr").doesNotContainKey("time_format"));
    }

    @Test
    void aDroppedKindsKeyIsTheConditionsOfItsDropRule() {
        assertThat(SourceNotes.dropConditions("action=purge")).containsExactly(entry("field", "action", "equals",
                "purge"));
        assertThat(SourceNotes.dropConditions("variant=linux: message matches ^usb \\S+: new")).containsExactly(
                entry("field", "variant", "equals", "linux"),
                entry("field", "message", "matches", "^usb \\S+: new"));
        assertThat(SourceNotes.dropConditions("category=SSLVPN: event_id = Message")).containsExactly(
                entry("field", "category", "equals", "SSLVPN"), entry("field", "event_id", "equals", "Message"));
        assertThat(SourceNotes.dropConditions("nonsense")).isEmpty();
    }

    @Test
    void anActionIsReadAgainstTheEnumerationOfItsActionElement() {
        // Seen in the agent eval (cases 60 and 65): "Add member to role" and "MFA" accepted as Actions.
        assertThat(SourceNotes.allowedActions(SCHEMA, "Authorise")).containsExactly("Request", "Modify");
        assertThat(SourceNotes.allowedAction(SCHEMA, "Authorise", "Add member to role")).isEmpty();
        assertThat(SourceNotes.allowedAction(SCHEMA, "Authenticate", "MFA")).isEmpty();
        assertThat(SourceNotes.allowedAction(SCHEMA, "/Authenticate/", "logon")).contains("Logon");
        assertThat(SourceNotes.allowedAction(SCHEMA, "Authenticate", "Logoff")).contains("Logoff");
        // No Action, or no action given: nothing to check.
        assertThat(SourceNotes.allowedActions(SCHEMA, "Alert")).isNull();
        assertThat(SourceNotes.allowedAction(SCHEMA, "Alert", "Blocked")).contains("Blocked");
        assertThat(SourceNotes.allowedAction(SCHEMA, "Authorise", null)).contains("");
    }

    private static Map<String, Object> mapping(final List<Map<String, Object>> common,
                                               final List<Map<String, Object>> events) {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("common", common);
        mapping.put("events", events);
        return mapping;
    }

    private static Map<String, Object> other() {
        return entry("name", "other", "fields", new ArrayList<>(List.of(entry("path", "EventDetail/TypeId", "field",
                "evt"))));
    }

    private static Map<String, Object> sampleRule(final String name,
                                                  final List<Map<String, Object>> when,
                                                  final Map<?, ?>... fields) {
        return entry("name", name, "when", when, "fields", new ArrayList<>(List.of(fields)));
    }

    private static Map<String, Object> entry(final Object... keysAndValues) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            entry.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> notes() {
        return (Map<String, Object>) ToolJson.loads(NOTES);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> guessed() {
        return (List<Map<String, Object>>) ToolJson.loads(GUESSED);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(final Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<Object, Map<String, Object>> byKey(final List<Map<String, Object>> items, final String key) {
        return items.stream().collect(Collectors.toMap(item -> item.get(key), item -> item, (a, b) -> a,
                LinkedHashMap::new));
    }

    private static EventSchema schema() {
        return EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);
    }
}
