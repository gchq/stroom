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

/// A survey_feed result too large for a client outside Stroom: seen in eval cases 36, 56, 61, 65 and 66 on Haiku,
/// results of 52,000 to 109,000 characters, nearly all shapes and locations, that Claude Code saved to files of its
/// own.
class TestSurveyOutline {

    private static final int LIMIT = LargeResults.WHOLE_RESULT_CHARS;
    private static final String SAVED = "/outputs/survey_feed/eval-65.json";

    @Test
    void aSurveyOfSixtyShapesIsOutlinedTheLocationsSteppedFromTheSavedFile() {
        final ChatWorkspace chats = TestLargeResults.writable();
        final Map<String, Object> result = surveyed(60, 170);
        final Map<String, Object> before = JsonUtil.readValue(JsonUtil.writeValueAsString(result, false), Map.class);
        assertThat(LargeResults.length(result)).isGreaterThan(LIMIT);

        assertThat(SurveyOutline.outlineIfLarge(TestLargeResults.CLIENT, Optional.of(new SessionWorkspace(chats, 1)),
                "eval-65", result, LIMIT)).isTrue();

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LIMIT);
        for (final String kept : List.of("feed", "coverage", "source_types", "survey_doc", "hint", "next",
                "new_shapes")) {
            assertThat(result.get(kept)).as(kept).isEqualTo(before.get(kept));
        }
        final Map<?, ?> locations = (Map<?, ?>) result.get("locations");
        assertThat(locations.get("records")).isEqualTo(170);
        assertThat(locations.get("streams")).isEqualTo(List.of(9, 10));
        assertThat((String) locations.get("step_them")).contains("step_records locations_from='" + SAVED + "'",
                "(a record of each shape)");
        final List<?> shapes = (List<?>) result.get("shapes");
        assertThat(shapes).hasSize(60).allSatisfy(shape -> assertThat(TestCatalogueOutline.<String, Object>asMap(shape))
                .doesNotContainKeys("locations", "more_examples")
                .containsKeys("signature", "count", "new", "share_percent", "streams", "source_types"));
        final List<?> shapesBefore = (List<?>) before.get("shapes");
        for (int i = 0; i < shapes.size(); i++) {
            assertThat(((Map<?, ?>) shapes.get(i)).get("signature")).as("the signature kept whole")
                    .isEqualTo(((Map<?, ?>) shapesBefore.get(i)).get("signature"));
        }
        final Map<?, ?> saved = JsonUtil.readValue(TestLargeResults.written(chats, SAVED), Map.class);
        assertThat(saved.get("locations")).isEqualTo(before.get("locations"));
        assertThat(saved.get("shapes")).isEqualTo(before.get("shapes"));
    }

    @Test
    void aSmallSurveyOrOneForStroomAisChatOrWithoutAWorkspaceIsGivenWhole() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        final Map<String, Object> small = surveyed(3, 3);
        final Map<String, Object> smallBefore = new LinkedHashMap<>(small);
        assertThat(SurveyOutline.outlineIfLarge(TestLargeResults.CLIENT, Optional.of(new SessionWorkspace(chats, 1)),
                "eval-65", small, LIMIT)).isFalse();
        assertThat(small).isEqualTo(smallBefore);

        final Map<String, Object> chat = surveyed(60, 170);
        final Map<String, Object> chatBefore = new LinkedHashMap<>(chat);
        assertThat(SurveyOutline.outlineIfLarge(ToolCaller.STROOM_AI_CHAT, Optional.of(new SessionWorkspace(chats, 1)),
                "eval-65", chat, LIMIT)).isFalse();
        assertThat(chat).isEqualTo(chatBefore);
        Mockito.verifyNoInteractions(chats);

        final Map<String, Object> noWorkspace = surveyed(60, 170);
        final Map<String, Object> noWorkspaceBefore = new LinkedHashMap<>(noWorkspace);
        assertThat(SurveyOutline.outlineIfLarge(TestLargeResults.CLIENT, Optional.empty(), "eval-65", noWorkspace,
                LIMIT)).isFalse();
        assertThat(noWorkspace).isEqualTo(noWorkspaceBefore);
    }

    // A survey as the tool gives it: shapes of a long signature, each with its examples and locations, and the
    // locations to step.
    static Map<String, Object> surveyed(final int shapeCount, final int locationCount) {
        final String fields = "fields:event,fields.collector,fields.env,fields.orig_rt,host,service,time > event: "
                              + "json:action,level,logger_name,message,new_value,setting,src_ip,thread,user";
        final List<Map<String, Object>> shapes = new ArrayList<>();
        for (int i = 0; i < shapeCount; i++) {
            final Map<String, Object> shape = new LinkedHashMap<>();
            shape.put("signature", fields + " | action=ACTION_" + i + ", logger_name=com.acme.audit.Logger" + i);
            shape.put("new", true);
            shape.put("count", 17);
            shape.put("share_percent", 2.8);
            shape.put("streams", List.of(9));
            shape.put("example", "{\"time\": \"2026-11-10T00:00:21\", \"event\": {\"action\": \"ACTION_" + i
                                 + "\", \"message\": \"" + "x".repeat(400) + "\"}}");
            shape.put("locations", List.of(Map.of("stream", 9, "part", 0, "record", i)));
            shape.put("source_types", List.of("com.acme.audit.Logger" + i));
            shape.put("more_examples", List.of("{\"time\": \"2026-11-10T00:00:58\", \"event\": {\"message\": \""
                                               + "y".repeat(300) + "\"}}"));
            shapes.add(shape);
        }
        final List<Map<String, Object>> locations = new ArrayList<>();
        for (int i = 0; i < locationCount; i++) {
            locations.add(Map.of("stream", i % 2 == 0
                            ? 9
                            : 10, "part", 0, "record", i, "shape", fields + " | action=ACTION_" + i % 60,
                    "source_types", List.of("com.acme.audit.Logger" + i % 60)));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", "EVAL-65");
        result.put("format", "json lines");
        result.put("coverage", "covered: no new kinds of event in the last 1 streams read");
        result.put("source_types", Map.of("field", "event>logger_name", "aggregated", true));
        result.put("survey_doc", Map.of("type", "Documentation", "uuid", "d1", "name", "EVAL-65 - Survey"));
        result.put("kinds", Map.of("event>action", Map.of("LOGIN_SUCCESS", 40)));
        result.put("shapes", shapes);
        result.put("new_shapes", shapeCount);
        result.put("locations", locations);
        result.put("hint", "Translate every shape and check it with step_records(pipeline, locations, draft_code).");
        result.put("next", Map.of("step", "source_types", "call", Map.of("tool", "record_source_types",
                "arguments", Map.of("build", "eval-65", "feed", "EVAL-65"))));
        return result;
    }
}
