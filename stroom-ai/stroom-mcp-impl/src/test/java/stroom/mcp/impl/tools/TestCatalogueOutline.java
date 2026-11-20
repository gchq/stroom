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
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// A catalogue_event_types result too large for a client outside Stroom: seen in eval case 68 (300 event types) on
/// Haiku, results of 61,000 to 93,000 characters that Claude Code saved to files of its own.
class TestCatalogueOutline {

    private static final int LIMIT = LargeResults.WHOLE_RESULT_CHARS;
    private static final String SAVED = "/outputs/catalogue_event_types/acme-v1.json";

    @Test
    void aCheckOfThreeHundredEventTypesIsOutlinedKeepingWhatTheAgentActsOn() {
        final ChatWorkspace chats = TestLargeResults.writable();
        final Map<String, Object> result = checked(300, 120);
        final Map<String, Object> before = JsonUtil.readValue(JsonUtil.writeValueAsString(result, false), Map.class);
        assertThat(LargeResults.length(result)).isGreaterThan(LIMIT);

        final boolean outlined = CatalogueOutline.outlineIfLarge(TestLargeResults.CLIENT,
                Optional.of(new SessionWorkspace(chats, 1)), "acme-v1", result, LIMIT);

        assertThat(outlined).isTrue();
        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LIMIT);
        for (final String kept : List.of("status", "recorded", "source_notes", "refused", "table", "hint", "next",
                "listing")) {
            assertThat(result.get(kept)).as(kept).isEqualTo(before.get(kept));
        }
        final Map<?, ?> check = (Map<?, ?>) result.get("check");
        final Map<?, ?> checkBefore = (Map<?, ?>) before.get("check");
        for (final String kept : List.of("problems", "overlaps", "uncatalogued", "several")) {
            assertThat(check.get(kept)).as(kept).isEqualTo(checkBefore.get(kept));
        }
        assertThat(check.containsKey("order")).isFalse();
        assertThat(TestCatalogueOutline.<Object, Object>asMap(check.get("counts"))).as("counted, beside the table")
                .isEqualTo(Map.of("event_types", 300, "records", 360, "event_types_with_none", 120));
        assertThat(TestCatalogueOutline.<Object, Object>asMap(result.get("catalogue")))
                .containsEntry("event_types", 300)
                .containsEntry("by_status", Map.of("proposed", 300));
        assertThat((String) result.get("not_in_sample")).startsWith("120 event type(s) not in the sample");
        final Map<?, ?> saved = JsonUtil.readValue(TestLargeResults.written(chats, SAVED), Map.class);
        assertThat(saved.get("catalogue")).isEqualTo(before.get("catalogue"));
        assertThat(saved.get("check")).isEqualTo(before.get("check"));
        assertThat(((Map<?, ?>) result.get(LargeResults.WHOLE_RESULT)).get("saved")).isEqualTo(SAVED);
    }

    @Test
    void aTableTooLargeToGiveWholeSaysSoAndWhereItsOtherRowsAre() {
        final ChatWorkspace chats = TestLargeResults.writable();
        final Map<String, Object> result = checked(600, 0);
        final String table = (String) result.get("table");
        assertThat(LargeResults.length(table)).isGreaterThan(LIMIT);

        CatalogueOutline.outlineIfLarge(TestLargeResults.CLIENT, Optional.of(new SessionWorkspace(chats, 1)),
                "acme-v1", result, LIMIT);

        assertThat(LargeResults.length(result)).isLessThanOrEqualTo(LIMIT);
        final String shown = (String) result.get("table");
        final Matcher said = Pattern.compile("these are the first (\\d+) of its 600 rows. Show the user all of it, "
                                             + "as it is: these rows, then the other (\\d+), read with jq \\(path="
                                             + SAVED + ", query='\\.table\\[(\\d+):]\\[]', raw=true\\)")
                .matcher(shown);
        assertThat(said.find()).as(shown.substring(shown.length() - 400)).isTrue();
        final int rowsShown = Integer.parseInt(said.group(1));
        assertThat(Integer.parseInt(said.group(2))).isEqualTo(600 - rowsShown);
        final List<?> savedLines = (List<?>) JsonUtil.readValue(TestLargeResults.written(chats, SAVED), Map.class)
                .get("table");
        final List<String> lines = table.lines().toList();
        assertThat(savedLines).isEqualTo(lines);
        assertThat(shown).startsWith(String.join("\n", lines.subList(0, Integer.parseInt(said.group(3)))));
        assertThat(savedLines.get(Integer.parseInt(said.group(3)))).isEqualTo(lines.get(2 + rowsShown));
    }

    @Test
    void aSmallResultOrOneForStroomAisChatOrWithoutAWorkspaceIsGivenWhole() {
        final ChatWorkspace chats = Mockito.mock(ChatWorkspace.class);
        final Map<String, Object> small = checked(10, 2);
        final Map<String, Object> smallBefore = new LinkedHashMap<>(small);
        assertThat(CatalogueOutline.outlineIfLarge(TestLargeResults.CLIENT,
                Optional.of(new SessionWorkspace(chats, 1)), "acme-v1", small, LIMIT)).isFalse();
        assertThat(small).isEqualTo(smallBefore);

        final Map<String, Object> chat = checked(300, 120);
        final Map<String, Object> chatBefore = new LinkedHashMap<>(chat);
        assertThat(CatalogueOutline.outlineIfLarge(ToolCaller.STROOM_AI_CHAT,
                Optional.of(new SessionWorkspace(chats, 1)), "acme-v1", chat, LIMIT)).isFalse();
        assertThat(chat).isEqualTo(chatBefore);
        Mockito.verifyNoInteractions(chats);

        final Map<String, Object> noWorkspace = checked(300, 120);
        final Map<String, Object> noWorkspaceBefore = new LinkedHashMap<>(noWorkspace);
        assertThat(CatalogueOutline.outlineIfLarge(TestLargeResults.CLIENT, Optional.empty(), "acme-v1",
                noWorkspace, LIMIT)).isFalse();
        assertThat(noWorkspace).isEqualTo(noWorkspaceBefore);
    }

    @SuppressWarnings("unchecked")
    static <K, V> Map<K, V> asMap(final Object value) {
        return (Map<K, V>) value;
    }

    // A checked result as the tool gives it: the set's table, the check, the catalogue and those not in the sample.
    private static Map<String, Object> checked(final int types, final int notInSample) {
        final StringBuilder table = new StringBuilder("| Event type | Recognised by | Records | Translated as |\n"
                                                      + "| --- | --- | --- | --- |\n");
        final List<Map<String, Object>> eventTypes = new ArrayList<>();
        final Map<String, Object> counts = new LinkedHashMap<>();
        final List<String> order = new ArrayList<>();
        final List<String> absent = new ArrayList<>();
        for (int k = 0; k < types; k++) {
            final String typeId = "Process-Code%03d".formatted(k);
            table.append("| `").append(typeId).append("` Code ").append(k).append(" run | code is E").append(k)
                    .append(" | 2 | Process, who acted: user, acted on: object_id (User/Id) |\n");
            eventTypes.add(Map.of("type_id", typeId, "title", "Code " + k + " run", "status", "proposed"));
            counts.put(typeId, Map.of("records", k < notInSample
                    ? 0
                    : 2, "by_way", List.of(2)));
            order.add(typeId);
            if (k < notInSample) {
                absent.add(typeId + ": not in the sample (kept, and said so in the review)");
            }
        }
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("counts", counts);
        check.put("overlaps", List.of(Map.of("record", 3, "type_ids", List.of("A-B", "C-D"))));
        check.put("uncatalogued", Map.of("records", 1, "shapes", List.of("code=Z")));
        check.put("unparsed", Map.of("records", 0, "why", List.of()));
        check.put("several", List.of());
        check.put("problems", List.of(Map.of("type_id", "A-B", "problem", "a regex too long")));
        check.put("order", order);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "checked");
        result.put("recorded", true);
        result.put("source_notes", Map.of("uuid", "u1", "name", "Acme source notes"));
        result.put("refused", List.of(Map.of("proposal", 0, "type_id", "Bad Id", "why", "not Verb-Object")));
        result.put("check", check);
        if (!absent.isEmpty()) {
            result.put("not_in_sample", absent);
        }
        result.put("table", table.toString());
        result.put("catalogue", Map.of("event_types", eventTypes, "catch_all", Map.of("status", "proposed")));
        result.put("listing", "/outputs/catalogue/acme-v1.json");
        result.put("hint", "When the catalogue is right, confirm=true puts it to the user.");
        result.put("next", Map.of("call", Map.of("tool", "catalogue_event_types",
                "arguments", Map.of("build", "acme-v1", "confirm", true))));
        return result;
    }
}
