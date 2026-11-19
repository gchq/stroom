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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                "data", 3));
        assertThat((String) result.get("events_note")).contains("Data");
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
