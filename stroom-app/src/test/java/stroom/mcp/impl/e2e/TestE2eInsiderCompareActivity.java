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
import stroom.mcp.impl.e2e.InsiderDataset.Period;
import stroom.mcp.impl.e2e.InsiderDataset.Source;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `compare_activity` over `/mcp` on the insider-risk dataset ([InsiderDataset], every day from February to
/// September at a low scale, so the baselines are continuous): the scenario people's plants found (the leaver's new
/// destinations and breadth before the leaving date, the drift to later hours, the curious viewer above their Finance
/// peers, read from the Event XML's Organisation) and the decoys not flagged (the source-wide pipeline change told as
/// the source's, the approved migration given as an exception).
@SuppressWarnings("unchecked")
class TestE2eInsiderCompareActivity extends AbstractInsiderE2eTest {

    /// Every day from February to September, a sixth of the routine actions, 12 background people.
    static final Options OPTIONS = new Options(InsiderDataset.SEED, 12, 0.15, List.of(new Period(
            LocalDate.of(2026, 2, 2), LocalDate.of(2026, 9, 30))));
    private static final String BASELINE_FROM = "2026-02-02";

    @Test
    void thePlantsAreFoundAndTheDecoysAreNot() {
        final Loaded loaded = load(OPTIONS);
        dump("acts", Map.of("count", loaded.dataset().acts().size()));

        // The leaver: job-site browsing from 1 July, personal cloud uploads, sites outside the usual.
        final Map<String, Object> leaver = compare(loaded, InsiderDataset.LEAVER, "2026-06-27", "2026-09-26", "week",
                List.of(Source.PROXY, Source.ENDPOINT, Source.CLOUD), List.of("destination.domain"),
                List.of("source.bytes"), Map.of("categories", true, "categoryField", "url.category",
                        "accessField", "cloud.site"));
        dump("leaver", leaver);
        final String leaverFindings = file(leaver, 2);
        assertThat(String.valueOf(leaver.get("note"))).contains("never a conclusion about the person");
        assertThat(leaverFindings).contains(",category:" + InsiderDataset.JOB_CATEGORY + ",")
                .contains(",access:cloud.site,");
        assertThat(changes(leaverFindings, "events", false)).anySatisfy(date -> assertThat(days(date,
                LocalDate.of(2026, 7, 1))).isLessThanOrEqualTo(3));
        // The pipeline change (15 June) is the source's, never the leaver's own change.
        assertThat(changes(leaverFindings, "events", false)).noneSatisfy(date -> assertThat(days(date,
                InsiderDataset.PIPELINE_CHANGE)).isLessThanOrEqualTo(5));
        assertThat((List<String>) leaver.get("flagged_days")).isNotEmpty();

        // The drift to later hours.
        final Map<String, Object> drift = compare(loaded, InsiderDataset.DRIFT, "2026-05-01", "2026-09-01", "month",
                List.of(Source.ENDPOINT), List.of(), List.of(), Map.of("peers", false));
        dump("drift", drift);
        assertThat(file(drift, 2)).contains(",drift,first_activity,");

        // The pipeline change, for someone with nothing else going on: listed as the source's, not theirs.
        final Map<String, Object> pipeline = compare(loaded, "P20003", "2026-06-01", "2026-07-13", "week",
                List.of(Source.PROXY), List.of(), List.of(), Map.of("peers", false));
        dump("pipeline decoy", pipeline);
        assertThat((List<Map<String, Object>>) pipeline.get("findings")).noneSatisfy(finding -> {
            assertThat(finding.get("feature")).isEqualTo("events");
            assertThat(String.valueOf(finding.get("window"))).startsWith("2026-06-15");
        });
        assertThat(String.valueOf(pipeline.get("source_wide"))).contains("2026-06-15");

        // The approved migration, given as an exception: none of its days flagged.
        final Map<String, Object> migration = compare(loaded, "P20001", "2026-05-04", "2026-05-25", "day",
                List.of(Source.values()), List.of(), List.of(), Map.of("peers", false, "exceptions",
                        List.of("2026-05-11..2026-05-15: approved data-centre migration")));
        dump("migration decoy", migration);
        assertThat((List<Map<String, Object>>) migration.get("findings")).noneSatisfy(finding -> assertThat(
                String.valueOf(finding.get("window"))).matches("2026-05-1[1-5].*"));
        assertThat(String.valueOf(migration.get("notes"))).contains("approved data-centre migration");

        // The curious viewer, above their Finance peers (Organisation read from one event each).
        final Map<String, Object> viewer = compare(loaded, InsiderDataset.VIEWER, "2026-06-01", "2026-06-27", "week",
                List.of(Source.CLOUD), List.of(), List.of(), Map.of());
        dump("viewer", viewer);
        assertThat((List<Map<String, Object>>) viewer.get("findings")).anySatisfy(finding -> {
            assertThat(finding).containsEntry("kind", "peers");
            assertThat(String.valueOf(finding.get("evidence"))).contains("peers in Organisation '"
                                                                         + InsiderDataset.FINANCE + "'");
        });
        assertThat(changes(file(viewer, 2), "events", false)).anySatisfy(date -> assertThat(days(date,
                LocalDate.of(2026, 6, 1))).isLessThanOrEqualTo(3));

        // The compromised account: a new country, on leave.
        final Map<String, Object> compromised = compare(loaded, InsiderDataset.COMPROMISED, "2026-04-06",
                "2026-04-20", "week", List.of(Source.CLOUD, Source.VPN), List.of(), List.of(), Map.of("peers", false,
                        "leave", List.of("2026-04-06..2026-04-17")));
        dump("compromised", compromised);
        final String compromisedFindings = file(compromised, 2);
        assertThat(compromisedFindings).contains(",location,country:new,RO,").contains(",country:on_leave,");
    }

    // The dates of the change findings of a feature in a findings file, the source's own or the person's.
    private static List<LocalDate> changes(final String findings, final String feature, final boolean sourceWide) {
        final List<LocalDate> dates = new ArrayList<>();
        for (final String line : findings.split("\n")) {
            final String[] cells = line.split(",", -1);
            if (cells.length > 5 && cells[2].equals("change") && cells[3].equals(feature)
                && line.endsWith("," + sourceWide)) {
                dates.add(LocalDate.parse(cells[4]));
            }
        }
        return dates;
    }

    private static long days(final LocalDate a, final LocalDate b) {
        return Math.abs(ChronoUnit.DAYS.between(a, b));
    }

    private String file(final Map<String, Object> result, final int index) {
        final String path = ((List<String>) result.get("files")).get(index);
        return McpTestClient.text(client.call("read_file", args("path", path, "lineCount", 5_000)));
    }

    private Map<String, Object> compare(final Loaded loaded,
                                        final String person,
                                        final String windowFrom,
                                        final String windowTo,
                                        final String unit,
                                        final List<Source> sources,
                                        final List<String> breadth,
                                        final List<String> sums,
                                        final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("person", person);
        arguments.put("windowFrom", windowFrom);
        arguments.put("windowTo", windowTo);
        arguments.put("baselineFrom", BASELINE_FROM);
        arguments.put("unit", unit);
        arguments.put("timeZone", InsiderDataset.ZONE.getId());
        arguments.put("sources", sources.stream().filter(loaded.indexes()::containsKey).map(loaded::index).toList());
        if (!breadth.isEmpty()) {
            arguments.put("breadthFields", breadth);
        }
        if (!sums.isEmpty()) {
            arguments.put("sumFields", sums);
        }
        arguments.putAll(more);
        return json("compare_activity", arguments);
    }
}
