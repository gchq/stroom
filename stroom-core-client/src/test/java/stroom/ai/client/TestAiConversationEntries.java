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

package stroom.ai.client;

import stroom.ai.shared.AiMessageType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiConversationEntries {

    private static final String DISCREPANCY = "One discrepancy to flag – before I carry on: build_status still "
                                              + "reports the splitter as missing.";

    @Test
    void whatTheModelWritesToTheUserWhileWorkingIsNotFoldedAway() {
        // Seen in testing: the text above was written alongside tool calls, put in a Working… group and hidden
        // when the group closed, as the turn's answer didn't repeat it. It is shown in the conversation once the
        // Working line has moved on from it, and when there is no Working line, i.e. the run has ended.
        assertThat(AiConversationEntries.isLeftToWorkingLine(AiMessageType.INTERIM_RESPONSE, DISCREPANCY,
                "Running build_status")).isFalse();
        assertThat(AiConversationEntries.isLeftToWorkingLine(AiMessageType.INTERIM_RESPONSE, DISCREPANCY,
                null)).isFalse();
    }

    @Test
    void whatTheWorkingLineShowsIsNotShownTwice() {
        assertThat(AiConversationEntries.isLeftToWorkingLine(AiMessageType.INTERIM_RESPONSE, DISCREPANCY,
                "One discrepancy to flag – before I carry on: build_status still reports… (build_status)"))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AiMessageType.class, mode = EnumSource.Mode.EXCLUDE, names = "INTERIM_RESPONSE")
    void onlyWhatTheModelWritesWhileWorkingIsLeftToTheWorkingLine(final AiMessageType type) {
        assertThat(AiConversationEntries.isLeftToWorkingLine(type, DISCREPANCY, DISCREPANCY)).isFalse();
    }

    @Test
    void theWorkingLineIsKeptBelowEverythingElse() {
        assertThat(AiConversationEntries.PROGRESS_IDS).containsExactly(
                AiConversationEntries.LIVE_THINKING_ID,
                AiConversationEntries.LIVE_ANSWER_ID,
                AiConversationEntries.WORKING_MESSAGE_ID);
        assertThat(AiConversationEntries.isProgress(AiConversationEntries.WORKING_MESSAGE_ID)).isTrue();
        assertThat(AiConversationEntries.isProgress(AiConversationEntries.LIVE_ANSWER_ID)).isTrue();
        assertThat(AiConversationEntries.isProgress(AiConversationEntries.LIVE_THINKING_ID)).isTrue();
    }

    @Test
    void messagesAreShownWhereTheyArrive() {
        // Only the lines showing what is happening now are moved to the end of the conversation.
        assertThat(AiConversationEntries.isProgress("2904")).isFalse();
        assertThat(AiConversationEntries.isProgress("")).isFalse();
        assertThat(AiConversationEntries.isProgress(null)).isFalse();
    }

    @Test
    void elementsAreMatchedToTheMessagesTheyShow() {
        // The steps a message left to the user are marked with the message's id and a suffix, which asking a
        // question again failed to read.
        assertThat(AiConversationEntries.messageIdOf("2904-actions")).isEqualTo(2904);
        assertThat(AiConversationEntries.messageIdOf("2904")).isEqualTo(2904);
        assertThat(AiConversationEntries.messageIdOf("pending")).isEqualTo(-1);
        assertThat(AiConversationEntries.messageIdOf("")).isEqualTo(-1);
        assertThat(AiConversationEntries.messageIdOf(null)).isEqualTo(-1);
    }
}
