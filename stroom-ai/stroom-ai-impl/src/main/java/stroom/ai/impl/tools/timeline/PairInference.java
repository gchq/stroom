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

package stroom.ai.impl.tools.timeline;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/// Pairs of event types that bound spans, inferred from a user's own events: two types that, for the same key value
/// (the same process id, window or session, on the same host), alternate (A, B, A, B) for nearly every key, occur
/// about equally often, and where B follows A within a plausible time. No lists of words (Open, Close...): the
/// test is the data's, so a vendor's pair passes in any naming and unrelated types sharing a field don't.
///
/// - **Clear** (used): [#CLEAR_SHARE] of the keys alternating, at least [#MIN_KEYS] keys (or spans of one key),
///   the counts within [#EVEN_COUNTS] of each other.
/// - **Borderline** (listed with their figures, not used): half or more alternating, or clear but lopsided.
///
/// A type is in at most one inferred pair (the best). Types already in a pair (a skill's, the schema's) aren't
/// paired again.
final class PairInference {

    /// The share of keys alternating for a pair to be used.
    static final double CLEAR_SHARE = 0.9;
    /// The share of keys alternating for a pair to be listed as borderline.
    static final double BORDERLINE_SHARE = 0.5;
    /// The fewer type's count over the other's, for counts to be about equal.
    static final double EVEN_COUNTS = 0.8;
    /// The fewest keys with both types (or spans, of one key) for a pair to be used.
    static final int MIN_KEYS = 3;
    /// The longest a B may follow its A: past it, they aren't one span.
    static final long MAX_GAP_MS = 24 * 3_600_000L;
    /// A key path must be held by this share of each type's events.
    static final double KEY_COVERAGE = 0.8;
    /// The commonest types tried, of each source.
    static final int MAX_TYPES = 40;

    private PairInference() {
        // Static only.
    }

    /// Infers the pairs of each source.
    ///
    /// @param events The user's typed events.
    /// @param known  The pairs already given (a skill's, the schema's), whose types aren't paired again.
    /// @return The clear pairs (used) and the borderline ones (listed), the best first.
    static Inferred infer(final Collection<Typed> events, final Collection<PairRule> known) {
        final Map<Integer, List<Typed>> bySource = new TreeMap<>();
        events.forEach(event -> bySource.computeIfAbsent(event.source(), s -> new ArrayList<>()).add(event));
        final List<Candidate> clear = new ArrayList<>();
        final List<Candidate> borderline = new ArrayList<>();
        for (final Map.Entry<Integer, List<Typed>> source : bySource.entrySet()) {
            final Set<String> taken = new HashSet<>();
            for (final PairRule rule : known) {
                if (rule.source() < 0 || rule.source() == source.getKey()) {
                    taken.add(lower(rule.start()));
                    taken.add(lower(rule.end()));
                }
            }
            final List<Candidate> candidates = candidates(source.getKey(), source.getValue(), taken);
            candidates.sort(Comparator.comparingDouble(Candidate::share).reversed()
                    .thenComparing(Comparator.comparingInt(Candidate::keys).reversed())
                    .thenComparing(Candidate::start).thenComparing(Candidate::end));
            for (final Candidate candidate : candidates) {
                final boolean free = !taken.contains(lower(candidate.start())) && !taken.contains(lower(
                        candidate.end()));
                if (candidate.clear() && free) {
                    clear.add(candidate);
                    taken.add(lower(candidate.start()));
                    taken.add(lower(candidate.end()));
                } else if (!candidate.clear() && free) {
                    borderline.add(candidate);
                }
            }
        }
        // A borderline pair whose type a clear one took later is no longer a choice.
        final Set<String> used = new HashSet<>();
        clear.forEach(candidate -> {
            used.add(candidate.source() + "\u0000" + lower(candidate.start()));
            used.add(candidate.source() + "\u0000" + lower(candidate.end()));
        });
        return new Inferred(clear, borderline.stream()
                .filter(candidate -> !used.contains(candidate.source() + "\u0000" + lower(candidate.start()))
                                     && !used.contains(candidate.source() + "\u0000" + lower(candidate.end())))
                .toList());
    }

    /// How well a pair alternates in some events (as `check_skill` checks a skill's pair still does).
    ///
    /// @param events The typed events.
    /// @param start  The start type.
    /// @param end    The end type.
    /// @param by     The key path, or null for the lane alone.
    /// @return The figures, or null if the events hold neither type.
    static Candidate evaluate(final Collection<Typed> events, final String start, final String end, final String by) {
        final List<Typed> a = events.stream().filter(event -> event.isType(start)).toList();
        final List<Typed> b = events.stream().filter(event -> event.isType(end)).toList();
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        final int source = a.isEmpty()
                ? b.getFirst().source()
                : a.getFirst().source();
        return figures(source, start, end, by, a, b);
    }

    // Each pair of the source's commonest types sharing a key path, with its figures, at its best key path.
    private static List<Candidate> candidates(final int source, final List<Typed> events, final Set<String> taken) {
        final Map<String, List<Typed>> byType = new LinkedHashMap<>();
        for (final Typed event : events) {
            // An event of a type in a pair already (by its TypeId, or by its element and Action, as the schema's
            // pairs name them) isn't paired again.
            if (event.type() != null && !taken.contains(lower(event.type()))
                && !taken.contains(lower(event.schemaType()))) {
                byType.computeIfAbsent(event.type(), t -> new ArrayList<>()).add(event);
            }
        }
        final List<String> types = byType.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .sorted((x, y) -> Integer.compare(y.getValue().size(), x.getValue().size()))
                .limit(MAX_TYPES)
                .map(Map.Entry::getKey)
                .toList();
        final Map<String, Set<String>> paths = new LinkedHashMap<>();
        types.forEach(type -> paths.put(type, keyPaths(byType.get(type))));
        final List<Candidate> out = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            for (int j = i + 1; j < types.size(); j++) {
                final String x = types.get(i);
                final String y = types.get(j);
                final List<String> shared = new ArrayList<>(paths.get(x));
                shared.retainAll(paths.get(y));
                // A key ties the two only where they share its values, and says more than the lane does.
                shared.removeIf(path -> !informative(path, byType.get(x), byType.get(y)));
                // The lane alone is a key too, for pairs such as a lock and an unlock of one host.
                shared.add(null);
                Candidate best = null;
                for (final String path : shared) {
                    for (final boolean forward : new boolean[]{true, false}) {
                        final String start = forward
                                ? x
                                : y;
                        final String end = forward
                                ? y
                                : x;
                        final Candidate candidate = figures(source, start, end, path, byType.get(start),
                                byType.get(end));
                        if (candidate != null && candidate.keys() > 0 && (best == null || better(candidate, best))) {
                            best = candidate;
                        }
                    }
                }
                if (best != null && best.share() >= BORDERLINE_SHARE && best.keys() >= 2) {
                    out.add(best);
                }
            }
        }
        return out;
    }

    private static boolean better(final Candidate a, final Candidate b) {
        if (a.share() != b.share()) {
            return a.share() > b.share();
        }
        if (a.keys() != b.keys()) {
            return a.keys() > b.keys();
        }
        // A key path over the lane alone: it says more.
        return a.by() != null && b.by() == null;
    }

    /// A key path the two types' events are worth matching by: some value is held by both (a key whose values
    /// never repeat across them, e.g. an event's own id, can't pair them), and it isn't fixed by the lane (a host's
    /// own name, a source's constant), which the lane alone already matches by.
    ///
    /// @param path The key path.
    /// @param a    One type's events.
    /// @param b    The other's.
    /// @return True if it is.
    static boolean informative(final String path, final List<Typed> a, final List<Typed> b) {
        final Set<String> inA = new HashSet<>();
        a.forEach(event -> NullSafe.consume(event.keys().get(path), inA::add));
        boolean shared = false;
        for (final Typed event : b) {
            if (inA.contains(event.keys().get(path))) {
                shared = true;
                break;
            }
        }
        if (!shared) {
            return false;
        }
        final Map<String, Set<String>> byLane = new HashMap<>();
        for (final List<Typed> events : List.of(a, b)) {
            for (final Typed event : events) {
                final String value = event.keys().get(path);
                if (value != null) {
                    byLane.computeIfAbsent(Objects.requireNonNullElse(event.lane(), ""), lane -> new HashSet<>())
                            .add(value);
                }
            }
        }
        return byLane.values().stream().anyMatch(values -> values.size() > 1);
    }

    // The key paths most of a type's events hold.
    private static Set<String> keyPaths(final List<Typed> events) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        events.forEach(event -> event.keys().keySet().forEach(path -> counts.merge(path, 1, Integer::sum)));
        final Set<String> out = new LinkedHashSet<>();
        counts.forEach((path, count) -> {
            if (count >= KEY_COVERAGE * events.size()) {
                out.add(path);
            }
        });
        return out;
    }

    // How the two types alternate, keyed by the lane and the path's value.
    private static Candidate figures(final int source,
                                     final String start,
                                     final String end,
                                     final String by,
                                     final List<Typed> a,
                                     final List<Typed> b) {
        final Map<String, List<Typed>> byKey = new LinkedHashMap<>();
        for (final Typed event : a) {
            final String key = key(event, by);
            if (key != null) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
            }
        }
        for (final Typed event : b) {
            final String key = key(event, by);
            if (key != null) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
            }
        }
        int keys = 0;
        int alternating = 0;
        final List<Long> gaps = new ArrayList<>();
        for (final List<Typed> group : byKey.values()) {
            if (group.size() < 2) {
                continue;
            }
            keys++;
            group.sort(Comparator.comparingLong(Typed::time).thenComparing(Typed::id));
            boolean alternates = true;
            for (int i = 0; i < group.size() && alternates; i++) {
                final boolean wantStart = i % 2 == 0;
                final Typed event = group.get(i);
                if (event.isType(start) != wantStart) {
                    alternates = false;
                } else if (!wantStart) {
                    final long gap = event.time() - group.get(i - 1).time();
                    if (gap > MAX_GAP_MS) {
                        alternates = false;
                    } else {
                        gaps.add(gap);
                    }
                }
            }
            if (alternates) {
                alternating++;
            }
        }
        if (keys == 0) {
            return new Candidate(source, start, end, by, 0, 0, a.size(), b.size(), 0, false);
        }
        gaps.sort(Long::compare);
        final double share = (double) alternating / keys;
        final double even = Math.min(a.size(), b.size()) / (double) Math.max(1, Math.max(a.size(), b.size()));
        // Enough evidence: several keys, or several spans of one key (a lock and an unlock of one host).
        final boolean clear = share >= CLEAR_SHARE && even >= EVEN_COUNTS
                              && (keys >= MIN_KEYS || gaps.size() >= MIN_KEYS) && !gaps.isEmpty();
        return new Candidate(source, start, end, by, share, keys, a.size(), b.size(), gaps.isEmpty()
                ? 0
                : gaps.get(gaps.size() / 2), clear);
    }

    private static String key(final Typed event, final String by) {
        if (by == null) {
            return Objects.requireNonNullElse(event.lane(), "");
        }
        final String value = event.keys().get(by);
        return value == null
                ? null
                : Objects.requireNonNullElse(event.lane(), "") + "\u0000" + value;
    }

    private static String lower(final String text) {
        return text == null
                ? ""
                : text.toLowerCase(Locale.ROOT);
    }


    // --------------------------------------------------------------------------------


    /// One event as pairs are found by.
    ///
    /// @param id         Its id.
    /// @param source     Its source, by its place.
    /// @param time       Its time.
    /// @param lane       Its host (else its source's name).
    /// @param type       Its type (its TypeId, else its element and Action).
    /// @param schemaType Its element and Action, or null.
    /// @param keys       The values under its element, by their paths.
    record Typed(String id, int source, long time, String lane, String type, String schemaType,
                 Map<String, String> keys) {

        /// Copies the map.
        Typed {
            keys = keys == null
                    ? Map.of()
                    : Map.copyOf(keys);
        }

        /// @param name A type.
        /// @return True if it is this event's (its TypeId's or its element and Action's), in any case.
        boolean isType(final String name) {
            return name != null && (name.equalsIgnoreCase(String.valueOf(type))
                                    || name.equalsIgnoreCase(String.valueOf(schemaType)));
        }
    }


    // --------------------------------------------------------------------------------


    /// A pair of types and how they alternate.
    ///
    /// @param source      Its source, by its place.
    /// @param start       The type that starts a span.
    /// @param end         The type that ends it.
    /// @param by          The key path the two are matched by, or null for the lane alone.
    /// @param share       The share of keys (with both types) where they alternate.
    /// @param keys        The keys with both types.
    /// @param startCount  The start type's events.
    /// @param endCount    The end type's events.
    /// @param medianGapMs The middle time from a start to its end.
    /// @param clear       True if it is used.
    record Candidate(int source,
                     String start,
                     String end,
                     String by,
                     double share,
                     int keys,
                     int startCount,
                     int endCount,
                     long medianGapMs,
                     boolean clear) {

        /// @return Its share as a whole percentage.
        int percent() {
            return (int) Math.round(share * 100);
        }
    }


    // --------------------------------------------------------------------------------


    /// The pairs inferred.
    ///
    /// @param clear      Those used.
    /// @param borderline Those listed with their figures, not used.
    record Inferred(List<Candidate> clear, List<Candidate> borderline) {

        /// Copies the lists.
        Inferred {
            clear = List.copyOf(clear);
            borderline = List.copyOf(borderline);
        }
    }
}
