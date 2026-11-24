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
import stroom.mcp.impl.e2e.InsiderDataset.Options;
import stroom.mcp.impl.e2e.InsiderDataset.Source;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` on the insider-risk dataset's reduced form ([AbstractInsiderE2eTest]) over `/mcp`, on a real
/// Stroom and Elasticsearch: a person read by their person id in every source, though their logon ids differ by
/// source; the fallback by logon id, each account counted, with close variants offered but never merged; the first
/// and last events where the period has none; and the leaver's evening of 2026-09-15: proxy periods labelled by
/// their destination.
@SuppressWarnings("unchecked")
class TestE2eInsiderTimeline extends AbstractInsiderE2eTest {

    private static final String EVENING_FROM = "2026-09-15T18:00:00+10:00";
    private static final String EVENING_TO = "2026-09-15T23:59:00+10:00";

    private Loaded loaded;

    @BeforeEach
    void loadTheDataset() {
        loaded = load(Options.reduced());
    }

    @Test
    void aPersonIsReadByTheirPersonIdInEverySourceThoughTheirLogonIdsDiffer() {
        final Map<String, Object> result = json("activity_timeline", timeline(Map.of("person",
                InsiderDataset.LEAVER), EVENING_FROM, EVENING_TO));
        dump("activity_timeline, the leaver by person id", result);

        final List<Map<String, Object>> sources = (List<Map<String, Object>>) result.get("sources");
        assertThat(sources).extracting(source -> source.get("by")).containsOnly("by person id");
        assertThat(sources).extracting(source -> source.get("user_field")).containsOnly("user.id");
        assertThat(sources).extracting(source -> source.get("index")).contains(name(Source.ENDPOINT),
                name(Source.PROXY), name(Source.VPN));
        assertThat(result).doesNotContainKey("other_accounts");
        // The proxy's events are Network events: their periods are labelled by the destination.
        final String spans = read("/outputs/timelines/" + InsiderDataset.LEAVER + "/spans.csv");
        assertThat(spans).contains("," + InsiderDataset.PERSONAL_CLOUD + ",");
        assertThat(spans).doesNotContain(",network,");
    }

    @Test
    void byLogonIdsEachAccountIsCountedAndCloseVariantsAreOfferedNotMerged() {
        final Map<String, Object> result = json("activity_timeline", timeline(Map.of("logonIds",
                List.of("dwhitfield", "dana.whitfield")), EVENING_FROM, EVENING_TO));
        dump("activity_timeline, the leaver by logon ids", result);

        final List<Map<String, Object>> sources = (List<Map<String, Object>>) result.get("sources");
        assertThat(sources).extracting(source -> source.get("by")).containsOnly("by logon id");
        final Map<String, Object> endpoint = source(sources, Source.ENDPOINT);
        assertThat((Map<String, Object>) endpoint.get("accounts")).containsOnlyKeys("dwhitfield");
        assertThat((Map<String, Object>) source(sources, Source.PROXY).get("accounts"))
                .containsOnlyKeys("dana.whitfield");
        assertThat(result).containsEntry("other_accounts",
                "By logon id: other accounts of the person (an admin or service account) aren't included unless "
                + "named.");

        // As the sources wrote them, not as the indexes hold them: nothing matches, the variants are offered.
        final String variants = McpTestClient.text(client.call("activity_timeline", timeline(Map.of("logonIds",
                List.of("CORP\\dwhitfield", "Dana.Whitfield@corp.example")), EVENING_FROM, EVENING_TO)));
        dump("activity_timeline, the leaver's logon ids as the sources wrote them", variants);
        assertThat(variants).startsWith("Error: No source holds").contains("close variants in its index: dwhitfield (not merged")
                .contains("close variants in its index: dana.whitfield (not merged");
    }

    @Test
    void aPeriodWithNoneOfThePersonsEventsGivesEachSourcesFirstAndLast() {
        final Map<String, Object> arguments = timeline(Map.of("person", InsiderDataset.LEAVER),
                "2026-10-12T00:00:00+11:00", "2026-10-13T00:00:00+11:00");
        arguments.put("sources", List.of(loaded.index(Source.ENDPOINT)));

        final Map<String, Object> result = json("activity_timeline", arguments);
        dump("activity_timeline, after the leaving date", result);

        assertThat(result).containsEntry("events_read", 0);
        assertThat((List<Map<String, Object>>) result.get("none_in_period")).singleElement().satisfies(range -> {
            assertThat((String) range.get("first")).startsWith("2025-11-0");
            // The reduced form's last day of the leaver's.
            assertThat((String) range.get("last")).startsWith("2026-09-1");
        });
    }

    // ------------------------------------------------------------------------------------------------

    private Map<String, Object> timeline(final Map<String, Object> who, final String from, final String to) {
        final Map<String, Object> arguments = new LinkedHashMap<>(who);
        arguments.put("timeFrom", from);
        arguments.put("timeTo", to);
        arguments.put("timeZone", InsiderDataset.ZONE.getId());
        arguments.put("sources", Arrays.stream(Source.values()).filter(loaded.indexes()::containsKey)
                .map(loaded::index).toList());
        return arguments;
    }

    private String name(final Source source) {
        return loaded.indexes().get(source).getName();
    }

    private Map<String, Object> source(final List<Map<String, Object>> sources, final Source source) {
        return sources.stream().filter(one -> name(source).equals(one.get("index"))).findFirst().orElseThrow();
    }

    // A workspace file of the session, read a page at a time as an agent would, without its line numbers.
    private String read(final String path) {
        final StringBuilder sb = new StringBuilder();
        int start = 1;
        while (true) {
            final String page = McpTestClient.text(client.call("read_file", Map.of("path", path,
                    "startLine", start, "lineCount", 500)));
            int last = -1;
            for (final String line : page.split("\n")) {
                final int tab = line.indexOf('\t');
                if (tab > 0 && line.substring(0, tab).strip().matches("\\d+")) {
                    last = Integer.parseInt(line.substring(0, tab).strip());
                    sb.append(line.substring(tab + 1)).append('\n');
                }
            }
            if (last < start) {
                return sb.toString();
            }
            start = last + 1;
        }
    }
}
