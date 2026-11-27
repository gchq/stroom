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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.LargeResults;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// A build_translation_xslt result too large for a client outside Stroom: each rule's Data counted, not listed.
/// Seen in eval cases 35 and 36 over Claude Code: results of about 50 KB saved to files, most of them the rules'
/// Data entries.
class TestBuiltRulesTrim {

    private static final ToolCaller CLIENT = new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "claude-code");

    @Test
    void aLargeResultForAClientOutsideStroomCountsEachRulesData() {
        final Map<String, Object> result = built(200);

        BuiltRulesTrim.trimIfLarge(CLIENT, result, 5_000);

        assertThat((List<?>) result.get("events")).hasSize(200).first().isEqualTo(Map.of(
                "event", "rule0",
                "when", List.of("code = '0'"),
                "fields", List.of("EventDetail/TypeId", "EventSource/User/Id"),
                "data", 3,
                "data_names", List.of("code", "host", "event.app")));
        assertThat((String) result.get("events_note")).contains("Data");
    }

    @Test
    void aLargeResultForAClientOutsideStroomSaysTheSampleChecksWarningsOnce() {
        // Seen in eval cases 35 and 36 (2026-11-20): results of 69,000 and 100,000 characters still, a third of each
        // the sample check's warnings, which the result's warnings repeat (each starting "sample: ").
        final Map<String, Object> result = built(1);
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("records", 33);
        check.put("problems", List.of());
        check.put("warnings", List.of("x".repeat(6_000)));
        check.put("fields_seen", List.of("code"));
        result.put("sample_check", check);
        result.put("warnings", List.of("sample: " + "x".repeat(6_000)));

        BuiltRulesTrim.trimIfLarge(CLIENT, result, 10_000);

        final Set<Object> kept = new HashSet<>(((Map<?, ?>) result.get("sample_check")).keySet());
        assertThat(kept).containsExactlyInAnyOrder("records", "problems", "fields_seen", "warnings_note");
        assertThat((List<?>) result.get("warnings")).hasSize(1);
    }

    @Test
    void aResultWithinTheLimitOrForStroomAisOwnChatIsLeftWhole() {
        final Map<String, Object> small = built(1);
        final Map<String, Object> before = new LinkedHashMap<>(small);
        BuiltRulesTrim.trimIfLarge(CLIENT, small, 1_000_000);
        assertThat(small).isEqualTo(before);

        final Map<String, Object> chat = built(200);
        final Map<String, Object> chatBefore = new LinkedHashMap<>(chat);
        BuiltRulesTrim.trimIfLarge(ToolCaller.STROOM_AI_CHAT, chat, 5_000);
        assertThat(chat).isEqualTo(chatBefore);
    }

    @Test
    void aResultStillTooLargeOnceTrimmedIsOutlinedARuleALineAndSavedWhole() {
        // Seen in eval case 68 (300 rules): 110,000 characters, the rules' summaries 98,000 even with their Data
        // counted; in cases 35 and 36, the sample check's warnings, some 430 characters each, most an example.
        final ChatWorkspace chats = TestLargeResults.writable();
        final Map<String, Object> result = built(300);
        result.put("problems", List.of());
        result.put("warnings", warnings());
        result.put("changes_applied", List.of("rule 'rule0' entry 'EventSource/User/Id' added"));
        result.put("saved", Map.of("type", "XSLT", "uuid", "x-1", "name", "Acme Translation"));
        result.put("hint", "Saved with its mapping.");
        result.put("next", Map.of("step", "pipeline"));
        BuiltRulesTrim.trimIfLarge(CLIENT, result, LargeResults.WHOLE_RESULT_CHARS);
        final Map<String, Object> trimmed = new LinkedHashMap<>(result);
        assertThat(LargeResults.length(result)).isGreaterThan(LargeResults.WHOLE_RESULT_CHARS);

        assertThat(BuiltRulesTrim.outlineIfLarge(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), "acme-v1",
                result, LargeResults.WHOLE_RESULT_CHARS)).isTrue();

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LargeResults.WHOLE_RESULT_CHARS);
        for (final String kept : List.of("ok", "problems", "saved", "hint", "next")) {
            assertThat(result.get(kept)).as(kept).isEqualTo(trimmed.get(kept));
        }
        assertThat(JsonValues.strings(result.get("events"))).hasSize(300)
                .startsWith("rule0: code = '0' (2 fields, 3 Data: code, host, event.app)");
        assertThat(JsonValues.strings(result.get("warnings"))).hasSize(81).startsWith(
                "[rule0, rule1, rule2, rule3, rule4 and 295 more rules] required ['EventTime'] are left out when "
                + "their input fields are empty.",
                "sample: extract[4] (EventData/Data): the regex matches 95 of the 200 sample texts; the rest leave "
                + "['authentication'] empty. (its example in the saved result)");
        final Map<?, ?> saved = JsonUtil.readValue(TestLargeResults.written(chats,
                "/outputs/build_translation_xslt/acme-v1.json"), Map.class);
        assertThat(saved.get("events")).isEqualTo(JsonUtil.readValue(JsonUtil.writeValueAsString(
                trimmed.get("events"), false), List.class));
        assertThat(saved.get("warnings")).isEqualTo(trimmed.get("warnings"));
    }

    @Test
    void eachRulesDataNamesAreSaidCappedSoTheAgentNeedNotDigForThem() {
        // Seen in chats 184-186 (Haiku): the outline said "send_mail: ... (16 fields, 23 Data)", and the agent read
        // the saved result 13 times to learn which fields each rule kept as Data, to type more of them.
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("events", List.of(rule("send_mail", 23), rule("catch_all", 400)));

        BuiltRulesTrim.trimIfLarge(CLIENT, result, 5_000);

        final List<Map<String, Object>> rules = JsonValues.maps(result.get("events"));
        assertThat(rules.getFirst()).containsEntry("data", 23);
        assertThat(JsonValues.strings(rules.getFirst().get("data_names"))).hasSize(13)
                .startsWith("d0", "d1", "d2")
                .endsWith("d11", "… 11 more");
        assertThat(JsonValues.strings(rules.get(1).get("data_names"))).hasSize(13).endsWith("… 388 more");

        final Map<String, Object> raw = Map.of("event", "send_mail", "when", List.of("op = 'Send'"),
                "fields", rule("send_mail", 2).get("fields"));
        assertThat(firstOutlined(raw)).containsExactly("send_mail: op = 'Send' (1 fields, 2 Data: d0, d1)");
        assertThat(firstOutlined(rules.getFirst())).containsExactly(
                "send_mail: op = 'Send' (1 fields, 23 Data: d0, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, … 11 "
                + "more)");
        assertThat(firstOutlined(rules.get(1))).containsExactly(
                "catch_all: op = 'Send' (1 fields, 400 Data: d0, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, … 388 "
                + "more)");
    }

    @Test
    void theDataNamesGiveWayToTheirCountsWhereTheOutlineHasNoRoomForThem() {
        // 600 rules of 40 Data each: twelve names a rule would take the outline past the limit.
        final ChatWorkspace chats = TestLargeResults.writable();
        final List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            events.add(rule("rule" + i, 40));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("events", events);
        BuiltRulesTrim.trimIfLarge(CLIENT, result, LargeResults.WHOLE_RESULT_CHARS);

        assertThat(BuiltRulesTrim.outlineIfLarge(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), "acme-v1",
                result, LargeResults.WHOLE_RESULT_CHARS)).isTrue();

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LargeResults.WHOLE_RESULT_CHARS);
        final List<String> lines = JsonValues.strings(result.get("events"));
        assertThat(lines).hasSize(600);
        assertThat(lines.getFirst()).startsWith("rule0: op = 'Send' (1 fields, 40 Data").doesNotContain("d11");
    }

    // The first rule's outline line, of a result of the rule repeated until it is past the limit.
    private static List<String> firstOutlined(final Map<String, Object> rule) {
        final ChatWorkspace chats = TestLargeResults.writable();
        final List<Map<String, Object>> events = new ArrayList<>();
        while (LargeResults.length(events) <= LargeResults.WHOLE_RESULT_CHARS) {
            events.add(rule);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("events", events);
        BuiltRulesTrim.outlineIfLarge(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), "acme-v1", result,
                LargeResults.WHOLE_RESULT_CHARS);
        return JsonValues.strings(result.get("events")).subList(0, 1);
    }

    private static Map<String, Object> rule(final String name, final int data) {
        final List<String> fields = new ArrayList<>();
        fields.add("EventDetail/TypeId");
        for (int i = 0; i < data; i++) {
            fields.add("EventDetail/Send/Data[d" + i + "]");
        }
        return Map.of("event", name, "when", List.of("op = 'Send'"), "fields", fields);
    }

    @Test
    void aTrimmedResultForStroomAisChatOrWithoutAWorkspaceIsNotOutlined() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        final Map<String, Object> chat = built(600);
        final Map<String, Object> chatBefore = new LinkedHashMap<>(chat);
        assertThat(BuiltRulesTrim.outlineIfLarge(ToolCaller.STROOM_AI_CHAT, Optional.of(new SessionWorkspace(chats,
                1)), "acme-v1", chat, LargeResults.WHOLE_RESULT_CHARS)).isFalse();
        assertThat(chat).isEqualTo(chatBefore);
        Mockito.verifyNoInteractions(chats);

        final Map<String, Object> noWorkspace = built(600);
        final Map<String, Object> noWorkspaceBefore = new LinkedHashMap<>(noWorkspace);
        assertThat(BuiltRulesTrim.outlineIfLarge(CLIENT, Optional.empty(), "acme-v1", noWorkspace,
                LargeResults.WHOLE_RESULT_CHARS)).isFalse();
        assertThat(noWorkspace).isEqualTo(noWorkspaceBefore);
    }

    private static List<String> warnings() {
        final List<String> rules = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            rules.add("rule" + i);
        }
        final List<String> warnings = new ArrayList<>();
        warnings.add("[" + String.join(", ", rules) + "] required ['EventTime'] are left out when their input "
                     + "fields are empty.");
        for (int i = 4; i < 84; i++) {
            warnings.add("sample: extract[" + i + "] (EventData/Data): the regex matches 95 of the 200 sample texts; "
                         + "the rest leave ['authentication'] empty. E.g. `Sep 27 2026 23:51:24 - User: "
                         + "domain.com\\Patel, Priya - [[SecretServer]] Event: [User] Action: [Logout] By User: "
                         + "domain.com\\priya.patel (Item Id: 221) | Session …`: It matches as far as `` and stops "
                         + "there: the regex goes on with `Authentication: (.*?)(?:\\s*[;|…`, the text with `Sep "
                         + "27 2026 23:51:24 - User: d…`.");
        }
        return warnings;
    }

    private static Map<String, Object> built(final int rules) {
        final List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < rules; i++) {
            events.add(Map.of("event", "rule" + i, "when", List.of("code = '" + i + "'"),
                    "fields", List.of("EventDetail/TypeId", "EventDetail/Process/Data[code]",
                            "EventDetail/Process/Data[host]", "EventDetail/Process/Data[event.app]",
                            "EventSource/User/Id")));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("events", events);
        return result;
    }
}
