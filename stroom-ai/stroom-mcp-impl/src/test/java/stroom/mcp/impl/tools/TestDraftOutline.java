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
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/// A draft too large for a client to take whole is saved to the workspace, and its result gives the mapping's
/// outline: seen in eval cases 35, 36 and 56 on Haiku, drafts of 74,000 to 168,000 characters past Claude Code's
/// limit, so the agent never saw them and wrote mappings of its own.
class TestDraftOutline {

    // A draft past it only by its long Description, which the outline cuts.
    private static final int LIMIT = 4_000;
    private static final String LONG_TEXT = "A user logs on. ".repeat(300);
    private static final ToolCaller CLIENT = new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "claude-code");

    @Test
    void stroomAisOwnChatIsGivenTheDraftWhole() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        final Map<String, Object> result = drafted();
        final Map<String, Object> before = new LinkedHashMap<>(result);

        DraftOutline.outlineIfLarge(ToolCaller.STROOM_AI_CHAT, result, Optional.of(new SessionWorkspace(chats, 1)),
                "acme-v1", 100);

        assertThat(result).isEqualTo(before);
        Mockito.verifyNoInteractions(chats);
    }

    @Test
    void aDraftWithinTheLimitIsGivenWhole() {
        final Map<String, Object> result = drafted();
        final Map<String, Object> before = new LinkedHashMap<>(result);

        DraftOutline.outlineIfLarge(CLIENT, result, Optional.empty(), "acme-v1", 1_000_000);

        assertThat(result).isEqualTo(before);
    }

    @Test
    void aDraftPastTheLimitIsSavedWholeAndOutlined() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chats.write(anyInt(), any(WorkspacePath.class), anyString(), any())).thenReturn(7);
        final Map<String, Object> result = drafted();
        final Object mapping = result.get("mapping");

        DraftOutline.outlineIfLarge(CLIENT, result, Optional.of(new SessionWorkspace(chats, 1)), "acme v1",
                LIMIT);

        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<WorkspacePath> path = ArgumentCaptor.forClass(WorkspacePath.class);
        Mockito.verify(chats).write(anyInt(), path.capture(), written.capture(), any());
        assertThat(path.getValue().toString()).isEqualTo("/outputs/drafts/acme_v1.json");
        assertThat(JsonUtil.readValue(written.getValue(), Map.class)).isEqualTo(mapping);
        assertThat(result).doesNotContainKey("mapping")
                .containsEntry("mapping_saved", "/outputs/drafts/acme_v1.json")
                .containsEntry("notes", List.of("a note"));
        assertThat(result.get("mapping_outline")).isEqualTo(Map.of(
                "input", "json",
                "common", List.of("EventTime/TimeCreated choose from time, ts (yours to decide)",
                        "EventDetail/Description <- msg"),
                "events", List.of(
                        Map.of("name", "Authenticate-Logon", "type_id", "= 'Authenticate-Logon'",
                                "recognised_by", List.of("action = login"),
                                "description", "= '" + LONG_TEXT.substring(0, 80) + "...'",
                                "vendor_code_data", List.of("action <- action"),
                                "writes", List.of("EventDetail/Authenticate/Action = 'Logon'"),
                                "other_data", 2),
                        Map.of("name", "uncatalogued", "type_id", "= 'Unknown'",
                                "recognised_by", List.of("uncatalogued records (no other rule recognises them)"),
                                "description", "(common's) <- msg",
                                "writes", List.of(), "other_data", 0, "catch_all", true))));
        assertThat((String) result.get("hint")).startsWith("The draft is too large to show whole here")
                .contains("saved whole at /outputs/drafts/acme_v1.json", "changes=", "jq")
                .endsWith("Build it as drafted.");
    }

    @Test
    void anOutlinePastTheLimitListsTheRulesThatFitAndSaysHowManyMore() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chats.write(anyInt(), any(WorkspacePath.class), anyString(), any())).thenReturn(7);
        final Map<String, Object> result = new LinkedHashMap<>();
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rules.add(Map.of("name", "Process-Code" + i,
                    "when", List.of(Map.of("field", "code", "equals", "c" + i)),
                    "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Process-Code" + i),
                            Map.of("path", "EventDetail/Process/Action", "value", "Execute"),
                            Map.of("path", "EventDetail/Process/Data", "data_name", "code", "field", "code"))));
        }
        result.put("mapping", Map.of("input", "json", "events", rules));
        final int limit = 6_000;

        DraftOutline.outlineIfLarge(CLIENT, result, Optional.of(new SessionWorkspace(chats, 1)), "big", limit);

        assertThat(JsonUtil.writeValueAsString(result, false).length()).isLessThanOrEqualTo(limit);
        @SuppressWarnings("unchecked") final Map<String, Object> outline =
                (Map<String, Object>) result.get("mapping_outline");
        @SuppressWarnings("unchecked") final List<Map<String, Object>> listed =
                (List<Map<String, Object>>) outline.get("events");
        assertThat(listed).isNotEmpty().hasSizeLessThan(200)
                .allSatisfy(rule -> assertThat(rule).doesNotContainKey("writes")
                        .containsKeys("type_id", "recognised_by", "vendor_code_data"));
        assertThat((String) outline.get("rules_not_listed")).startsWith((200 - listed.size()) + " more rules")
                .endsWith("/outputs/drafts/big.json");
    }

    @Test
    void anOutlinedDraftsDataPlacementListsEachActionElementsElementsOnce() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chats.write(anyInt(), any(WorkspacePath.class), anyString(), any())).thenReturn(7);
        final Map<String, Object> result = drafted();
        final List<String> elements = List.of("Source/Device/HostName", "Destination/Device/HostName");
        result.put("data_placement", Map.of("event_source", List.of("Device/HostName"), "rules", List.of(
                Map.of("rule", "a", "data", List.of("x"), "action", "Export", "action_elements", elements),
                Map.of("rule", "b", "data", List.of("y"), "action", "Export", "action_elements", elements))));

        DraftOutline.outlineIfLarge(CLIENT, result, Optional.of(new SessionWorkspace(chats, 1)), "acme", LIMIT);

        assertThat(result.get("data_placement")).isEqualTo(Map.of(
                "event_source", List.of("Device/HostName"),
                "action_elements", Map.of("Export", elements),
                "rules", List.of(Map.of("rule", "a", "data", List.of("x"), "action", "Export"),
                        Map.of("rule", "b", "data", List.of("y"), "action", "Export"))));
    }

    @Test
    void aDraftStillTooLargeOnceOutlinedHasItsOtherPartsOutlinedAndSavedWhole() {
        // Seen in eval case 68 (300 event types): 58,000 characters outlined, the source notes, the notes and
        // data_placement each some 15,000, listing every event type.
        final ChatWorkspace chats = TestLargeResults.writable();
        final Map<String, Object> result = new LinkedHashMap<>();
        final List<Map<String, Object>> rules = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final List<Map<String, Object>> placed = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            rules.add(Map.of("name", "process_code" + i,
                    "when", List.of(Map.of("field", "code", "equals", "EV" + i)),
                    "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Process-Code" + i))));
            events.add("Process-Code" + i + " (code = EV" + i + ") -> Process");
            placed.add(Map.of("rule", "process_code" + i, "data", List.of("code"), "action", "Process"));
        }
        result.put("mapping", Map.of("input", "data_splitter", "events", rules));
        result.put("source_notes", Map.of("fields", List.of("code -> EventDetail/TypeId"), "catalogued", true,
                "events", events, "not_in_sample", events.subList(0, 180)));
        result.put("notes", List.of("Ask the user for EventSource/System/Name and Environment.",
                "Drafted from the catalogue of event types: " + String.join("; ", events)));
        result.put("data_placement", Map.of("event_source", List.of("Device/HostName"), "rules", placed));
        result.put("schema_check", Map.of("valid", true, "warnings", List.of("[" + String.join(", ",
                events.stream().map(e -> e.substring(0, e.indexOf(' '))).toList()) + "] required ['EventTime'] "
                + "are left out when their input fields are empty.")));
        result.put("schema_version", "4.1.0");
        result.put("hint", "Build it as drafted.");
        final Map<String, Object> before = new LinkedHashMap<>(result);

        DraftOutline.outlineIfLarge(CLIENT, result, Optional.of(new SessionWorkspace(chats, 1)), "eval-68",
                LargeResults.WHOLE_RESULT_CHARS);

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LargeResults.WHOLE_RESULT_CHARS);
        assertThat(result).containsEntry("mapping_saved", "/outputs/drafts/eval-68.json")
                .containsEntry("schema_version", "4.1.0")
                .containsKeys("mapping_outline", "whole_result");
        assertThat((String) result.get("hint")).startsWith("The draft is too large to show whole here");
        assertThat(JsonValues.strings(result.get("notes"))).first().isEqualTo("Ask the user for "
                                                                             + "EventSource/System/Name and "
                                                                             + "Environment.");
        assertThat((String) ((Map<?, ?>) result.get("source_notes")).get("events")).startsWith("300: ");
        assertThat(JsonValues.maps(((Map<?, ?>) result.get("data_placement")).get("rules"))).containsExactly(
                Map.of("data", List.of("code"), "action", "Process", "rules", 300, "e_g", "process_code0"));
        final Map<?, ?> saved = JsonUtil.readValue(TestLargeResults.written(chats,
                "/outputs/draft_translation_mapping/eval-68.json"), Map.class);
        assertThat(saved.get("source_notes")).isEqualTo(before.get("source_notes"));
        assertThat(saved.get("notes")).isEqualTo(before.get("notes"));
        assertThat(JsonValues.map(JsonUtil.readValue(TestLargeResults.written(chats, "/outputs/drafts/eval-68.json"),
                Map.class)).get("events")).isEqualTo(rules);
    }

    @Test
    void withoutAWorkspaceTheDraftIsGivenWhole() {
        final Map<String, Object> result = drafted();

        DraftOutline.outlineIfLarge(CLIENT, result, Optional.empty(), "acme-v1", 100);

        assertThat(result).containsKey("mapping").doesNotContainKey("mapping_outline");
    }

    private static Map<String, Object> drafted() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("input", "json");
        mapping.put("common", List.of(Map.of("path", "EventTime/TimeCreated", "choose_from", List.of("time", "ts")),
                Map.of("path", "EventDetail/Description", "field", "msg")));
        mapping.put("events", List.of(
                Map.of("name", "Authenticate-Logon", "when", List.of(Map.of("field", "action", "equals", "login")),
                        "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Authenticate-Logon"),
                                Map.of("path", "EventDetail/Description", "value", LONG_TEXT),
                                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                                Map.of("path", "EventDetail/Authenticate/Data", "data_name", "action",
                                        "field", "action"),
                                Map.of("path", "EventDetail/Authenticate/Data", "data_name", "a", "field", "a")),
                        "data", List.of("b")),
                Map.of("name", "uncatalogued", "catch_all", true,
                        "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Unknown")))));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("mapping", mapping);
        result.put("notes", List.of("a note"));
        result.put("hint", "Build it as drafted.");
        return result;
    }
}
