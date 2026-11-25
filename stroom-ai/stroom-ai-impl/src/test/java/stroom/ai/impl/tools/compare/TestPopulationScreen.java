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
import stroom.ai.impl.tools.compare.ActivityScoring.Kind;
import stroom.ai.impl.tools.compare.ActivityScoring.Spec;
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.ai.impl.tools.compare.PopulationScreen.Ranking;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class TestPopulationScreen {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 6, 1);

    @Test
    void peopleRankByTheirThreeLargestDifferentFeatures() {
        final Map<String, List<Finding>> findings = new LinkedHashMap<>();
        // One feature found in each unit counts once: 40, not 120.
        findings.put("one-thing", List.of(week("events", 40), month("events", 40), finding(Kind.DAY,
                ActivityScoring.FIRST, "2026-06-10", 9, 1, 3)));
        findings.put("three-things", List.of(week("events", 20), week("sum:bytes", 20), week("distinct:host", 20),
                week("new:host", 20)));
        findings.put("nothing", List.of());

        final Ranking ranking = PopulationScreen.ranked(findings, WINDOW_FROM);

        assertThat(ranking.ranked()).extracting(PopulationScreen.Ranked::person)
                .containsExactly("three-things", "one-thing");
        assertThat(ranking.ranked().getFirst().score()).isEqualTo(60);
        assertThat(ranking.ranked().get(1).score()).isEqualTo(43);
        assertThat(ranking.ranked().getFirst().findings()).hasSize(4);
    }

    @Test
    void aFindingManyShareOverOverlappingDaysIsThePopulations() {
        final Map<String, List<Finding>> findings = new LinkedHashMap<>();
        // 4 of 12 (at least a quarter, and 3) with events up over overlapping weeks: the population's.
        for (int i = 0; i < 4; i++) {
            findings.put("p" + i, List.of(finding(Kind.WEEK, "events", "2026-06-0" + (1 + i) + " to 2026-06-20",
                    10, 30, 8)));
        }
        // Another with the same feature in other weeks: theirs.
        findings.put("later", List.of(finding(Kind.WEEK, "events", "2026-09-01 to 2026-09-07", 10, 30, 8)));
        for (int i = 0; i < 7; i++) {
            findings.put("q" + i, List.of());
        }

        final Ranking ranking = PopulationScreen.ranked(findings, WINDOW_FROM);

        assertThat(ranking.ranked()).extracting(PopulationScreen.Ranked::person).containsExactly("later");
        assertThat(ranking.common()).containsEntry("S|week|events", 4);
    }

    @Test
    void justUnderTheShareIsStillAPersonsOwn() {
        final Map<String, List<Finding>> findings = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            findings.put("p" + i, List.of(week("events", 8)));
        }
        for (int i = 0; i < 10; i++) {
            findings.put("q" + i, List.of());
        }

        // 3 of 13 is under a quarter (4).
        assertThat(PopulationScreen.ranked(findings, WINDOW_FROM).ranked()).hasSize(3);
    }

    @Test
    void whatIsntRankedIsLeftOut() {
        final Map<String, List<Finding>> findings = new LinkedHashMap<>();
        findings.put("p", List.of(
                // The source's.
                new Finding("S", "events", "2026-06-15 to 2026-06-21", 10, 30, 9, "e", "a", Kind.WEEK, true),
                // An absence.
                finding(Kind.WEEK, "events", "2026-07-06 to 2026-07-12", 50, 0, 9),
                // A day's count.
                finding(Kind.DAY, "events", "2026-07-08", 50, 200, 9),
                // A category they already used.
                finding(Kind.WEEK, "category:Business", "2026-07-06 to 2026-07-12", 5, 15, 9),
                // A lasting change before the window, and the drift that is its step.
                finding(Kind.CHANGE, "distinct:host", "2026-03-02", 1, 3, 9),
                finding(Kind.DRIFT, "distinct:host", "2025-11 to 2026-10", 1, 3, 9),
                // Ranked: a night, a category new to them, a change in the window.
                finding(Kind.DAY, ActivityScoring.LAST, "2026-07-09", 1_000, 1_400, 7),
                finding(Kind.WEEK, "category:Job Search", "2026-07-06 to 2026-07-12", 0, 4, 6),
                finding(Kind.CHANGE, "events", "2026-08-01", 50, 90, 5)));

        final Ranking ranking = PopulationScreen.ranked(findings, WINDOW_FROM);

        assertThat(ranking.ranked()).singleElement().satisfies(person -> {
            assertThat(person.findings()).extracting(Finding::feature)
                    .containsExactly(ActivityScoring.LAST, "category:Job Search", "events");
            assertThat(person.score()).isEqualTo(18);
        });
        assertThat(ranking.absent()).isEqualTo(1);
        assertThat(ranking.earlier()).isEqualTo(2);
    }

    @Test
    void aPersonIsRebaselinedFromTheirLatestLastingChangeMonthsBeforeTheWindow() {
        final Spec spec = new Spec(LocalDate.of(2025, 11, 1), WINDOW_FROM, WINDOW_FROM, LocalDate.of(2026, 11, 1),
                Unit.WEEK, List.of(), ZONE);

        assertThat(PopulationScreen.rebaseline(spec, List.of(
                finding(Kind.CHANGE, ActivityScoring.FIRST, "2026-01-12", 600, 480, 20),
                finding(Kind.CHANGE, "events", "2026-03-02", 10, 30, 9)))).contains(LocalDate.of(2026, 3, 2));
        // Too near the baseline's start, too near the window, the source's, or not a change: none.
        assertThat(PopulationScreen.rebaseline(spec, List.of(
                finding(Kind.CHANGE, "events", "2025-11-20", 10, 30, 9),
                finding(Kind.CHANGE, "events", "2026-04-20", 10, 30, 9),
                new Finding("S", "events", "2026-02-02", 10, 30, 9, "e", "a", Kind.CHANGE, true),
                finding(Kind.WEEK, "events", "2026-02-02 to 2026-02-08", 10, 30, 9)))).isEmpty();
        assertThat(PopulationScreen.from(spec, LocalDate.of(2026, 3, 2)).baselineFrom())
                .isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void aFindingIsCheckedAgainstEveryoneElses() {
        final Spec spec = new Spec(LocalDate.of(2026, 1, 5), WINDOW_FROM, WINDOW_FROM, LocalDate.of(2026, 7, 6),
                Unit.WEEK, List.of(), ZONE);
        final PopulationScreen.Everyone everyone = new PopulationScreen.Everyone();
        final Map<String, Map<LocalDate, Double>> riser = features(spec, 5, 15);
        everyone.add(riser);
        // Everyone else more than doubles too: at least half as far as the riser's tripling, so the population's.
        for (int i = 0; i < 5; i++) {
            everyone.add(features(spec, 5, 11));
        }
        final Finding week = finding(Kind.WEEK, "events", "2026-06-01 to 2026-06-07", 5, 15, 9);

        final Finding checked = everyone.checked(week, riser, spec);

        assertThat(checked.sourceWide()).isTrue();
        assertThat(checked.alternative()).startsWith("Everyone else's events per person moved as far");
        // A time of day is the person's own.
        assertThat(everyone.checked(finding(Kind.WEEK, ActivityScoring.FIRST, "2026-06-01 to 2026-06-07", 500,
                700, 9), riser, spec).sourceWide()).isFalse();
    }

    @Test
    void aRiseEveryoneElseDoesntShareIsTheirOwn() {
        final Spec spec = new Spec(LocalDate.of(2026, 1, 5), WINDOW_FROM, WINDOW_FROM, LocalDate.of(2026, 7, 6),
                Unit.WEEK, List.of(), ZONE);
        final PopulationScreen.Everyone everyone = new PopulationScreen.Everyone();
        final Map<String, Map<LocalDate, Double>> riser = features(spec, 5, 15);
        everyone.add(riser);
        for (int i = 0; i < 5; i++) {
            everyone.add(features(spec, 5, 5));
        }

        assertThat(everyone.checked(finding(Kind.WEEK, "events", "2026-06-01 to 2026-06-07", 5, 15, 9), riser,
                spec).sourceWide()).isFalse();
        assertThat(everyone.checked(finding(Kind.CHANGE, "events", "2026-06-01", 5, 15, 9), riser, spec)
                .sourceWide()).isFalse();
    }

    @Test
    void aQueryIsCutIntoWeeksFromMidnights() {
        final Instant from = LocalDate.of(2026, 6, 1).atStartOfDay(ZONE).toInstant();
        final Instant to = LocalDate.of(2026, 6, 20).atStartOfDay(ZONE).toInstant();
        final AggregateQuery query = new AggregateQuery(CompareFixtures.SOURCE, Map.of(), from, to,
                AggregateQuery.Bucket.HOUR, List.of("user.id"), List.of(), 10);

        final List<AggregateQuery> slices = PopulationScreen.sliced(query, ZONE, 7);

        assertThat(slices).extracting(AggregateQuery::from).containsExactly(from,
                LocalDate.of(2026, 6, 8).atStartOfDay(ZONE).toInstant(),
                LocalDate.of(2026, 6, 15).atStartOfDay(ZONE).toInstant());
        assertThat(slices.getLast().to()).isEqualTo(to);
    }

    @Test
    void anAggregateOfEveryoneIsSplitAPersonAsTheirOwnWouldBe() {
        final Table table = new Table(List.of(
                row("2026-06-01", "p1", "Webmail", "3"),
                row("2026-06-02", "p2", "Job Search", "4"),
                row("2026-06-02", "p1", "other", "9"),
                row("2026-06-03", "", "Webmail", "1")), true, 2, List.of("n"), "q");

        final Map<String, Table> byPerson = PopulationScreen.byPerson(table);

        assertThat(byPerson).containsOnlyKeys("p1", "p2");
        assertThat(byPerson.get("p1").rows().getFirst()).containsEntry(AggregateQuery.groupColumn(0), "Webmail")
                .doesNotContainKey(AggregateQuery.groupColumn(1));
        assertThat(byPerson.get("p1").cutShort()).isTrue();
        assertThat(PopulationScreen.categories(byPerson.get("p1"))).containsOnlyKeys("category:Webmail");
        assertThat(PopulationScreen.categories(null)).isEmpty();
    }

    @Test
    void aValueSeenInSeveralSlicesIsMerged() {
        final Table table = new Table(List.of(
                seen("a", "2026-06-02T00:00:00Z", "2026-06-03T00:00:00Z", "2"),
                seen("a", "2026-05-01T00:00:00Z", "2026-05-02T00:00:00Z", "3"),
                seen("b", "2026-06-02T00:00:00Z", "2026-06-02T00:00:00Z", "1")), false, 2, List.of(), "q");

        assertThat(PopulationScreen.seen(table)).satisfiesExactly(
                a -> {
                    assertThat(a.value()).isEqualTo("a");
                    assertThat(a.first()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
                    assertThat(a.last()).isEqualTo(Instant.parse("2026-06-03T00:00:00Z"));
                    assertThat(a.events()).isEqualTo(5);
                },
                b -> assertThat(b.value()).isEqualTo("b"));
    }

    @Test
    void peersAreTheOthersOfTheirGroupFromEveryonesDailyAggregate() {
        final Map<String, Table> daily = new LinkedHashMap<>();
        daily.put("p1", new Table(List.of(Map.of(AggregateQuery.BUCKET_COLUMN, "2026-06-01",
                ActivityProfiles.EVENTS, "5")), false, 1, List.of(), "q"));
        daily.put("p2", new Table(List.of(Map.of(AggregateQuery.BUCKET_COLUMN, "2026-06-01",
                ActivityProfiles.EVENTS, "7")), false, 1, List.of(), "q"));
        daily.put("p3", new Table(List.of(), false, 1, List.of(), "q"));
        final Map<String, String> groups = new TreeMap<>(Map.of("p1", "Finance", "p2", "Finance", "p3", "Sales"));

        final Map<String, ActivityScoring.PeerData> peers = PopulationScreen.peers(groups, daily, "Organisation");

        assertThat(peers).containsOnlyKeys("p1", "p2");
        assertThat(peers.get("p1").group()).isEqualTo("Organisation 'Finance'");
        assertThat(peers.get("p1").days().get(LocalDate.of(2026, 6, 1))).containsExactly(Map.entry("p2", 7.0));
    }

    private static Map<String, Map<LocalDate, Double>> features(final Spec spec,
                                                                final double before,
                                                                final double after) {
        final Map<LocalDate, Double> events = new TreeMap<>();
        for (LocalDate date = spec.baselineFrom(); date.isBefore(spec.windowTo()); date = date.plusDays(1)) {
            // A little spread, so the baseline has some.
            events.put(date, (date.isBefore(spec.windowFrom())
                    ? before
                    : after) + (date.getDayOfMonth() % 3) * 0.1);
        }
        return Map.of("events", events);
    }

    private static Finding week(final String feature, final double score) {
        return finding(Kind.WEEK, feature, "2026-06-08 to 2026-06-14", 10, 30, score);
    }

    private static Finding month(final String feature, final double score) {
        return finding(Kind.MONTH, feature, "2026-06-01 to 2026-06-30", 10, 30, score);
    }

    private static Finding finding(final Kind kind,
                                   final String feature,
                                   final String window,
                                   final double baseline,
                                   final double value,
                                   final double score) {
        return new Finding("S", feature, window, baseline, value, score, feature + " " + window, "another", kind);
    }

    private static Map<String, String> row(final String day, final String person, final String category,
                                           final String events) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(AggregateQuery.BUCKET_COLUMN, day);
        row.put(AggregateQuery.groupColumn(0), person);
        row.put(AggregateQuery.groupColumn(1), category);
        row.put(ActivityProfiles.EVENTS, events);
        return row;
    }

    private static Map<String, String> seen(final String value, final String first, final String last,
                                            final String events) {
        final Map<String, String> row = new LinkedHashMap<>();
        row.put(AggregateQuery.groupColumn(0), value);
        row.put(ActivityProfiles.FIRST, first);
        row.put(ActivityProfiles.LAST, last);
        row.put(ActivityProfiles.EVENTS, events);
        return row;
    }
}
