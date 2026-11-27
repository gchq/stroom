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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.ToolCallRecord;
import stroom.ai.impl.ToolResultRecord;
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
    void theResultsOfASkillsTimelineRunsAreReadNewestFirst() {
        final List<AiChatMessage> chat = List.of(
                message(1, AiMessageType.TOOL_CALL, timelineCall("t1", "User activity timeline")),
                message(1, AiMessageType.TOOL_RESULT, result("t1", false, "first run")),
                // Another skill's timeline, and one with no skill: not this skill's runs.
                message(2, AiMessageType.TOOL_CALL, timelineCall("t2", "Other")),
                message(2, AiMessageType.TOOL_RESULT, result("t2", false, "other run")),
                message(3, AiMessageType.TOOL_CALL, JsonUtil.writeValueAsString(new ToolCallRecord("t3",
                        SkillChecks.TIMELINE_TOOL, "{}", null))),
                message(3, AiMessageType.TOOL_RESULT, result("t3", false, "no skill")),
                // By its slug; a failed run says nothing.
                message(4, AiMessageType.TOOL_CALL, timelineCall("t4", "user-activity-timeline")),
                message(4, AiMessageType.TOOL_RESULT, result("t4", false, "second run")),
                message(5, AiMessageType.TOOL_CALL, timelineCall("t5", "User activity timeline")),
                message(5, AiMessageType.TOOL_RESULT, result("t5", true, "Error: failed")));

        assertThat(SkillUsage.timelineResults(List.of(chat), "User activity timeline"))
                .containsExactly("second run", "first run");
    }

    @Test
    void aShortNameInAQuestionIsntAskingForIt() {
        assertThat(SkillUsage.askedByName("a timeline please", "Line")).isFalse();
        assertThat(SkillUsage.askedByName("/skill:line", "Line")).isTrue();
    }

    private static AiChatMessage message(final int turn, final AiMessageType type, final String text) {
        return new AiChatMessage(null, 1, 0L, type, null, text, turn, null);
    }

    private static String timelineCall(final String id, final String skill) {
        return JsonUtil.writeValueAsString(new ToolCallRecord(id, SkillChecks.TIMELINE_TOOL,
                JsonUtil.writeValueAsString(Map.of("entity", "pat", "skill", skill)), null));
    }

    private static String result(final String id, final boolean error, final String text) {
        return JsonUtil.writeValueAsString(new ToolResultRecord(id, SkillChecks.TIMELINE_TOOL, error, text, null,
                text.length(), 0, null));
    }

    private static String call(final String name) {
        return JsonUtil.writeValueAsString(new ToolCallRecord("c", FindSkillsTool.NAME,
                JsonUtil.writeValueAsString(Map.of("name", name)), null));
    }
}
