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

import stroom.ai.impl.tools.compare.ActivityProfiles.Fields;
import stroom.ai.impl.tools.compare.ActivityScoring.Finding;
import stroom.ai.impl.tools.compare.ActivityScoring.Kind;
import stroom.ai.impl.tools.compare.ActivityScoring.Scored;
import stroom.ai.impl.tools.compare.ActivityScoring.SourceData;
import stroom.ai.impl.tools.compare.ActivityScoring.Spec;
import stroom.ai.impl.tools.compare.ActivityScoring.Status;
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.ActivityScoring.Values;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestActivityScoring {

    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    // Monday 5 January 2026 to Monday 4 May (17 weeks), then a 4-week window.
    private static final LocalDate BASE_FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 5, 4);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 6, 1);
    private static final LocalDate STEP = LocalDate.of(2026, 5, 11);
    private static final Fields NO_FIELDS = new Fields(List.of(), List.of());

    @Test
    void aStepUpIsFoundAtItsDate() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                ? 5
                : 20, date -> Map.of());
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                WINDOW_TO, List.of()));

        final Finding change = only(scored, Kind.CHANGE, ActivityScoring.EVENTS);
        assertThat(change.window()).isEqualTo(STEP.toString());
        // Weekly means a day, the weekends' zeros in them: 40 and 160 on weekdays.
        assertThat(change.baseline()).isCloseTo(40 * 5 / 7.0, within(1e-9));
        assertThat(change.value()).isCloseTo(160 * 5 / 7.0, within(1e-9));
        assertThat(change.alternative()).doesNotContain("Everyone else");
        assertThat(change.sourceWide()).isFalse();
        assertThat(scored.findings()).anySatisfy(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.WEEK);
            assertThat(finding.feature()).isEqualTo(ActivityScoring.EVENTS);
            // The three weeks from the step are one finding.
            assertThat(finding.window()).isEqualTo("2026-05-11 to 2026-05-31");
            assertThat(finding.evidence()).startsWith("In 3 weeks in a row from 2026-05-11 to 2026-05-31; the "
                                                      + "largest: ").contains("in the week from 2026-05-11");
        });
        // The week before the step isn't flagged.
        assertThat(scored.findings()).noneSatisfy(finding -> assertThat(finding.window())
                .isEqualTo("2026-05-04 to 2026-05-10"));
    }

    @Test
    void aStepWhereAFeedJoinedTheSourceIsNoChangePointOfThePersons() {
        // The person's events step up the day a second feed (their laptop's) joined the source.
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                ? 5
                : 20, date -> Map.of());
        final ActivityScoring.SourceData data = fixtures.data("p1", NO_FIELDS, BASE_FROM, WINDOW_TO, List.of())
                .withFeeds(List.of(new ActivityScoring.FeedSpan("DESKTOP-EVENTS", BASE_FROM, WINDOW_TO.minusDays(1)),
                        new ActivityScoring.FeedSpan("LAPTOP-EVENTS", STEP.minusDays(2), WINDOW_TO.minusDays(1))));

        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), data);

        assertThat(scored.findings()).noneSatisfy(finding -> assertThat(finding.kind()).isEqualTo(Kind.CHANGE));
        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains("not scored, as feed LAPTOP-EVENTS "
                                                                                + "joined the source on 2026-05-09"));
        // A feed there from the start, or one ending with the window, moves nothing.
        assertThat(ActivityScoring.feedEdge(spec(Unit.WEEK), data, BASE_FROM.plusDays(3))).isEmpty();
        assertThat(ActivityScoring.feedEdge(spec(Unit.WEEK), data, STEP.plusDays(30))).isEmpty();
    }

    @Test
    void aStepWhereAFeedLeftTheSourceIsNoChangePointEither() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final ActivityScoring.SourceData data = fixtures.data("p1", NO_FIELDS, BASE_FROM, WINDOW_TO, List.of())
                .withFeeds(List.of(new ActivityScoring.FeedSpan("OLD-EVENTS", BASE_FROM, STEP.minusDays(1))));

        assertThat(ActivityScoring.feedEdge(spec(Unit.WEEK), data, STEP.plusDays(2))).contains(
                "feed OLD-EVENTS left the source after 2026-05-10");
        assertThat(ActivityScoring.feedEdge(spec(Unit.WEEK), data, STEP.plusDays(20))).isEmpty();
    }

    @Test
    void aStepWhereThePersonsFieldStartsBeingHeldIsNoChangePointOfThePersons() {
        // The person id is held only from a reprocessed range's start: before it, no one's events match by it.
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> 20, date -> Map.of());
        fixtures.dropFieldBefore(CompareFixtures.PERSON_FIELD, STEP);

        final ActivityScoring.SourceData data = fixtures.data("p1", NO_FIELDS, BASE_FROM, WINDOW_TO, List.of());
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), data);

        assertThat(scored.findings()).noneSatisfy(finding -> assertThat(finding.kind()).isEqualTo(Kind.CHANGE));
        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains(
                "user.id is held only from 2026-05-11 of the source's days (2026-01-05 to 2026-05-31)",
                "the person's events outside it can't be matched by it"));
        // The person's field is read by every feature.
        assertThat(ActivityScoring.coverageEdge(spec(Unit.WEEK), data, ActivityScoring.SUM + "bytes",
                STEP.plusDays(2))).hasValueSatisfying(edge -> assertThat(edge).isEqualTo(
                "field user.id is held from 2026-05-11 (no event before holds it)"));
        assertThat(ActivityScoring.coverageEdge(spec(Unit.WEEK), data, ActivityScoring.EVENTS, STEP.plusDays(30)))
                .isEmpty();
    }

    @Test
    void aBreadthFieldStartingBeingHeldStopsOnlyItsOwnChangePoints() {
        // Destinations are held only from the step, when the person's events also quadruple.
        final Fields fields = new Fields(List.of(), List.of(CompareFixtures.DESTINATION_FIELD));
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        for (LocalDate date = BASE_FROM; date.isBefore(WINDOW_TO); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() <= 5) {
                for (int hour = 9; hour < 17; hour++) {
                    fixtures.hours("p1", date, hour, hour + 1, date.isBefore(STEP)
                            ? 5
                            : 20, Map.of(CompareFixtures.DESTINATION_FIELD, "site" + hour));
                }
            }
        }
        fixtures.dropFieldBefore(CompareFixtures.DESTINATION_FIELD, STEP);

        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", fields, BASE_FROM,
                WINDOW_TO, List.of()));

        assertThat(only(scored, Kind.CHANGE, ActivityScoring.EVENTS).window()).isEqualTo(STEP.toString());
        assertThat(scored.findings()).noneSatisfy(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.CHANGE);
            assertThat(finding.feature()).endsWith(CompareFixtures.DESTINATION_FIELD);
        });
        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains(
                "destination.domain is held only from 2026-05-11"));
    }

    @Test
    void aFieldHeldFromMidPeriodIsNoWeekFindingRisingFromNothing() {
        // Decided by the user (2026-11-25): destinations held only from the step, the person's the same 8 sites a
        // day throughout; before the step no one's events hold the field, so "distinct destinations rising from 0"
        // is no finding: its weeks are scored only over the days it is held.
        final Fields fields = new Fields(List.of(), List.of(CompareFixtures.DESTINATION_FIELD));
        final CompareFixtures fixtures = siteWorkdays(date -> 8);
        fixtures.dropFieldBefore(CompareFixtures.DESTINATION_FIELD, STEP);

        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", fields, BASE_FROM,
                WINDOW_TO, List.of()));

        assertThat(scored.findings()).noneSatisfy(finding ->
                assertThat(finding.feature()).endsWith(CompareFixtures.DESTINATION_FIELD));
        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains(
                "destination.domain is held only from 2026-05-11",
                "its week findings are scored only over the days it is held"));
    }

    @Test
    void aFieldHeldFromWithinTheBaselineIsScoredOverItsOwnDays() {
        // Held from 2 March: the baseline's 9 weeks from then hold 8 sites a day, the window's from the step 16.
        final Fields fields = new Fields(List.of(), List.of(CompareFixtures.DESTINATION_FIELD));
        final LocalDate held = LocalDate.of(2026, 3, 2);
        final CompareFixtures fixtures = siteWorkdays(date -> date.isBefore(STEP)
                ? 8
                : 16);
        fixtures.dropFieldBefore(CompareFixtures.DESTINATION_FIELD, held);

        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", fields, BASE_FROM,
                WINDOW_TO, List.of()));

        // Against the held weeks' 8, not the weeks of nothing before them.
        final Finding rise = only(scored, Kind.WEEK, ActivityScoring.DISTINCT + CompareFixtures.DESTINATION_FIELD);
        assertThat(rise.window()).isEqualTo("2026-05-11 to 2026-05-31");
        assertThat(rise.baseline()).isCloseTo(8 * 5 / 7.0, within(1e-9));
        assertThat(rise.evidence()).contains("over 9 baseline weeks");
        // Held from within the baseline, unlike the person's own field (held throughout): a note says from when.
        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains(
                "destination.domain is held only from 2026-03-02"));
        // A feature reading no partly held field is scored over every day, as before.
        assertThat(ActivityScoring.heldDays(spec(Unit.WEEK), fixtures.data("p1", fields, BASE_FROM, WINDOW_TO,
                List.of()), ActivityScoring.EVENTS)).isEmpty();
    }

    @Test
    void aFieldThatStopsBeingHeldIsABoundaryToo() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final ActivityScoring.SourceData data = fixtures.data("p1", NO_FIELDS, BASE_FROM, WINDOW_TO, List.of())
                .withFieldSpans(List.of(new ActivityScoring.FieldSpan(CompareFixtures.PERSON_FIELD, BASE_FROM,
                        STEP.minusDays(1), true)));

        assertThat(ActivityScoring.coverageEdge(spec(Unit.WEEK), data, ActivityScoring.EVENTS, STEP.plusDays(2)))
                .hasValueSatisfying(edge -> assertThat(edge).startsWith("field user.id is held until 2026-05-10"));
        // A sum or breadth field's own stop is no boundary for the other features.
        assertThat(ActivityScoring.coverageEdge(spec(Unit.WEEK), data.withFieldSpans(List.of(
                new ActivityScoring.FieldSpan("bytes", BASE_FROM, STEP.minusDays(1), false))),
                ActivityScoring.EVENTS, STEP.plusDays(2))).isEmpty();
        assertThat(ActivityScoring.coverageEdge(spec(Unit.WEEK), data.withFieldSpans(List.of(
                new ActivityScoring.FieldSpan("bytes", BASE_FROM, STEP.minusDays(1), false))),
                ActivityScoring.SUM + "bytes", STEP.plusDays(2))).isPresent();
        assertThat(ActivityScoring.coverageEdge(spec(Unit.WEEK), data, ActivityScoring.EVENTS, STEP.plusDays(20)))
                .isEmpty();
        // A field held throughout the source's days is no boundary.
        assertThat(ActivityScoring.coverageEdge(spec(Unit.WEEK), data.withFieldSpans(List.of(
                new ActivityScoring.FieldSpan(CompareFixtures.PERSON_FIELD, BASE_FROM, WINDOW_TO.minusDays(1),
                        true))), ActivityScoring.EVENTS, BASE_FROM.plusDays(2))).isEmpty();
    }

    @Test
    void aStepEveryoneSharesIsTheSources() {
        final CompareFixtures fixtures = new CompareFixtures(ZONE);
        // A pipeline change: everyone's events quadruple from the same day.
        for (int i = 0; i < 10; i++) {
            fixtures.workdays("other" + i, BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                    ? 5
                    : 20, date -> Map.of());
        }
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                ? 5
                : 20, date -> Map.of());
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                WINDOW_TO, List.of()));

        final Finding change = only(scored, Kind.CHANGE, ActivityScoring.EVENTS);
        assertThat(change.alternative()).contains("Everyone else's events per person shifted the same way");
        // Listed apart, as the source's.
        assertThat(change.sourceWide()).isTrue();
        assertThat(scored.findings()).filteredOn(finding -> finding.kind() == Kind.WEEK)
                .isNotEmpty()
                .allSatisfy(finding -> {
                    assertThat(finding.alternative()).contains("Everyone else");
                    assertThat(finding.sourceWide()).isTrue();
                });
    }

    @Test
    void aStepFarBeyondEveryoneElsesIsThePersons() {
        // Everyone's events double from the step and the person's go up eightfold: the step is still the person's.
        final CompareFixtures fixtures = new CompareFixtures(ZONE);
        for (int i = 0; i < 10; i++) {
            fixtures.workdays("other" + i, BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                    ? 5
                    : 10, date -> Map.of());
        }
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                ? 5
                : 40, date -> Map.of());
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                WINDOW_TO, List.of()));

        assertThat(scored.findings()).filteredOn(finding -> finding.feature().equals(ActivityScoring.EVENTS))
                .isNotEmpty()
                .allSatisfy(finding -> assertThat(finding.sourceWide()).isFalse());
    }

    @Test
    void aCategoryIsNeverTheSourcesAndNothingDriftsFromNothing() {
        // Everyone's volume doubles from the step (a pipeline change); the person starts using job sites then.
        final CompareFixtures fixtures = new CompareFixtures(ZONE);
        for (int i = 0; i < 10; i++) {
            fixtures.workdays("other" + i, BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                    ? 5
                    : 10, date -> Map.of());
        }
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(STEP)
                ? 5
                : 10, date -> Map.of());
        final SortedMap<LocalDate, Double> jobs = new TreeMap<>();
        final SortedMap<LocalDate, Double> none = new TreeMap<>();
        for (LocalDate date = STEP; date.isBefore(WINDOW_TO); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() <= 5) {
                jobs.put(date, 6.0);
            }
        }
        final SourceData data = fixtures.data("p1", NO_FIELDS, BASE_FROM, WINDOW_TO, List.of()).withExtra(Map.of(
                ActivityScoring.CATEGORY + "job search", jobs, ActivityScoring.CATEGORY + "webmail", none));
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), data);

        assertThat(scored.findings()).filteredOn(finding -> finding.feature().equals(ActivityScoring.CATEGORY
                                                                                     + "job search"))
                .isNotEmpty()
                .allSatisfy(finding -> assertThat(finding.sourceWide()).isFalse());
        assertThat(scored.findings()).noneSatisfy(finding -> assertThat(finding.feature())
                .isEqualTo(ActivityScoring.CATEGORY + "webmail"));
        assertThat(scored.findings()).allSatisfy(finding -> assertThat(finding.score())
                .isBetween(0.0, ActivityScoring.MAX_Z));
    }

    @Test
    void leaveIsNotScoredAndAQuietRunIsReported() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final LocalDate leaveFrom = LocalDate.of(2026, 3, 2);
        final LocalDate leaveTo = LocalDate.of(2026, 3, 13);
        final LocalDate quietFrom = LocalDate.of(2026, 5, 18);
        final LocalDate quietTo = LocalDate.of(2026, 5, 29);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of());
        // Remove the person's events on leave and in the quiet run.
        fixtures.aggregates().source(CompareFixtures.PROXY.getUuid()).removeIf(event -> {
            final LocalDate date = ((Instant) event.get(CompareFixtures.TIME_FIELD)).atZone(ZONE)
                    .toLocalDate();
            return "p1".equals(event.get(CompareFixtures.PERSON_FIELD))
                   && (!date.isBefore(leaveFrom) && !date.isAfter(leaveTo)
                       || !date.isBefore(quietFrom) && !date.isAfter(quietTo));
        });
        final Spec spec = new Spec(BASE_FROM, WINDOW_FROM, WINDOW_FROM, WINDOW_TO, Unit.DAY,
                List.<LocalDate[]>of(new LocalDate[]{leaveFrom, leaveTo}), ZONE);
        final Scored scored = ActivityScoring.score(spec, fixtures.data("p1", NO_FIELDS, BASE_FROM, WINDOW_TO,
                List.of()));

        assertThat(scored.status().get(LocalDate.of(2026, 3, 4))).isEqualTo(Status.LEAVE);
        assertThat(scored.status().get(LocalDate.of(2026, 5, 20))).isEqualTo(Status.QUIET_RUN);
        assertThat(scored.features().get(ActivityScoring.EVENTS)).doesNotContainKey(LocalDate.of(2026, 5, 20));
        // No low day is flagged in the quiet run: it isn't scored.
        assertThat(scored.findings()).filteredOn(finding -> finding.kind() == Kind.DAY)
                .noneSatisfy(finding -> assertThat(finding.window()).startsWith("2026-05-2"));
        assertThat(scored.months()).anySatisfy(line -> {
            assertThat(line.month().toString()).isEqualTo("2026-03");
            assertThat(line.leave()).isEqualTo(12);
        });
    }

    @Test
    void daysWithNoDataInTheSourceAreNotScored() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of());
        // Retention: nothing at all in February.
        fixtures.aggregates().source(CompareFixtures.PROXY.getUuid()).removeIf(event -> ((Instant) event
                .get(CompareFixtures.TIME_FIELD)).atZone(ZONE).getMonthValue() == 2);
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                WINDOW_TO, List.of()));

        assertThat(scored.status().get(LocalDate.of(2026, 2, 10))).isEqualTo(Status.NO_SOURCE_DATA);
        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains("no data in the source at all"));
        assertThat(scored.months()).anySatisfy(line -> {
            assertThat(line.month().toString()).isEqualTo("2026-02");
            assertThat(line.covered()).isZero();
            assertThat(CompareResult.monthLine(line)).contains("no data in the source (not scored)");
        });
        // Nothing flagged for it: the gap isn't the person's.
        assertThat(scored.findings()).isEmpty();
    }

    @Test
    void tooFewBaselineMonthsAreNotScored() {
        final CompareFixtures fixtures = background(ZONE, LocalDate.of(2026, 3, 2), WINDOW_TO, 1.0);
        fixtures.workdays("p1", LocalDate.of(2026, 3, 2), WINDOW_TO, 9, 17, date -> 5, date -> Map.of());
        final Spec spec = new Spec(LocalDate.of(2026, 3, 2), WINDOW_FROM, WINDOW_FROM, WINDOW_TO, Unit.MONTH,
                List.of(), ZONE);
        final Scored scored = ActivityScoring.score(spec, fixtures.data("p1", NO_FIELDS, LocalDate.of(2026, 3, 2),
                WINDOW_TO, List.of()));

        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains("Fewer than 5 baseline months"));
        assertThat(scored.findings()).noneSatisfy(finding -> assertThat(finding.kind()).isEqualTo(Kind.MONTH));
    }

    @Test
    void nothingMatched() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("nobody", NO_FIELDS, BASE_FROM,
                WINDOW_TO, List.of()));

        assertThat(scored.findings()).isEmpty();
        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains("Nothing matched by person id"));
    }

    @Test
    void anEmptySourceAndAOneDayPeriod() {
        final CompareFixtures fixtures = new CompareFixtures(ZoneOffset.UTC);
        final LocalDate day = LocalDate.of(2026, 5, 4);
        final Spec spec = new Spec(day.minusDays(1), day, day, day.plusDays(1), Unit.DAY, List.of(),
                ZoneOffset.UTC);
        final Scored scored = ActivityScoring.score(spec, fixtures.data("p1", NO_FIELDS, day.minusDays(1),
                day.plusDays(1), List.of()));

        assertThat(scored.findings()).isEmpty();
        assertThat(scored.status()).containsOnlyKeys(day.minusDays(1), day);
        assertThat(scored.status().values()).containsOnly(Status.NO_SOURCE_DATA);
    }

    @Test
    void newValuesAreCountedAndListed() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final List<String> usual = List.of("intranet.example", "mail.example", "wiki.example");
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> 3, date -> Map.of(
                CompareFixtures.DESTINATION_FIELD, usual.get(date.getDayOfMonth() % 3)));
        // From the step, three new destinations a day.
        for (LocalDate date = STEP; date.isBefore(WINDOW_TO); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() <= 5) {
                for (int i = 0; i < 3; i++) {
                    fixtures.hours("p1", date, 12, 13, 1, Map.of(CompareFixtures.DESTINATION_FIELD,
                            "new-" + date + "-" + i + ".example"));
                }
            }
        }
        final Fields fields = new Fields(List.of(), List.of(CompareFixtures.DESTINATION_FIELD));
        final SourceData data = fixtures.data("p1", fields, BASE_FROM, WINDOW_TO, List.of());
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), data);

        final Values values = scored.values().getFirst();
        assertThat(values.field()).isEqualTo(CompareFixtures.DESTINATION_FIELD);
        assertThat(values.started()).isEqualTo(45);
        assertThat(values.stopped()).isZero();
        assertThat(values.total()).isEqualTo(48);
        assertThat(values.startedNames()).hasSize(45).contains("new-2026-05-11-0.example");
        assertThat(scored.findings()).anySatisfy(finding -> {
            assertThat(finding.feature()).isEqualTo(ActivityScoring.NOVEL + CompareFixtures.DESTINATION_FIELD);
            assertThat(finding.kind()).isEqualTo(Kind.WEEK);
        });
    }

    @Test
    void theFirstWeeksValuesAreNotNew() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> 1, date -> Map.of(
                CompareFixtures.DESTINATION_FIELD, "d" + date.getDayOfWeek().getValue()));
        final Fields fields = new Fields(List.of(), List.of(CompareFixtures.DESTINATION_FIELD));
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", fields, BASE_FROM,
                WINDOW_TO, List.of()));

        assertThat(scored.features().get(ActivityScoring.NOVEL + CompareFixtures.DESTINATION_FIELD).values())
                .containsOnly(0.0);
    }

    @Test
    void aRhythmMovedToTheNightIsFound() {
        final LocalDate from = LocalDate.of(2025, 11, 3);
        final CompareFixtures fixtures = background(ZONE, from, WINDOW_TO, 1.0);
        fixtures.workdays("p1", from, WINDOW_TO, 9, 17, date -> date.isBefore(LocalDate.of(2026, 5, 1))
                ? 3
                : 0, date -> Map.of());
        fixtures.workdays("p1", LocalDate.of(2026, 5, 1), WINDOW_TO, 21, 26, date -> 3, date -> Map.of());
        final Spec spec = new Spec(from, WINDOW_FROM, WINDOW_FROM, WINDOW_TO, Unit.WEEK, List.of(), ZONE);
        final Scored scored = ActivityScoring.score(spec, fixtures.data("p1", NO_FIELDS, from, WINDOW_TO,
                List.of()));

        final Finding rhythm = only(scored, Kind.RHYTHM, ActivityScoring.RHYTHM);
        assertThat(rhythm.window()).isEqualTo("2026-05");
        assertThat(rhythm.value()).isGreaterThan(0.9);
        assertThat(rhythm.evidence()).contains("its busiest hours").contains("the baseline's: ");
        assertThat(scored.findings()).anySatisfy(finding -> assertThat(finding.feature())
                .isEqualTo(ActivityScoring.FIRST));
    }

    @Test
    void aDriftToLaterHoursIsFound() {
        final LocalDate from = LocalDate.of(2025, 12, 1);
        final CompareFixtures fixtures = background(ZONE, from, WINDOW_TO, 1.0);
        // Starting 30 minutes later each month: 08:00 in December, 10:30 by May.
        for (LocalDate date = from; date.isBefore(WINDOW_TO); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() <= 5) {
                final int months = (int) ChronoUnit.MONTHS.between(from.withDayOfMonth(1), date.withDayOfMonth(1));
                final int startMinutes = 8 * 60 + 30 * months;
                fixtures.minutes("p1", date, startMinutes, startMinutes + 8 * 60, 20);
            }
        }
        final Spec spec = new Spec(from, WINDOW_FROM, WINDOW_FROM, WINDOW_TO, Unit.WEEK, List.of(), ZONE);
        final Scored scored = ActivityScoring.score(spec, fixtures.data("p1", NO_FIELDS, from, WINDOW_TO,
                List.of()));

        final Finding drift = only(scored, Kind.DRIFT, ActivityScoring.FIRST);
        assertThat(drift.evidence()).contains("drifted by +30 minutes a month over 5 months");
        assertThat(drift.baseline()).isEqualTo(8 * 60 + 30.0);
    }

    @Test
    void peersParting() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final List<String> peers = IntStream.range(0, 6).mapToObj(i -> "peer" + i).toList();
        peers.forEach(peer -> fixtures.workdays(peer, BASE_FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of()));
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> date.isBefore(WINDOW_FROM)
                ? 5
                : 25, date -> Map.of());
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                WINDOW_TO, peers));

        assertThat(scored.findings()).filteredOn(finding -> finding.kind() == Kind.PEERS)
                .anySatisfy(finding -> assertThat(finding.evidence()).contains("times the median of 6 peers in "
                                                                               + "Organisation 'Finance'"))
                .anySatisfy(finding -> assertThat(finding.evidence()).contains("events per day in the window"));
        // The peers' median a scored day in May: 40 on its 21 weekdays, 0 at weekends.
        assertThat(scored.months().getLast().peerMedian()).isCloseTo(40 * 21 / 31.0, within(1e-9));
    }

    @Test
    void tooFewPeers() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final List<String> peers = List.of("peer0", "peer1");
        peers.forEach(peer -> fixtures.workdays(peer, BASE_FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of()));
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of());
        final Scored scored = ActivityScoring.score(spec(Unit.WEEK), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                WINDOW_TO, peers));

        assertThat(scored.notes()).anySatisfy(note -> assertThat(note).contains("Only 2 peer(s)"));
        assertThat(scored.findings()).noneSatisfy(finding -> assertThat(finding.kind()).isEqualTo(Kind.PEERS));
    }

    @Test
    void aDaySpikeAgainstTheSameWeekdays() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final LocalDate spike = LocalDate.of(2026, 5, 19);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> date.equals(spike)
                ? 50
                : 5 + date.getDayOfMonth() % 2, date -> Map.of());
        final Scored scored = ActivityScoring.score(spec(Unit.DAY), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                WINDOW_TO, List.of()));

        assertThat(scored.findings().getFirst().kind()).isEqualTo(Kind.DAY);
        assertThat(scored.findings().getFirst().window()).isEqualTo("2026-05-19");
        assertThat(scored.findings().getFirst().evidence()).contains("on Tuesday 2026-05-19").contains(
                "8 previous Tuesdays");
    }

    @Test
    void anApprovedExceptionIsNotScored() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        final LocalDate migrationFrom = LocalDate.of(2026, 5, 18);
        final LocalDate migrationTo = LocalDate.of(2026, 5, 22);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> !date.isBefore(migrationFrom)
                                                                     && !date.isAfter(migrationTo)
                ? 50
                : 5, date -> Map.of());
        final Spec spec = new Spec(BASE_FROM, WINDOW_FROM, WINDOW_FROM, WINDOW_TO, Unit.DAY, List.of(), ZONE,
                List.of(new ActivityScoring.Excepted(migrationFrom, migrationTo, "approved data-centre migration")));
        final Scored scored = ActivityScoring.score(spec, fixtures.data("p1", NO_FIELDS, BASE_FROM, WINDOW_TO,
                List.of()));

        assertThat(scored.status().get(LocalDate.of(2026, 5, 19))).isEqualTo(Status.EXCEPTION);
        assertThat(scored.findings()).isEmpty();
        assertThat(scored.notes()).contains("5 day(s) from 2026-05-18 to 2026-05-22 not scored: approved "
                                            + "data-centre migration (an approved exception given).");
        assertThat(CompareActivityTool.exceptions(List.of("2026-05-18..2026-05-22: approved migration")))
                .singleElement().satisfies(excepted -> assertThat(excepted.reason()).isEqualTo("approved migration"));
    }

    @Test
    void constantActivityFindsNothing() {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        fixtures.workdays("p1", BASE_FROM, WINDOW_TO, 9, 17, date -> 5, date -> Map.of());
        for (final Unit unit : Unit.values()) {
            final Scored scored = ActivityScoring.score(spec(unit), fixtures.data("p1", NO_FIELDS, BASE_FROM,
                    WINDOW_TO, List.of()));
            assertThat(scored.findings()).as(unit.word()).isEmpty();
        }
    }

    @Test
    void daysAndHoursInTheZoneAcrossADaylightSavingChange() {
        final ZoneId london = ZoneId.of("Europe/London");
        final CompareFixtures fixtures = new CompareFixtures(london);
        // Summer time starts on 29 March 2026: 09:00 is 09:00Z before and 08:00Z after.
        fixtures.workdays("p1", LocalDate.of(2026, 3, 26), LocalDate.of(2026, 4, 1), 9, 17, date -> 2,
                date -> Map.of());
        final SourceData data = fixtures.data("p1", NO_FIELDS, LocalDate.of(2026, 3, 26), LocalDate.of(2026, 4, 1),
                List.of());

        assertThat(data.days().get(LocalDate.of(2026, 3, 27)).firstMinute()).isEqualTo(9 * 60 + 5);
        assertThat(data.days().get(LocalDate.of(2026, 3, 30)).firstMinute()).isEqualTo(9 * 60 + 5);
        assertThat(data.days().get(LocalDate.of(2026, 3, 30)).activeHours()).isEqualTo(8);
        assertThat(data.days().get(LocalDate.of(2026, 3, 30)).lastMinute()).isEqualTo(16 * 60 + 30);
        // Monday 30 March 09:00 local (08:00Z) is slot 9 of the week, as Mondays before the change are.
        assertThat(data.hourOfWeek().get(YearMonth.of(2026, 3))[9]).isEqualTo(2.0);
    }

    @Test
    void sameWeekdayBaseline() {
        final Map<LocalDate, Double> series = new TreeMap<>();
        final LocalDate day = LocalDate.of(2026, 5, 19);
        for (int week = 1; week <= 10; week++) {
            series.put(day.minusWeeks(week), (double) week);
        }
        series.put(day.minusDays(1), 99.0);
        assertThat(ActivityScoring.sameWeekday(series, day)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void blocks() {
        assertThat(ActivityScoring.blocks(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10), Unit.MONTH))
                .extracting(ActivityScoring.Block::label)
                .containsExactly("2026-01-15 to 2026-01-31", "2026-02-01 to 2026-02-28", "2026-03-01 to 2026-03-09");
        assertThat(ActivityScoring.blocks(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), Unit.WEEK))
                .extracting(ActivityScoring.Block::label)
                .containsExactly("2026-01-01 to 2026-01-07", "2026-01-08 to 2026-01-09");
    }

    // ten colleagues and an always-on service account, so every day of the period has data in the source
    private static CompareFixtures background(final ZoneId zone,
                                              final LocalDate from,
                                              final LocalDate to,
                                              final double scale) {
        final CompareFixtures fixtures = new CompareFixtures(zone);
        for (int i = 0; i < 10; i++) {
            fixtures.workdays("other" + i, from, to, 9, 17, date -> (int) Math.round(5 * scale), date -> Map.of());
        }
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            fixtures.hours("svc", date, 0, 24, 1, Map.of());
        }
        return fixtures;
    }

    // The person's workdays from 9 to 17 in the background, 5 events an hour, the day's number of sites (one an
    // hour, cycling) as destinations.
    private static CompareFixtures siteWorkdays(final ToIntFunction<LocalDate> sites) {
        final CompareFixtures fixtures = background(ZONE, BASE_FROM, WINDOW_TO, 1.0);
        for (LocalDate date = BASE_FROM; date.isBefore(WINDOW_TO); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() <= 5) {
                final int count = sites.applyAsInt(date);
                for (int site = 0; site < count; site++) {
                    final int hour = 9 + site % 8;
                    fixtures.hours("p1", date, hour, hour + 1, 1, Map.of(CompareFixtures.DESTINATION_FIELD,
                            "site" + site));
                }
            }
        }
        return fixtures;
    }

    private static Spec spec(final Unit unit) {
        return new Spec(BASE_FROM, WINDOW_FROM, WINDOW_FROM, WINDOW_TO, unit, List.of(), ZONE);
    }

    private static Finding only(final Scored scored, final Kind kind, final String feature) {
        final List<Finding> found = scored.findings().stream()
                .filter(finding -> finding.kind() == kind && finding.feature().equals(feature))
                .toList();
        assertThat(found).as(kind + " " + feature + " in " + scored.findings()).hasSize(1);
        return found.getFirst();
    }
}
