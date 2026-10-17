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

class TestAiActivityGroups {

    @Test
    void whatTheModelSaysWhileWorkingIsInTheActivityGroup() {
        // So it doesn't push the group, and the tool calls added to it, up out of view.
        assertThat(AiActivityGroups.isActivity(AiMessageType.INTERIM_RESPONSE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AiMessageType.class, names = {
            "THINKING", "INTERIM_RESPONSE", "TOOL_CALL", "TOOL_RESULT", "MEMORY_UPDATE", "DEBUG_DETAIL"})
    void activityIsInTheActivityGroup(final AiMessageType type) {
        assertThat(AiActivityGroups.isActivity(type)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AiMessageType.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "THINKING", "INTERIM_RESPONSE", "TOOL_CALL", "TOOL_RESULT", "MEMORY_UPDATE", "DEBUG_DETAIL"})
    void theRestIsInTheConversation(final AiMessageType type) {
        assertThat(AiActivityGroups.isActivity(type)).isFalse();
    }

    @Test
    void elementsAreMatchedToTheMessagesTheyShow() {
        // The steps a message left to the user are marked with the message's id and a suffix, which asking a
        // question again failed to read.
        assertThat(AiActivityGroups.messageIdOf("2904-actions")).isEqualTo(2904);
        assertThat(AiActivityGroups.messageIdOf("2904")).isEqualTo(2904);
        assertThat(AiActivityGroups.messageIdOf("pending")).isEqualTo(-1);
        assertThat(AiActivityGroups.messageIdOf("")).isEqualTo(-1);
        assertThat(AiActivityGroups.messageIdOf(null)).isEqualTo(-1);
    }
}
