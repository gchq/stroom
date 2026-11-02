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

package stroom.ai.impl;

import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class TestUntrustedChatContent {

    private static final int CHAT_ID = 7;
    // The tools that read only trusted content; any other tool reads untrusted content, as BuiltInTools says.
    private static final Set<String> TRUSTED = Set.of("get_current_time", "task_list", "memory");
    private static final Predicate<String> READS_UNTRUSTED = tool -> !TRUSTED.contains(tool);
    // The tab snapshots, by id, whose notes preview some of the tab's content.
    private static final int PREVIEWED_TAB = 41;
    private static final int UNPREVIEWED_TAB = 42;

    private final List<AiChatMessage> history = new ArrayList<>();

    @Test
    void aChatOfQuestionsAndAnswersHoldsNoUntrustedContent() {
        add(AiMessageType.USER_MESSAGE, "What time is it?");
        add(AiMessageType.AI_RESPONSE, "It is noon.");

        assertThat(isIn()).isFalse();
    }

    @Test
    void resultsOfToolsThatReadOnlyTrustedContentDoNotCount() {
        addCallAndResult("call-1", "get_current_time");
        addCallAndResult("call-2", "task_list");
        addCallAndResult("call-3", "memory");

        assertThat(isIn()).isFalse();
    }

    @Test
    void aResultOfAToolThatReadsUntrustedContentCounts() {
        addCallAndResult("call-1", "get_current_time");
        addCallAndResult("call-2", "fetch_events");

        assertThat(isIn()).isTrue();
    }

    @Test
    void aResultOfAnMcpServersToolCounts() {
        // An MCP server's tool isn't one of Stroom's own, so reads untrusted content.
        addCallAndResult("call-1", "stroom__find_feeds");

        assertThat(isIn()).isTrue();
    }

    @Test
    void aResultWithoutTheToolsNameIsToldByItsCall() {
        add(AiMessageType.TOOL_CALL, JsonUtil.writeValueAsString(
                new ToolCallRecord("call-1", "get_current_time", "{}", null), false));
        add(AiMessageType.TOOL_RESULT, "{\"callId\":\"call-1\",\"text\":\"12:00\"}");

        assertThat(isIn()).isFalse();
    }

    @Test
    void aResultWhoseToolCannotBeToldCounts() {
        // An old row: neither it nor a call says which tool gave it.
        add(AiMessageType.TOOL_RESULT, "{\"callId\":\"call-1\",\"text\":\"12:00\"}");

        assertThat(isIn()).isTrue();
    }

    @Test
    void aResultThatCannotBeReadCounts() {
        add(AiMessageType.TOOL_RESULT, "not json");

        assertThat(isIn()).isTrue();
    }

    @Test
    void anEmptyResultCounts() {
        add(AiMessageType.TOOL_RESULT, "{}");

        assertThat(isIn()).isTrue();
    }

    @Test
    void aResultOfAToolSinceRemovedCounts() {
        // BuiltInTools says any name it doesn't know reads untrusted content.
        addCallAndResult("call-1", "list_feeds_v1");

        assertThat(isIn()).isTrue();
    }

    @Test
    void aCallWithNoResultDoesNotCount() {
        // E.g. a call waiting for the user's approval, or one that asked the user a form and stopped the run to
        // wait for the answer: the model has been given nothing from it yet, and it counts once it is made again.
        add(AiMessageType.TOOL_CALL, JsonUtil.writeValueAsString(
                new ToolCallRecord("call-1", "get_field_conventions", "{}", null), false));

        assertThat(isIn()).isFalse();
    }

    @Test
    void anAttachmentCounts() {
        add(AiMessageType.ATTACHMENT, "Firewall events");

        assertThat(isIn()).isTrue();
    }

    @Test
    void stroomDataPutInTheChatCounts() {
        for (final AiMessageType type : List.of(AiMessageType.DASHBOARD_DATA, AiMessageType.QUERY_DATA,
                AiMessageType.TABLE_DATA)) {
            history.clear();
            add(type, "data");

            assertThat(isIn()).as(type.name()).isTrue();
        }
    }

    @Test
    void aQuestionWhoseTabNotePreviewsItsContentCounts() {
        // The note sent with the question shows the first lines of the tab's small parts, e.g. a stream's data.
        addQuestion(PREVIEWED_TAB);
        add(AiMessageType.AI_RESPONSE, "It looks like CSV.");

        assertThat(isIn()).isTrue();
    }

    @Test
    void aQuestionWhoseTabNoteShowsNoContentDoesNotCount() {
        // E.g. a tab whose parts are all too large to preview: the note gives only names, paths and sizes.
        addQuestion(UNPREVIEWED_TAB);
        add(AiMessageType.AI_RESPONSE, "It is a large stream.");

        assertThat(isIn()).isFalse();
    }

    @Test
    void anEarlierQuestionsPreviewedTabCounts() {
        addQuestion(PREVIEWED_TAB);
        add(AiMessageType.AI_RESPONSE, "It looks like CSV.");
        addQuestion(null);

        assertThat(isIn()).isTrue();
    }

    private boolean isIn() {
        return UntrustedChatContent.isIn(history, READS_UNTRUSTED, id -> id == PREVIEWED_TAB);
    }

    private void addQuestion(final Integer tabSnapshotId) {
        history.add(new AiChatMessage(history.size() + 1, CHAT_ID, 0L, AiMessageType.USER_MESSAGE, tabSnapshotId,
                "What is this?", 1, null));
    }

    private void addCallAndResult(final String callId, final String tool) {
        add(AiMessageType.TOOL_CALL, JsonUtil.writeValueAsString(new ToolCallRecord(callId, tool, "{}", null),
                false));
        add(AiMessageType.TOOL_RESULT, JsonUtil.writeValueAsString(new ToolResultRecord(callId, tool, false,
                "result", null, 6, 1, null), false));
    }

    private void add(final AiMessageType type, final String text) {
        history.add(new AiChatMessage(history.size() + 1, CHAT_ID, 0L, type, null, text, 1, null));
    }
}
