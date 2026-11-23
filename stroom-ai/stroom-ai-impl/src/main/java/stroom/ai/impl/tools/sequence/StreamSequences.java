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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// The sequences of one key's events, step by step in time, for the streaming join: "X then Y within N after X".
/// The events of each step come in time order (as a scan reads them). A sequence starts at an event of the first step
/// (its `runs` events, for N in a row) and takes, for each later step, its earliest events at or after the step
/// before's (never one event for two steps), all within the window of the first event; a missing step has no event
/// between the steps either side of it (to the window's end when it is last); an `until` event between the first
/// event and the last ends the sequence before it is complete. As in EQL, a later first-step event before the second
/// step's event takes the sequence over (the latest start is kept), and one entity's sequences don't overlap: the
/// next starts after the last event of the one before. So these are at least the sequences there are, as EQL's are.
final class StreamSequences {

    private StreamSequences() {
        // Static helpers only.
    }

    /// The sequences of one key.
    ///
    /// @param steps     Each timed step's rule, in order; the first must happen.
    /// @param rows      Each step's events, in time order (each with a time).
    /// @param until     The until step's events, in time order; empty for none.
    /// @param maxspanMs How far after its first event a sequence's last may be, in milliseconds.
    /// @return The sequences, each its events per step (empty for a missing step), in time order.
    static List<List<List<Row>>> match(final List<Rule> steps,
                                       final List<List<Row>> rows,
                                       final List<Row> until,
                                       final long maxspanMs) {
        final List<List<List<Row>>> found = new ArrayList<>();
        if (steps.isEmpty() || steps.getFirst().missing()) {
            return found;
        }
        final List<Row> starts = rows.getFirst();
        final int runs = steps.getFirst().runs();
        long notBefore = Long.MIN_VALUE;
        int j = 0;
        while (j + runs - 1 < starts.size()) {
            if (time(starts.get(j)) < notBefore) {
                j++;
                continue;
            }
            List<List<Row>> attempt = attempt(steps, rows, until, maxspanMs, j);
            if (attempt == null) {
                j++;
                continue;
            }
            // A later start still before the next step's first event takes the sequence over, as in EQL.
            while (j + runs < starts.size() && time(starts.get(j + runs)) <= nextStepTime(attempt)) {
                final List<List<Row>> later = attempt(steps, rows, until, maxspanMs, j + 1);
                if (later == null) {
                    break;
                }
                j++;
                attempt = later;
            }
            found.add(attempt);
            notBefore = lastTime(attempt) + 1;
            j += runs;
        }
        return found;
    }

    // The sequence starting at the first step's event `start`, or null if there is none.
    private static List<List<Row>> attempt(final List<Rule> steps,
                                           final List<List<Row>> rows,
                                           final List<Row> until,
                                           final long maxspanMs,
                                           final int start) {
        final List<Row> starts = rows.getFirst();
        final long first = time(starts.get(start));
        final long deadline = first + maxspanMs;
        final List<List<Row>> chosen = new ArrayList<>();
        final Set<String> used = new HashSet<>();
        final List<Row> opening = new ArrayList<>(starts.subList(start, start + steps.getFirst().runs()));
        if (time(opening.getLast()) > deadline) {
            return null;
        }
        opening.forEach(row -> used.add(id(row)));
        chosen.add(opening);
        long previous = time(opening.getLast());
        final List<Integer> pendingMissing = new ArrayList<>();
        final List<Long> pendingFrom = new ArrayList<>();
        for (int i = 1; i < steps.size(); i++) {
            final Rule rule = steps.get(i);
            if (rule.missing()) {
                pendingMissing.add(i);
                pendingFrom.add(previous);
                chosen.add(List.of());
                continue;
            }
            final List<Row> picked = pick(rows.get(i), previous, deadline, rule.runs(), used);
            if (picked == null) {
                return null;
            }
            final long pickedFirst = time(picked.getFirst());
            for (int m = 0; m < pendingMissing.size(); m++) {
                if (anyBetween(rows.get(pendingMissing.get(m)), pendingFrom.get(m), pickedFirst)) {
                    return null;
                }
            }
            pendingMissing.clear();
            pendingFrom.clear();
            chosen.add(picked);
            previous = time(picked.getLast());
        }
        for (int m = 0; m < pendingMissing.size(); m++) {
            if (anyBetween(rows.get(pendingMissing.get(m)), pendingFrom.get(m), deadline)) {
                return null;
            }
        }
        if (anyBetween(until, first, previous)) {
            return null;
        }
        return chosen;
    }

    // The step's first `runs` events at or after `from` and by `deadline`, none already used; null if too few.
    private static List<Row> pick(final List<Row> events,
                                  final long from,
                                  final long deadline,
                                  final int runs,
                                  final Set<String> used) {
        final List<Row> picked = new ArrayList<>();
        for (int k = lowerBound(events, from); k < events.size() && picked.size() < runs; k++) {
            final Row row = events.get(k);
            if (time(row) > deadline) {
                break;
            }
            if (used.add(id(row))) {
                picked.add(row);
            }
        }
        return picked.size() == runs
                ? picked
                : null;
    }

    // Whether any event is at or after `from` and at or before `to`.
    private static boolean anyBetween(final List<Row> events, final long from, final long to) {
        final int k = lowerBound(events, from);
        return k < events.size() && time(events.get(k)) <= to;
    }

    // The first position whose time is at or after `time`.
    private static int lowerBound(final List<Row> events, final long time) {
        int low = 0;
        int high = events.size();
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (time(events.get(mid)) < time) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    // The time of the first event after the first step's, or the last of the first step's if it is alone.
    private static long nextStepTime(final List<List<Row>> sequence) {
        for (int i = 1; i < sequence.size(); i++) {
            if (!sequence.get(i).isEmpty()) {
                return time(sequence.get(i).getFirst());
            }
        }
        return time(sequence.getFirst().getLast());
    }

    private static long lastTime(final List<List<Row>> sequence) {
        long last = Long.MIN_VALUE;
        for (final List<Row> step : sequence) {
            for (final Row row : step) {
                last = Math.max(last, time(row));
            }
        }
        return last;
    }

    private static long time(final Row row) {
        return row.time() == null
                ? Long.MIN_VALUE
                : row.time();
    }

    private static String id(final Row row) {
        return row.index() + "/" + row.id();
    }


    // --------------------------------------------------------------------------------


    /// What a timed step needs of a key's events.
    ///
    /// @param missing True if it must not happen.
    /// @param runs    How many events in a row it needs (1 for one).
    record Rule(boolean missing, int runs) {

    }
}
