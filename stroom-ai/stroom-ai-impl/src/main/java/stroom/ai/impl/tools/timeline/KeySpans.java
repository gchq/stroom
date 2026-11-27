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

import stroom.ai.impl.tools.timeline.PairInference.Typed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/// Spans bounded by a key (agreed 2026-11-24): from the first to the last of a user's events sharing a key's value
/// (a session id, a destination, an application), even where no event starts or ends it, split where the user is
/// idle past the cap. Found by the same key search pairs are ([PairInference]): a key is **session-like**, and used
/// without being asked for, when events of two or more types hold it, its values repeat (on [PairInference#MIN_KEYS]
/// values or more), say more than the lane does, and nearly every value ([#ONE_RUN_SHARE]) makes one stretch only (a
/// VPN session's id, where a domain or an application recurs all day, every day); a key a pair is already matched by
/// is left to the pair. Other keys (a destination domain, an application) are the call's (a source's `spanBy`).
final class KeySpans {

    /// The share of a key's values that make one stretch only, for it to be session-like.
    static final double ONE_RUN_SHARE = 0.8;

    private KeySpans() {
        // Static only.
    }

    /// The session-like keys of each source.
    ///
    /// @param events The user's typed events.
    /// @param pairs  The pairs applied, whose keys are left to them.
    /// @param idle   The user's idle gaps, past the cap.
    /// @return The keys, by source then path.
    static List<KeyRule> find(final Collection<Typed> events, final Collection<PairRule> pairs, final Idle idle) {
        final Map<Integer, List<Typed>> bySource = new TreeMap<>();
        events.forEach(event -> bySource.computeIfAbsent(event.source(), s -> new ArrayList<>()).add(event));
        final List<KeyRule> out = new ArrayList<>();
        for (final Map.Entry<Integer, List<Typed>> source : bySource.entrySet()) {
            final Set<String> paired = new HashSet<>();
            for (final PairRule pair : pairs) {
                if ((pair.source() < 0 || pair.source() == source.getKey()) && pair.by() != null) {
                    for (final String path : pair.by().split("\\|")) {
                        paired.add(path.strip());
                    }
                }
            }
            final Map<String, Set<String>> typesByPath = new LinkedHashMap<>();
            for (final Typed event : source.getValue()) {
                event.keys().keySet().forEach(path -> typesByPath.computeIfAbsent(path, p -> new LinkedHashSet<>())
                        .add(event.type()));
            }
            typesByPath.keySet().stream().sorted().forEach(path -> {
                if (!paired.contains(path) && typesByPath.get(path).size() >= 2
                    && sessionLike(path, source.getValue(), idle)) {
                    out.add(new KeyRule(source.getKey(), path, false));
                }
            });
        }
        return out;
    }

    // Nearly every value one stretch, enough values repeating, and more than the lane says.
    private static boolean sessionLike(final String path, final List<Typed> events, final Idle idle) {
        final List<Typed> holding = events.stream().filter(event -> event.keys().get(path) != null).toList();
        if (!PairInference.informative(path, holding, holding)) {
            return false;
        }
        final Map<String, List<Typed>> byValue = new LinkedHashMap<>();
        for (final Typed event : holding) {
            byValue.computeIfAbsent(Objects.requireNonNullElse(event.lane(), "") + "\u0000"
                                    + event.keys().get(path), value -> new ArrayList<>()).add(event);
        }
        int repeating = 0;
        int oneRun = 0;
        for (final List<Typed> group : byValue.values()) {
            if (group.size() < 2) {
                continue;
            }
            repeating++;
            group.sort(Comparator.comparingLong(Typed::time));
            if (idle.runs(group.stream().map(Typed::time).toList()).size() == 1) {
                oneRun++;
            }
        }
        return repeating >= PairInference.MIN_KEYS && oneRun >= ONE_RUN_SHARE * repeating;
    }


    // --------------------------------------------------------------------------------


    /// A key spans are bounded by.
    ///
    /// @param source Its source, by its place.
    /// @param path   The key's path (or an index field's name).
    /// @param given  True if the call (or a skill) gave it; false if it was found as session-like.
    record KeyRule(int source, String path, boolean given) {

        /// @param value A value of it.
        /// @return A span's label: the value, where the key was given; else the key's name and the value, e.g.
        /// `SessionId 8614`.
        String label(final String value) {
            if (given) {
                return value;
            }
            final String last = path.substring(path.lastIndexOf('/') + 1);
            final String name = last.startsWith("Data[") && last.endsWith("]")
                    ? last.substring(5, last.length() - 1)
                    : last;
            return name + " " + value;
        }
    }


    // --------------------------------------------------------------------------------


    /// When the user was idle: the gaps between their consecutive events (any source, any host) past the cap.
    static final class Idle {

        private final List<long[]> gaps = new ArrayList<>();

        /// @param times Every event's time.
        /// @param capMs The idle cap.
        Idle(final Collection<Long> times, final long capMs) {
            final List<Long> sorted = new ArrayList<>(times);
            sorted.sort(Long::compare);
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i) - sorted.get(i - 1) > capMs) {
                    gaps.add(new long[]{sorted.get(i - 1), sorted.get(i)});
                }
            }
        }

        /// @param times Times, in order.
        /// @return Them in runs, a run split where an idle gap falls between two of them.
        List<List<Long>> runs(final List<Long> times) {
            final List<List<Long>> runs = new ArrayList<>();
            List<Long> current = null;
            Long last = null;
            for (final Long time : times) {
                if (current == null || idleBetween(last, time)) {
                    current = new ArrayList<>();
                    runs.add(current);
                }
                current.add(time);
                last = time;
            }
            return runs;
        }

        private boolean idleBetween(final long from, final long to) {
            for (final long[] gap : gaps) {
                if (gap[0] >= from && gap[1] <= to) {
                    return true;
                }
            }
            return false;
        }
    }
}
