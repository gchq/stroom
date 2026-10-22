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

import stroom.ai.impl.tools.sample.PyJson;
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

/// Source notes from the user's documentation, kept readable by the tools and followed by the draft (the Stroom MCP
/// server's `utils/sourcenotes.py`, and its tests in `tests/test_sourcenotes.py`).
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
        // Indented by one, as the Stroom MCP server writes it.
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
    void theDraftFollowsTheDocumentation() {
        final Map<String, Object> mapping = mapping(new ArrayList<>(List.of(entry("path", "EventSource/User/Id",
                "field", "user"))), new ArrayList<>(List.of(other())));

        final Map<String, Object> applied = SourceNotes.applyToDraft(mapping, NAMES, VALUES, notes(), CHECK);

        final Map<Object, Map<String, Object>> common = byKey(list(mapping.get("common")), "path");
        assertThat(common.get("EventSource/User/Id").get("field")).isEqualTo("usr");
        assertThat(common.get("EventSource/Client/IPAddress").get("field")).isEqualTo("src");
        final Map<Object, Map<String, Object>> rules = byKey(list(mapping.get("events")), "name");
        assertThat(rules.get("4625").get("when")).isEqualTo(List.of(Map.of("field", "evt", "equals", "4625")));
        final Map<Object, Map<String, Object>> failed = byKey(list(rules.get("4625").get("fields")), "path");
        assertThat(failed.get("EventDetail/TypeId").get("value")).isEqualTo("Logon failed");
        assertThat(failed.get("EventDetail/Authenticate/Action").get("value")).isEqualTo("Logon");
        assertThat(failed.get("EventDetail/Authenticate/Outcome/Success").get("value")).isEqualTo("false");
        assertThat(failed.get("EventDetail/Description").get("value")).isEqualTo("An account failed to log on");
        // The schema wants the user authenticating: the one the event is about.
        assertThat(failed.get("EventDetail/Authenticate/User/Id").get("field")).isEqualTo("usr");
        assertThat(byKey(list(rules.get("4634").get("fields")), "path").get("EventDetail/Authenticate/Action")
                .get("value")).isEqualTo("Logoff");
        // Documented, though the sample has none.
        assertThat(rules).containsKey("4648");
        // The rule for everything else stays last.
        assertThat(list(mapping.get("events")).getLast().get("name")).isEqualTo("other");
        assertThat(applied.get("not_in_sample")).isEqualTo(List.of("evt=4648"));
        assertThat(applied.get("not_in_catalogue")).isEqualTo(List.of("evt=9999"));
        assertThat(applied.get("fields")).isEqualTo(List.of("usr -> EventSource/User/Id",
                "src -> EventSource/Client/IPAddress"));
        assertThat(applied.get("events")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains("4648 (evt=4648) -> Authenticate, not in the sample");
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
        final Map<String, Object> mapping = mapping(new ArrayList<>(List.of(entry("path", "EventSource/User/Id",
                "field", "user"))), new ArrayList<>(List.of(other())));
        SourceNotes.applyToDraft(mapping, NAMES, VALUES, notes(), CHECK);
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
    void withoutCataloguedEventsOnlyTheFieldsAreApplied() {
        final List<Map<String, Object>> events = new ArrayList<>(List.of(other()));
        final Map<String, Object> mapping = mapping(new ArrayList<>(), events);
        final Map<String, Object> notes = notes();
        notes.remove("events");

        final Map<String, Object> applied = SourceNotes.applyToDraft(mapping, NAMES, VALUES, notes, CHECK);

        assertThat(applied.get("events")).isEqualTo(List.of());
        assertThat(mapping.get("events")).isSameAs(events);
    }

    @Test
    void whereTheCatalogueCantBeFollowedTheSamplesOwnRulesAreKept() {
        final Map<String, Object> permitted = sampleRule("traffic_permitted", List.of(
                        entry("field", "event_type", "equals", "TRAFFIC"), entry("field", "action", "equals", "ALLOW")),
                entry("path", "EventDetail/Network/Permit/Source/Device/IPAddress", "field", "src_ip"));
        final Map<String, Object> denied = sampleRule("traffic_denied", List.of(
                        entry("field", "event_type", "equals", "TRAFFIC"), entry("field", "action", "equals", "DENY")),
                entry("path", "EventDetail/Network/Deny/Source/Device/IPAddress", "field", "src_ip"));
        final Map<String, Object> login = sampleRule("login_success", List.of(entry("field", "action", "one_of",
                        List.of("LOGIN_SUCCESS"))),
                entry("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                entry("path", "EventDetail/Authenticate/Data", "data_name", "x", "field", "x"));
        final Map<String, Object> config = sampleRule("config_change", List.of(entry("field", "action", "equals",
                        "CONFIG_CHANGE")),
                entry("path", "EventDetail/Update/After/Configuration/Type", "value", "Policy"));
        final Map<String, Object> mapping = mapping(new ArrayList<>(), new ArrayList<>(List.of(permitted, denied,
                login, config, other())));
        final List<String> names = List.of("event_type", "action", "src_ip");
        final Map<String, List<String>> values = Map.of("event_type", List.of("TRAFFIC", "ADMIN", "SYSTEM"),
                "action", List.of("ALLOW", "DENY", "LOGIN_SUCCESS", "LOGIN_FAILED", "CONFIG_CHANGE",
                        "VPN_TUNNEL_DOWN"), "src_ip", List.of("192.0.2.10"));
        final Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("events", guessed());

        final Map<String, Object> applied = SourceNotes.applyToDraft(mapping, names, values, notes, CHECK);

        final Map<Object, Map<String, Object>> rules = byKey(list(mapping.get("events")), "name");
        assertThat(rules.get("traffic_permitted")).isSameAs(permitted);
        assertThat(rules.get("traffic_denied")).isSameAs(denied);
        assertThat(rules.values()).noneMatch(rule -> list(rule.get("fields")).stream().anyMatch(field ->
                String.valueOf(field.get("path")).startsWith("EventDetail/Allow")
                || String.valueOf(field.get("path")).startsWith("EventDetail/Deny")));
        assertThat(applied.get("from_sample")).isEqualTo(List.of("event_type=TRAFFIC: the catalogue has more than "
                                                                 + "one event for it, so the sample's rules are "
                                                                 + "kept (traffic_permitted: Network; "
                                                                 + "traffic_denied: Network)"));
        // The catalogue's own events, completed from the sample's rule for the same records where the schema wants
        // more.
        final Map<Object, Map<String, Object>> loginFields = byKey(list(rules.get("login_success").get("fields")),
                "path");
        assertThat(loginFields).containsKey("EventDetail/Authenticate/Action");
        assertThat(loginFields.get("EventDetail/Authenticate/Outcome/Success").get("value")).isEqualTo("true");
        assertThat(rules.get("config_change").get("fields")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .anyMatch(field -> "EventDetail/Update/After/Configuration/Type".equals(((Map<?, ?>) field)
                        .get("path")));
        // Unknown is left for the user to agree when the XSLT is built, never agreed by the draft.
        assertThat(byKey(list(rules.get("vpn_down").get("fields")), "path")).containsKey("EventDetail/Unknown/Data");
        assertThat(rules.values()).noneMatch(rule -> rule.containsKey("allow_unknown"));
    }

    @Test
    void ruleNamesAreMadeUnique() {
        final Map<String, Object> mapping = mapping(new ArrayList<>(), new ArrayList<>());
        final Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("events", List.of(
                entry("event", "Log on", "description", "a", "event_detail", "Authenticate", "field", "e", "value",
                        "1"),
                entry("event", "log-on", "description", "b", "event_detail", "Authenticate", "field", "e", "value",
                        "2")));

        SourceNotes.applyToDraft(mapping, List.of("e"), Map.of("e", List.of("1", "2")), notes, CHECK);

        assertThat(list(mapping.get("events"))).extracting(rule -> rule.get("name"))
                .containsExactly("log_on", "log_on_");
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
        return (Map<String, Object>) PyJson.loads(NOTES);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> guessed() {
        return (List<Map<String, Object>>) PyJson.loads(GUESSED);
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
