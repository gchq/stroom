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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// A write_documentation result too large for a client outside Stroom: seen in eval cases 35, 36 and 56 on Haiku,
/// results of 73,000 to 88,000 characters, almost all the generated Field mapping section.
class TestDocumentationOutline {

    private static final int LIMIT = LargeResults.WHOLE_RESULT_CHARS;
    private static final String SAVED = "/outputs/write_documentation/eval-36.json";

    @Test
    void aLargeFieldMappingSectionIsOutlinedByItsHeadingsAndSavedWhole() {
        final ChatWorkspace chats = TestLargeResults.writable();
        final Map<String, Object> result = written(300);
        final Map<String, Object> before = new LinkedHashMap<>(result);
        assertThat(LargeResults.length(result)).isGreaterThan(LIMIT);

        assertThat(DocumentationOutline.outlineIfLarge(TestLargeResults.CLIENT,
                Optional.of(new SessionWorkspace(chats, 1)), "eval-36", result, LIMIT)).isTrue();

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LIMIT);
        for (final String kept : List.of("type", "uuid", "name", "written", "link", "user_actions", "next")) {
            assertThat(result.get(kept)).as(kept).isEqualTo(before.get(kept));
        }
        final Map<?, ?> section = (Map<?, ?>) result.get("field_mapping");
        assertThat(JsonValues.strings(section.get("headings"))).hasSize(302).startsWith("### Extracted fields",
                "### By event type");
        assertThat(section.get("characters")).isEqualTo(((String) before.get("field_mapping")).length());
        final Map<?, ?> note = (Map<?, ?>) result.get(LargeResults.WHOLE_RESULT);
        assertThat(note.get("text_lines")).isEqualTo(List.of("field_mapping"));
        assertThat((String) note.get("read")).contains("query='.field_mapping[]'");
        final Map<?, ?> saved = JsonUtil.readValue(TestLargeResults.written(chats, SAVED), Map.class);
        assertThat(String.join("\n", ((List<?>) saved.get("field_mapping")).stream().map(String::valueOf).toList())
                .stripTrailing())
                .isEqualTo(((String) before.get("field_mapping")).stripTrailing());
    }

    @Test
    void fieldsToDescribeAreKeptEachWithWhereItsValueComesFromCutShort() {
        // Seen in eval case 68 (300 event types): needs_descriptions of 46,000 characters, each TypeId and
        // Description listed in its field's `from`.
        final ChatWorkspace chats = TestLargeResults.writable();
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final String path : List.of("EventDetail/TypeId", "EventDetail/Description", "EventSource/User/Id")) {
            fields.add(Map.of("field", path, "from", "EventSource/User/Id".equals(path)
                            ? "`user`"
                            : "\"Account created\" (account created)\n".repeat(600),
                    "values", List.of("a", "b"), "schemas_say", Map.of("event-logging", "The ...")));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "needs_descriptions");
        result.put("written", false);
        result.put("fields", fields);
        result.put("hint", "NOT WRITTEN. Describe each field.");

        assertThat(DocumentationOutline.outlineIfLarge(TestLargeResults.CLIENT,
                Optional.of(new SessionWorkspace(chats, 1)), "eval-68", result, LIMIT)).isTrue();

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LIMIT);
        final List<Map<String, Object>> said = JsonValues.maps(result.get("fields"));
        assertThat(said).extracting(field -> field.get("field")).containsExactly("EventDetail/TypeId",
                "EventDetail/Description", "EventSource/User/Id");
        assertThat((String) said.getFirst().get("from")).endsWith("(600 lines in all: the rest in the saved result)");
        assertThat(said.getLast()).isEqualTo(fields.getLast());
        assertThat(said.getFirst().get("values")).isEqualTo(List.of("a", "b"));
    }

    @Test
    void aSmallResultOrOneForStroomAisChatOrWithoutAWorkspaceIsGivenWhole() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        final Map<String, Object> small = written(3);
        final Map<String, Object> smallBefore = new LinkedHashMap<>(small);
        assertThat(DocumentationOutline.outlineIfLarge(TestLargeResults.CLIENT,
                Optional.of(new SessionWorkspace(chats, 1)), "eval-36", small, LIMIT)).isFalse();
        assertThat(small).isEqualTo(smallBefore);

        final Map<String, Object> chat = written(300);
        final Map<String, Object> chatBefore = new LinkedHashMap<>(chat);
        assertThat(DocumentationOutline.outlineIfLarge(ToolCaller.STROOM_AI_CHAT,
                Optional.of(new SessionWorkspace(chats, 1)), "eval-36", chat, LIMIT)).isFalse();
        assertThat(chat).isEqualTo(chatBefore);
        Mockito.verifyNoInteractions(chats);

        final Map<String, Object> noWorkspace = written(300);
        final Map<String, Object> noWorkspaceBefore = new LinkedHashMap<>(noWorkspace);
        assertThat(DocumentationOutline.outlineIfLarge(TestLargeResults.CLIENT, Optional.empty(), "eval-36",
                noWorkspace, LIMIT)).isFalse();
        assertThat(noWorkspace).isEqualTo(noWorkspaceBefore);
    }

    // A pipeline's documentation written, its Field mapping section given back.
    private static Map<String, Object> written(final int eventTypes) {
        final StringBuilder section = new StringBuilder("Counts are of 600 sample records.\n\n### Extracted fields\n\n"
                                                        + "- `user`: parsed from `EventData/Data`\n\n"
                                                        + "### By event type\n\n");
        for (int k = 0; k < eventTypes; k++) {
            section.append("#### Process-Code").append(k)
                    .append("\n\n| Path | Source | Values |\n| --- | --- | --- |\n")
                    .append("| EventDetail/TypeId | fixed | Process-Code").append(k).append(" |\n")
                    .append("| EventSource/User/Id | user | user1, user2 |\n\n");
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "Documentation");
        result.put("uuid", "d-1");
        result.put("name", "EVAL-36 Events V1.0");
        result.put("written", "created");
        result.put("link", "http://localhost:8080/?action=open-doc&docType=Documentation&docUuid=d-1");
        result.put("user_actions", List.of(Map.of("id", "open-d-1", "action", "open_document")));
        result.put("field_mapping", section.toString());
        result.put("next", Map.of("step", "index", "call", Map.of("tool", "draft_index_mapping")));
        return result;
    }
}
