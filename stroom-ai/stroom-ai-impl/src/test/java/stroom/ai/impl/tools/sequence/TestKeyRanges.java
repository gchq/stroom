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

import stroom.ai.api.ElasticKeyStreams.KeyCount;
import stroom.ai.api.ElasticKeyStreams.KeyRange;
import stroom.ai.impl.tools.sequence.KeyRanges.Planned;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests [KeyRanges]: the key space cut for workers.
class TestKeyRanges {

    @Test
    void boundariesCutTheKeysIntoRangesCoveringEveryKey() {
        final List<Planned> ranges = KeyRanges.fromBoundaries(List.of("f", "m", "m", "t"));

        assertThat(ranges).extracting(Planned::range).containsExactly(new KeyRange(null, "f"),
                new KeyRange("f", "m"), new KeyRange("m", "t"), new KeyRange("t", null));
        assertThat(KeyRanges.fromBoundaries(List.of())).extracting(Planned::range).containsExactly(KeyRange.ALL);
    }

    @Test
    void pushedDownKeysAreCutIntoPartsAndBatchesUnderTheTermsLimit() {
        final List<KeyCount> keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            keys.add(new KeyCount(List.of("u" + i, "h" + (i % 2)), 1));
        }

        final List<Planned> ranges = KeyRanges.fromKeys(keys, 2, 3);

        assertThat(ranges).hasSize(2);
        assertThat(ranges.getFirst().range()).isEqualTo(new KeyRange(null, "u4"));
        assertThat(ranges.getLast().range()).isEqualTo(new KeyRange("u4", null));
        // Five keys a part, in batches of at most three, each its own range in key order.
        assertThat(ranges.getFirst().chunks()).extracting(chunk -> chunk.range()).containsExactly(
                new KeyRange(null, "u2"), new KeyRange("u2", "u4"));
        assertThat(ranges.getFirst().chunks().getFirst().terms()).containsExactly(List.of("u0", "u1", "u2"),
                List.of("h0", "h1"));
        assertThat(ranges.getLast().chunks().getLast().range()).isEqualTo(new KeyRange("u7", null));
    }

    @Test
    void oneFirstFieldValueIsNeverSplitBetweenRanges() {
        final List<KeyCount> keys = List.of(new KeyCount(List.of("a", "1"), 1), new KeyCount(List.of("a", "2"), 1),
                new KeyCount(List.of("a", "3"), 1), new KeyCount(List.of("b", "1"), 1));

        final List<Planned> ranges = KeyRanges.fromKeys(keys, 4, 100);

        assertThat(ranges).extracting(Planned::range).containsExactly(new KeyRange(null, "a"),
                new KeyRange("a", null));
        assertThat(KeyRanges.fromKeys(List.of(), 4, 100)).isEmpty();
    }
}
