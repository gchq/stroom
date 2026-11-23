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

package stroom.ai.impl.tools;

import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.impl.tools.StreamSequences.Rule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests [StreamSequences]: one key's events matched step by step in time.
class TestStreamSequences {

    private static final Rule ONCE = new Rule(false, 1);
    private static final Rule NOT = new Rule(true, 1);
    private static final long MINUTE = 60_000;

    @Test
    void aStepThenAnotherWithinTheWindowIsASequenceAndOnePastItIsnt() {
        final List<List<List<Row>>> found = StreamSequences.match(List.of(ONCE, ONCE),
                List.of(rows("a", 0, 10 * MINUTE), rows("b", MINUTE, 13 * MINUTE)), List.of(), 2 * MINUTE);

        assertThat(ids(found)).containsExactly(List.of("a0", "b0"));
    }

    @Test
    void aLaterStartBeforeTheNextStepTakesTheSequenceOverAsInEql() {
        // Downloads at 0 and 30 s, a copy at 60 s: the latest download before the copy is kept, once.
        final List<List<List<Row>>> found = StreamSequences.match(List.of(ONCE, ONCE),
                List.of(rows("d", 0, 30_000), rows("c", MINUTE)), List.of(), 5 * MINUTE);

        assertThat(ids(found)).containsExactly(List.of("d1", "c0"));
    }

    @Test
    void oneEntitysSequencesDontOverlap() {
        final List<List<List<Row>>> found = StreamSequences.match(List.of(ONCE, ONCE),
                List.of(rows("d", 0, 2 * MINUTE), rows("c", MINUTE, 3 * MINUTE)), List.of(), 5 * MINUTE);

        assertThat(ids(found)).containsExactly(List.of("d0", "c0"), List.of("d1", "c1"));
    }

    @Test
    void aMissingStepBetweenTheOthersRulesOutTheSequence() {
        final List<List<Row>> rows = List.of(rows("copy", 0, 10 * MINUTE), rows("cancel", 30_000),
                rows("shell", MINUTE, 11 * MINUTE));

        final List<List<List<Row>>> found = StreamSequences.match(List.of(ONCE, NOT, ONCE), rows, List.of(),
                2 * MINUTE);

        assertThat(ids(found)).containsExactly(List.of("copy1", "shell1"));
    }

    @Test
    void aMissingLastStepIsLookedForToTheWindowsEnd() {
        final List<List<List<Row>>> found = StreamSequences.match(List.of(ONCE, NOT),
                List.of(rows("login", 0, 10 * MINUTE), rows("logout", MINUTE)), List.of(), 2 * MINUTE);

        assertThat(ids(found)).containsExactly(List.of("login1"));
    }

    @Test
    void runsNeedThatManyEventsInTheWindow() {
        final List<List<List<Row>>> found = StreamSequences.match(List.of(new Rule(false, 3), ONCE),
                List.of(rows("fail", 0, 1_000, 2_000, 10 * MINUTE, 10 * MINUTE + 1_000), rows("ok", 3_000,
                        10 * MINUTE + 2_000)), List.of(), MINUTE);

        assertThat(ids(found)).containsExactly(List.of("fail0", "fail1", "fail2", "ok0"));
    }

    @Test
    void anUntilEventBeforeTheSequenceEndsCancelsIt() {
        final List<List<List<Row>>> found = StreamSequences.match(List.of(ONCE, ONCE),
                List.of(rows("start", 0, 5 * MINUTE), rows("end", MINUTE, 6 * MINUTE)), rows("reset", 30_000),
                2 * MINUTE);

        assertThat(ids(found)).containsExactly(List.of("start1", "end1"));
    }

    @Test
    void oneEventIsNeverTwoSteps() {
        // The same event matches both steps (one doc, overlapping conditions): it can't follow itself.
        final List<Row> same = rows("x", 0);
        final List<List<List<Row>>> found = StreamSequences.match(List.of(ONCE, ONCE), List.of(same, same),
                List.of(), MINUTE);

        assertThat(found).isEmpty();
    }

    // --------------------------------------------------------------------------------

    private static List<Row> rows(final String name, final long... times) {
        final List<Row> rows = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            rows.add(new Row(List.of("alice"), times[i], "index", name + i, Map.of()));
        }
        return rows;
    }

    private static List<List<String>> ids(final List<List<List<Row>>> sequences) {
        return sequences.stream()
                .map(sequence -> sequence.stream().flatMap(List::stream).map(Row::id).toList())
                .toList();
    }
}
