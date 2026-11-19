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
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The event types the user confirmed that a mapping translates otherwise, compared by the records the rules take.
class TestConfirmedEventTypes {

    private static final List<Object> RECORDS = List.of(
            Map.of("action", "login", "user", "alice"),
            Map.of("action", "logout", "user", "alice"),
            Map.of("action", "logout", "user", "bob"),
            Map.of("action", "usb", "user", ""));

    private static final EventSchema SCHEMA = schema();

    @Test
    void anEventTypeTranslatedAsConfirmedIsNotLost() {
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");

        assertThat(ConfirmedEventTypes.lost(mapping, RECORDS, catalogue(confirmed("Authenticate-Logon", "login",
                "Authenticate", "Logon")))).isEmpty();
    }

    @Test
    void rulesAreComparedByTheRecordsTheyTakeNotByConditionText() {
        // A rule told by another condition (a pattern, not the value) that takes the same records keeps the type.
        final TranslationMapping mapping = mapping("""
                [{"name": "signin", "when": [{"field": "action", "matches": "^log(in|on)$"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");

        assertThat(ConfirmedEventTypes.lost(mapping, RECORDS, catalogue(confirmed("Authenticate-Logon", "login",
                "Authenticate", "Logon")))).isEmpty();
    }

    @Test
    void anEventTypeWhoseRecordsGoToTheUnknownCatchAllIsLost() {
        // From the chat 163 review: the logoffs went into the rule for the rest, written as Unknown.
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "rest", "allow_unknown": "records not seen before",
                  "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]}]""");

        final List<ConfirmedEventTypes.Lost> lost = ConfirmedEventTypes.lost(mapping, RECORDS,
                catalogue(confirmed("Authenticate-Logoff", "logout", "Authenticate", "Logoff")));

        assertThat(lost).containsExactly(new ConfirmedEventTypes.Lost("Authenticate-Logoff", "logout records",
                "action = logout", "Authenticate, Action Logoff, TypeId Authenticate-Logoff", "rest",
                "Unknown", 2));
        assertThat(ConfirmedEventTypes.takenBy(lost, "rest")).containsExactly("Authenticate-Logoff (logout records) "
                                                                              + "(2 records), confirmed as "
                                                                              + "Authenticate, Action Logoff, TypeId "
                                                                              + "Authenticate-Logoff");
        assertThat(ConfirmedEventTypes.keys(lost)).containsExactly(Map.entry("Authenticate-Logoff in rest",
                "Unknown"));
    }

    @Test
    void anEventTypeWrittenWithAnotherActionOrTypeIdIsLost() {
        // From the chat 163 review: an event type confirmed as Action Other became Logon.
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");

        assertThat(ConfirmedEventTypes.lost(mapping, RECORDS, catalogue(confirmed("Authenticate-Logon", "login",
                "Authenticate", "Other")))).singleElement().satisfies(one -> {
                    assertThat(one.writes()).isEqualTo("Authenticate, Action Logon");
                    assertThat(one.wanted()).isEqualTo("Authenticate, Action Other, TypeId Authenticate-Logon");
                });
        assertThat(ConfirmedEventTypes.lost(mapping, RECORDS, catalogue(confirmed("Authenticate-SessionStart",
                "login", "Authenticate", "Logon")))).singleElement()
                .extracting(ConfirmedEventTypes.Lost::writes).isEqualTo("TypeId Authenticate-Logon");
    }

    @Test
    void anEventTypeConfirmedWithAnActionTheSchemaDoesNotAllowIsNotLostToAnAllowedOne() {
        // Seen in the agent eval (case 60): "Add member to role" confirmed as an Authorise Action, which takes only
        // Request or Modify.
        final TranslationMapping modify = mapping("""
                [{"name": "role", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authorise/Action", "value": "Modify"}]}]""");
        final Catalogue types = catalogue(confirmed("Authorise-Role", "login", "Authorise", "Add member to role"));

        assertThat(ConfirmedEventTypes.lost(modify, RECORDS, types, null, SCHEMA)).isEmpty();
        // Without the schema, compared as confirmed.
        assertThat(ConfirmedEventTypes.lost(modify, RECORDS, types, null, null)).singleElement()
                .extracting(ConfirmedEventTypes.Lost::writes).isEqualTo("Authorise, Action Modify");
        // An Action the schema allows, confirmed, is still followed.
        assertThat(ConfirmedEventTypes.lost(modify, RECORDS, catalogue(confirmed("Authorise-Role", "login",
                "Authorise", "Request")), null, SCHEMA)).singleElement()
                .extracting(ConfirmedEventTypes.Lost::writes).isEqualTo("Authorise, Action Modify");
        // A written Action the schema doesn't allow either is still lost.
        final TranslationMapping invalid = mapping("""
                [{"name": "role", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authorise/Action", "value": "Grant"}]}]""");
        assertThat(ConfirmedEventTypes.lost(invalid, RECORDS, types, null, SCHEMA)).hasSize(1);
    }

    @Test
    void anEventTypeTakenByNoRuleOrDroppedIsLost() {
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""",
                """
                        [{"reason": "noise", "when": [{"field": "action", "equals": "usb"}]}]""");

        final List<ConfirmedEventTypes.Lost> lost = ConfirmedEventTypes.lost(mapping, RECORDS, catalogue(
                confirmed("Authenticate-Logoff", "logout", "Authenticate", "Logoff"),
                confirmed("Alert-Usb", "usb", "Alert", null)));

        assertThat(lost).extracting(ConfirmedEventTypes.Lost::writes).containsExactly("taken by no rule",
                "dropped (noise)");
        assertThat(ConfirmedEventTypes.markdown(lost, 4)).startsWith("**Event types you confirmed that this "
                                                                     + "mapping translates otherwise**")
                .contains("| Authenticate-Logoff (logout records) | action = logout | Authenticate, Action "
                          + "Logoff, TypeId Authenticate-Logoff | 2 | taken by no rule | none |");
    }

    @Test
    void anEventTypeRecognisedByAPatternOrTwoWaysIsFollowedByEachRecord() {
        final TranslationMapping mapping = mapping("""
                [{"name": "rest", "allow_unknown": "other lines",
                  "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "msg", "field": "msg"}]}]""");
        final List<Way> ways = List.of(
                new Way(List.of(Condition.fromJson(Map.of("field", "msg", "matches", "^Failed password for"))),
                        List.of()),
                new Way(List.of(Condition.fromJson(Map.of("field", "msg", "starts_with", "Invalid user"))),
                        List.of()));
        final EventType failed = new EventType("Authenticate-FailedLogon", "Failed password", null,
                "Authenticate", "Logon", null, null, null, null, ways, false, EventType.USER_CONFIRMED, "high", null,
                EventType.CONFIRMED);

        final List<ConfirmedEventTypes.Lost> lost = ConfirmedEventTypes.lost(mapping, List.of(
                Map.of("msg", "Failed password for alice from 10.0.0.1"),
                Map.of("msg", "Invalid user bob from 10.0.0.2"),
                Map.of("msg", "Server listening on port 22")), catalogue(failed));

        assertThat(lost).singleElement().satisfies(one -> {
            assertThat(one.records()).isEqualTo(2);
            assertThat(one.writes()).isEqualTo("Unknown");
            assertThat(one.recognisedBy()).startsWith("msg matches ^Failed password for; or msg starts with ")
                    .contains("Invalid user");
        });
    }

    @Test
    void recordsOfSourceTypesOutsideAGroupBuildsScopeAreNotCompared() {
        // Seen (eval 58): a group build of PegaIntegration alone was asked, five to eight times, to keep or follow
        // what the user confirmed for PegaDB and the other source types parked for later builds.
        final List<Object> records = List.of(
                Map.of("sourcetype", "PegaDB", "action", "query"),
                Map.of("sourcetype", "PegaDB", "action", "query"),
                Map.of("sourcetype", "PegaIntegration", "action", "GetCustomer"),
                Map.of("sourcetype", "PegaIntegration", "action", "query"));
        final TranslationMapping mapping = mapping("""
                [{"name": "getcustomer", "when": [{"field": "action", "equals": "GetCustomer"}],
                  "fields": [{"path": "EventDetail/View/Action", "value": "View"}]}]""");
        final Catalogue types = catalogue(confirmed("Search-Query", "query", "Search", null));
        final SourceTypeScope scope = new SourceTypeScope("pega-2", "sourcetype", List.of(
                new SourceTypeRows.Row("PegaDB", SourceTypeRows.VALUE, "PegaDB"),
                new SourceTypeRows.Row("PegaIntegration", SourceTypeRows.VALUE, "PegaIntegration")),
                List.of("PegaIntegration"));

        // Without the scope, every record is compared.
        assertThat(ConfirmedEventTypes.lost(mapping, records, types)).singleElement()
                .extracting(ConfirmedEventTypes.Lost::records).isEqualTo(3);
        // Within it, only the group's own records.
        assertThat(ConfirmedEventTypes.lost(mapping, records, types, scope, null)).singleElement()
                .extracting(ConfirmedEventTypes.Lost::records).isEqualTo(1);
    }

    @Test
    void aDroppedEventTypeARuleStillTranslatesIsWarnedOf() {
        final TranslationMapping translated = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "rest", "allow_unknown": "other",
                  "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]}]""");
        final TranslationMapping dropped = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""", """
                [{"reason": "the user said to leave these untranslated: Alert-Usb",
                  "when": [{"field": "action", "equals": "usb"}]}]""");
        final Catalogue types = catalogue(confirmed("Alert-Usb", "usb", "Alert", null).droppedByUser());

        assertThat(ConfirmedEventTypes.undropped(translated, RECORDS, types)).singleElement().asString()
                .startsWith("dropped event type Alert-Usb: 1 sample records are translated by rule 'rest'");
        assertThat(ConfirmedEventTypes.undropped(dropped, RECORDS, types)).isEmpty();
        // A dropped event type isn't one to translate as confirmed.
        assertThat(ConfirmedEventTypes.lost(dropped, RECORDS, types)).isEmpty();
    }

    @Test
    void onlyWhatTheUserConfirmedIsCheckedAndEventTypesTheSampleLacksAreNot() {
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");
        final EventType proposed = new EventType("Authenticate-Logoff", null, null, "Authenticate", "Logoff", null,
                null, null, null, List.of(way("logout")), false, EventType.DOCUMENTATION, "high", null,
                EventType.PROPOSED);

        assertThat(ConfirmedEventTypes.lost(mapping, RECORDS, catalogue(proposed, confirmed("Process-Reboot",
                "reboot", "Process", "Execute")))).isEmpty();
        assertThat(ConfirmedEventTypes.lost(mapping, List.of(), catalogue(confirmed("Authenticate-Logoff",
                "logout", "Authenticate", "Logoff")))).isEmpty();
        assertThat(ConfirmedEventTypes.lost(mapping, RECORDS, null)).isEmpty();
    }

    @Test
    void itemRulesAreSaidToBeNotCheckedNotSkippedSilently() {
        final TranslationMapping items = TranslationMapping.fromJson(JsonUtil.readValue("""
                {"input": "json", "for_each": "items", "events": [{"name": "item",
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]}""", Map.class));

        assertThat(ConfirmedEventTypes.notChecked(items, RECORDS, catalogue(confirmed("Authenticate-Logon",
                "login", "Authenticate", "Logon")))).singleElement().asString()
                .contains("not checked", "for_each 'items'");
        // Nothing confirmed: nothing to say.
        assertThat(ConfirmedEventTypes.notChecked(items, RECORDS, null)).isEmpty();
    }

    private static Catalogue catalogue(final EventType... types) {
        return new Catalogue(List.of(types), null, null);
    }

    private static EventType confirmed(final String typeId,
                                       final String value,
                                       final String detail,
                                       final String action) {
        return new EventType(typeId, value + " records", null, detail, action, null, null, null, null,
                List.of(way(value)), false, EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED);
    }

    private static Way way(final String value) {
        return new Way(List.of(Condition.fromJson(Map.of("field", "action", "equals", value))), List.of());
    }

    private static EventSchema schema() {
        try (final InputStream in = TestConfirmedEventTypes.class.getResourceAsStream("event-logging-v4.1.0.xsd")) {
            return EventSchema.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TranslationMapping mapping(final String events) {
        return mapping(events, "[]");
    }

    @SuppressWarnings("unchecked")
    private static TranslationMapping mapping(final String events, final String dropWhen) {
        return TranslationMapping.fromJson(JsonUtil.readValue("{\"input\": \"json\", \"events\": " + events
                                                              + ", \"drop_when\": " + dropWhen + "}", Map.class));
    }
}
