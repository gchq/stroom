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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` on the insider-risk dataset's reduced form ([AbstractInsiderE2eTest]) over `/mcp`, on a real
/// Stroom and Elasticsearch: a person read by their person id in every source, though their logon ids differ by
/// source; the fallback by logon id, each account counted, matched exactly (no other written forms searched for
/// unasked); the first and last events where the period has none; and the leaver's week of 2026-09-14: the VPN's
/// sessions (a pair keyed only by a session id), a window's focus to its loss (a vendor's pair) placed in
/// activities, and the proxy's periods labelled by their destination; and, with `includeTargets`, what was done
/// to them (the leaver's privileged group, the compromised account's failed logons, reset and MFA device) apart,
/// never in their activity.
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
        assertThat(spans).contains(" · " + InsiderDataset.PERSONAL_CLOUD + ",");
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

        // As the sources wrote them, not as the indexes hold them: nothing matches, and no search is made for other
        // forms; the result says how to ask for them.
        final String variants = McpTestClient.text(client.call("activity_timeline", timeline(Map.of("logonIds",
                List.of("CORP\\dwhitfield", "Dana.Whitfield@corp.example")), EVENING_FROM, EVENING_TO)));
        dump("activity_timeline, the leaver's logon ids as the sources wrote them", variants);
        assertThat(variants).startsWith("Error: No source holds")
                .contains("give logonForms [strip_domain] (slower: a script runs over every event in the period)")
                .doesNotContain("close variants");
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

    @Test
    void theLeaversWeekHasVpnSessionsWindowFocusPairsInActivitiesAndProxyPeriodsByDestination() {
        final Map<String, Object> arguments = timeline(Map.of("person", InsiderDataset.LEAVER, "view", "both"),
                "2026-09-14T00:00:00+10:00", "2026-09-19T00:00:00+10:00");

        final Map<String, Object> result = json("activity_timeline", arguments);
        dump("activity_timeline, the leaver's week", result);

        final List<Map<String, Object>> pairs = (List<Map<String, Object>>) result.get("pairs");
        // The VPN's sessions: a vendor's pair keyed only by a session id in Data, inferred.
        assertThat(pairs).anySatisfy(pair -> assertThat(pair).containsEntry("start", "vpn.login")
                .containsEntry("end", "vpn.logout").containsEntry("by", "Data[SessionId]")
                .containsEntry("origin", "inferred").containsEntry("unpaired_starts", 0));
        // A window's focus to its loss, by its handle in Data.
        assertThat(pairs).anySatisfy(pair -> assertThat(pair).containsEntry("start", "focus.in")
                .containsEntry("end", "focus.out").containsEntry("by", "Data[WindowHandle]"));
        final String folder = "/outputs/timelines/" + InsiderDataset.LEAVER + "/";
        final List<Map<String, String>> spans = rows(read(folder + "spans.csv"));
        final String activities = read(folder + "activities.csv");
        // The VPN sessions are spans on the gateway's lane: the three of the week (a Friday, two evenings).
        assertThat(spans).filteredOn(row -> "pair".equals(row.get("kind")) && row.get("lane").startsWith("ns-gw"))
                .hasSize(3);
        // Focus pairs placed in activities and rolled up with them, by their own ids; each says what happened.
        final List<Map<String, String>> placedFocus = spans.stream()
                .filter(row -> "pair".equals(row.get("kind")) && row.get("how").contains("focus.in")
                               && !row.get("activity").isEmpty())
                .toList();
        assertThat(placedFocus).isNotEmpty();
        assertThat(placedFocus).anySatisfy(row -> assertThat(activities).contains(row.get("span")));
        assertThat(placedFocus).allSatisfy(row -> {
            assertThat(row.get("action")).isEqualTo("focus.in to focus.out");
            assertThat(row.get("label")).startsWith("focus.in · ");
        });
        // The proxy's periods by their destination, led by what was done there.
        assertThat(spans).anySatisfy(row -> {
            assertThat(row.get("kind")).isEqualTo("activity");
            assertThat(row.get("label")).endsWith(InsiderDataset.PERSONAL_CLOUD);
            assertThat(row.get("action")).isNotEmpty();
        });
    }

    @Test
    void theLeaversPrivilegedGroupIsDoneToThemOnlyWithIncludeTargetsAndNeverInTheirActivity() {
        final Map<String, Object> without = json("activity_timeline", timeline(Map.of("person",
                InsiderDataset.LEAVER), "2026-09-14T00:00:00+10:00", "2026-09-15T00:00:00+10:00"));
        final String spansWithout = read("/outputs/timelines/" + InsiderDataset.LEAVER + "/spans.csv");
        final Map<String, Object> with = json("activity_timeline", timeline(Map.of("person",
                InsiderDataset.LEAVER, "includeTargets", true), "2026-09-14T00:00:00+10:00",
                "2026-09-15T00:00:00+10:00"));
        dump("activity_timeline, the leaver's 14 September with what was done to them", with);

        assertThat(without).doesNotContainKey("done_to_them");
        // Their own activity is the same either way.
        assertThat(with.get("sources")).isEqualTo(without.get("sources"));
        assertThat(with.get("events_read")).isEqualTo(without.get("events_read"));
        assertThat(with.get("spans")).isEqualTo(without.get("spans"));
        assertThat(with.get("rows")).isEqualTo(without.get("rows"));
        final String admin = InsiderRecords.normalised(loaded.dataset().person(InsiderDataset.GRANTING_ADMIN)
                .logon(Source.CLOUD));
        final Map<String, Object> done = (Map<String, Object>) with.get("done_to_them");
        assertThat(((Number) done.get("events")).longValue()).isEqualTo(1L);
        assertThat((List<Map<String, Object>>) done.get("sources")).filteredOn(source -> name(Source.CLOUD)
                .equals(source.get("source"))).singleElement().satisfies(source -> {
                    assertThat(source).containsEntry("kinds", Map.of("AddGroup", 1L))
                            .containsEntry("done_by", List.of(admin));
                    assertThat((String) source.get("by")).startsWith("done to them, by target person id "
                                                                     + "(user.target.id");
                });
        // The sources with no target field are said, not read.
        assertThat((List<Map<String, Object>>) done.get("left_out")).extracting(left -> left.get("source"))
                .contains(name(Source.ENDPOINT), name(Source.PROXY));
        final String spans = read("/outputs/timelines/" + InsiderDataset.LEAVER + "/spans.csv");
        assertThat(spans).contains(",AddGroup by " + admin + ",done to them: " + name(Source.CLOUD) + ",target,");
        assertThat(spans.lines().filter(line -> !line.endsWith(",target")).toList())
                .isEqualTo(spansWithout.lines().toList());
    }

    @Test
    void theCompromisedAccountsFailedLogonsResetAndMfaDeviceAreDoneToThem() {
        final Map<String, Object> result = json("activity_timeline", timeline(Map.of("person",
                InsiderDataset.COMPROMISED, "includeTargets", true), "2026-04-08T00:00:00+10:00",
                "2026-04-09T00:00:00+10:00"));
        dump("activity_timeline, the compromised account's night", result);

        final Map<String, Object> done = (Map<String, Object>) result.get("done_to_them");
        assertThat((List<Map<String, Object>>) done.get("sources")).filteredOn(source -> name(Source.CLOUD)
                .equals(source.get("source"))).singleElement().satisfies(source -> {
                    assertThat(source).containsEntry("kinds", Map.of("Logon", 6L, "AddMfaDevice", 1L,
                            "ResetPassword", 1L));
                    assertThat((List<String>) source.get("done_by")).containsExactly("no account",
                            InsiderRecords.normalised(InsiderDataset.SERVICE_DESK));
                });
        // Their own night (the sign-in from RO and the downloads) is theirs; what was done to them isn't in it.
        assertThat(((Number) result.get("events_read")).longValue()).isPositive();
        assertThat((List<Map<String, Object>>) done.get("listed")).extracting(event -> event.get("type"))
                .startsWith("Logon").contains("ResetPassword", "AddMfaDevice");
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

    // A table's rows by its header's column names; a quoted value (one holding a comma) read whole.
    private static List<Map<String, String>> rows(final String csv) {
        final List<List<String>> lines = csv.lines().map(TestE2eInsiderTimeline::cells).toList();
        final List<String> header = lines.getFirst();
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final List<String> line : lines.subList(1, lines.size())) {
            final Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), i < line.size()
                        ? line.get(i)
                        : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<String> cells(final String line) {
        final List<String> cells = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == '"' && quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                cell.append('"');
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
