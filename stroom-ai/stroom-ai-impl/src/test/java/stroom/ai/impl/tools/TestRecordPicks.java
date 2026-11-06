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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/// A block of records at a stream's head, then the rest spread over what follows. Seen in testing: the head alone
/// missed kinds of event further in, and spread alone missed a field only some events early in the stream had.
class TestRecordPicks {

    @Test
    void aBigStreamIsReadAtItsHeadAndSpreadOverTheRest() {
        final List<Long> picks = RecordPicks.headAndSpread(20_000, 200);

        assertThat(picks).hasSize(200).doesNotHaveDuplicates().isSorted();
        // A quarter at the head: records 7 and 23 (a field only they have) among them.
        assertThat(picks.subList(0, 50)).containsExactlyElementsOf(range(0, 50));
        assertThat(picks).contains(7L, 23L);
        // The rest reaches the stream's end.
        assertThat(picks.getLast()).isGreaterThan(19_800L).isLessThan(20_000L);
        assertThat(RecordPicks.headCount(20_000, 200)).isEqualTo(50);
    }

    @Test
    void aStreamNoBiggerThanWantedIsReadWhole() {
        assertThat(RecordPicks.headAndSpread(5, 200)).containsExactly(0L, 1L, 2L, 3L, 4L);
        assertThat(RecordPicks.headCount(5, 200)).isEqualTo(5);
        assertThat(RecordPicks.headAndSpread(0, 200)).isEmpty();
        assertThat(RecordPicks.headAndSpread(100, 0)).isEmpty();
    }

    @Test
    void aFewWantedStillReadTheHeadFirst() {
        assertThat(RecordPicks.headAndSpread(1_000, 2)).containsExactly(0L, 1L);
        assertThat(RecordPicks.headAndSpread(1_000, 3)).hasSize(3).startsWith(0L).doesNotHaveDuplicates();
    }

    private static List<Long> range(final long from, final long to) {
        return LongStream.range(from, to).boxed().toList();
    }
}
