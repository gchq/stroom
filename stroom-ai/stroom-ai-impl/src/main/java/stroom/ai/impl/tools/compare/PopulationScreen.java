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
import stroom.ai.impl.tools.compare.ActivityProfiles.Seen;
import stroom.ai.impl.tools.compare.ActivityScoring.Finding;
import stroom.ai.impl.tools.compare.ActivityScoring.PeerData;
import stroom.ai.impl.tools.compare.ActivityScoring.Spec;
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.AggregateQuery.Bucket;
import stroom.ai.impl.tools.compare.AggregateQuery.Kind;
import stroom.ai.impl.tools.compare.AggregateQuery.Metric;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `compare_activity`'s population screen (`scope: population`): every person in a source scored against their own
/// baseline by the same aggregates and scoring as one person's comparison ([ActivityProfiles], [ActivityScoring]),
/// the person field a group of each aggregate, so one aggregate a slice carries everyone (never a query a person);
/// then ranked. The aggregates by hour (and by category or country a day or hour) are cut into weeks, the rest into
/// months (by the runner), so a slice stays under the row limit for a few hundred people; a slice cut short is said.
final class PopulationScreen {

    /// The most people the result ranks; the rest are in the people file.
    static final int TOP = 10;
    /// A person's score is the sum of their largest this many findings' scores.
    static final int RANKED_FINDINGS = 3;
    /// The days an aggregate by hour is cut into.
    static final int SLICE_DAYS = 7;
    /// The share of the people screened who must share a finding's kind and feature for it to be the population's
    /// (everyone's work, or the source's), not ranked.
    static final double COMMON_SHARE = 0.25;
    /// The fewest people who must share a finding's kind and feature for it to be the population's.
    static final int MIN_COMMON = 3;
    /// The fewest days after the baseline's start a lasting change must be to re-baseline a person from it.
    static final int REBASE_AFTER_DAYS = 28;
    /// The fewest days of baseline a person re-baselined must keep before the window.
    static final int REBASE_KEEP_DAYS = 56;
    /// The minutes a day's first (last) activity must be before (after) every baseline day's to be ranked.
    static final int HOUR_MARGIN = 60;
    static final String DAILY = "daily";
    static final String HOURLY = "hourly";
    static final String POPULATION = "population";
    static final String SEEN = "seen:";
    static final String CATEGORIES = "categories";
    static final String LOCATION = "location";
    static final String FEEDS = "feeds";
    static final String ACTIVE = "active";
    private static final Pattern GROUP_COLUMN = Pattern.compile(AggregateQuery.GROUP_PREFIX + "(\\d+)");

    private PopulationScreen() {
        // Static only.
    }

    /// Adds one source's aggregates of everyone to a batch, each named `<prefix><name>`: the daily features and the
    /// events by hour by person, everyone's volume a day (not limited by `where`, so a day the source holds no
    /// matching event isn't taken for a gap), each breadth field's values by person with their first and last times,
    /// and, where the source has them, events by person and category a day, by person and country an hour, the
    /// feeds and (for peers) the people active with one event's ids each.
    ///
    /// @param batch  The batch.
    /// @param prefix The source's prefix of its aggregates' names.
    /// @param source The source.
    /// @param where  Each field's values an event must hold (the call's `where`); empty for all.
    /// @param from   The period's start.
    /// @param to     The period's end.
    /// @param zone   The zone the days are in.
    /// @param peers  True to add the people active, for their groups.
    static void queries(final Batch batch,
                        final String prefix,
                        final CompareActivityTool.Planned source,
                        final Map<String, List<String>> where,
                        final Instant from,
                        final Instant to,
                        final ZoneId zone,
                        final boolean peers) {
        final Source index = source.source();
        final String person = source.scope().field();
        final Fields fields = source.fields();
        batch.add(prefix + DAILY, List.of(daily(index, person, fields, where, from, to)));
        batch.add(prefix + HOURLY, sliced(new AggregateQuery(index, where, from, to, Bucket.HOUR, List.of(person),
                List.of(new Metric(Kind.COUNT, null, ActivityProfiles.EVENTS)), ActivityProfiles.MAX_ROWS), zone,
                SLICE_DAYS));
        batch.add(prefix + POPULATION, List.of(ActivityProfiles.population(index, person, fields, from, to)));
        for (final String field : fields.breadth()) {
            // Not bucketed, so cut into months here: a value is a row a person each month, merged when read.
            final List<Metric> metrics = List.of(new Metric(Kind.MIN_TIME, null, ActivityProfiles.FIRST),
                    new Metric(Kind.MAX_TIME, null, ActivityProfiles.LAST),
                    new Metric(Kind.COUNT, null, ActivityProfiles.EVENTS));
            batch.add(prefix + SEEN + field, IndexAggregates.months(new AggregateQuery(index, where, from, to,
                    Bucket.NONE, List.of(person, field), metrics, ActivityProfiles.MAX_ROWS), zone));
        }
        if (source.categories() && source.categoryField() != null) {
            batch.add(prefix + CATEGORIES, sliced(new AggregateQuery(index, where, from, to, Bucket.DAY,
                    List.of(person, source.categoryField()), List.of(new Metric(Kind.COUNT, null,
                    ActivityProfiles.EVENTS)), ActivityProfiles.MAX_ROWS), zone, SLICE_DAYS));
        }
        if (source.location() && source.countryField() != null) {
            final List<Metric> metrics = new ArrayList<>();
            metrics.add(new Metric(Kind.COUNT, null, ActivityProfiles.EVENTS));
            if (source.pointField() != null) {
                metrics.add(new Metric(Kind.CENTROID, source.pointField(), ActivityLocation.PLACE));
            }
            batch.add(prefix + LOCATION, sliced(new AggregateQuery(index, where, from, to, Bucket.HOUR,
                    List.of(person, source.countryField()), metrics, ActivityProfiles.MAX_ROWS), zone, SLICE_DAYS));
        }
        if (source.feedBy() != null) {
            batch.add(prefix + FEEDS, List.of(ActivityProfiles.feeds(index, source.feedBy(), from, to)));
        }
        if (peers && source.hasEventIds()) {
            final List<Metric> metrics = List.of(new Metric(Kind.COUNT, null, ActivityProfiles.EVENTS),
                    new Metric(Kind.ONE_EVENT, null, ActivityProfiles.ONE_EVENT));
            batch.add(prefix + ACTIVE, List.of(new AggregateQuery(index, where, from, to, Bucket.NONE,
                    List.of(person), metrics, ActivityProfiles.MAX_ROWS)));
        }
    }

    /// Everyone's daily features by person: events, first and last times, each sum and each distinct count.
    ///
    /// @param source The index.
    /// @param person The person field.
    /// @param fields The sum and breadth fields.
    /// @param where  Each field's values an event must hold; empty for all.
    /// @param from   The start.
    /// @param to     The end.
    /// @return The aggregate.
    static AggregateQuery daily(final Source source,
                                final String person,
                                final Fields fields,
                                final Map<String, List<String>> where,
                                final Instant from,
                                final Instant to) {
        return new AggregateQuery(source, where, from, to, Bucket.DAY, List.of(person),
                ActivityProfiles.dailyMetrics(fields), ActivityProfiles.MAX_ROWS);
    }

    /// Cuts a query's range into slices of some days, each starting at a midnight in the zone (the first at the
    /// query's start), so a day is never in two slices.
    ///
    /// @param query The query.
    /// @param zone  The zone.
    /// @param days  The days a slice.
    /// @return The slices, in order.
    static List<AggregateQuery> sliced(final AggregateQuery query, final ZoneId zone, final int days) {
        final List<AggregateQuery> slices = new ArrayList<>();
        Instant start = query.from();
        while (start.isBefore(query.to())) {
            final Instant next = start.atZone(zone).toLocalDate().plusDays(days).atStartOfDay(zone).toInstant();
            final Instant end = next.isBefore(query.to())
                    ? next
                    : query.to();
            slices.add(query.between(start, end));
            start = end;
        }
        return slices;
    }

    /// Joins the tables of a query's slices into one.
    ///
    /// @param tables The slices' tables.
    /// @return Their rows together, cut short if any was, the queries counted.
    static Table merged(final List<Table> tables) {
        final List<Map<String, String>> rows = new ArrayList<>();
        final Set<String> notes = new LinkedHashSet<>();
        boolean cutShort = false;
        int queries = 0;
        String text = null;
        for (final Table table : tables) {
            rows.addAll(table.rows());
            notes.addAll(table.notes());
            cutShort |= table.cutShort();
            queries += table.queries();
            if (text == null) {
                text = table.text();
            }
        }
        return new Table(rows, cutShort, queries, new ArrayList<>(notes), text);
    }

    /// Splits an aggregate grouped first by the person field into a table a person, as one person's aggregate would
    /// give it: the person's column taken out and the other group columns moved down (`g1` becomes `g0`). A row with
    /// no person is left out.
    ///
    /// @param table The aggregate's table.
    /// @return Each person's table, in the order first seen.
    static Map<String, Table> byPerson(final Table table) {
        final Map<String, List<Map<String, String>>> rows = new LinkedHashMap<>();
        final String personColumn = AggregateQuery.groupColumn(0);
        for (final Map<String, String> row : table.rows()) {
            final String who = NullSafe.trim(row.get(personColumn));
            if (who.isEmpty()) {
                continue;
            }
            final Map<String, String> own = new LinkedHashMap<>();
            row.forEach((column, value) -> {
                if (!column.equals(personColumn)) {
                    own.put(shifted(column), value);
                }
            });
            rows.computeIfAbsent(who, k -> new ArrayList<>()).add(own);
        }
        final Map<String, Table> tables = new LinkedHashMap<>();
        rows.forEach((who, own) -> tables.put(who, new Table(own, table.cutShort(), table.queries(), List.of(),
                table.text())));
        return tables;
    }

    // A group column one place down; any other column as it is.
    private static String shifted(final String column) {
        final Matcher matcher = GROUP_COLUMN.matcher(column);
        if (!matcher.matches()) {
            return column;
        }
        return AggregateQuery.groupColumn(Integer.parseInt(matcher.group(1)) - 1);
    }

    /// Reads a field's values from a person's table of slices (a value a row a slice), each merged: the earliest
    /// first time, the latest last and the events added.
    ///
    /// @param table The person's table.
    /// @return Each value once, in the order first given.
    static List<Seen> seen(final Table table) {
        final Map<String, Seen> merged = new LinkedHashMap<>();
        for (final Seen seen : ActivityProfiles.seen(table)) {
            merged.merge(seen.value(), seen, (a, b) -> new Seen(a.value(), a.first().isBefore(b.first())
                    ? a.first()
                    : b.first(), a.last().isAfter(b.last())
                    ? a.last()
                    : b.last(), a.events() + b.events()));
        }
        return List.copyOf(merged.values());
    }

    /// Reads a person's events by category a day (their table of the category aggregate: the day and the
    /// category), as more daily features, `category:<name>`; the category `other` left out, as one person's are.
    ///
    /// @param table The person's table, or null.
    /// @return Each category's events a day.
    static Map<String, SortedMap<LocalDate, Double>> categories(final Table table) {
        final Map<String, SortedMap<LocalDate, Double>> daily = new TreeMap<>();
        if (table == null) {
            return daily;
        }
        for (final Map<String, String> row : table.rows()) {
            final String category = NullSafe.trim(row.get(AggregateQuery.groupColumn(0)));
            final LocalDate date = date(row.get(AggregateQuery.BUCKET_COLUMN));
            if (category.isEmpty() || date == null || ActivityCategories.OTHER.equalsIgnoreCase(category)) {
                continue;
            }
            daily.computeIfAbsent(ActivityScoring.CATEGORY + category, k -> new TreeMap<>()).merge(date,
                    ActivityProfiles.number(row.get(ActivityProfiles.EVENTS)), Double::sum);
        }
        return daily;
    }

    /// Each person's peers' daily events, from everyone's daily aggregate: the others of their group, none for a
    /// person whose group isn't known or who is alone in it.
    ///
    /// @param groupOf Each person's group (people whose group isn't known left out).
    /// @param daily   Each person's daily table.
    /// @param element The element grouped by, as the result names it (`Organisation`).
    /// @return Each person's peers' days.
    static Map<String, PeerData> peers(final Map<String, String> groupOf,
                                       final Map<String, Table> daily,
                                       final String element) {
        final Map<String, SortedMap<LocalDate, Double>> events = new LinkedHashMap<>();
        daily.forEach((who, table) -> {
            final SortedMap<LocalDate, Double> days = new TreeMap<>();
            for (final Map<String, String> row : table.rows()) {
                final LocalDate date = date(row.get(AggregateQuery.BUCKET_COLUMN));
                if (date != null) {
                    days.merge(date, ActivityProfiles.number(row.get(ActivityProfiles.EVENTS)), Double::sum);
                }
            }
            events.put(who, days);
        });
        final Map<String, PeerData> peers = new LinkedHashMap<>();
        for (final String who : daily.keySet()) {
            final String group = groupOf.get(who);
            if (NullSafe.isBlankString(group)) {
                continue;
            }
            final SortedMap<LocalDate, Map<String, Double>> days = new TreeMap<>();
            groupOf.forEach((other, otherGroup) -> {
                if (!other.equals(who) && group.equals(otherGroup) && events.containsKey(other)) {
                    events.get(other).forEach((date, count) -> days.computeIfAbsent(date,
                            k -> new LinkedHashMap<>()).put(other, count));
                }
            });
            if (!days.isEmpty()) {
                peers.put(who, new PeerData(element + " '" + group + "'", days));
            }
        }
        return peers;
    }

    /// The day a person is re-baselined from: their latest lasting change (a change point of their own, not the
    /// source's) at least [#REBASE_AFTER_DAYS] after the baseline's start and [#REBASE_KEEP_DAYS] before the
    /// window, so a new role or team months before the window is their new normal, not an indicator in it.
    ///
    /// @param spec     Their scoring's periods.
    /// @param findings Their findings over the whole baseline.
    /// @return The day, if any.
    static Optional<LocalDate> rebaseline(final Spec spec, final List<Finding> findings) {
        final LocalDate earliest = spec.baselineFrom().plusDays(REBASE_AFTER_DAYS);
        final LocalDate latest = spec.windowFrom().minusDays(REBASE_KEEP_DAYS);
        return findings.stream()
                .filter(finding -> finding.kind() == ActivityScoring.Kind.CHANGE && !finding.sourceWide())
                .map(finding -> date(finding.window()))
                .filter(date -> date != null && !date.isBefore(earliest) && !date.isAfter(latest))
                .max(Comparator.naturalOrder());
    }

    /// Scores one person in one source as their own comparison would, in each unit asked: first over the whole
    /// baseline, then, where a lasting change months before the window re-baselines them ([#rebaseline]), from it.
    ///
    /// @param given The periods, their leave and exceptions (the unit aside).
    /// @param data  Their aggregates in the source.
    /// @param units The units windows are compared in.
    /// @return Their findings (each once), their daily features and the day re-baselined from, if any.
    static PersonScore person(final Spec given, final ActivityScoring.SourceData data, final List<Unit> units) {
        final ActivityScoring.Scored whole = ActivityScoring.score(inUnit(given, Unit.WEEK), data);
        final Optional<LocalDate> rebased = rebaseline(given, whole.findings());
        final Spec based = rebased.map(day -> from(given, day)).orElse(given);
        final ActivityScoring.Scored weekly = rebased.isEmpty()
                ? whole
                : ActivityScoring.score(inUnit(based, Unit.WEEK), data);
        final List<Finding> all = new ArrayList<>();
        for (final Unit unit : units) {
            all.addAll(unit == Unit.WEEK
                    ? weekly.findings()
                    : ActivityScoring.score(inUnit(based, unit), data).findings());
        }
        return new PersonScore(based, distinct(all), weekly.features(), rebased.orElse(null));
    }

    private static Spec inUnit(final Spec spec, final Unit unit) {
        return new Spec(spec.baselineFrom(), spec.baselineTo(), spec.windowFrom(), spec.windowTo(), unit,
                spec.leave(), spec.zone(), spec.exceptions());
    }

    /// @param spec A person's scoring's periods.
    /// @param from The day their baseline starts from instead.
    /// @return The periods with it.
    static Spec from(final Spec spec, final LocalDate from) {
        return new Spec(from, spec.baselineTo(), spec.windowFrom(), spec.windowTo(), spec.unit(), spec.leave(),
                spec.zone(), spec.exceptions());
    }

    /// Ranks the people by their findings: each person's score the sum of their largest scores of
    /// [#RANKED_FINDINGS] different features (one finding a source and feature counted, whatever its kind), so
    /// someone with several strong indicators ranks above someone with one, and one indicator found in each unit
    /// counts once; those with none aren't ranked. Not ranked (still in the findings file): a finding everyone else's
    /// activity shares (the source's); a finding of a kind and feature that at least [#COMMON_SHARE] of the people
    /// (and [#MIN_COMMON]) have over overlapping days, the population's (everyone's browsing stepping up together); a
    /// window with no activity at all, an absence (leave not given); a day's count (one-off busy days are everyone's:
    /// a day is ranked by when its activity was, and only outside the hours of every day of their baseline in the
    /// source); a category the person already used (only one new to them is
    /// ranked); and a lasting change dated before the window,
    /// with a drift of the same feature (that step), their new normal by the window.
    ///
    /// @param findings   Each person's findings, across the sources.
    /// @param windowFrom The window's first day.
    /// @param hours      Each person's hours in each source's baseline ([#hours]); a person or source missing has
    ///                   every day's time ranked.
    /// @return The people with any finding ranked, highest first (then by person), and what wasn't ranked.
    static Ranking ranked(final Map<String, List<Finding>> findings,
                          final LocalDate windowFrom,
                          final Map<String, Map<String, Hours>> hours) {
        // Each kind and feature's findings, by person, with the days they span.
        final Map<String, Map<String, List<LocalDate[]>>> spans = new TreeMap<>();
        findings.forEach((who, all) -> all.stream()
                .filter(finding -> !finding.sourceWide() && isComparable(finding))
                .forEach(finding -> NullSafe.consume(span(finding), days -> spans.computeIfAbsent(key(finding),
                        k -> new LinkedHashMap<>()).computeIfAbsent(who, k -> new ArrayList<>()).add(days))));
        final int common = Math.max(MIN_COMMON, (int) Math.ceil(COMMON_SHARE * findings.size()));
        final Map<String, Integer> shared = new TreeMap<>();
        final List<Ranked> ranked = new ArrayList<>();
        int earlier = 0;
        int absent = 0;
        for (final Map.Entry<String, List<Finding>> person : findings.entrySet()) {
            final Set<String> stepped = new LinkedHashSet<>();
            person.getValue().stream()
                    .filter(finding -> isEarlierChange(finding, windowFrom))
                    .forEach(finding -> stepped.add(finding.source() + "|" + finding.feature()));
            final List<Finding> own = new ArrayList<>();
            for (final Finding finding : person.getValue()) {
                if (finding.sourceWide()) {
                    continue;
                }
                final int sharing = sharing(finding, person.getKey(), spans);
                if (sharing >= common) {
                    shared.merge(key(finding), sharing, Math::max);
                    continue;
                }
                if (isAbsence(finding)) {
                    absent++;
                    continue;
                }
                if (isDayVolume(finding) || isUsualCategory(finding)
                    || isWithinHours(finding, hours.getOrDefault(person.getKey(), Map.of()).get(finding.source()))) {
                    continue;
                }
                if (isEarlierChange(finding, windowFrom) || (finding.kind() == ActivityScoring.Kind.DRIFT
                                                             && stepped.contains(finding.source() + "|"
                                                                                 + finding.feature()))) {
                    earlier++;
                    continue;
                }
                own.add(finding);
            }
            if (own.isEmpty()) {
                continue;
            }
            own.sort(Comparator.comparingDouble(Finding::score).reversed());
            final Set<String> counted = new LinkedHashSet<>();
            double score = 0;
            for (final Finding finding : own) {
                if (counted.size() < RANKED_FINDINGS && counted.add(finding.source() + "|" + finding.feature())) {
                    score += finding.score();
                }
            }
            final Set<String> sources = new LinkedHashSet<>();
            own.forEach(finding -> sources.add(finding.source()));
            ranked.add(new Ranked(person.getKey(), score, own, List.copyOf(sources)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(Ranked::person));
        return new Ranking(ranked, shared, earlier, absent);
    }

    /// Each finding once: the same kind, feature and window found in more than one unit (a change point, a drift)
    /// kept once, the largest.
    ///
    /// @param findings The findings of every unit.
    /// @return Them, each once, largest first.
    static List<Finding> distinct(final List<Finding> findings) {
        final Map<String, Finding> kept = new LinkedHashMap<>();
        for (final Finding finding : findings) {
            kept.merge(finding.source() + "|" + finding.kind() + "|" + finding.feature() + "|" + finding.window(),
                    finding, (a, b) -> a.score() >= b.score()
                            ? a
                            : b);
        }
        return kept.values().stream().sorted(Comparator.comparingDouble(Finding::score).reversed()).toList();
    }

    // What a finding is of: its source, kind and feature.
    private static String key(final Finding finding) {
        return finding.source() + "|" + finding.kind().name().toLowerCase() + "|" + finding.feature();
    }

    // The people (the person among them) with a finding of the same kind and feature over days overlapping it.
    private static int sharing(final Finding finding,
                               final String person,
                               final Map<String, Map<String, List<LocalDate[]>>> spans) {
        final LocalDate[] days = isComparable(finding)
                ? span(finding)
                : null;
        if (days == null) {
            return 0;
        }
        int sharing = 0;
        for (final Map.Entry<String, List<LocalDate[]>> other : spans.getOrDefault(key(finding), Map.of())
                .entrySet()) {
            if (other.getKey().equals(person) || other.getValue().stream().anyMatch(theirs ->
                    !theirs[0].isAfter(days[1]) && !theirs[1].isBefore(days[0]))) {
                sharing++;
            }
        }
        return sharing;
    }

    // The kinds a population shares as one: a person's own rarities, places, peers and access are theirs alone.
    private static boolean isComparable(final Finding finding) {
        return switch (finding.kind()) {
            case DAY, WEEK, MONTH, CHANGE, DRIFT, RHYTHM -> true;
            default -> false;
        };
    }

    /// The days a finding spans, read from its window: a day; days `from to to`; months `yyyy-MM to yyyy-MM`; a
    /// month; a change point the week either side of its day.
    ///
    /// @param finding The finding.
    /// @return Its first and last day, or null if its window can't be read.
    static LocalDate[] span(final Finding finding) {
        final String[] parts = finding.window().split(" to ");
        final LocalDate[] first = days(parts[0]);
        final LocalDate[] last = parts.length > 1
                ? days(parts[1])
                : first;
        if (first == null || last == null) {
            return null;
        }
        return finding.kind() == ActivityScoring.Kind.CHANGE
                ? new LocalDate[]{first[0].minusDays(7), first[0].plusDays(7)}
                : new LocalDate[]{first[0], last[1]};
    }

    // A date's day twice, or a month's first and last days.
    private static LocalDate[] days(final String value) {
        final LocalDate date = date(value);
        if (date != null) {
            return new LocalDate[]{date, date};
        }
        try {
            final YearMonth month = YearMonth.parse(value.strip());
            return new LocalDate[]{month.atDay(1), month.atEndOfMonth()};
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    // A window with no activity at all where the baseline had some: an absence (leave no one gave), not ranked.
    private static boolean isAbsence(final Finding finding) {
        final boolean block = finding.kind() == ActivityScoring.Kind.DAY || finding.kind() == ActivityScoring.Kind.WEEK
                              || finding.kind() == ActivityScoring.Kind.MONTH;
        return block && finding.value() == 0 && finding.baseline() > 0;
    }

    // A day's count against the same weekdays (one-off busy days are everyone's): a screen ranks a day only by
    // when its activity was (a night's), and counts by the week and month.
    private static boolean isDayVolume(final Finding finding) {
        return finding.kind() == ActivityScoring.Kind.DAY && !finding.feature().equals(ActivityScoring.FIRST)
               && !finding.feature().equals(ActivityScoring.LAST);
    }

    /// A person's hours in one source's baseline: their earliest first and latest last activity of any day, in
    /// minutes since midnight.
    ///
    /// @param features   Their daily features in the source.
    /// @param windowFrom The window's first day: the days before it are the baseline's.
    /// @return Their hours, or null where the baseline has no day with activity.
    static Hours hours(final Map<String, Map<LocalDate, Double>> features, final LocalDate windowFrom) {
        final double earliest = features.getOrDefault(ActivityScoring.FIRST, Map.of()).entrySet().stream()
                .filter(day -> day.getKey().isBefore(windowFrom))
                .mapToDouble(Map.Entry::getValue)
                .min()
                .orElse(Double.NaN);
        final double latest = features.getOrDefault(ActivityScoring.LAST, Map.of()).entrySet().stream()
                .filter(day -> day.getKey().isBefore(windowFrom))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(Double.NaN);
        return Double.isNaN(earliest) || Double.isNaN(latest)
                ? null
                : new Hours(earliest, latest);
    }

    // A day's first or last activity within the hours of every day of their baseline in the source (an hour's
    // margin): a sparse source's times wander within the working day; a night outside any day of theirs is ranked.
    private static boolean isWithinHours(final Finding finding, final Hours hours) {
        if (hours == null || finding.kind() != ActivityScoring.Kind.DAY) {
            return false;
        }
        if (finding.feature().equals(ActivityScoring.FIRST)) {
            return finding.value() >= hours.earliest() - HOUR_MARGIN;
        } else if (finding.feature().equals(ActivityScoring.LAST)) {
            return finding.value() <= hours.latest() + HOUR_MARGIN;
        }
        return false;
    }

    // A rise in a category the person already used (a median above nothing): their work's ebb and flow, as
    // everyone's; a category new to them is ranked.
    private static boolean isUsualCategory(final Finding finding) {
        return finding.feature().startsWith(ActivityScoring.CATEGORY) && finding.baseline() > 0;
    }

    private static boolean isEarlierChange(final Finding finding, final LocalDate windowFrom) {
        final LocalDate date = finding.kind() == ActivityScoring.Kind.CHANGE
                ? date(finding.window())
                : null;
        return date != null && date.isBefore(windowFrom);
    }

    private static LocalDate date(final String value) {
        if (NullSafe.isBlankString(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (final DateTimeParseException e) {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// One person ranked.
    ///
    /// @param person   The person (their person id, or logon id where a source has no person field).
    /// @param score    Their score: the sum of their largest findings' scores.
    /// @param findings Their own findings, largest first.
    /// @param sources  The sources their findings are from.
    record Ranked(String person, double score, List<Finding> findings, List<String> sources) {

        /// Copies the lists.
        Ranked {
            findings = List.copyOf(findings);
            sources = List.copyOf(sources);
        }
    }


    // --------------------------------------------------------------------------------


    /// One person scored in one source.
    ///
    /// @param spec     Their periods, re-baselined where they were.
    /// @param findings Their findings, each once, largest first.
    /// @param features Each feature's value on each day scored.
    /// @param rebased  The day they were re-baselined from, or null.
    record PersonScore(Spec spec,
                       List<Finding> findings,
                       Map<String, Map<LocalDate, Double>> features,
                       LocalDate rebased) {

        /// Copies the findings.
        PersonScore {
            findings = List.copyOf(findings);
        }
    }


    // --------------------------------------------------------------------------------


    /// Everyone's daily features in one source, summed by feature and day, so a person's finding is compared with
    /// everyone else's: a rise everyone else's per person shares as far (a category everyone's browsing doubles in
    /// after a pipeline change) is the population's, told apart as the source's shifts are.
    static final class Everyone {

        // A feature's days, each the sum of everyone's values and how many people had one.
        private final Map<String, Map<LocalDate, double[]>> totals = new LinkedHashMap<>();

        /// @param features One person's daily features.
        void add(final Map<String, Map<LocalDate, Double>> features) {
            features.forEach((feature, days) -> {
                final Map<LocalDate, double[]> sums = totals.computeIfAbsent(feature, k -> new TreeMap<>());
                days.forEach((date, value) -> {
                    final double[] sum = sums.computeIfAbsent(date, k -> new double[2]);
                    sum[0] += value;
                    sum[1]++;
                });
            });
        }

        /// A person's finding, marked as the population's (listed apart, not ranked) where everyone else's per
        /// person moved the same way, past the threshold and at least half as far: a window against the baseline's
        /// windows of the same unit, a change point the four weeks after it against the four before, a drift its
        /// last three months against its first three. Times of day, rhythm and the rest are a person's own.
        ///
        /// @param finding The finding.
        /// @param own     The person's daily features.
        /// @param spec    The periods.
        /// @return It, or it marked as the population's.
        Finding checked(final Finding finding, final Map<String, Map<LocalDate, Double>> own, final Spec spec) {
            final String feature = finding.feature();
            if (finding.sourceWide() || !totals.containsKey(feature) || feature.equals(ActivityScoring.FIRST)
                || feature.equals(ActivityScoring.LAST)) {
                return finding;
            }
            final Map<LocalDate, Double> mine = own.getOrDefault(feature, Map.of());
            final double sign = Math.signum(finding.value() - finding.baseline());
            final boolean shared = switch (finding.kind()) {
                case WEEK, MONTH -> sharedWindow(finding, mine, spec, sign);
                case CHANGE -> {
                    final LocalDate date = date(finding.window());
                    yield date != null && sharedStep(feature, mine, date.minusDays(28), date, date,
                            date.plusDays(28), finding, sign);
                }
                case DRIFT -> {
                    final String[] months = finding.window().split(" to ");
                    final LocalDate first = month(months[0]);
                    final LocalDate last = months.length > 1
                            ? month(months[1])
                            : null;
                    yield first != null && last != null && sharedStep(feature, mine, first, first.plusMonths(3),
                            last.minusMonths(2), last.plusMonths(1), finding, sign);
                }
                default -> false;
            };
            return shared
                    ? new Finding(finding.source(), feature, finding.window(), finding.baseline(), finding.value(),
                    finding.score(), finding.evidence(), "Everyone else's " + ActivityScoring.label(feature)
                                                         .toLowerCase(Locale.ROOT)
                                                         + " per person moved as far the same way: the "
                                                         + "population's (the source, or everyone's work), not the "
                                                         + "person's.", finding.kind(), true)
                    : finding;
        }

        private boolean sharedWindow(final Finding finding,
                                     final Map<LocalDate, Double> mine,
                                     final Spec spec,
                                     final double sign) {
            final String[] days = finding.window().split(" to ");
            final LocalDate from = date(days[0]);
            final LocalDate to = days.length > 1
                    ? date(days[1])
                    : null;
            if (from == null || to == null) {
                return false;
            }
            final Double others = others(finding.feature(), mine, from, to.plusDays(1));
            final Unit unit = finding.kind() == ActivityScoring.Kind.WEEK
                    ? Unit.WEEK
                    : Unit.MONTH;
            final double[] baseline = ActivityScoring.blocks(spec.baselineFrom(), spec.baselineTo(), unit).stream()
                    .map(block -> others(finding.feature(), mine, block.from(), block.to()))
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            if (others == null || baseline.length < ActivityStatistics.MIN_BASELINE) {
                return false;
            }
            return ActivityStatistics.robustZ(others, baseline)
                    .map(theirs -> Math.signum(theirs.z()) == sign
                                   && Math.abs(theirs.z()) >= ActivityScoring.Z_THRESHOLD
                                   && relative(others, theirs.median())
                                      >= 0.5 * relative(finding.value(), finding.baseline()))
                    .orElse(false);
        }

        private boolean sharedStep(final String feature,
                                   final Map<LocalDate, Double> mine,
                                   final LocalDate beforeFrom,
                                   final LocalDate beforeTo,
                                   final LocalDate afterFrom,
                                   final LocalDate afterTo,
                                   final Finding finding,
                                   final double sign) {
            final Double before = others(feature, mine, beforeFrom, beforeTo);
            final Double after = others(feature, mine, afterFrom, afterTo);
            return before != null && after != null && Math.signum(after - before) == sign
                   && relative(after, before) >= 0.5 * relative(finding.value(), finding.baseline());
        }

        // Everyone else's mean a person-day of a feature over some days, the person's own left out; null with
        // fewer than a window's days.
        private Double others(final String feature,
                              final Map<LocalDate, Double> mine,
                              final LocalDate from,
                              final LocalDate to) {
            final Map<LocalDate, double[]> sums = totals.getOrDefault(feature, Map.of());
            double total = 0;
            int days = 0;
            for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
                final double[] sum = sums.get(date);
                if (sum == null) {
                    continue;
                }
                final Double value = mine.get(date);
                final double people = sum[1] - (value == null
                        ? 0
                        : 1);
                if (people > 0) {
                    total += (sum[0] - (value == null
                            ? 0
                            : value)) / people;
                    days++;
                }
            }
            return days < ActivityScoring.MIN_WINDOW_DAYS
                    ? null
                    : total / days;
        }

        private static double relative(final double value, final double baseline) {
            return baseline == 0
                    ? (value == 0
                            ? 0
                            : 1)
                    : Math.abs(value - baseline) / Math.abs(baseline);
        }

        private static LocalDate month(final String value) {
            try {
                return YearMonth.parse(value.strip()).atDay(1);
            } catch (final DateTimeParseException e) {
                return null;
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// A person's hours in one source's baseline.
    ///
    /// @param earliest Their earliest first activity of any day, in minutes since midnight.
    /// @param latest   Their latest last activity of any day, likewise.
    record Hours(double earliest, double latest) {

    }


    // --------------------------------------------------------------------------------


    /// The people ranked, and what wasn't ranked.
    ///
    /// @param ranked  The people with any finding ranked, highest first.
    /// @param common  Each finding's `source|kind|feature` shared by many people over overlapping days, with the
    ///                most sharing one: not ranked.
    /// @param earlier The lasting changes before the window (and drifts that are their step): not ranked.
    /// @param absent  The windows with no activity at all (absences): not ranked.
    record Ranking(List<Ranked> ranked, Map<String, Integer> common, int earlier, int absent) {

        /// Copies the collections.
        Ranking {
            ranked = List.copyOf(ranked);
            common = new TreeMap<>(common);
        }
    }


    // --------------------------------------------------------------------------------


    /// Aggregates gathered to run together, each named and perhaps cut into slices, read back by name with the
    /// slices joined.
    static final class Batch {

        private final List<AggregateQuery> queries = new ArrayList<>();
        private final Map<String, int[]> at = new LinkedHashMap<>();

        /// @param name  The aggregate's name.
        /// @param parts Its query, or its slices.
        void add(final String name, final List<AggregateQuery> parts) {
            at.put(name, new int[]{queries.size(), parts.size()});
            queries.addAll(parts);
        }

        /// @return Every query, in order.
        List<AggregateQuery> queries() {
            return List.copyOf(queries);
        }

        /// @param tables The queries' tables, in order.
        /// @return Each aggregate's table by name, its slices joined.
        Map<String, Table> read(final List<Table> tables) {
            final Map<String, Table> read = new LinkedHashMap<>();
            at.forEach((name, place) -> read.put(name, merged(tables.subList(place[0], place[0] + place[1]))));
            return read;
        }
    }
}
