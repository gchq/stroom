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

import stroom.ai.impl.tools.compare.ActivityProfiles.Day;
import stroom.ai.impl.tools.compare.ActivityProfiles.Population;
import stroom.ai.impl.tools.compare.ActivityProfiles.Seen;
import stroom.ai.impl.tools.compare.ActivityStatistics.Score;
import stroom.ai.impl.tools.compare.ActivityStatistics.Shift;
import stroom.util.shared.NullSafe;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/// Scores one person's daily features against their own past and their peers, in code, as `compare_activity`
/// reports them: robust z-scores against a rolling same-weekday baseline (a day) or the baseline period's windows of
/// the same length (a week or a month); lasting shifts (change points, with their date and size); drifts (Theil-Sen
/// slopes of monthly medians); a month's hour-of-week rhythm against the baseline's; and where the person parts from
/// their peers' daily median. Checked first: the days the source holds any data (a gap, e.g. retention, is not
/// scored), the days given as leave, and long quiet runs (reported, not scored); and the whole source's volume, so a
/// shift everyone shares is told as the source's, not the person's. Findings are indicators with their evidence and
/// an alternative explanation, never a verdict.
final class ActivityScoring {

    /// The most a z-score counts for in ranking, and is shown as: past it, a baseline with almost no spread makes the
    /// number meaningless.
    static final double MAX_Z = 50;

    /// The |z| from which a value is a finding.
    static final double Z_THRESHOLD = 3.5;
    /// The previous same weekdays a day is scored against.
    static final int SAME_WEEKDAY_WEEKS = 8;
    /// The shortest quiet run reported, and not scored, in days.
    static final int QUIET_RUN_DAYS = 7;
    /// The days at the period's start whose values aren't counted new (everything is new at first).
    static final int NOVELTY_BURN_IN_DAYS = 28;
    /// The fewest weeks on each side of a change point.
    static final int CHANGE_MIN_WEEKS = 2;
    /// The days within which a source-wide change point is taken as the same change.
    static final int SOURCE_SHIFT_DAYS = 7;
    /// The fewest scored days a window (week or month) needs to be scored.
    static final int MIN_WINDOW_DAYS = 3;
    /// The fewest months a drift is fitted on.
    static final int MIN_DRIFT_MONTHS = 4;
    /// The fewest peers compared with.
    static final int MIN_PEERS = 5;
    /// A field with at most this many values has its started and stopped values listed.
    static final int LISTED_VALUES = 50;

    static final String EVENTS = "events";
    static final String ACTIVE_HOURS = "active_hours";
    static final String FIRST = "first_activity";
    static final String LAST = "last_activity";
    static final String SUM = "sum:";
    static final String DISTINCT = "distinct:";
    static final String NOVEL = "new:";
    static final String CATEGORY = "category:";
    static final String RHYTHM = "rhythm";
    static final String PEERS = "events_vs_peers";

    private ActivityScoring() {
        // Static only.
    }

    /// Scores one source.
    ///
    /// @param spec The periods, unit, leave and zone.
    /// @param data The source's aggregates, read.
    /// @return Its findings, days, months and notes.
    static Scored score(final Spec spec, final SourceData data) {
        final List<String> notes = new ArrayList<>();
        final List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = spec.baselineFrom(); d.isBefore(spec.windowTo()); d = d.plusDays(1)) {
            dates.add(d);
        }
        final Map<LocalDate, Status> status = status(spec, data, dates);
        final Map<String, Map<LocalDate, Double>> features = features(spec, data, dates, status);
        // Everyone else's events per person each day: the person's own left out, so their change can't make it.
        final Map<LocalDate, Double> population = new TreeMap<>();
        for (final LocalDate date : dates) {
            final Population p = data.population().get(date);
            final Day own = data.days().get(date);
            final double ownEvents = own == null
                    ? 0
                    : own.events();
            final double others = p == null
                    ? 0
                    : p.people() - (ownEvents > 0
                            ? 1
                            : 0);
            if (p != null && others > 0) {
                population.put(date, Math.max(0, p.events() - ownEvents) / others);
            }
        }
        final List<Finding> findings = new ArrayList<>();
        if (data.days().isEmpty()) {
            notes.add("Nothing matched " + data.label() + " in this source in the period.");
        } else {
            switch (spec.unit()) {
                case DAY -> findings.addAll(dayFindings(spec, data, features, population));
                case WEEK, MONTH -> findings.addAll(merged(windowFindings(spec, data, features, status, population,
                        notes), spec.unit()));
            }
            findings.addAll(changePoints(spec, data, features, population));
            findings.addAll(drifts(data, features));
            findings.addAll(rhythm(spec, data, notes));
            findings.addAll(peers(spec, data, status, notes));
        }
        for (final Excepted excepted : spec.exceptions()) {
            final long days = status.entrySet().stream()
                    .filter(day -> day.getValue() == Status.EXCEPTION && excepted.contains(day.getKey()))
                    .count();
            if (days > 0) {
                notes.add(days + " day(s) from " + excepted.from() + " to " + excepted.to() + " not scored: "
                          + excepted.reason() + " (an approved exception given).");
            }
        }
        final long uncovered = status.values().stream().filter(s -> s == Status.NO_SOURCE_DATA).count();
        if (uncovered > 0) {
            notes.add(uncovered + " day(s) with no data in the source at all (e.g. retention or a gap in "
                      + "collection): not scored.");
        }
        findings.sort(Comparator.comparingDouble(Finding::score).reversed());
        return new Scored(data.source(), findings, status, features, months(spec, data, features, status), notes,
                values(spec, data));
    }

    // --------------------------------------------------------------------------------
    // Days

    private static Map<LocalDate, Status> status(final Spec spec, final SourceData data, final List<LocalDate> dates) {
        final Map<LocalDate, Status> status = new LinkedHashMap<>();
        final boolean[] quiet = new boolean[dates.size()];
        final boolean anyPopulation = !data.population().isEmpty();
        for (int i = 0; i < dates.size(); i++) {
            final LocalDate date = dates.get(i);
            final Population population = data.population().get(date);
            final boolean covered = anyPopulation
                    ? population != null && population.events() > 0
                    : data.days().containsKey(date);
            final Status s = !covered
                    ? Status.NO_SOURCE_DATA
                    : spec.onLeave(date)
                            ? Status.LEAVE
                            : spec.exception(date).isPresent()
                                    ? Status.EXCEPTION
                                    : Status.SCORED;
            status.put(date, s);
            final Day day = data.days().get(date);
            // Quiet: a day the source holds data, not leave, and the person did nothing; else the run breaks.
            quiet[i] = s == Status.SCORED && (day == null || day.events() == 0);
        }
        // A run of quiet days (an absence no one named, e.g. leave not recorded) is reported, not scored.
        final boolean[] active = new boolean[quiet.length];
        for (int i = 0; i < quiet.length; i++) {
            active[i] = !quiet[i];
        }
        for (final int[] run : ActivityStatistics.quietRuns(active, QUIET_RUN_DAYS)) {
            for (int i = run[0]; i < run[0] + run[1]; i++) {
                status.put(dates.get(i), Status.QUIET_RUN);
            }
        }
        return status;
    }

    // Every feature's value on each scored day; the first and last activity only on active days.
    private static Map<String, Map<LocalDate, Double>> features(final Spec spec,
                                                                final SourceData data,
                                                                final List<LocalDate> dates,
                                                                final Map<LocalDate, Status> status) {
        final Map<String, Map<LocalDate, Double>> features = new LinkedHashMap<>();
        for (final String name : featureNames(data)) {
            features.put(name, new TreeMap<>());
        }
        final Map<String, Map<LocalDate, Integer>> novel = novel(spec, data);
        for (final LocalDate date : dates) {
            if (status.get(date) != Status.SCORED) {
                continue;
            }
            final Day day = data.days().get(date);
            features.get(EVENTS).put(date, day == null
                    ? 0.0
                    : day.events());
            features.get(ACTIVE_HOURS).put(date, day == null
                    ? 0.0
                    : (double) day.activeHours());
            if (day != null && day.events() > 0 && day.firstMinute() != null) {
                features.get(FIRST).put(date, (double) day.firstMinute());
                features.get(LAST).put(date, (double) day.lastMinute());
            }
            for (final String field : data.fields().sums()) {
                features.get(SUM + field).put(date, day == null
                        ? 0.0
                        : day.sums().getOrDefault(field, 0.0));
            }
            for (final String field : data.fields().breadth()) {
                features.get(DISTINCT + field).put(date, day == null
                        ? 0.0
                        : day.distinct().getOrDefault(field, 0.0));
                if (data.seen().containsKey(field)) {
                    features.get(NOVEL + field).put(date,
                            (double) novel.getOrDefault(field, Map.of()).getOrDefault(date, 0));
                }
            }
            for (final Map.Entry<String, SortedMap<LocalDate, Double>> extra : data.extra().entrySet()) {
                features.get(extra.getKey()).put(date, extra.getValue().getOrDefault(date, 0.0));
            }
        }
        return features;
    }

    private static List<String> featureNames(final SourceData data) {
        final List<String> names = new ArrayList<>(List.of(EVENTS, ACTIVE_HOURS, FIRST, LAST));
        data.fields().sums().forEach(field -> names.add(SUM + field));
        data.fields().breadth().forEach(field -> names.add(DISTINCT + field));
        data.fields().breadth().stream()
                .filter(data.seen()::containsKey)
                .forEach(field -> names.add(NOVEL + field));
        data.extra().keySet().stream().filter(name -> !names.contains(name)).forEach(names::add);
        return names;
    }

    // The values of each field first seen on each day, after the burn-in.
    private static Map<String, Map<LocalDate, Integer>> novel(final Spec spec, final SourceData data) {
        final LocalDate burnedIn = spec.baselineFrom().plusDays(NOVELTY_BURN_IN_DAYS);
        final Map<String, Map<LocalDate, Integer>> novel = new LinkedHashMap<>();
        for (final Map.Entry<String, List<Seen>> field : data.seen().entrySet()) {
            final Map<LocalDate, Integer> byDay = new TreeMap<>();
            for (final Seen seen : field.getValue()) {
                final LocalDate first = ZonedDateTime.ofInstant(seen.first(), spec.zone()).toLocalDate();
                if (!first.isBefore(burnedIn)) {
                    byDay.merge(first, 1, Integer::sum);
                }
            }
            novel.put(field.getKey(), byDay);
        }
        return novel;
    }

    // Each scored day in the window against the same weekday in the previous weeks.
    private static List<Finding> dayFindings(final Spec spec,
                                             final SourceData data,
                                             final Map<String, Map<LocalDate, Double>> features,
                                             final Map<LocalDate, Double> population) {
        final List<Finding> findings = new ArrayList<>();
        for (final Map.Entry<String, Map<LocalDate, Double>> feature : features.entrySet()) {
            final Map<LocalDate, Double> series = feature.getValue();
            for (LocalDate date = spec.windowFrom(); date.isBefore(spec.windowTo()); date = date.plusDays(1)) {
                final Double value = series.get(date);
                if (value == null) {
                    continue;
                }
                final double[] baseline = sameWeekday(series, date);
                final Optional<Score> score = ActivityStatistics.robustZ(value, baseline);
                if (score.isEmpty() || !notable(feature.getKey(), value, score.get())) {
                    continue;
                }
                final String weekday = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK);
                String alternative = alternative(feature.getKey(), score.get().z());
                final boolean shared = sharedBySource(feature.getKey(), population, date, value, score.get());
                if (shared) {
                    alternative = "Everyone else's events per person shifted as far the same way that day: a change "
                                  + "in the source (collection, a pipeline), not the person's.";
                }
                findings.add(new Finding(data.source(), feature.getKey(), date.toString(), score.get().median(),
                        value, capped(score.get().z()), label(feature.getKey()) + " " + shown(feature.getKey(), value)
                                                        + " on " + weekday + " " + date + " against a median of "
                                                        + shown(feature.getKey(), score.get().median()) + " on the "
                                                        + score.get().n() + " previous " + weekday + "s (robust z "
                                                        + robustZText(score.get().z()) + ")", alternative, Kind.DAY,
                        shared));
            }
        }
        return findings;
    }

    // The same weekday's values in the previous weeks, those scored.
    static double[] sameWeekday(final Map<LocalDate, Double> series, final LocalDate date) {
        final List<Double> values = new ArrayList<>();
        for (int week = 1; week <= SAME_WEEKDAY_WEEKS; week++) {
            final Double value = series.get(date.minusWeeks(week));
            if (value != null) {
                values.add(value);
            }
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // Whether everyone else's events per person moved the same way on that day, past the threshold and by at least
    // half as much relative to their level as the person's did.
    private static boolean sharedBySource(final String feature,
                                          final Map<LocalDate, Double> population,
                                          final LocalDate date,
                                          final double value,
                                          final Score score) {
        if (!comparableWithSource(feature)) {
            return false;
        }
        final Double others = population.get(date);
        if (others == null) {
            return false;
        }
        return ActivityStatistics.robustZ(others, sameWeekday(population, date))
                .map(theirs -> sharedShift(value, score, others, theirs))
                .orElse(false);
    }

    /// Two departures from their baselines (the person's, everyone else's) the same way, everyone else's past the
    /// threshold and at least half the person's relative to its level.
    ///
    /// @param value  The person's value.
    /// @param score  The person's score against their baseline.
    /// @param others Everyone else's value.
    /// @param theirs Everyone else's score against theirs.
    /// @return True if everyone else moved as far the same way: a change in the source.
    static boolean sharedShift(final double value,
                                       final Score score,
                                       final double others,
                                       final Score theirs) {
        return Math.signum(theirs.z()) == Math.signum(score.z())
               && Math.abs(theirs.z()) >= Z_THRESHOLD
               && relativeChange(others, theirs.median()) >= 0.5 * relativeChange(value, score.median());
    }

    private static double relativeChange(final double value, final double baseline) {
        return baseline == 0
                ? (value == 0
                        ? 0
                        : 1)
                : Math.abs(value - baseline) / Math.abs(baseline);
    }

    /// @param z A robust z.
    /// @return Its size, at most [#MAX_Z], for ranking.
    static double capped(final double z) {
        return Math.min(Math.abs(z), MAX_Z);
    }

    /// @param z A robust z.
    /// @return It as the result says it: "over 50" past [#MAX_Z].
    static String robustZText(final double z) {
        return Math.abs(z) > MAX_Z
                ? (z > 0
                        ? "over "
                        : "under -") + round(MAX_Z)
                : round(z);
    }

    // --------------------------------------------------------------------------------
    // Weeks and months

    private static List<Finding> windowFindings(final Spec spec,
                                                final SourceData data,
                                                final Map<String, Map<LocalDate, Double>> features,
                                                final Map<LocalDate, Status> status,
                                                final Map<LocalDate, Double> population,
                                                final List<String> notes) {
        final List<Block> windows = blocks(spec.windowFrom(), spec.windowTo(), spec.unit());
        final List<Block> baseline = blocks(spec.baselineFrom(), spec.baselineTo(), spec.unit());
        final List<Finding> findings = new ArrayList<>();
        boolean tooFew = false;
        for (final Map.Entry<String, Map<LocalDate, Double>> feature : features.entrySet()) {
            final Function<Block, Double> value = block -> aggregate(feature.getKey(), feature.getValue(), block);
            final double[] baseValues = baseline.stream()
                    .map(value)
                    .filter(v -> v != null)
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            if (baseValues.length < ActivityStatistics.MIN_BASELINE) {
                tooFew = true;
                continue;
            }
            final double[] basePopulation = baseline.stream()
                    .map(block -> aggregate(EVENTS, population, block))
                    .filter(v -> v != null)
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            for (final Block window : windows) {
                final Double v = value.apply(window);
                if (v == null) {
                    continue;
                }
                final Score score = ActivityStatistics.robustZ(v, baseValues).orElseThrow();
                if (!notable(feature.getKey(), v, score)) {
                    continue;
                }
                String alternative = alternative(feature.getKey(), score.z());
                final Double populationValue = aggregate(EVENTS, population, window);
                final boolean shared = populationValue != null && comparableWithSource(feature.getKey())
                                       && ActivityStatistics.robustZ(populationValue, basePopulation)
                                               .map(theirs -> sharedShift(v, score, populationValue, theirs))
                                               .orElse(false);
                if (shared) {
                    alternative = "Everyone else's events per person moved as far the same way in that "
                                  + spec.unit().word() + ": a change in the source, not the person's.";
                }
                findings.add(new Finding(data.source(), feature.getKey(), window.label(), score.median(), v,
                        capped(score.z()), label(feature.getKey()) + " " + shown(feature.getKey(), v)
                                           + perDay(feature.getKey()) + " in the " + spec.unit().word() + " from "
                                           + window.from() + " against a median of " + shown(feature.getKey(),
                                score.median()) + " over " + score.n() + " baseline " + spec.unit().word()
                                           + "s (robust z " + robustZText(score.z()) + ")", alternative,
                        spec.unit() == Unit.WEEK
                                ? Kind.WEEK
                                : Kind.MONTH, shared));
            }
        }
        if (tooFew) {
            notes.add("Fewer than " + ActivityStatistics.MIN_BASELINE + " baseline " + spec.unit().word() + "s with "
                      + "enough data for some features: those weren't scored (a longer baseline, or unit day).");
        }
        return findings;
    }

    /// Runs of consecutive windows of one feature moving the same way (and alike in being the source's or not),
    /// merged into one finding each, so a lasting rise is one finding, not one a week: its window the run's, its
    /// value and score the largest window's.
    ///
    /// @param findings The window findings, in window order for each feature.
    /// @param unit     The unit.
    /// @return The findings, runs merged.
    static List<Finding> merged(final List<Finding> findings, final Unit unit) {
        final List<Finding> out = new ArrayList<>();
        final Map<String, List<Finding>> byFeature = new LinkedHashMap<>();
        findings.forEach(finding -> byFeature.computeIfAbsent(finding.feature() + "|" + finding.sourceWide(),
                k -> new ArrayList<>()).add(finding));
        for (final List<Finding> feature : byFeature.values()) {
            feature.sort(Comparator.comparing(Finding::window));
            List<Finding> run = new ArrayList<>();
            for (final Finding finding : feature) {
                if (!run.isEmpty() && !(follows(run.getLast(), finding)
                                        && Math.signum(run.getLast().value() - run.getLast().baseline())
                                           == Math.signum(finding.value() - finding.baseline()))) {
                    out.add(mergeRun(run, unit));
                    run = new ArrayList<>();
                }
                run.add(finding);
            }
            if (!run.isEmpty()) {
                out.add(mergeRun(run, unit));
            }
        }
        return out;
    }

    // Whether a window finding's window starts the day after another's ends ("from to to" labels).
    private static boolean follows(final Finding before, final Finding after) {
        final String[] a = before.window().split(" to ");
        final String[] b = after.window().split(" to ");
        return a.length == 2 && b.length == 2 && LocalDate.parse(a[1]).plusDays(1).equals(LocalDate.parse(b[0]));
    }

    private static Finding mergeRun(final List<Finding> run, final Unit unit) {
        if (run.size() == 1) {
            return run.getFirst();
        }
        final Finding largest = run.stream().max(Comparator.comparingDouble(Finding::score)).orElseThrow();
        final String from = run.getFirst().window().split(" to ")[0];
        final String to = run.getLast().window().split(" to ")[1];
        return new Finding(largest.source(), largest.feature(), from + " to " + to, largest.baseline(),
                largest.value(), largest.score(), "In " + run.size() + " " + unit.word() + "s in a row from " + from
                                                  + " to " + to + "; the largest: " + largest.evidence(),
                largest.alternative(), largest.kind(), largest.sourceWide());
    }

    // A feature over a block: the median of the first and last activity, else the mean per scored day; null if the
    // block has too few scored days.
    private static Double aggregate(final String feature, final Map<LocalDate, Double> series, final Block block) {
        final List<Double> values = new ArrayList<>();
        for (LocalDate d = block.from(); d.isBefore(block.to()); d = d.plusDays(1)) {
            final Double value = series.get(d);
            if (value != null) {
                values.add(value);
            }
        }
        if (values.size() < MIN_WINDOW_DAYS) {
            return null;
        }
        final double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
        if (isTime(feature)) {
            return ActivityStatistics.median(array);
        }
        double sum = 0;
        for (final double value : array) {
            sum += value;
        }
        return sum / array.length;
    }

    /// Cuts a range into weeks (7 days from its start) or calendar months, the first and last cut to the range.
    ///
    /// @param from The start.
    /// @param to   The end (exclusive).
    /// @param unit The unit.
    /// @return The blocks, in order.
    static List<Block> blocks(final LocalDate from, final LocalDate to, final Unit unit) {
        final List<Block> blocks = new ArrayList<>();
        LocalDate start = from;
        while (start.isBefore(to)) {
            final LocalDate next = switch (unit) {
                case DAY -> start.plusDays(1);
                case WEEK -> start.plusWeeks(1);
                case MONTH -> start.withDayOfMonth(1).plusMonths(1);
            };
            final LocalDate end = next.isBefore(to)
                    ? next
                    : to;
            blocks.add(new Block(start, end));
            start = end;
        }
        return blocks;
    }

    // --------------------------------------------------------------------------------
    // Change points and drifts

    private static List<Finding> changePoints(final Spec spec,
                                              final SourceData data,
                                              final Map<String, Map<LocalDate, Double>> features,
                                              final Map<LocalDate, Double> population) {
        final Optional<Dated> sourceShift = dated(population, CHANGE_MIN_WEEKS);
        final List<Finding> findings = new ArrayList<>();
        for (final Map.Entry<String, Map<LocalDate, Double>> feature : features.entrySet()) {
            final Optional<Dated> shift = dated(feature.getValue(), CHANGE_MIN_WEEKS);
            if (shift.isEmpty() || shift.get().shift().score() < Z_THRESHOLD
                || !lasting(feature.getKey(), shift.get().shift())) {
                continue;
            }
            final Shift s = shift.get().shift();
            String alternative = alternative(feature.getKey(), s.after() - s.before());
            final double size = Math.min(s.score(), MAX_Z);
            boolean shared = false;
            if (comparableWithSource(feature.getKey()) && sourceShift.isPresent()
                && Math.abs(ChronoUnit.DAYS.between(sourceShift.get().date(), shift.get().date())) <= SOURCE_SHIFT_DAYS
                && Math.signum(sourceShift.get().shift().after() - sourceShift.get().shift().before())
                   == Math.signum(s.after() - s.before())
                && sourceShift.get().shift().score() >= Z_THRESHOLD
                && relative(sourceShift.get().shift()) >= 0.5 * relative(s)) {
                alternative = "Everyone else's events per person shifted the same way from "
                              + sourceShift.get().date() + ": a change in the source (collection, a pipeline), "
                              + "not the person's.";
                shared = true;
            }
            final long beforeWindow = ChronoUnit.DAYS.between(shift.get().date(), spec.windowTo());
            findings.add(new Finding(data.source(), feature.getKey(), shift.get().date().toString(), s.before(),
                    s.after(), size, label(feature.getKey()) + " shifted from a median of "
                                     + shown(feature.getKey(), s.before()) + " to "
                                     + shown(feature.getKey(), s.after()) + perDay(feature.getKey()) + " from "
                                     + shift.get().date() + " (" + beforeWindow + " days before the window's end; "
                                     + "size " + robustZText(s.score()) + ")", alternative, Kind.CHANGE, shared));
        }
        return findings;
    }

    // The series' change point, with its date: found on weekly means (so a weekly cycle, e.g. quiet weekends, isn't
    // read as noise or as a shift), then dated to the day within a week either side, where the 7 days from a day
    // differ most from the 7 before it in the shift's direction.
    private static Optional<Dated> dated(final Map<LocalDate, Double> series, final int minWeeks) {
        if (series.isEmpty()) {
            return Optional.empty();
        }
        final LocalDate first = series.keySet().iterator().next();
        LocalDate last = first;
        for (final LocalDate date : series.keySet()) {
            last = date;
        }
        final List<Block> weeks = new ArrayList<>();
        final List<Double> means = new ArrayList<>();
        for (final Block block : blocks(first, last.plusDays(1), Unit.WEEK)) {
            final Double mean = aggregate(EVENTS, series, block);
            if (mean != null) {
                weeks.add(block);
                means.add(mean);
            }
        }
        final Optional<Shift> weekly = ActivityStatistics.changePoint(means.stream()
                .mapToDouble(Double::doubleValue)
                .toArray(), minWeeks);
        if (weekly.isEmpty()) {
            return Optional.empty();
        }
        final Shift shift = weekly.get();
        final LocalDate around = weeks.get(shift.index()).from();
        final double direction = Math.signum(shift.after() - shift.before());
        final Map<LocalDate, Double> differences = new TreeMap<>();
        for (LocalDate d = around.minusDays(6); !d.isAfter(around.plusDays(6)); d = d.plusDays(1)) {
            final Double after = aggregate(EVENTS, series, new Block(d, d.plusDays(7)));
            final Double before = aggregate(EVENTS, series, new Block(d.minusDays(7), d));
            if (after != null && before != null) {
                differences.put(d, direction * (after - before));
            }
        }
        if (differences.isEmpty()) {
            return Optional.of(new Dated(around, shift));
        }
        final double most = differences.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        // Of the days that differ most (a quiet weekend before a step ties with the step's own day), the first whose
        // own value is already on the new side.
        LocalDate best = null;
        for (final Map.Entry<LocalDate, Double> difference : differences.entrySet()) {
            if (difference.getValue() >= most - 1e-9) {
                final Double value = series.get(difference.getKey());
                if (best == null) {
                    best = difference.getKey();
                }
                if (value != null && direction * (value - shift.before()) > 0) {
                    best = difference.getKey();
                    break;
                }
            }
        }
        // The first day the new level shows, from it on: a quiet day (a weekend) before a step isn't its start.
        for (LocalDate d = best; d.isBefore(best.plusDays(7)); d = d.plusDays(1)) {
            final Double value = series.get(d);
            if (value != null && direction * (value - shift.before()) > 0) {
                return Optional.of(new Dated(d, shift));
            }
        }
        return Optional.of(new Dated(best, shift));
    }

    // A shift's size relative to the level before it (a shift from nothing counts as 100%).
    private static double relative(final Shift shift) {
        final double before = Math.abs(shift.before());
        return before == 0
                ? 1
                : Math.abs(shift.after() - shift.before()) / before;
    }

    // A shift big enough to tell: half the level before, or an hour for a time.
    private static boolean lasting(final String feature, final Shift shift) {
        final double difference = Math.abs(shift.after() - shift.before());
        if (isTime(feature)) {
            return difference >= 60;
        }
        return difference >= minimum(feature) && difference >= 0.5 * Math.abs(shift.before());
    }

    // Month-on-month drifts of the monthly medians (first and last activity) and means (the rest).
    private static List<Finding> drifts(final SourceData data, final Map<String, Map<LocalDate, Double>> features) {
        final List<Finding> findings = new ArrayList<>();
        for (final Map.Entry<String, Map<LocalDate, Double>> feature : features.entrySet()) {
            final SortedMap<YearMonth, List<Double>> byMonth = new TreeMap<>();
            feature.getValue().forEach((date, value) -> byMonth.computeIfAbsent(YearMonth.from(date),
                    k -> new ArrayList<>()).add(value));
            final List<Double> x = new ArrayList<>();
            final List<Double> y = new ArrayList<>();
            YearMonth firstMonth = null;
            for (final Map.Entry<YearMonth, List<Double>> month : byMonth.entrySet()) {
                if (month.getValue().size() < ActivityStatistics.MIN_BASELINE) {
                    continue;
                }
                if (firstMonth == null) {
                    firstMonth = month.getKey();
                }
                final double[] values = month.getValue().stream().mapToDouble(Double::doubleValue).toArray();
                x.add((double) ChronoUnit.MONTHS.between(firstMonth, month.getKey()));
                y.add(isTime(feature.getKey())
                        ? ActivityStatistics.median(values)
                        : mean(values));
            }
            if (x.size() < MIN_DRIFT_MONTHS) {
                continue;
            }
            final double slope = ActivityStatistics.theilSen(x.stream().mapToDouble(Double::doubleValue).toArray(),
                    y.stream().mapToDouble(Double::doubleValue).toArray());
            final double months = x.getLast() - x.getFirst();
            final double total = slope * months;
            final double start = ActivityStatistics.median(y.subList(0, Math.min(3, y.size())).stream()
                    .mapToDouble(Double::doubleValue).toArray());
            final double threshold = isTime(feature.getKey())
                    ? 60
                    : Math.max(minimum(feature.getKey()), 0.5 * Math.abs(start));
            // A drift needs a scale: from nothing, with no least change, it can't be sized.
            if (Double.isNaN(slope) || threshold <= 0 || total == 0 || Math.abs(total) < threshold) {
                continue;
            }
            findings.add(new Finding(data.source(), feature.getKey(), firstMonth + " to "
                                                                     + firstMonth.plusMonths((long) months),
                    start, start + total, Math.min(MAX_Z, Z_THRESHOLD * Math.abs(total) / threshold),
                    label(feature.getKey()) + " drifted by " + signed(feature.getKey(), slope) + " a month over "
                    + (long) months + " months (Theil-Sen), " + signed(feature.getKey(), total) + " in all",
                    alternative(feature.getKey(), total), Kind.DRIFT));
        }
        return findings;
    }

    // --------------------------------------------------------------------------------
    // Rhythm

    // Each window month's hour-of-week profile against the baseline months' summed, scored against how far each
    // baseline month was from the rest.
    private static List<Finding> rhythm(final Spec spec, final SourceData data, final List<String> notes) {
        final SortedMap<YearMonth, double[]> profiles = data.hourOfWeek();
        final YearMonth baselineEnd = YearMonth.from(spec.baselineTo().minusDays(1));
        final double[] total = new double[ActivityProfiles.HOURS_OF_WEEK];
        final List<YearMonth> baseMonths = new ArrayList<>();
        for (final Map.Entry<YearMonth, double[]> month : profiles.entrySet()) {
            if (!month.getKey().isAfter(baselineEnd) && !month.getKey().isBefore(YearMonth.from(spec.baselineFrom()))) {
                baseMonths.add(month.getKey());
                for (int i = 0; i < total.length; i++) {
                    total[i] += month.getValue()[i];
                }
            }
        }
        final List<Double> reference = new ArrayList<>();
        for (final YearMonth month : baseMonths) {
            final double[] rest = total.clone();
            final double[] own = profiles.get(month);
            for (int i = 0; i < rest.length; i++) {
                rest[i] -= own[i];
            }
            final double distance = ActivityStatistics.profileDistance(own, rest);
            if (!Double.isNaN(distance)) {
                reference.add(distance);
            }
        }
        final double[] referenceValues = reference.stream().mapToDouble(Double::doubleValue).toArray();
        final List<Finding> findings = new ArrayList<>();
        if (referenceValues.length < ActivityStatistics.MIN_BASELINE) {
            notes.add("Fewer than " + ActivityStatistics.MIN_BASELINE + " baseline months of hourly activity: the "
                      + "hour-of-week rhythm wasn't scored.");
            return findings;
        }
        for (final Map.Entry<YearMonth, double[]> month : profiles.entrySet()) {
            if (month.getKey().isBefore(YearMonth.from(spec.windowFrom()))) {
                continue;
            }
            final double distance = ActivityStatistics.profileDistance(month.getValue(), total);
            if (Double.isNaN(distance)) {
                continue;
            }
            final Score score = ActivityStatistics.robustZ(distance, referenceValues).orElseThrow();
            if (score.z() >= Z_THRESHOLD && distance - score.median() >= 0.1) {
                findings.add(new Finding(data.source(), RHYTHM, month.getKey().toString(), score.median(), distance,
                        capped(score.z()), "The hour-of-week pattern in " + month.getKey()
                                           + " differs from the baseline's by " + round(distance)
                                           + " (total variation, 0 to 1) against a median of "
                                           + round(score.median()) + " for the baseline months (robust z "
                                           + robustZText(score.z()) + "); its busiest hours: "
                                           + busiest(month.getValue()) + "; the baseline's: " + busiest(total),
                        "A change of shift, on-call duty, travel or a time zone change; or another person using "
                        + "the account.", Kind.RHYTHM));
            }
        }
        return findings;
    }

    // The three busiest hours of the week, e.g. "Mon 09:00, Tue 10:00, Mon 14:00".
    private static String busiest(final double[] profile) {
        final List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < profile.length; i++) {
            if (profile[i] > 0) {
                slots.add(i);
            }
        }
        slots.sort(Comparator.comparingDouble((Integer i) -> profile[i]).reversed());
        return String.join(", ", slots.stream()
                .limit(3)
                .map(i -> DayOfWeek.of(i / 24 + 1).getDisplayName(TextStyle.SHORT, Locale.UK) + " "
                          + String.format(Locale.ROOT, "%02d:00", i % 24))
                .toList());
    }

    // --------------------------------------------------------------------------------
    // Peers

    private static List<Finding> peers(final Spec spec,
                                       final SourceData data,
                                       final Map<LocalDate, Status> status,
                                       final List<String> notes) {
        final List<Finding> findings = new ArrayList<>();
        if (data.peers() == null) {
            return findings;
        }
        final Set<String> peers = new LinkedHashSet<>();
        data.peers().days().values().forEach(day -> peers.addAll(day.keySet()));
        if (peers.size() < MIN_PEERS) {
            notes.add("Only " + peers.size() + " peer(s) in " + data.peers().group() + " had events here: too few to "
                      + "compare with (at least " + MIN_PEERS + ").");
            return findings;
        }
        // Each block's events per scored day, the person's and each peer's.
        final List<Block> baseline = blocks(spec.baselineFrom(), spec.baselineTo(), Unit.WEEK);
        final List<Block> window = blocks(spec.windowFrom(), spec.windowTo(), Unit.WEEK);
        final List<Double> baseRatios = new ArrayList<>();
        for (final Block block : baseline) {
            ratio(block, data, peers, status).ifPresent(baseRatios::add);
        }
        final double[] base = baseRatios.stream().mapToDouble(Double::doubleValue).toArray();
        for (final Block block : window) {
            final Optional<Double> ratio = ratio(block, data, peers, status);
            if (ratio.isEmpty()) {
                continue;
            }
            final Optional<Score> score = ActivityStatistics.robustZ(ratio.get(), base);
            if (score.isPresent() && Math.abs(score.get().z()) >= Z_THRESHOLD
                && Math.abs(ratio.get() - score.get().median()) >= 0.5) {
                findings.add(new Finding(data.source(), PEERS, block.label(), score.get().median(), ratio.get(),
                        capped(score.get().z()), "Events per day " + round(ratio.get()) + " times the median of "
                                                   + peers.size() + " peers in " + data.peers().group()
                                                   + " in the week from " + block.from() + ", against "
                                                   + round(score.get().median()) + " times in the baseline weeks "
                                                   + "(robust z " + robustZText(score.get().z()) + ")",
                        "A role or workload unlike the group's (e.g. a project, cover for a colleague), or the "
                        + "group in the Event XML out of date.", Kind.PEERS));
            }
        }
        // The window as a whole, against the peers' own spread over it.
        final Block whole = new Block(spec.windowFrom(), spec.windowTo());
        final double person = perScoredDay(whole, data, null, status);
        final double[] others = peers.stream()
                .mapToDouble(peer -> perScoredDay(whole, data, peer, status))
                .toArray();
        final Optional<Score> overall = ActivityStatistics.robustZ(person, others);
        if (overall.isPresent() && overall.get().z() >= Z_THRESHOLD && person >= 2 * overall.get().median()) {
            findings.add(new Finding(data.source(), PEERS, spec.windowFrom() + " to " + spec.windowTo(),
                    overall.get().median(), person, capped(overall.get().z()), round(person)
                                                                              + " events per day in the window "
                                                                       + "against a median of "
                                                                       + round(overall.get().median()) + " for "
                                                                       + peers.size() + " peers in "
                                                                       + data.peers().group() + " (robust z "
                                                                       + robustZText(overall.get().z()) + ")",
                    "A role with more of this activity than the group's (e.g. a caseload, a support rota), or the "
                    + "group in the Event XML out of date.", Kind.PEERS));
        }
        return findings;
    }

    // The person's events per scored day in a block over the peers' median.
    private static Optional<Double> ratio(final Block block,
                                          final SourceData data,
                                          final Set<String> peers,
                                          final Map<LocalDate, Status> status) {
        int scored = 0;
        for (LocalDate d = block.from(); d.isBefore(block.to()); d = d.plusDays(1)) {
            if (status.get(d) == Status.SCORED) {
                scored++;
            }
        }
        if (scored < MIN_WINDOW_DAYS) {
            return Optional.empty();
        }
        final double person = perScoredDay(block, data, null, status);
        final double median = ActivityStatistics.median(peers.stream()
                .mapToDouble(peer -> perScoredDay(block, data, peer, status))
                .toArray());
        return Optional.of((person + 1) / (median + 1));
    }

    // The events per scored day in a block, the person's (peer null) or a peer's.
    private static double perScoredDay(final Block block,
                                       final SourceData data,
                                       final String peer,
                                       final Map<LocalDate, Status> status) {
        double sum = 0;
        int days = 0;
        for (LocalDate d = block.from(); d.isBefore(block.to()); d = d.plusDays(1)) {
            if (status.get(d) != Status.SCORED) {
                continue;
            }
            days++;
            if (peer == null) {
                final Day day = data.days().get(d);
                sum += day == null
                        ? 0
                        : day.events();
            } else {
                sum += data.peers().days().getOrDefault(d, Map.of()).getOrDefault(peer, 0.0);
            }
        }
        return days == 0
                ? 0
                : sum / days;
    }

    /// @param date  A day.
    /// @param peers The peers' events by day.
    /// @return The peers' median events that day (a peer without events counted as 0), or null without peers.
    static Double peersMedian(final LocalDate date, final PeerData peers) {
        if (peers == null) {
            return null;
        }
        final Set<String> all = new LinkedHashSet<>();
        peers.days().values().forEach(day -> all.addAll(day.keySet()));
        if (all.isEmpty()) {
            return null;
        }
        final Map<String, Double> day = peers.days().getOrDefault(date, Map.of());
        return ActivityStatistics.median(all.stream().mapToDouble(p -> day.getOrDefault(p, 0.0)).toArray());
    }

    // --------------------------------------------------------------------------------
    // Months and values

    private static List<MonthLine> months(final Spec spec,
                                          final SourceData data,
                                          final Map<String, Map<LocalDate, Double>> features,
                                          final Map<LocalDate, Status> status) {
        final List<MonthLine> lines = new ArrayList<>();
        for (final Block month : blocks(spec.baselineFrom(), spec.windowTo(), Unit.MONTH)) {
            int days = 0;
            int covered = 0;
            int active = 0;
            int leave = 0;
            for (LocalDate d = month.from(); d.isBefore(month.to()); d = d.plusDays(1)) {
                days++;
                final Status s = status.get(d);
                if (s != Status.NO_SOURCE_DATA) {
                    covered++;
                }
                if (s == Status.LEAVE) {
                    leave++;
                }
                final Day day = data.days().get(d);
                if (day != null && day.events() > 0) {
                    active++;
                }
            }
            final Map<String, Double> values = new LinkedHashMap<>();
            for (final Map.Entry<String, Map<LocalDate, Double>> feature : features.entrySet()) {
                final Double value = aggregate(feature.getKey(), feature.getValue(), month);
                if (value != null) {
                    values.put(feature.getKey(), value);
                }
            }
            double peerMedian = Double.NaN;
            if (data.peers() != null) {
                final List<Double> medians = new ArrayList<>();
                for (LocalDate d = month.from(); d.isBefore(month.to()); d = d.plusDays(1)) {
                    if (status.get(d) == Status.SCORED) {
                        NullSafe.consume(peersMedian(d, data.peers()), medians::add);
                    }
                }
                peerMedian = mean(medians.stream().mapToDouble(Double::doubleValue).toArray());
            }
            lines.add(new MonthLine(data.source(), YearMonth.from(month.from()), days, covered, active, leave, values,
                    peerMedian, month.to().isAfter(spec.windowFrom())));
        }
        return lines;
    }

    // The values each breadth field started (first seen in the window) and stopped (last seen before it, after being
    // seen on at least three events in the baseline), listed for a field of few values.
    private static List<Values> values(final Spec spec, final SourceData data) {
        final List<Values> values = new ArrayList<>();
        final LocalDate burnedIn = spec.baselineFrom().plusDays(NOVELTY_BURN_IN_DAYS);
        for (final Map.Entry<String, List<Seen>> field : data.seen().entrySet()) {
            final List<String> started = new ArrayList<>();
            final List<String> stopped = new ArrayList<>();
            // In the order first seen, then by value.
            final List<Seen> ordered = field.getValue().stream()
                    .sorted(Comparator.comparing(Seen::first).thenComparing(Seen::value))
                    .toList();
            for (final Seen seen : ordered) {
                final LocalDate first = ZonedDateTime.ofInstant(seen.first(), spec.zone()).toLocalDate();
                final LocalDate last = ZonedDateTime.ofInstant(seen.last(), spec.zone()).toLocalDate();
                if (!first.isBefore(spec.windowFrom()) && !first.isBefore(burnedIn)) {
                    started.add(seen.value());
                } else if (last.isBefore(spec.windowFrom()) && seen.events() >= 3) {
                    stopped.add(seen.value());
                }
            }
            final boolean listed = field.getValue().size() <= LISTED_VALUES;
            values.add(new Values(field.getKey(), field.getValue().size(), started.size(), stopped.size(), listed
                    ? started
                    : List.of(), listed
                    ? stopped
                    : List.of(), data.seenCutShort().contains(field.getKey())));
        }
        return values;
    }

    // --------------------------------------------------------------------------------
    // Wording

    // A value notable enough to report: past the threshold, and by enough to matter.
    private static boolean notable(final String feature, final double value, final Score score) {
        if (Math.abs(score.z()) < Z_THRESHOLD) {
            return false;
        }
        final double difference = Math.abs(value - score.median());
        if (isTime(feature)) {
            return difference >= 60;
        }
        return difference >= minimum(feature) && difference >= 0.5 * Math.abs(score.median());
    }

    // The least change in a feature worth a finding, in its own units.
    private static double minimum(final String feature) {
        if (feature.equals(EVENTS)) {
            return 10;
        } else if (feature.equals(ACTIVE_HOURS)) {
            return 2;
        } else if (feature.startsWith(DISTINCT) || feature.startsWith(NOVEL) || feature.startsWith(CATEGORY)) {
            return 1;
        }
        return 0;
    }

    private static boolean isTime(final String feature) {
        return feature.equals(FIRST) || feature.equals(LAST);
    }

    // A feature the whole source's volume (everyone else's events per person) can explain: not a category's share.
    private static boolean comparableWithSource(final String feature) {
        return feature.equals(EVENTS) || feature.equals(ACTIVE_HOURS) || feature.startsWith(SUM);
    }

    /// @param feature A feature's name.
    /// @return How the result names it.
    static String label(final String feature) {
        if (feature.equals(EVENTS)) {
            return "Events";
        } else if (feature.equals(ACTIVE_HOURS)) {
            return "Active hours";
        } else if (feature.equals(FIRST)) {
            return "First activity of the day";
        } else if (feature.equals(LAST)) {
            return "Last activity of the day";
        } else if (feature.startsWith(SUM)) {
            return "Sum of " + feature.substring(SUM.length());
        } else if (feature.startsWith(DISTINCT)) {
            return "Distinct " + feature.substring(DISTINCT.length()) + " values";
        } else if (feature.startsWith(NOVEL)) {
            return "New " + feature.substring(NOVEL.length()) + " values";
        } else if (feature.equals(RHYTHM)) {
            return "Hour-of-week rhythm";
        } else if (feature.startsWith(CATEGORY)) {
            return "Events in the category " + feature.substring(CATEGORY.length());
        }
        return "Events against peers";
    }

    // The obvious other reading of a change, by feature and direction.
    private static String alternative(final String feature, final double direction) {
        final boolean up = direction > 0;
        if (feature.startsWith(CATEGORY)) {
            return up
                    ? "A work use of such a service (e.g. an approved file share), or values put in the wrong "
                      + "category (check the categories, and correct them)."
                    : "Less use of such services, or a source no longer recording them.";
        }
        if (isTime(feature)) {
            return "A change of hours, shift or time zone, travel, or on-call duty.";
        } else if (feature.startsWith(DISTINCT) || feature.startsWith(NOVEL)) {
            return up
                    ? "A new project, role or tool (e.g. a rollout), or reorganised systems."
                    : "Work narrowing to fewer systems, or a source no longer recording the field.";
        }
        return up
                ? "A busy period, a deadline, an approved bulk task (e.g. a migration), or a source recording more."
                : "Leave, sickness or reduced hours not recorded, or a source recording less.";
    }

    /// @param feature A feature's name.
    /// @param value   A value of it.
    /// @return The value as the result shows it: a time of day for the first and last activity.
    static String shown(final String feature, final double value) {
        if (isTime(feature)) {
            final long minutes = Math.round(value);
            return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
        }
        return round(value);
    }

    private static String signed(final String feature, final double value) {
        if (isTime(feature)) {
            return (value >= 0
                    ? "+"
                    : "-") + Math.round(Math.abs(value)) + " minutes";
        }
        return (value >= 0
                ? "+"
                : "") + round(value);
    }

    private static String perDay(final String feature) {
        return isTime(feature)
                ? ""
                : " a day";
    }

    /// @param value A value.
    /// @return It rounded to at most two decimal places, without trailing zeros.
    static String round(final double value) {
        if (Double.isNaN(value)) {
            return "-";
        }
        final double rounded = Math.round(value * 100) / 100.0;
        return rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded)
                : String.valueOf(rounded);
    }

    private static double mean(final double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double sum = 0;
        for (final double value : values) {
            sum += value;
        }
        return sum / values.length;
    }


    // --------------------------------------------------------------------------------


    /// The unit windows are compared in.
    enum Unit {
        /// A day, against the same weekday in the previous weeks.
        DAY("day"),
        /// A week (7 days from the window's start), against the baseline's weeks.
        WEEK("week"),
        /// A calendar month, against the baseline's months.
        MONTH("month");

        private final String word;

        Unit(final String word) {
            this.word = word;
        }

        /// @return Its name in a sentence.
        String word() {
            return word;
        }
    }


    // --------------------------------------------------------------------------------


    /// Why a day is or isn't scored.
    enum Status {
        /// Scored.
        SCORED,
        /// The source holds no data that day (retention, a gap).
        NO_SOURCE_DATA,
        /// Given as leave.
        LEAVE,
        /// In an approved exception given (e.g. a migration, an on-call week).
        EXCEPTION,
        /// In a run of quiet days no one named: reported, not scored.
        QUIET_RUN
    }


    // --------------------------------------------------------------------------------


    /// What a finding is of.
    enum Kind {
        /// A day against the same weekdays.
        DAY,
        /// A week against the baseline's.
        WEEK,
        /// A month against the baseline's.
        MONTH,
        /// A lasting shift.
        CHANGE,
        /// A drift month on month.
        DRIFT,
        /// The hour-of-week rhythm.
        RHYTHM,
        /// Against the peers.
        PEERS,
        /// Access outside the person's cluster.
        ACCESS,
        /// Where the activity came from.
        LOCATION
    }


    // --------------------------------------------------------------------------------


    /// What is compared.
    ///
    /// @param baselineFrom The baseline's first day.
    /// @param baselineTo   The day after the baseline's last.
    /// @param windowFrom   The window's first day.
    /// @param windowTo     The day after the window's last.
    /// @param unit         The unit windows are compared in.
    /// @param leave        The person's leave, each `{from, to}` (to inclusive).
    /// @param zone         The person's zone.
    /// @param exceptions   Approved exceptions (e.g. a migration): their days aren't scored, and are said.
    record Spec(LocalDate baselineFrom,
                LocalDate baselineTo,
                LocalDate windowFrom,
                LocalDate windowTo,
                Unit unit,
                List<LocalDate[]> leave,
                ZoneId zone,
                List<Excepted> exceptions) {

        /// Copies the lists.
        Spec {
            leave = List.copyOf(leave);
            exceptions = List.copyOf(exceptions);
        }

        /// Without exceptions.
        ///
        /// @param baselineFrom The baseline's first day.
        /// @param baselineTo   The day after the baseline's last.
        /// @param windowFrom   The window's first day.
        /// @param windowTo     The day after the window's last.
        /// @param unit         The unit windows are compared in.
        /// @param leave        The person's leave, each `{from, to}` (to inclusive).
        /// @param zone         The person's zone.
        Spec(final LocalDate baselineFrom,
             final LocalDate baselineTo,
             final LocalDate windowFrom,
             final LocalDate windowTo,
             final Unit unit,
             final List<LocalDate[]> leave,
             final ZoneId zone) {
            this(baselineFrom, baselineTo, windowFrom, windowTo, unit, leave, zone, List.of());
        }

        /// @param date A day.
        /// @return The approved exception it is in, if any.
        Optional<Excepted> exception(final LocalDate date) {
            return exceptions.stream().filter(excepted -> excepted.contains(date)).findFirst();
        }

        /// @param date A day.
        /// @return True if it is in the leave given.
        boolean onLeave(final LocalDate date) {
            return leave.stream().anyMatch(range -> !date.isBefore(range[0]) && !date.isAfter(range[1]));
        }
    }


    // --------------------------------------------------------------------------------


    /// An approved exception: days whose activity is explained (e.g. an approved migration, an on-call week).
    ///
    /// @param from   Its first day.
    /// @param to     Its last day (inclusive).
    /// @param reason Why, as given.
    record Excepted(LocalDate from, LocalDate to, String reason) {

        /// @param date A day.
        /// @return True if it is in it.
        boolean contains(final LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }


    // --------------------------------------------------------------------------------


    /// One source's aggregates, read.
    ///
    /// @param source       The source's name.
    /// @param label        Whose events they are, as the result says it (e.g. "person id 1234").
    /// @param fields       The sum and breadth fields.
    /// @param days         The person's days with activity.
    /// @param hourOfWeek   The person's hour-of-week profile a month.
    /// @param seen         Each breadth field's values with their first and last times (fields whose values
    ///                     couldn't be read are left out).
    /// @param seenCutShort The breadth fields whose values were cut short by the row limit.
    /// @param population   The whole source's volume by day.
    /// @param peers        The peers' daily events, or null without peers.
    /// @param extra        More daily features by name (e.g. `category:webmail`, events a day), scored as volume.
    record SourceData(String source,
                      String label,
                      ActivityProfiles.Fields fields,
                      SortedMap<LocalDate, Day> days,
                      SortedMap<YearMonth, double[]> hourOfWeek,
                      Map<String, List<Seen>> seen,
                      Set<String> seenCutShort,
                      SortedMap<LocalDate, Population> population,
                      PeerData peers,
                      Map<String, SortedMap<LocalDate, Double>> extra) {

        /// Without more features.
        ///
        /// @param source       The source's name.
        /// @param label        Whose events they are.
        /// @param fields       The sum and breadth fields.
        /// @param days         The person's days with activity.
        /// @param hourOfWeek   The person's hour-of-week profile a month.
        /// @param seen         Each breadth field's values with their first and last times.
        /// @param seenCutShort The breadth fields whose values were cut short.
        /// @param population   The whole source's volume by day.
        /// @param peers        The peers' daily events, or null without peers.
        SourceData(final String source,
                   final String label,
                   final ActivityProfiles.Fields fields,
                   final SortedMap<LocalDate, Day> days,
                   final SortedMap<YearMonth, double[]> hourOfWeek,
                   final Map<String, List<Seen>> seen,
                   final Set<String> seenCutShort,
                   final SortedMap<LocalDate, Population> population,
                   final PeerData peers) {
            this(source, label, fields, days, hourOfWeek, seen, seenCutShort, population, peers, Map.of());
        }

        /// @param more More daily features by name.
        /// @return It with them.
        SourceData withExtra(final Map<String, SortedMap<LocalDate, Double>> more) {
            return new SourceData(source, label, fields, days, hourOfWeek, seen, seenCutShort, population, peers,
                    more);
        }

        /// @param peerData The peers' daily events.
        /// @return It with them.
        SourceData withPeers(final PeerData peerData) {
            return new SourceData(source, label, fields, days, hourOfWeek, seen, seenCutShort, population, peerData,
                    extra);
        }
    }


    // --------------------------------------------------------------------------------


    /// The peers' daily events in one source.
    ///
    /// @param group The group, as the result names it (e.g. "Organisation 'Finance'").
    /// @param days  Each day's events by peer.
    record PeerData(String group, SortedMap<LocalDate, Map<String, Double>> days) {

    }


    // --------------------------------------------------------------------------------


    /// A finding: an indicator with its evidence, never a verdict.
    ///
    /// @param source      The source.
    /// @param feature     The feature.
    /// @param window      The day, week, month or range it is of.
    /// @param baseline    The baseline's value (a median).
    /// @param value       The value.
    /// @param score       Its size, for ranking (a robust z, or a shift's size, at most [#MAX_Z]).
    /// @param evidence    What was seen, in words.
    /// @param alternative An alternative explanation.
    /// @param kind        What it is of.
    /// @param sourceWide  True if everyone else's activity in the source moved as far the same way: a change in the
    ///                    source, listed apart from the person's findings.
    record Finding(String source,
                   String feature,
                   String window,
                   double baseline,
                   double value,
                   double score,
                   String evidence,
                   String alternative,
                   Kind kind,
                   boolean sourceWide) {

        /// The person's own (not source-wide).
        ///
        /// @param source      The source.
        /// @param feature     The feature.
        /// @param window      The day, week, month or range it is of.
        /// @param baseline    The baseline's value.
        /// @param value       The value.
        /// @param score       Its size, for ranking.
        /// @param evidence    What was seen, in words.
        /// @param alternative An alternative explanation.
        /// @param kind        What it is of.
        Finding(final String source,
                final String feature,
                final String window,
                final double baseline,
                final double value,
                final double score,
                final String evidence,
                final String alternative,
                final Kind kind) {
            this(source, feature, window, baseline, value, score, evidence, alternative, kind, false);
        }
    }


    // --------------------------------------------------------------------------------


    /// A month's line for one source.
    ///
    /// @param source     The source.
    /// @param month      The month.
    /// @param days       The month's days in the period.
    /// @param covered    The days the source held any data.
    /// @param active     The days the person had events.
    /// @param leave      The days of leave.
    /// @param values     Each feature's mean per scored day (the first and last activity: the median).
    /// @param peerMedian The peers' median events per day, or NaN.
    /// @param inWindow   True if the month overlaps the window.
    record MonthLine(String source,
                     YearMonth month,
                     int days,
                     int covered,
                     int active,
                     int leave,
                     Map<String, Double> values,
                     double peerMedian,
                     boolean inWindow) {

    }


    // --------------------------------------------------------------------------------


    /// A breadth field's values started and stopped.
    ///
    /// @param field        The field.
    /// @param total        Its distinct values in the period.
    /// @param started      How many were first seen in the window.
    /// @param stopped      How many were last seen before it.
    /// @param startedNames The started values, for a field of few values; else empty.
    /// @param stoppedNames The stopped values, likewise.
    /// @param cutShort     True if its values were cut short by the row limit, so the counts are approximate.
    record Values(String field,
                  int total,
                  int started,
                  int stopped,
                  List<String> startedNames,
                  List<String> stoppedNames,
                  boolean cutShort) {

    }


    // --------------------------------------------------------------------------------


    /// A block of days.
    ///
    /// @param from Its first day.
    /// @param to   The day after its last.
    record Block(LocalDate from, LocalDate to) {

        /// @return It, as the result names it.
        String label() {
            return from + " to " + to.minusDays(1);
        }
    }


    // --------------------------------------------------------------------------------


    // A change point with its date.
    private record Dated(LocalDate date, Shift shift) {

    }


    // --------------------------------------------------------------------------------


    /// One source scored.
    ///
    /// @param source   The source.
    /// @param findings Its findings, largest first.
    /// @param status   Each day's status.
    /// @param features Each feature's value on each scored day.
    /// @param months   A line a month.
    /// @param notes    What the result should say of it.
    /// @param values   Each breadth field's values started and stopped.
    record Scored(String source,
                  List<Finding> findings,
                  Map<LocalDate, Status> status,
                  Map<String, Map<LocalDate, Double>> features,
                  List<MonthLine> months,
                  List<String> notes,
                  List<Values> values) {

    }
}
