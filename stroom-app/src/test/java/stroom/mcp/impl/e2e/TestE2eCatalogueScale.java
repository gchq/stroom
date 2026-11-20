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

package stroom.mcp.impl.e2e;

import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.WholeResults;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// A catalogue of 300 event types (seen: 304 kinds in one build), lean: generated key=value lines, no indexing.
/// Proposals add event types in batches, as an agent sends them, never the catalogue whole; every reply is compact
/// (counts and problems, within a budget, as TestE2eSourceDocs measures replies), the full listing saved to the
/// workspace; the high-confidence event types are confirmed in one form for the source type group, the medium and
/// low ones in sets of up to ten. A client outside Stroom takes at most 40,000 characters of a reply (seen in eval
/// case 68 on Haiku: replies of 61,000 to 93,000 characters saved by Claude Code to files of its own): past it, a
/// reply is given in outline, saved whole to the workspace, and read from there as the agent reads it.
class TestE2eCatalogueScale extends AbstractE2eTest {

    private static final int TYPES = 300;
    private static final int BATCH = 50;
    // The most of a reply a client outside Stroom takes (LargeResults.WHOLE_RESULT_CHARS).
    private static final int REPLY_BUDGET = 40_000;
    // A workspace file a reply names.
    private static final Pattern SAVED = Pattern.compile("\"(/[^\"]+\\.json)\"");

    @Test
    void threeHundredEventTypesAreCheckedAndConfirmedInCompactReplies() {
        final String build = "e2e-cat-scale";
        final String feed = "E2E-CAT-SCALE";
        createFeed(build, feed);
        final List<Long> raw = List.of(upload(feed, sample()));
        surveyed(build, feed, raw);
        final Map<String, Integer> replies = new LinkedHashMap<>();
        replies.put("listed", replyText(CATALOGUE, args("build", build, "stream_ids", raw)).length());

        // ### Added in batches, each reply compact: counts and problems, the full listing in the workspace.
        Map<String, Object> checked = Map.of();
        for (int from = 0; from < TYPES; from += BATCH) {
            final List<Map<String, Object>> batch = new ArrayList<>();
            for (int k = from; k < from + BATCH; k++) {
                batch.add(eventType(typeId(k), "Process", confidence(k), when("code", "equals", code(k))));
            }
            final Map<String, Object> given = client.jsonAsGiven(CATALOGUE, args("build", build, "stream_ids", raw,
                    "proposals", batch));
            replies.put("proposals from " + from, jsonOf(given).length());
            checked = WholeResults.of(client, given);
            if (from + BATCH >= TYPES) {
                outlinedAsAnAgentReadsIt(given);
            }
        }
        assertThat(countsOf(checked)).hasSize(TYPES).allSatisfy((typeId, records) -> assertThat(records)
                .isEqualTo(2L));
        assertThat(map(checkOf(checked), "uncatalogued")).containsEntry("records", 0);
        final Matcher saved = SAVED.matcher(jsonOf(checked));
        assertThat(saved.find()).as("the full listing saved to the workspace: %s", checked).isTrue();
        assertThat(McpTestClient.text(client.call("jq", args("path", saved.group(1), "query",
                "[.. | objects | .type_id? // empty] | unique | length"))))
                .as("every event type in the saved listing").isEqualTo(String.valueOf(TYPES));

        // ### Confirmed: the high-confidence ones in one form, the medium and low ones in sets of up to ten.
        final int formsBefore = client.forms().size();
        Map<String, Object> step = Map.of();
        for (int round = 0; round < 40 && !"confirmed".equals(step.get("status")); round++) {
            final Map<String, Object> given = client.jsonAsGiven(CATALOGUE, args("build", build, "confirm", true,
                    "catch_all", Map.of("allow_unknown", "E2E: kept as Unknown")));
            replies.put("confirm " + round, jsonOf(given).length());
            step = WholeResults.of(client, given);
        }
        assertThat(step).containsEntry("status", "confirmed");
        final List<Integer> sets = eventTypesPutInEachForm(formsSince(formsBefore));
        assertThat(sets.getFirst()).as("the high-confidence ones, one form").isEqualTo(TYPES / 3);
        assertThat(sets.subList(1, sets.size())).as("the medium and low ones").hasSize(20)
                .allSatisfy(size -> assertThat(size).isLessThanOrEqualTo(10));
        assertThat(sets.stream().mapToInt(Integer::intValue).sum()).isEqualTo(TYPES);

        final Map.Entry<String, Integer> worst = replies.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        dump("reply sizes", replies);
        assertThat(worst.getValue()).as("the largest reply is %s's", worst.getKey()).isLessThanOrEqualTo(
                REPLY_BUDGET);
    }

    // The check of all 300, given in outline: what the agent acts on whole, the rest read from the saved file.
    private void outlinedAsAnAgentReadsIt(final Map<String, Object> given) {
        assertThat(WholeResults.outlined(given)).as("past 40,000 characters, given in outline: %s", given).isTrue();
        assertThat(given).containsKeys("status", "recorded", "hint", "next", "table");
        assertThat(map(checkOf(given), "counts")).as("counted").containsEntry("event_types", TYPES);
        assertThat(checkOf(given)).containsKeys("problems", "overlaps", "uncatalogued", "several");
        final String saved = String.valueOf(map(given, "whole_result").get("saved"));
        assertThat(saved).isEqualTo("/outputs/catalogue_event_types/e2e-cat-scale.json");
        assertThat(McpTestClient.text(client.call("jq", args("path", saved, "query", ".check.counts | length"))))
                .as("every event type's count in the saved result").isEqualTo(String.valueOf(TYPES));
        final String rows = McpTestClient.text(client.call("jq", args("path", saved, "query",
                ".check.counts | to_entries[] | select(.value.records != 2) | .key")));
        assertThat(rows).as("every event type recognises its two records").isEqualTo("No results.");
    }

    private static String typeId(final int k) {
        return "Process-Code%03d".formatted(k);
    }

    private static String code(final int k) {
        return "E%03d".formatted(k);
    }

    // A third of each confidence.
    private static String confidence(final int k) {
        if (k < TYPES / 3) {
            return "high";
        }
        return k < 2 * TYPES / 3
                ? "medium"
                : "low";
    }

    // Two records of each code.
    private static String sample() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < 2 * TYPES; i++) {
            out.append("time=2026-11-06T%02d:%02d:%02dZ code=%s user=user%d src=10.0.%d.%d\n".formatted(i / 3600,
                    i / 60 % 60, i % 60, code(i % TYPES), i % 7, i % 5, 1 + i % 200));
        }
        return out.toString();
    }
}
