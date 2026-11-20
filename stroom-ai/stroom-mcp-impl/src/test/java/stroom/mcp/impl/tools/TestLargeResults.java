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

/// A result too large for a client outside Stroom is saved whole to the workspace and given in outline: seen in the
/// Haiku evals of 2026-11-20, results of 50,000 to 110,000 characters that Claude Code saved to files of its own,
/// which the agent couldn't read through Stroom's tools.
class TestLargeResults {

    static final ToolCaller CLIENT = new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "claude-code");
    private static final int LIMIT = 8_000;

    @Test
    void stroomAisOwnChatIsGivenTheResultWhole() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        final Map<String, Object> result = large();
        final Map<String, Object> before = new LinkedHashMap<>(result);

        final boolean outlined = plan().apply(ToolCaller.STROOM_AI_CHAT, Optional.of(new SessionWorkspace(chats, 1)),
                result, LIMIT);

        assertThat(outlined).isFalse();
        assertThat(result).isEqualTo(before);
        Mockito.verifyNoInteractions(chats);
    }

    @Test
    void aResultWithinTheLimitIsGivenWhole() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        final Map<String, Object> result = large();
        final Map<String, Object> before = new LinkedHashMap<>(result);

        assertThat(plan().apply(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), result, 1_000_000)).isFalse();

        assertThat(result).isEqualTo(before);
        Mockito.verifyNoInteractions(chats);
    }

    @Test
    void withoutAWorkspaceOrWhenTheFileCantBeWrittenTheResultIsGivenWhole() {
        final Map<String, Object> result = large();
        final Map<String, Object> before = new LinkedHashMap<>(result);
        assertThat(plan().apply(CLIENT, Optional.empty(), result, LIMIT)).isFalse();
        assertThat(result).isEqualTo(before);

        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chats.write(anyInt(), any(WorkspacePath.class), anyString(), any()))
                .thenThrow(new IllegalStateException("full"));
        assertThat(plan().apply(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), result, LIMIT)).isFalse();
        assertThat(result).isEqualTo(before);
    }

    @Test
    void aResultPastTheLimitIsSavedWholeAnEntryALineAndOutlinedOnlyAsFarAsItNeeds() {
        final ChatWorkspace chats = writable();
        final Map<String, Object> result = large();
        final Map<String, Object> before = new LinkedHashMap<>(result);
        final int limit = LargeResults.length(result) - 1_000;

        final boolean outlined = plan().apply(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), result, limit);

        assertThat(outlined).isTrue();
        final String written = written(chats, "/outputs/a_tool/acme_v1.json");
        assertThat(written.lines()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(2_000));
        assertThat(written.lines().filter(line -> line.contains("\"rule-"))).hasSize(400);
        final Map<?, ?> saved = JsonUtil.readValue(written, Map.class);
        assertThat(saved.get("rules")).isEqualTo(before.get("rules"));
        assertThat(saved.get("table")).as("a long text is saved a line an entry")
                .isEqualTo(((String) before.get("table")).lines().toList());
        assertThat(result.keySet()).first().isEqualTo(LargeResults.WHOLE_RESULT);
        assertThat(result).containsEntry("rules", Map.of("rules", 400))
                .containsEntry("hint", before.get("hint"))
                .containsEntry("table", before.get("table"))
                .as("outlined no further than it needed").containsEntry("notes", before.get("notes"));
        final Map<?, ?> note = (Map<?, ?>) result.get(LargeResults.WHOLE_RESULT);
        assertThat(note.get("saved")).isEqualTo("/outputs/a_tool/acme_v1.json");
        assertThat(note.get("outlined")).isEqualTo(List.of("rules"));
        assertThat((String) note.get("read")).contains("jq (path=/outputs/a_tool/acme_v1.json, query='.rules[]'");
    }

    @Test
    void aResultStillPastTheLimitHasTheRestCutSayingHowManyMoreAndKeepsWhatTheAgentActsOn() {
        final ChatWorkspace chats = writable();
        final Map<String, Object> result = large();
        final Map<String, Object> before = new LinkedHashMap<>(result);
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("problems", lines("a problem", 60));
        check.put("counts", lines("a count", 60));
        result.put("check", check);

        LargeResults.of("a_tool", "acme v1")
                .keep("table", "check.problems")
                .outline("rules", (value, room) -> Map.of("rules", ((List<?>) value).size()))
                .apply(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), result, LIMIT);

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LIMIT);
        assertThat(result).containsEntry("hint", before.get("hint")).containsEntry("table", before.get("table"));
        final List<?> notes = (List<?>) result.get("notes");
        assertThat(notes).hasSizeLessThan(200);
        assertThat((String) notes.getLast()).matches("\\(\\d+ more in the saved file: whole_result says where\\)");
        assertThat(((Map<?, ?>) result.get("check")).get("problems")).isEqualTo(check.get("problems"));
        assertThat((List<?>) ((Map<?, ?>) result.get("check")).get("counts")).hasSizeLessThan(60);
        assertThat(((Map<?, ?>) result.get(LargeResults.WHOLE_RESULT)).get("cut")).isEqualTo(List.of("notes",
                "check"));
    }

    @Test
    void aResultWithANullPartIsSavedAndOutlined() {
        // Seen in eval cases 36 and 68: build_translation_xslt's field_mapping is null, and the save threw, so the
        // result was given whole.
        final ChatWorkspace chats = writable();
        final Map<String, Object> result = large();
        result.put("field_mapping", null);
        final int limit = LargeResults.length(result) - 1_000;

        assertThat(plan().apply(CLIENT, Optional.of(new SessionWorkspace(chats, 1)), result, limit)).isTrue();

        assertThat(JsonUtil.readValue(written(chats, "/outputs/a_tool/acme_v1.json"), Map.class))
                .containsEntry("field_mapping", null);
        assertThat(result).containsEntry("field_mapping", null);
    }

    @Test
    void savedPathsArePlainFileNames() {
        assertThat(LargeResults.savedPath("survey_feed", "acme v1/x")).isEqualTo("/outputs/survey_feed/acme_v1_x.json");
        assertThat(LargeResults.savedPath("survey_feed", null)).isEqualTo("/outputs/survey_feed/result.json");
    }

    private static LargeResults plan() {
        return LargeResults.of("a_tool", "acme v1")
                .keep("table", "check.problems")
                .outline("rules", (value, room) -> Map.of("rules", ((List<?>) value).size()))
                .outline("notes", (value, room) -> List.of(((List<?>) value).size() + " notes"));
    }

    private static Map<String, Object> large() {
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            rules.add(Map.of("name", "rule-" + i, "when", "code = " + i));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "checked");
        result.put("rules", rules);
        result.put("notes", lines("a note on the draft", 200));
        result.put("table", "| Event type |\n| --- |\n" + "| Create-Account |\n".repeat(150));
        result.put("hint", "Call again.");
        return result;
    }

    private static List<String> lines(final String text, final int count) {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(text + " " + i);
        }
        return lines;
    }

    static ChatWorkspace writable() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        Mockito.when(chats.write(anyInt(), any(WorkspacePath.class), anyString(), any())).thenReturn(7);
        return chats;
    }

    static String written(final ChatWorkspace chats, final String path) {
        final ArgumentCaptor<String> written = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<WorkspacePath> paths = ArgumentCaptor.forClass(WorkspacePath.class);
        Mockito.verify(chats, Mockito.atLeastOnce()).write(anyInt(), paths.capture(), written.capture(), any());
        for (int i = 0; i < paths.getAllValues().size(); i++) {
            if (paths.getAllValues().get(i).toString().equals(path)) {
                return written.getAllValues().get(i);
            }
        }
        throw new AssertionError("Nothing written to " + path + ": " + paths.getAllValues());
    }
}
