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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A hand-edited XSLT's rules (read back into a mapping) read back into the catalogue of event types.
class TestCatalogueFromMapping {

    private static final String COMMON = """
            "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                       {"path": "EventSource/System/Name", "value": "Acme"},
                       {"path": "EventSource/System/Environment", "value": "Test"},
                       {"path": "EventSource/Generator", "value": "Acme"},
                       {"path": "EventSource/Device/HostName", "field": "host"},
                       {"path": "EventSource/User/Id", "field": "user"}]""";

    @Test
    void aWayEditedByHandIsReadBackIntoItsEventTypeTheRestKept() {
        final Catalogue before = new Catalogue(List.of(
                type("Authenticate-Logon", "User logon", "Authenticate", "Logon", "login"),
                type("Authenticate-Logoff", "User logoff", "Authenticate", "Logoff", "logout")), null, null);
        final TranslationMapping edited = mapping("""
                [{"name": "authenticate_logon", "when": [{"field": "action", "equals": "signin"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "authenticate_logoff", "when": [{"field": "action", "equals": "logout"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logoff"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logoff"}]},
                 {"name": "uncatalogued", "catch_all": true, "allow_unknown": "records no event type recognises",
                  "fields": [{"path": "EventDetail/TypeId", "value": "Unknown"},
                             {"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]}]""");

        final CatalogueFromMapping.FromMapping read = CatalogueFromMapping.read(edited, before);

        final EventType logon = read.catalogue().eventType("Authenticate-Logon");
        assertThat(logon.recognisedBy()).extracting(way -> way.when().getFirst().equalTo()).containsExactly("signin");
        assertThat(logon.title()).isEqualTo("User logon");
        assertThat(logon.status()).isEqualTo(EventType.CONFIRMED);
        assertThat(read.catalogue().eventType("Authenticate-Logoff")).isEqualTo(before.eventType(
                "Authenticate-Logoff"));
        assertThat(read.said()).singleElement().asString().startsWith("changed: event type Authenticate-Logon "
                                                                      + "(recognised by action = signin, was "
                                                                      + "action = login)");
        assertThat(read.notRead()).isEmpty();
        assertThat(read.catalogue().catchAll().confirmed()).isTrue();
        assertThat(read.catalogue().catchAll().translation()).isNull();
    }

    @Test
    void rulesOfOneTypeIdAreOneEventTypeRecognisedTwoWaysAndRulesAddedOrGoneAreSaid() {
        final Catalogue before = new Catalogue(List.of(
                type("Authenticate-Logon", "User logon", "Authenticate", "Logon", "login"),
                type("Process-Reboot", "Reboot", "Process", "Execute", "reboot")), null, null);
        final TranslationMapping edited = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "logon_sso", "when": [{"field": "action", "equals": "sso"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "view", "when": [{"field": "action", "equals": "view"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "View-Resource"},
                             {"path": "EventDetail/View/Action", "value": "View"},
                             {"path": "EventDetail/Description", "value": "A user viewed a resource"}]},
                 {"name": "by_field", "when": [{"field": "action", "equals": "other"}],
                  "fields": [{"path": "EventDetail/TypeId", "field": "action"},
                             {"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}],
                  "allow_unknown": "other"}]""");

        final CatalogueFromMapping.FromMapping read = CatalogueFromMapping.read(edited, before);

        assertThat(read.catalogue().eventTypes()).extracting(EventType::typeId).containsExactly(
                "Authenticate-Logon", "View-Resource");
        assertThat(read.catalogue().eventType("Authenticate-Logon").recognisedBy()).hasSize(2);
        final EventType view = read.catalogue().eventType("View-Resource");
        assertThat(view.eventDetail()).isEqualTo("View");
        assertThat(view.action()).isEqualTo("View");
        assertThat(view.description()).isEqualTo("A user viewed a resource");
        assertThat(view.actor()).isEqualTo("user");
        assertThat(view.basis()).isEqualTo(EventType.USER_CONFIRMED);
        assertThat(read.said()).anySatisfy(line -> assertThat(line).startsWith("changed: event type "
                                                                               + "Authenticate-Logon"))
                .anySatisfy(line -> assertThat(line).startsWith("added: event type View-Resource"))
                .anySatisfy(line -> assertThat(line).startsWith("removed: event type Process-Reboot"));
        // An edit the catalogue can't express: named.
        assertThat(read.notRead()).singleElement().asString().startsWith("rule by_field: its TypeId isn't fixed")
                .endsWith("not read");
    }

    @Test
    void aHandWrittenLastRuleWritingARealEventIsReadBackAsTheCatchAllWithItsOwnTranslation() {
        final TranslationMapping hand = mapping("""
                [{"name": "failed", "when": [{"field": "result", "equals": "fail"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-FailedLogon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                             {"path": "EventDetail/Authenticate/Outcome/Success", "value": "false"}]},
                 {"name": "rest",
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");

        final CatalogueFromMapping.FromMapping read = CatalogueFromMapping.read(hand, null);

        assertThat(read.catalogue().eventTypes()).extracting(EventType::typeId)
                .containsExactly("Authenticate-FailedLogon");
        assertThat(read.catalogue().eventTypes().getFirst().success()).isEqualTo("false");
        assertThat(read.catalogue().catchAll().translation()).isEqualTo(new Catalogue.CatchAll.Translation(
                "Authenticate-Logon", "Authenticate", "Logon", null));
        assertThat(read.catalogue().catchAll().toJson()).containsKey("translation");
        assertThat(read.said()).anySatisfy(line -> assertThat(line).contains("the catch-all", "rule rest",
                "real event", "not Unknown"));
        // Read again, the catalogue holds it: nothing new to say.
        assertThat(CatalogueFromMapping.read(hand, read.catalogue()).said()).isEmpty();
        // Through its JSON, as the source notes keep it.
        assertThat(Catalogue.parse(ToolJson.loads(ToolJson.dumps(read.catalogue().toJson()))).catalogue())
                .isEqualTo(read.catalogue());
    }

    @Test
    void lineFormatsCatchAllsAreTheCatchAllAndTheirConditionIsNotTheEventTypes() {
        final TranslationMapping variants = mapping("""
                [{"name": "gw_logon", "when": [{"field": "variant", "equals": "gw"},
                                               {"field": "event", "equals": "LOGIN"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "gw_uncatalogued", "when": [{"field": "variant", "equals": "gw"}], "catch_all": true,
                  "allow_unknown": "uncatalogued",
                  "fields": [{"path": "EventDetail/TypeId", "value": "Unknown"},
                             {"path": "EventDetail/Unknown/Data", "data_name": "event", "field": "event"}]},
                 {"name": "linux_sudo", "when": [{"field": "variant", "equals": "linux"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Process-Sudo"},
                             {"path": "EventDetail/Process/Action", "value": "Execute"}]},
                 {"name": "linux_uncatalogued", "when": [{"field": "variant", "equals": "linux"}],
                  "catch_all": true, "allow_unknown": "uncatalogued",
                  "fields": [{"path": "EventDetail/TypeId", "value": "Unknown"},
                             {"path": "EventDetail/Unknown/Data", "data_name": "event", "field": "event"}]}]""");

        final CatalogueFromMapping.FromMapping read = CatalogueFromMapping.read(variants, null);

        assertThat(read.catalogue().eventTypes()).extracting(EventType::typeId).containsExactly(
                "Authenticate-Logon", "Process-Sudo");
        assertThat(read.catalogue().eventType("Authenticate-Logon").recognisedBy().getFirst().when())
                .extracting(Condition::field).containsExactly("event");
        // A sole event type of its line format: recognised by its format's condition alone.
        assertThat(read.catalogue().eventType("Process-Sudo").recognisedBy().getFirst().when())
                .extracting(Condition::field).containsExactly("variant");
        assertThat(read.notRead()).isEmpty();
    }

    @Test
    void dropRulesOfDroppedEventTypesAreReadBackAndOthersNamed() {
        final Map<String, Object> json = json("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/TypeId", "value": "Authenticate-Logon"},
                             {"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");
        json.put("drop_when", List.of(
                Map.of("reason", "the user said to leave these untranslated: Alert-Usb",
                        "when", List.of(Map.of("field", "action", "equals", "usb"))),
                Map.of("reason", "noise", "when", List.of(Map.of("field", "action", "equals", "ping")))));

        final CatalogueFromMapping.FromMapping read = CatalogueFromMapping.read(TranslationMapping.fromJson(json),
                null);

        assertThat(read.catalogue().eventType("Alert-Usb").drop()).isTrue();
        assertThat(read.notRead()).singleElement().asString().startsWith("drop rule 'noise'");
    }

    private static EventType type(final String typeId,
                                  final String title,
                                  final String detail,
                                  final String action,
                                  final String value) {
        return new EventType(typeId, title, null, detail, action, null, "user", null, null, List.of(new Way(List.of(
                Condition.fromJson(Map.of("field", "action", "equals", value))), List.of())), false,
                EventType.USER_CONFIRMED, "high", null, EventType.CONFIRMED);
    }

    private static TranslationMapping mapping(final String events) {
        return TranslationMapping.fromJson(json(events));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(final String events) {
        return (Map<String, Object>) ToolJson.loads("{\"input\": \"data_splitter\", " + COMMON + ", \"events\": "
                                                    + events + "}");
    }
}
