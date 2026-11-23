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

import stroom.ai.api.ElasticKeyStreams.KeyCount;
import stroom.ai.api.ElasticKeyStreams.Row;
import stroom.ai.impl.tools.KeyGroupSource.KeyGroup;
import stroom.ai.impl.tools.KeyStreamJoin.RangeResult;
import stroom.ai.impl.tools.KeyStreamJoin.StepRole;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests [KeyStreamJoin]: the sort-merge of key-ordered sides.
class TestKeyStreamJoin {

    private static final Comparator<List<Object>> TEXT = JoinKeys.comparator(List.of(JoinKeys.Kind.TEXT));
    private static final StepRole KEYS = new StepRole(false, false, 1);
    private static final StepRole NOT_KEYS = new StepRole(false, true, 1);
    private static final StepRole TIMED = new StepRole(true, false, 1);

    @Test
    void keysInEveryStepThatMustHappenAndNoneThatMustNotAreTheMatches() {
        final RangeResult result = KeyStreamJoin.run(List.of(KEYS, KEYS, NOT_KEYS), List.of(
                keys("alice", "bob", "carol", "dave"), keys("bob", "carol", "erin"), keys("carol")), null, TEXT,
                0, 10, budget(1_000));

        assertThat(result.matches()).isEqualTo(1);
        assertThat(result.kept()).extracting(found -> found.key()).containsExactly(List.of("bob"));
        // erin was never merged: step 1, which every match needs, had ended.
        assertThat(result.keysByStep()).containsExactly(4L, 2L, 1L);
        assertThat(result.sharedWithFirst()).containsExactly(4L, 2L, 1L);
    }

    @Test
    void stepsInTimeAreMatchedPerKeyAndOnlyTheCurrentKeysEventsAreHeld() {
        final RangeResult result = KeyStreamJoin.run(List.of(TIMED, TIMED), List.of(
                events(group("alice", 0, 600_000), group("bob", 0)),
                events(group("alice", 30_000), group("bob", 300_000))), null, TEXT, 60_000, 10, budget(1_000));

        assertThat(result.matches()).isEqualTo(1);
        assertThat(result.entities()).isEqualTo(1);
        assertThat(result.kept().getFirst().key()).containsExactly("alice");
        assertThat(result.kept().getFirst().byStep().get(1)).extracting(Row::time).containsExactly(30_000L);
    }

    @Test
    void keysWhoseFirstStepHappenedButWhoseLastNeverDidAreCounted() {
        final RangeResult result = KeyStreamJoin.run(List.of(TIMED, TIMED), List.of(
                events(group("alice", 0), group("bob", 0), group("carol", 0)), events(group("alice", 1_000))),
                null, TEXT, 60_000, 10, budget(1_000));

        assertThat(result.matches()).isEqualTo(1);
        assertThat(result.openedNotClosed()).isEqualTo(2);
        assertThat(result.openedExamples()).containsExactly(List.of("bob"), List.of("carol"));
    }

    @Test
    void aSideAMatchNeedsEndingEndsTheMergeAndOnlyTheLimitIsKept() {
        final RangeResult result = KeyStreamJoin.run(List.of(KEYS, KEYS), List.of(keys("a", "b", "c", "d"),
                keys("a", "b")), null, TEXT, 0, 1, budget(1_000));

        assertThat(result.matches()).isEqualTo(2);
        assertThat(result.kept()).hasSize(1);
        // c and d were never merged: the second side had ended.
        assertThat(result.keysByStep().getFirst()).isEqualTo(2L);
    }

    // --------------------------------------------------------------------------------

    private static JoinBudget budget(final long maxRows) {
        return new JoinBudget(maxRows, maxRows, Instant.now().plusSeconds(60), () -> false);
    }

    private static KeyGroupSource keys(final String... keys) {
        final List<KeyCount> counts = new ArrayList<>();
        for (final String key : keys) {
            counts.add(new KeyCount(List.of(key), 1));
        }
        return KeyGroupSource.listed(counts);
    }

    private static KeyGroup group(final String key, final long... times) {
        final List<Row> rows = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            rows.add(new Row(List.of(key), times[i], "index", key + times[i], Map.of()));
        }
        return new KeyGroup(List.of(key), rows.size(), rows);
    }

    private static KeyGroupSource events(final KeyGroup... groups) {
        return new KeyGroupSource() {
            private int at;

            @Override
            public KeyGroup next() {
                return at < groups.length
                        ? groups[at++]
                        : null;
            }

            @Override
            public long cutKeys() {
                return 0;
            }

            @Override
            public void close() {
                // Nothing held.
            }
        };
    }
}
