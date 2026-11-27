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

import stroom.ai.impl.tools.compare.ActivityProfiles.Population;
import stroom.ai.impl.tools.compare.ActivityProfiles.Seen;
import stroom.ai.impl.tools.compare.ActivityScoring.Block;
import stroom.ai.impl.tools.compare.ActivityScoring.Finding;
import stroom.ai.impl.tools.compare.ActivityScoring.Kind;
import stroom.ai.impl.tools.compare.ActivityScoring.Spec;
import stroom.ai.impl.tools.compare.ActivityScoring.Unit;
import stroom.ai.impl.tools.compare.ActivityStatistics.Score;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
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

/// What was done to the person (`compare_activity` with `includeTargets`), scored apart from their own activity:
/// daily features of the events whose target is them (their own acts on themselves left out), all together and by
/// type, and the accounts acting on them for the first time (each actor's first time, after the burn-in), each
/// scored as the person's own features are (robust z against the same weekdays, or the window's total against the
/// baseline's weeks or months, runs merged), rises only (fewer things done to someone isn't an indicator). Their
/// own findings group, never ranked with the person's, nor in volume, rhythm, breadth or peers. The person's leave
/// is scored (things done to an account while its owner is away are the point), approved exceptions aren't, a day
/// the source holds no data isn't; and the source-wide check applies to them separately: everyone else's events
/// done to them per person, where the index can give it.
final class TargetScoring {

    /// The prefix of every feature of what was done to the person.
    static final String PREFIX = "done_to_them:";
    /// The events done to them a day.
    static final String EVENTS = PREFIX + "events";
    /// The prefix of the events of one type done to them a day.
    static final String TYPE = PREFIX + "type:";
    /// The accounts acting on them for the first time a day.
    static final String NEW_ACTORS = PREFIX + "new_actors";
    /// The most new actors named in a finding.
    static final int NAMED_ACTORS = 5;
    /// The most types scored, the most frequent first.
    static final int MAX_TYPES = 20;

    private TargetScoring() {
        // Static only.
    }

    /// @param feature A feature's name.
    /// @return True if it is of what was done to the person.
    static boolean isTarget(final String feature) {
        return feature != null && feature.startsWith(PREFIX);
    }

    /// Scores what was done to the person in one source.
    ///
    /// @param spec The periods, unit, leave and zone.
    /// @param data What was done to them, read.
    /// @return Its findings and what the window held.
    static Scored score(final Spec spec, final TargetData data) {
        final List<String> notes = new ArrayList<>();
        final List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = spec.baselineFrom(); d.isBefore(spec.windowTo()); d = d.plusDays(1)) {
            dates.add(d);
        }
        final Set<LocalDate> scored = new LinkedHashSet<>();
        for (final LocalDate date : dates) {
            final boolean covered = data.coverage().isEmpty()
                                    || (data.coverage().get(date) != null
                                        && data.coverage().get(date).events() > 0);
            if (covered && spec.exception(date).isEmpty()) {
                scored.add(date);
            }
        }
        final Map<String, Map<LocalDate, String>> names = new LinkedHashMap<>();
        final Map<String, Map<LocalDate, Double>> features = features(spec, data, scored, names);
        final Map<LocalDate, Double> population = population(data, scored, features.get(EVENTS));
        final List<Finding> findings = new ArrayList<>();
        switch (spec.unit()) {
            case DAY -> findings.addAll(dayFindings(spec, data, features, population, names));
            case WEEK, MONTH -> findings.addAll(ActivityScoring.merged(windowFindings(spec, data, features,
                    scored, population, names, notes), spec.unit()));
        }
        findings.sort(Comparator.comparingDouble(Finding::score).reversed());
        if (data.population().isEmpty()) {
            notes.add("Everyone else's events done to them couldn't be read here (an index other than Elastic "
                      + "compares no two fields): what was done to the person isn't checked against the source.");
        }
        final Map<String, Long> inWindow = new TreeMap<>();
        data.byType().forEach((date, types) -> {
            if (!date.isBefore(spec.windowFrom()) && date.isBefore(spec.windowTo())) {
                types.forEach((type, count) -> inWindow.merge(type, Math.round(count), Long::sum));
            }
        });
        final List<String> newInWindow = new ArrayList<>();
        names.getOrDefault(NEW_ACTORS, Map.of()).forEach((date, actors) -> {
            if (!date.isBefore(spec.windowFrom()) && date.isBefore(spec.windowTo())) {
                newInWindow.addAll(List.of(actors.split(", ")));
            }
        });
        return new Scored(data.source(), data.label(), findings, inWindow, newInWindow, notes);
    }

    // The features on each scored day: the events done to them, by type, and the accounts acting on them for the
    // first time (with their names by day).
    private static Map<String, Map<LocalDate, Double>> features(final Spec spec,
                                                                final TargetData data,
                                                                final Set<LocalDate> scored,
                                                                final Map<String, Map<LocalDate, String>> names) {
        final Map<String, Double> totals = new LinkedHashMap<>();
        data.byType().values().forEach(types -> types.forEach((type, count) -> totals.merge(type, count,
                Double::sum)));
        final List<String> types = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .filter(type -> !type.isEmpty())
                .limit(MAX_TYPES)
                .toList();
        final Map<String, Map<LocalDate, Double>> features = new LinkedHashMap<>();
        features.put(EVENTS, new TreeMap<>());
        types.forEach(type -> features.put(TYPE + type, new TreeMap<>()));
        final boolean actors = data.actors() != null;
        if (actors) {
            features.put(NEW_ACTORS, new TreeMap<>());
        }
        final LocalDate burnedIn = spec.baselineFrom().plusDays(ActivityScoring.NOVELTY_BURN_IN_DAYS);
        final Map<LocalDate, List<String>> firstSeen = new TreeMap<>();
        if (actors) {
            for (final Seen seen : data.actors()) {
                final LocalDate first = ZonedDateTime.ofInstant(seen.first(), spec.zone()).toLocalDate();
                if (!first.isBefore(burnedIn)) {
                    firstSeen.computeIfAbsent(first, k -> new ArrayList<>()).add(seen.value());
                }
            }
        }
        for (final LocalDate date : scored) {
            final Map<String, Double> day = data.byType().getOrDefault(date, Map.of());
            features.get(EVENTS).put(date, day.values().stream().mapToDouble(Double::doubleValue).sum());
            types.forEach(type -> features.get(TYPE + type).put(date, day.getOrDefault(type, 0.0)));
            if (actors) {
                final List<String> first = firstSeen.getOrDefault(date, List.of());
                features.get(NEW_ACTORS).put(date, (double) first.size());
                if (!first.isEmpty()) {
                    names.computeIfAbsent(NEW_ACTORS, k -> new TreeMap<>()).put(date, String.join(", ", first));
                }
            }
        }
        return features;
    }

    // Everyone else's events done to them per person each scored day: the person's own left out.
    private static Map<LocalDate, Double> population(final TargetData data,
                                                     final Set<LocalDate> scored,
                                                     final Map<LocalDate, Double> own) {
        final Map<LocalDate, Double> population = new TreeMap<>();
        for (final LocalDate date : scored) {
            final Population p = data.population().get(date);
            final double ownEvents = own.getOrDefault(date, 0.0);
            final double others = p == null
                    ? 0
                    : p.people() - (ownEvents > 0
                            ? 1
                            : 0);
            if (p != null && others > 0) {
                population.put(date, Math.max(0, p.events() - ownEvents) / others);
            } else if (!data.population().isEmpty()) {
                population.put(date, 0.0);
            }
        }
        return population;
    }

    private static List<Finding> dayFindings(final Spec spec,
                                             final TargetData data,
                                             final Map<String, Map<LocalDate, Double>> features,
                                             final Map<LocalDate, Double> population,
                                             final Map<String, Map<LocalDate, String>> names) {
        final List<Finding> findings = new ArrayList<>();
        for (final Map.Entry<String, Map<LocalDate, Double>> feature : features.entrySet()) {
            for (LocalDate date = spec.windowFrom(); date.isBefore(spec.windowTo()); date = date.plusDays(1)) {
                final Double value = feature.getValue().get(date);
                if (value == null) {
                    continue;
                }
                final Optional<Score> score = ActivityStatistics.robustZ(value, ActivityScoring.sameWeekday(
                        feature.getValue(), date));
                if (score.isEmpty() || !notable(value, score.get())) {
                    continue;
                }
                final String weekday = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK);
                final Double others = population.get(date);
                final boolean shared = !feature.getKey().equals(NEW_ACTORS) && others != null
                                       && ActivityStatistics.robustZ(others, ActivityScoring.sameWeekday(population,
                        date)).map(theirs -> ActivityScoring.sharedShift(value, score.get(), others, theirs))
                                               .orElse(false);
                findings.add(new Finding(data.source(), feature.getKey(), date.toString(), score.get().median(),
                        value, ActivityScoring.capped(score.get().z()), label(feature.getKey()) + " "
                                                                         + ActivityScoring.round(value) + " on "
                                                                         + weekday + " " + date
                                                                         + named(names, feature.getKey(), date,
                                                                                 date.plusDays(1))
                                                                         + " against a median of "
                                                                         + ActivityScoring.round(score.get()
                                .median()) + " on the " + score.get().n() + " previous " + weekday + "s (robust z "
                                                                         + ActivityScoring.robustZText(score.get()
                                .z()) + ")", alternative(feature.getKey(), shared, spec.unit()), Kind.DAY, shared));
            }
        }
        return findings;
    }

    private static List<Finding> windowFindings(final Spec spec,
                                                final TargetData data,
                                                final Map<String, Map<LocalDate, Double>> features,
                                                final Set<LocalDate> scored,
                                                final Map<LocalDate, Double> population,
                                                final Map<String, Map<LocalDate, String>> names,
                                                final List<String> notes) {
        final List<Block> windows = ActivityScoring.blocks(spec.windowFrom(), spec.windowTo(), spec.unit());
        final List<Block> baseline = ActivityScoring.blocks(spec.baselineFrom(), spec.baselineTo(), spec.unit());
        final double[] basePopulation = baseline.stream()
                .map(block -> total(population, block))
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray();
        final List<Finding> findings = new ArrayList<>();
        boolean tooFew = false;
        for (final Map.Entry<String, Map<LocalDate, Double>> feature : features.entrySet()) {
            final double[] base = baseline.stream()
                    .map(block -> total(feature.getValue(), block))
                    .filter(v -> v != null)
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            if (base.length < ActivityStatistics.MIN_BASELINE) {
                tooFew = true;
                continue;
            }
            for (final Block window : windows) {
                final Double value = total(feature.getValue(), window);
                if (value == null) {
                    continue;
                }
                final Score score = ActivityStatistics.robustZ(value, base).orElseThrow();
                if (!notable(value, score)) {
                    continue;
                }
                final Double others = total(population, window);
                final boolean shared = !feature.getKey().equals(NEW_ACTORS) && others != null
                                       && ActivityStatistics.robustZ(others, basePopulation)
                                               .map(theirs -> ActivityScoring.sharedShift(value, score, others,
                                                       theirs))
                                               .orElse(false);
                findings.add(new Finding(data.source(), feature.getKey(), window.label(), score.median(), value,
                        ActivityScoring.capped(score.z()), label(feature.getKey()) + " " + ActivityScoring.round(value)
                                                           + " in the " + spec.unit().word() + " from "
                                                           + window.from() + named(names, feature.getKey(),
                        window.from(), window.to()) + " against a median of " + ActivityScoring.round(score.median())
                                                           + " over " + score.n() + " baseline " + spec.unit().word()
                                                           + "s (robust z " + ActivityScoring.robustZText(score.z())
                                                           + ")", alternative(feature.getKey(), shared, spec.unit()),
                        spec.unit() == Unit.WEEK
                                ? Kind.WEEK
                                : Kind.MONTH, shared));
            }
        }
        if (tooFew) {
            notes.add("Fewer than " + ActivityStatistics.MIN_BASELINE + " baseline " + spec.unit().word() + "s for "
                      + "what was done to them: not scored (a longer baseline, or unit day).");
        }
        return findings;
    }

    // A rise only, past the threshold, by at least one and half the baseline's level.
    private static boolean notable(final double value, final Score score) {
        final double difference = value - score.median();
        return score.z() >= ActivityScoring.Z_THRESHOLD && difference >= 1
               && difference >= 0.5 * Math.abs(score.median());
    }

    // A feature's total over a block's scored days; null if it has too few (the window's total, as one event done
    // to someone is the indicator, not its mean a day).
    private static Double total(final Map<LocalDate, Double> series, final Block block) {
        int days = 0;
        double sum = 0;
        for (LocalDate d = block.from(); d.isBefore(block.to()); d = d.plusDays(1)) {
            final Double value = series.get(d);
            if (value != null) {
                days++;
                sum += value;
            }
        }
        return days < ActivityScoring.MIN_WINDOW_DAYS
                ? null
                : sum;
    }

    // The new actors' names in a range, for the evidence.
    private static String named(final Map<String, Map<LocalDate, String>> names,
                                final String feature,
                                final LocalDate from,
                                final LocalDate to) {
        if (!NEW_ACTORS.equals(feature)) {
            return "";
        }
        final List<String> actors = new ArrayList<>();
        names.getOrDefault(NEW_ACTORS, Map.of()).forEach((date, text) -> {
            if (!date.isBefore(from) && date.isBefore(to)) {
                actors.addAll(List.of(text.split(", ")));
            }
        });
        return actors.isEmpty()
                ? ""
                : " (" + String.join(", ", actors.stream().limit(NAMED_ACTORS).toList()) + (actors.size()
                                                                                           > NAMED_ACTORS
                        ? ", ..."
                        : "") + ")";
    }

    /// @param feature A feature of what was done to the person.
    /// @return How the result names it.
    static String label(final String feature) {
        if (feature.equals(EVENTS)) {
            return "Events done to them";
        } else if (feature.equals(NEW_ACTORS)) {
            return "Accounts acting on them for the first time";
        }
        return "Done to them: " + feature.substring(TYPE.length());
    }

    private static String alternative(final String feature, final boolean shared, final Unit unit) {
        if (shared) {
            return "Everyone else had as much more done to them that " + unit.word() + ": a change in the source "
                   + "(e.g. an access review or a bulk change), not the person's.";
        }
        if (feature.equals(NEW_ACTORS)) {
            return "A new administrator, helpdesk or service account taking over routine work, or someone acting on "
                   + "the account who hasn't before: check who they are and why.";
        }
        return "Routine administration (an access review, a reset the person asked for, a joiner, mover or leaver "
               + "change), or someone else acting on the account: check who did it and why.";
    }


    // --------------------------------------------------------------------------------


    /// What was done to the person in one source, read.
    ///
    /// @param source     The source's name.
    /// @param label      How the target field was found, as the result says it.
    /// @param byType     Each day's events done to them by type ("" where the index has no type field).
    /// @param actors     Each account that acted on them, with its first and last times; null if not read.
    /// @param population Everyone's events done to them by someone else and the people they were done to, by day;
    ///                   empty where the index can't give it.
    /// @param coverage   The whole source's volume by day, for the days it holds data; empty if not known.
    record TargetData(String source,
                      String label,
                      SortedMap<LocalDate, Map<String, Double>> byType,
                      List<Seen> actors,
                      SortedMap<LocalDate, Population> population,
                      SortedMap<LocalDate, Population> coverage) {

    }


    // --------------------------------------------------------------------------------


    /// What was done to the person in one source, scored.
    ///
    /// @param source    The source.
    /// @param label     How the target field was found.
    /// @param findings  The findings, largest first (some may be source-wide).
    /// @param inWindow  The events done to them in the window, by type.
    /// @param newActors The accounts acting on them for the first time in the window.
    /// @param notes     What the result should say of it.
    record Scored(String source,
                  String label,
                  List<Finding> findings,
                  Map<String, Long> inWindow,
                  List<String> newActors,
                  List<String> notes) {

    }
}
