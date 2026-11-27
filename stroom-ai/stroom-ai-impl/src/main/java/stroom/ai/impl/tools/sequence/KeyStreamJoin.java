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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.impl.tools.sequence.KeyGroupSource.KeyGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/// The sort-merge of a streaming join over one key range: each step's side read in key order ([KeyGroupSource]),
/// merged holding only the current key's events. A key counts when every step that must happen has it and none
/// that must not; a step read as keys (an entity condition, e.g. a team's users) says only that, and the steps read
/// as events are then matched in time ([StreamSequences]). It ends when a side every match needs has no more keys,
/// or the budget ends reading.
///
/// It also counts what a join that finds nothing is diagnosed by: the keys of each step, and how many of them step
/// 1 shares with each other step; and, for a bounded sequence, the keys whose first timed step happened but whose
/// last never did, so "it didn't happen" is told apart from "the source may be missing events".
final class KeyStreamJoin {

    /// How many example keys are kept for what the result says.
    static final int EXAMPLES = 5;

    private KeyStreamJoin() {
        // Static helpers only.
    }

    /// Merges the sides of one key range.
    ///
    /// @param steps      Each step: how it is read and whether it must happen.
    /// @param sources    Each step's side, in the steps' order.
    /// @param until      The until step's side, or null.
    /// @param comparator The order of keys.
    /// @param maxspanMs  The window of a sequence, in milliseconds.
    /// @param keep       The most matches to keep (all are counted).
    /// @param budget     What the join may read.
    /// @return What the range gave.
    static RangeResult run(final List<StepRole> steps,
                           final List<KeyGroupSource> sources,
                           final KeyGroupSource until,
                           final Comparator<List<Object>> comparator,
                           final long maxspanMs,
                           final int keep,
                           final JoinBudget budget) {
        final int n = steps.size();
        final List<Integer> timed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (steps.get(i).timed()) {
                timed.add(i);
            }
        }
        final List<StreamSequences.Rule> rules = timed.stream()
                .map(i -> new StreamSequences.Rule(steps.get(i).missing(), steps.get(i).runs()))
                .toList();
        final int opener = timed.isEmpty()
                ? -1
                : timed.getFirst();
        int closer = -1;
        for (final int i : timed) {
            if (!steps.get(i).missing()) {
                closer = i;
            }
        }
        final boolean bounded = opener >= 0 && closer > opener;

        final List<KeyGroup> current = new ArrayList<>();
        for (final KeyGroupSource source : sources) {
            current.add(source.next());
        }
        KeyGroup untilGroup = until == null
                ? null
                : until.next();
        final Tally tally = new Tally(n);
        while (true) {
            List<Object> min = null;
            for (final KeyGroup group : current) {
                if (group != null && (min == null || comparator.compare(group.key(), min) < 0)) {
                    min = group.key();
                }
            }
            if (min == null || ended(steps, current, opener, closer, bounded)) {
                break;
            }
            budget.mergedKey();
            final List<KeyGroup> atKey = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final KeyGroup group = current.get(i);
                if (group != null && comparator.compare(group.key(), min) == 0) {
                    atKey.add(group);
                    current.set(i, sources.get(i).next());
                } else {
                    atKey.add(null);
                }
            }
            while (untilGroup != null && comparator.compare(untilGroup.key(), min) < 0) {
                untilGroup = until.next();
            }
            final List<Row> untilRows = untilGroup != null && comparator.compare(untilGroup.key(), min) == 0
                    ? untilGroup.rows()
                    : List.of();
            tally.counted(min, atKey);
            if (!entityHolds(steps, atKey)) {
                continue;
            }
            if (bounded && atKey.get(opener) != null && atKey.get(closer) == null) {
                tally.openedNotClosed(min);
            }
            if (timed.isEmpty()) {
                tally.found(new Found(min, counts(atKey), emptyRows(n)), keep);
                tally.entity();
                continue;
            }
            final List<List<Row>> rows = new ArrayList<>();
            for (final int i : timed) {
                rows.add(atKey.get(i) == null
                        ? List.of()
                        : atKey.get(i).rows());
            }
            if (rows.getFirst().isEmpty()) {
                continue;
            }
            final List<List<List<Row>>> sequences = StreamSequences.match(rules, rows, untilRows, maxspanMs);
            if (sequences.isEmpty()) {
                continue;
            }
            final List<Long> counts = counts(atKey);
            for (final List<List<Row>> sequence : sequences) {
                final List<List<Row>> byStep = emptyRows(n);
                for (int t = 0; t < timed.size(); t++) {
                    byStep.set(timed.get(t), sequence.get(t));
                }
                tally.found(new Found(min, counts, byStep), keep);
            }
            tally.entity();
        }
        long cut = 0;
        for (final KeyGroupSource source : sources) {
            cut += source.cutKeys();
        }
        return tally.result(cut);
    }

    // Whether no more key can count: a side a match needs has no more keys. A bounded sequence's last step having
    // none still reads its first step's keys, to count those that never ended.
    private static boolean ended(final List<StepRole> steps,
                                 final List<KeyGroup> current,
                                 final int opener,
                                 final int closer,
                                 final boolean bounded) {
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).missing() && current.get(i) == null && !(bounded && i == closer)) {
                return true;
            }
        }
        return bounded && current.get(opener) == null;
    }

    // Whether the key holds every step read as keys: present where it must happen, absent where it must not; and
    // present in every timed step that must happen.
    private static boolean entityHolds(final List<StepRole> steps, final List<KeyGroup> atKey) {
        for (int i = 0; i < steps.size(); i++) {
            final StepRole step = steps.get(i);
            final boolean present = atKey.get(i) != null;
            if (!step.timed() && step.missing() == present) {
                return false;
            }
            if (step.timed() && !step.missing() && !present && i == 0) {
                return false;
            }
        }
        return true;
    }

    private static List<Long> counts(final List<KeyGroup> atKey) {
        final List<Long> counts = new ArrayList<>();
        atKey.forEach(group -> counts.add(group == null
                ? 0L
                : group.count()));
        return counts;
    }

    private static List<List<Row>> emptyRows(final int n) {
        return new ArrayList<>(Collections.nCopies(n, List.of()));
    }


    // --------------------------------------------------------------------------------


    /// How a step is read and matched.
    ///
    /// @param timed   True if its events are read and matched in time; false if it is read as keys (an entity
    ///                condition, at any time).
    /// @param missing True if it must not happen.
    /// @param runs    How many events in a row it needs.
    record StepRole(boolean timed, boolean missing, int runs) {

    }


    // --------------------------------------------------------------------------------


    /// One match: a sequence (steps read as events), or an entity (every step read as keys).
    ///
    /// @param key    Its key.
    /// @param counts Each step's events (or keys' counts) for the key.
    /// @param byStep Each step's events of the sequence, empty for a step read as keys or missing.
    record Found(List<Object> key, List<Long> counts, List<List<Row>> byStep) {

    }


    // --------------------------------------------------------------------------------


    /// What one key range gave.
    ///
    /// @param matches           The matches found (sequences, or entities when every step is read as keys).
    /// @param entities          The keys with at least one match.
    /// @param kept              The matches kept, in key order.
    /// @param keysByStep        How many keys each step had.
    /// @param sharedWithFirst   How many keys step 1 shared with each step (itself first).
    /// @param examplesByStep    A few keys of each step, in key order.
    /// @param openedNotClosed   The keys whose first timed step happened and whose last never did.
    /// @param openedExamples    A few of them.
    /// @param cutKeys           The keys with more events than are held at once.
    record RangeResult(long matches,
                       long entities,
                       List<Found> kept,
                       List<Long> keysByStep,
                       List<Long> sharedWithFirst,
                       List<List<List<Object>>> examplesByStep,
                       long openedNotClosed,
                       List<List<Object>> openedExamples,
                       long cutKeys) {

    }


    // --------------------------------------------------------------------------------


    // The counts kept as a range merges.
    private static final class Tally {

        private final int steps;
        private final long[] keysByStep;
        private final long[] shared;
        private final List<List<List<Object>>> examples = new ArrayList<>();
        private final List<Found> kept = new ArrayList<>();
        private final List<List<Object>> openedExamples = new ArrayList<>();
        private long matches;
        private long entities;
        private long opened;

        private Tally(final int steps) {
            this.steps = steps;
            this.keysByStep = new long[steps];
            this.shared = new long[steps];
            for (int i = 0; i < steps; i++) {
                examples.add(new ArrayList<>());
            }
        }

        private void counted(final List<Object> key, final List<KeyGroup> atKey) {
            final boolean inFirst = atKey.getFirst() != null;
            for (int i = 0; i < steps; i++) {
                if (atKey.get(i) != null) {
                    keysByStep[i]++;
                    if (examples.get(i).size() < EXAMPLES) {
                        examples.get(i).add(key);
                    }
                    if (inFirst) {
                        shared[i]++;
                    }
                }
            }
        }

        private void openedNotClosed(final List<Object> key) {
            opened++;
            if (openedExamples.size() < EXAMPLES) {
                openedExamples.add(key);
            }
        }

        private void found(final Found found, final int keep) {
            matches++;
            if (kept.size() < keep) {
                kept.add(found);
            }
        }

        private void entity() {
            entities++;
        }

        private RangeResult result(final long cutKeys) {
            final List<Long> keys = new ArrayList<>();
            final List<Long> sharedWithFirst = new ArrayList<>();
            for (int i = 0; i < steps; i++) {
                keys.add(keysByStep[i]);
                sharedWithFirst.add(shared[i]);
            }
            return new RangeResult(matches, entities, kept, keys, sharedWithFirst, examples, opened, openedExamples,
                    cutKeys);
        }
    }
}
