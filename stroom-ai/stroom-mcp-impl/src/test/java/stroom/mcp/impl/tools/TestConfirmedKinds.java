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

import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The kinds the user confirmed that a mapping translates otherwise.
class TestConfirmedKinds {

    private static final List<Object> RECORDS = List.of(
            Map.of("action", "login", "user", "alice"),
            Map.of("action", "logout", "user", "alice"),
            Map.of("action", "logout", "user", "bob"),
            Map.of("action", "usb", "user", ""));

    @Test
    void aKindTranslatedAsConfirmedIsNotLost() {
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");

        assertThat(ConfirmedKinds.lost(mapping, RECORDS, List.of(confirmed("logon", "login", "Authenticate",
                "Logon")))).isEmpty();
    }

    @Test
    void aKindWhoseRecordsGoToTheUnknownCatchAllIsLost() {
        // From the chat 163 review: the logoffs went into the rule for the rest, written as Unknown.
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "rest", "allow_unknown": "kinds not seen before",
                  "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]}]""");

        final List<ConfirmedKinds.Lost> lost = ConfirmedKinds.lost(mapping, RECORDS,
                List.of(confirmed("logoff", "logout", "Authenticate", "Logoff")));

        assertThat(lost).containsExactly(new ConfirmedKinds.Lost("logoff", "action", "logout",
                "Authenticate, Action Logoff", "rest", "Unknown", 2));
        assertThat(ConfirmedKinds.takenBy(lost, "rest")).containsExactly("logoff (2 records), confirmed as "
                                                                         + "Authenticate, Action Logoff");
        assertThat(ConfirmedKinds.keys(lost)).containsExactly(Map.entry("action=logout in rest", "Unknown"));
    }

    @Test
    void aKindWrittenWithAnotherActionIsLost() {
        // From the chat 163 review: a kind confirmed as Action Other became Logon.
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");

        assertThat(ConfirmedKinds.lost(mapping, RECORDS, List.of(confirmed("new session", "login", "Authenticate",
                "Other")))).singleElement().satisfies(one -> {
                    assertThat(one.writes()).isEqualTo("Authenticate, Action Logon");
                    assertThat(one.wanted()).isEqualTo("Authenticate, Action Other");
                });
    }

    @Test
    void aKindTakenByNoRuleOrDroppedIsLost() {
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""",
                """
                        [{"reason": "noise", "when": [{"field": "action", "equals": "usb"}]}]""");

        final List<ConfirmedKinds.Lost> lost = ConfirmedKinds.lost(mapping, RECORDS, List.of(
                confirmed("logoff", "logout", "Authenticate", "Logoff"),
                confirmed("usb device", "usb", "Alert", "")));

        assertThat(lost).extracting(ConfirmedKinds.Lost::writes).containsExactly("taken by no rule",
                "dropped (noise)");
        assertThat(ConfirmedKinds.markdown(lost, 4)).contains("| logoff (action=logout) | Authenticate, Action "
                                                              + "Logoff | 2 | taken by no rule | none |");
    }

    @Test
    void aPatternKindWhoseRecordsGoElsewhereIsLost() {
        // A kind told by a pattern its message matches (asked for by the user after chat 166).
        final TranslationMapping mapping = mapping("""
                [{"name": "rest", "allow_unknown": "other lines",
                  "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "msg", "field": "msg"}]}]""");
        final Map<String, Object> failed = new LinkedHashMap<>();
        failed.put("event", "Failed password");
        failed.put("field", "msg");
        failed.put("matches", "^Failed password for");
        failed.put("event_detail", "Authenticate");
        failed.put("action", "Logon");
        failed.put("basis", RecordSourceNotesTool.USER_CONFIRMED);

        final List<ConfirmedKinds.Lost> lost = ConfirmedKinds.lost(mapping, List.of(
                Map.of("msg", "Failed password for alice from 10.0.0.1"),
                Map.of("msg", "Failed password for bob from 10.0.0.2"),
                Map.of("msg", "Server listening on port 22")), List.of(failed));

        assertThat(lost).singleElement().satisfies(one -> {
            assertThat(one.records()).isEqualTo(2);
            assertThat(one.writes()).isEqualTo("Unknown");
            assertThat(one.value()).isEqualTo("~^Failed password for");
        });
    }

    @Test
    void aDroppedKindARuleStillTranslatesIsWarnedOf() {
        final TranslationMapping translated = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                 {"name": "rest", "allow_unknown": "other",
                  "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "action", "field": "action"}]}]""");
        final TranslationMapping dropped = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""", """
                [{"reason": "the user said to leave these untranslated: action=usb",
                  "when": [{"field": "action", "equals": "usb"}]}]""");

        assertThat(ConfirmedKinds.undropped(translated, RECORDS, List.of("action=usb"))).singleElement().asString()
                .startsWith("dropped kind action=usb: 1 sample records are translated by rule 'rest'");
        assertThat(ConfirmedKinds.undropped(dropped, RECORDS, List.of("action=usb"))).isEmpty();
    }

    @Test
    void onlyWhatTheUserConfirmedIsCheckedAndKindsTheSampleLacksAreNot() {
        final TranslationMapping mapping = mapping("""
                [{"name": "logon", "when": [{"field": "action", "equals": "login"}],
                  "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]}]""");
        final Map<String, Object> documented = new LinkedHashMap<>(confirmed("logoff", "logout", "Authenticate",
                "Logoff"));
        documented.remove("basis");

        assertThat(ConfirmedKinds.lost(mapping, RECORDS, List.of(documented,
                confirmed("reboot", "reboot", "Process", "Execute")))).isEmpty();
        assertThat(ConfirmedKinds.lost(mapping, List.of(), List.of(confirmed("logoff", "logout", "Authenticate",
                "Logoff")))).isEmpty();
    }

    private static Map<String, Object> confirmed(final String event,
                                                 final String value,
                                                 final String detail,
                                                 final String action) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("event", event);
        out.put("field", "action");
        out.put("value", value);
        out.put("event_detail", detail);
        out.put("action", action);
        out.put("basis", RecordSourceNotesTool.USER_CONFIRMED);
        return out;
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
