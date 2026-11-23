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

package stroom.ai.impl.tools;

import stroom.ai.impl.ToolCallRecord;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSkillUsage {

    @Test
    void offeredChosenAndAskedByNameAreCountedFromTheChats() {
        final List<AiChatMessage> first = List.of(
                message(1, AiMessageType.USER_MESSAGE, "build a timeline for jsmith"),
                message(1, AiMessageType.SKILLS_OFFERED, SkillUsage.offeredMessage(List.of("user activity "
                                                                                          + "timeline"))),
                message(1, AiMessageType.TOOL_CALL, call("User activity timeline")),
                message(2, AiMessageType.USER_MESSAGE, "and for bob"),
                message(2, AiMessageType.SKILLS_OFFERED, SkillUsage.offeredMessage(List.of("user activity "
                                                                                          + "timeline"))));
        final List<AiChatMessage> second = List.of(
                message(1, AiMessageType.USER_MESSAGE, "/skill:user-activity-timeline user=bob"),
                message(1, AiMessageType.TOOL_CALL, call("user-activity-timeline")),
                message(2, AiMessageType.USER_MESSAGE, "run the User Activity Timeline again"));

        final Map<String, SkillUsage.Counts> counts = SkillUsage.collect(List.of(first, second),
                List.of("User activity timeline", "Other"));

        assertThat(counts.get("user activity timeline")).isEqualTo(new SkillUsage.Counts(2, 2, List.of(
                "/skill:user-activity-timeline user=bob", "run the User Activity Timeline again")));
        assertThat(counts.get("other")).isEqualTo(new SkillUsage.Counts(0, 0, List.of()));
    }

    @Test
    void aShortNameInAQuestionIsntAskingForIt() {
        assertThat(SkillUsage.askedByName("a timeline please", "Line")).isFalse();
        assertThat(SkillUsage.askedByName("/skill:line", "Line")).isTrue();
    }

    private static AiChatMessage message(final int turn, final AiMessageType type, final String text) {
        return new AiChatMessage(null, 1, 0L, type, null, text, turn, null);
    }

    private static String call(final String name) {
        return JsonUtil.writeValueAsString(new ToolCallRecord("c", FindSkillsTool.NAME,
                JsonUtil.writeValueAsString(Map.of("name", name)), null));
    }
}
