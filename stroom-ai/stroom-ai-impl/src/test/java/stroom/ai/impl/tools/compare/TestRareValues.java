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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.tools.compare.ActivityScoring.Finding;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.ai.impl.tools.compare.RareValues.Held;
import stroom.ai.impl.tools.compare.RareValues.Picked;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestRareValues {

    private static final Instant WINDOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant IN_WINDOW = Instant.parse("2026-06-10T00:00:00Z");

    @Test
    void theThresholdIsThreePeopleOrOnePercentWhicheverIsLarger() {
        assertThat(RareValues.threshold(10)).isEqualTo(3);
        assertThat(RareValues.threshold(300)).isEqualTo(3);
        assertThat(RareValues.threshold(301)).isEqualTo(4);
        assertThat(RareValues.threshold(1_000)).isEqualTo(10);
        assertThat(RareValues.threshold(0)).isEqualTo(3);
    }

    @Test
    void aValueHeldByTheThresholdIsRareAndOneMoreIsNot() {
        final Picked picked = RareValues.pick("process.name", 20, table(List.of(
                value("netdump64.exe", 1, IN_WINDOW),
                value("rare3.exe", 3, BEFORE),
                value("common4.exe", 4, BEFORE),
                value("explorer.exe", 20, BEFORE))));

        assertThat(picked.threshold()).isEqualTo(3);
        assertThat(picked.distinct()).isEqualTo(4);
        assertThat(picked.values()).extracting(RareValues.Value::value)
                .containsExactly("netdump64.exe", "rare3.exe");
        assertThat(picked.hostnames()).isFalse();
        assertThat(RareValues.hostnameNote(picked, List.of("url.registered_domain"), null)).isEmpty();
    }

    @Test
    void theFewestPeopleComeFirstThenTheNewest() {
        final Picked picked = RareValues.pick("f", 10, table(List.of(
                value("older-one", 1, BEFORE),
                value("two", 2, IN_WINDOW),
                value("newer-one", 1, IN_WINDOW))));

        assertThat(picked.values()).extracting(RareValues.Value::value)
                .containsExactly("newer-one", "older-one", "two");
    }

    @Test
    void aServicesManyRareSubdomainsAreLeftOutAsCdnNoiseAndTheBetterFieldNamed() {
        final List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < RareValues.SUBDOMAINS; i++) {
            rows.add(value("d" + i + "a9.cdn-edge.example", 1, IN_WINDOW));
        }
        rows.add(value("mydrive.personal-cloud.example", 1, IN_WINDOW));
        rows.add(value("www.news.example.com", 15, BEFORE));
        final Picked picked = RareValues.pick("destination.domain", 20, table(rows));

        assertThat(picked.hostnames()).isTrue();
        assertThat(picked.values()).extracting(RareValues.Value::value)
                .containsExactly("mydrive.personal-cloud.example");
        assertThat(picked.noise()).containsEntry("cdn-edge.example", RareValues.SUBDOMAINS);
        final Optional<String> note = RareValues.hostnameNote(picked, List.of("destination.domain",
                "destination.registered_domain", "url.category"), "url.category");
        assertThat(note).hasValueSatisfying(text -> assertThat(text)
                .contains("destination.domain holds full hostnames")
                .contains("CDN noise")
                .contains("cdn-edge.example (" + RareValues.SUBDOMAINS + ")")
                .contains("rare=destination.registered_domain"));
        // Without a registered domain field, the category field; without either, the note alone.
        assertThat(RareValues.hostnameNote(picked, List.of("destination.domain", "url.category"), "url.category"))
                .hasValueSatisfying(text -> assertThat(text).contains("rare=url.category"));
        assertThat(RareValues.hostnameNote(picked, List.of("destination.domain"), null))
                .hasValueSatisfying(text -> assertThat(text).doesNotContain("rare="));
    }

    @Test
    void fewerSubdomainsThanTheCutAreKept() {
        final List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < RareValues.SUBDOMAINS - 1; i++) {
            rows.add(value("s" + i + ".service.example", 1, IN_WINDOW));
        }
        final Picked picked = RareValues.pick("destination.domain", 20, table(rows));

        assertThat(picked.values()).hasSize(RareValues.SUBDOMAINS - 1);
        assertThat(picked.noise()).isEmpty();
    }

    @Test
    void aValueIsNewForAHolderActiveBeforeTheWindowOnly() {
        final Picked picked = RareValues.pick("destination.domain", 20, table(List.of(
                value("mydrive.personal-cloud.example", 2, IN_WINDOW))));
        final Table holders = new Table(List.of(
                holder("mydrive.personal-cloud.example", "leaver", IN_WINDOW),
                holder("mydrive.personal-cloud.example", "joiner", IN_WINDOW)), false, 1, List.of(), "q");
        final Map<String, Instant> firstActive = new LinkedHashMap<>();
        firstActive.put("leaver", BEFORE);
        // Joined in the window: everything is new for them, so nothing is.
        firstActive.put("joiner", IN_WINDOW.minusSeconds(3_600));

        final List<Held> held = RareValues.held(picked, holders, WINDOW, firstActive);

        assertThat(held).singleElement().satisfies(one -> {
            assertThat(one.isNew()).isTrue();
            assertThat(one.holders()).extracting(RareValues.Holder::person).containsExactly("joiner", "leaver");
            assertThat(one.holders()).filteredOn(RareValues.Holder::isNew).extracting(RareValues.Holder::person)
                    .containsExactly("leaver");
        });
        final Map<String, Finding> findings = RareValues.findings("Proxy", picked, held, "2026-06-01 to "
                                                                                       + "2026-06-30",
                ZoneOffset.UTC);
        assertThat(findings).containsOnlyKeys("leaver");
        assertThat(findings.get("leaver").kind()).isEqualTo(ActivityScoring.Kind.RARE);
        assertThat(findings.get("leaver").feature()).isEqualTo("rare:destination.domain");
        assertThat(findings.get("leaver").score()).isEqualTo(ActivityScoring.Z_THRESHOLD);
        assertThat(findings.get("leaver").evidence()).contains("mydrive.personal-cloud.example (2026-06-10, held "
                                                               + "by 2)");
    }

    @Test
    void moreNewRareValuesScoreALittleMoreUpToTwiceTheThreshold() {
        final List<Map<String, String>> values = new ArrayList<>();
        final List<Map<String, String>> holders = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            values.add(value("v" + i, 1, IN_WINDOW));
            holders.add(holder("v" + i, "p", IN_WINDOW));
        }
        final Picked picked = RareValues.pick("f", 20, table(values));
        final List<Held> held = RareValues.held(picked, new Table(holders, false, 1, List.of(), "q"), WINDOW,
                Map.of("p", BEFORE));

        final Finding finding = RareValues.findings("S", picked, held, "w", ZoneOffset.UTC).get("p");

        assertThat(finding.score()).isEqualTo(2 * ActivityScoring.Z_THRESHOLD);
        assertThat(finding.evidence()).startsWith("12 value(s) of f").endsWith(", ...");
    }

    @Test
    void theFieldsOfferedAreThoseWithManyValues() {
        final Table table = new Table(List.of(Map.of(ActivityProfiles.DISTINCT_PREFIX + "0", "400",
                ActivityProfiles.DISTINCT_PREFIX + "1", "3", ActivityProfiles.DISTINCT_PREFIX + "2",
                String.valueOf(RareValues.MANY_VALUES))), false, 1, List.of(), "q");

        assertThat(RareValues.usable(List.of("destination.domain", "event.code", "process.name"), table))
                .extracting(RareValues.FieldValues::field)
                .containsExactly("destination.domain", "process.name");
        assertThat(RareValues.usable(List.of("a"), new Table(List.of(), false, 1, List.of(), "q"))).isEmpty();
    }

    @Test
    void hostnamesAndTheirParents() {
        assertThat(RareValues.isHostname("mydrive.personal-cloud.example")).isTrue();
        assertThat(RareValues.isHostname("netdump64.exe")).isTrue();
        assertThat(RareValues.isHostname("powershell")).isFalse();
        assertThat(RareValues.isHostname("C:\\Tools\\x.exe")).isFalse();
        assertThat(RareValues.isHostname(null)).isFalse();
        assertThat(RareValues.hasSubdomain("netdump64.exe")).isFalse();
        assertThat(RareValues.hasSubdomain("mydrive.personal-cloud.example")).isTrue();
        assertThat(RareValues.parent("a.b.cdn-edge.example")).isEqualTo("cdn-edge.example");
        assertThat(RareValues.parent("www.example.co.uk")).isEqualTo("example.co.uk");
        assertThat(RareValues.parent("example.com")).isEqualTo("example.com");
    }

    @Test
    void theAggregatesFilterAndGroupAsAsked() {
        final AggregateQuery byValue = RareValues.byValue(CompareFixtures.SOURCE, Map.of("process.name",
                List.of("powershell.exe")), "user.id", "destination.domain", BEFORE, WINDOW);
        assertThat(byValue.groups()).containsExactly("destination.domain");
        assertThat(byValue.filters()).containsOnlyKeys("process.name");
        assertThat(byValue.metrics()).extracting(AggregateQuery.Metric::name).contains(RareValues.PEOPLE);

        final AggregateQuery holders = RareValues.holders(CompareFixtures.SOURCE, Map.of("process.name",
                List.of("powershell.exe")), "user.id", "destination.domain", List.of("a", "b"), BEFORE, WINDOW);
        assertThat(holders.groups()).containsExactly("destination.domain", "user.id");
        assertThat(holders.filters()).containsEntry("destination.domain", List.of("a", "b"))
                .containsEntry("process.name", List.of("powershell.exe"));
    }

    private static Table table(final List<Map<String, String>> rows) {
        return new Table(rows, false, 1, List.of(), "q");
    }

    private static Map<String, String> value(final String value, final int people, final Instant first) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(AggregateQuery.groupColumn(0), value);
        row.put(RareValues.PEOPLE, String.valueOf(people));
        row.put(ActivityProfiles.EVENTS, "5");
        row.put(ActivityProfiles.FIRST, first.toString());
        row.put(ActivityProfiles.LAST, first.plusSeconds(3_600).toString());
        return row;
    }

    private static Map<String, String> holder(final String value, final String person, final Instant first) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(AggregateQuery.groupColumn(0), value);
        row.put(AggregateQuery.groupColumn(1), person);
        row.put(ActivityProfiles.FIRST, first.toString());
        row.put(ActivityProfiles.LAST, first.plusSeconds(60).toString());
        row.put(ActivityProfiles.EVENTS, "2");
        return row;
    }
}
