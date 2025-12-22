/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.meta.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestEffectiveMetaSet {

    private static final String FEED_1 = "FEED1";
    private static final String REFERENCE_TYPE = "Reference";
    private static final String FEED_2 = "FEED2";

    @Test
    void testDeDep() {
        final EffectiveMetaSet effectiveMetaSet = EffectiveMetaSet.builder(FEED_1, "Reference")
                .add(1, 123)
                .add(2, 456)
                .add(3, 456)
                .add(4, 456)
                .add(5, 789)
                .build();

        assertThat(effectiveMetaSet.stream()
                .map(EffectiveMeta::getId)
                .sorted()
                .toList())
                .containsExactly(1L, 4L, 5L);
    }

    @Test
    void testOrder() {
        final EffectiveMetaSet effectiveMetaSet = EffectiveMetaSet.builder(FEED_1, "Reference")
                .add(1, 100)
                .add(2, 900)
                .add(3, 300)
                .add(4, 200)
                .add(5, 500)
                .build();

        assertThat(effectiveMetaSet.stream()
                .map(EffectiveMeta::getId)
                .toList())
                .containsExactly(1L, 4L, 3L, 5L, 2L);

        assertThat(effectiveMetaSet.first().orElseThrow())
                .extracting(this::metaToIdAndTimeEntry)
                .isEqualTo(entry(1, 100));

        assertThat(effectiveMetaSet.last().orElseThrow())
                .extracting(this::metaToIdAndTimeEntry)
                .isEqualTo(entry(2, 900));

        // Make sure Iterable works OK
        final List<Long> ids = new ArrayList<>();
        for (final EffectiveMeta effectiveMeta : effectiveMetaSet) {
            ids.add(effectiveMeta.getId());
        }
        assertThat(ids)
                .containsExactly(1L, 4L, 3L, 5L, 2L);

        assertThat(effectiveMetaSet.asList())
                .extracting(EffectiveMeta::getId)
                .containsExactly(1L, 4L, 3L, 5L, 2L);

        assertThat(effectiveMetaSet.asSet())
                .extracting(EffectiveMeta::getId)
                .containsExactly(1L, 4L, 3L, 5L, 2L);
    }

    @Test
    void testCollector() {
        final EffectiveMetaSet effectiveMetaSet = IntStream.of(1, 3, 5, 4, 2)
                .mapToObj(i -> new EffectiveMeta(i, FEED_1, REFERENCE_TYPE, 100 + i))
                .collect(EffectiveMetaSet.collector(FEED_1, REFERENCE_TYPE));

        assertThat(effectiveMetaSet.asList())
                .extracting(EffectiveMeta::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);

        assertThat(effectiveMetaSet.asList())
                .extracting(EffectiveMeta::getEffectiveMs)
                .containsExactly(101L, 102L, 103L, 104L, 105L);
    }

    @Test
    void testEqualsAndHash() {
        final EffectiveMetaSet set1a = EffectiveMetaSet.builder(FEED_1, REFERENCE_TYPE)
                .add(101, 100)
                .add(102, 200)
                .build();

        // Same as set1
        final EffectiveMetaSet set1b = EffectiveMetaSet.builder(FEED_1, REFERENCE_TYPE)
                .add(101, 100)
                .add(102, 200)
                .build();

        // One less item than set 1a
        final EffectiveMetaSet set1c = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .add(101, 100)
                .build();

        // One more item than set 1a
        final EffectiveMetaSet set1d = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .add(101, 100)
                .add(102, 200)
                .add(103, 300)
                .build();

        final EffectiveMetaSet set2 = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .add(201, 100)
                .add(201, 200)
                .build();

        assertThat(set1a)
                .isEqualTo(set1b);
        assertThat(set1a.hashCode())
                .isEqualTo(set1b.hashCode());

        assertThat(set1a)
                .isNotEqualTo(set2);
        assertThat(set1a.hashCode())
                .isNotEqualTo(set2.hashCode());

        assertThat(set1b)
                .isNotEqualTo(set2);
        assertThat(set1b.hashCode())
                .isNotEqualTo(set2.hashCode());

        assertThat(set1c)
                .isNotEqualTo(set1a);
        assertThat(set1c.hashCode())
                .isNotEqualTo(set1a.hashCode());

        assertThat(set1d)
                .isNotEqualTo(set1a);
        assertThat(set1d.hashCode())
                .isNotEqualTo(set1a.hashCode());
    }

    @Test
    void testEmpty() {
        final EffectiveMetaSet set1 = EffectiveMetaSet.empty();
        final EffectiveMetaSet set2 = EffectiveMetaSet.empty();
        // The feed/type are ignored as it is an empty set.
        final EffectiveMetaSet set3 = EffectiveMetaSet.builder("FEED1", "Reference")
                .build();
        final EffectiveMetaSet set4 = EffectiveMetaSet.builder("FEED2", "Raw Reference")
                .build();

        assertThat(set1)
                .isEqualTo(set2);
        assertThat(set1)
                .isSameAs(set2);

        assertThat(set1)
                .isEqualTo(set3);
        assertThat(set1)
                .isSameAs(set3);

        // Equality
        assertThat(set1)
                .isEqualTo(set4);
        assertThat(set1)
                .isSameAs(set4);

        assertThat(set1.isEmpty())
                .isTrue();

        assertThat(set1.first())
                .isEmpty();
        assertThat(set1.last())
                .isEmpty();
    }

    @Test
    void testSize() {
        final EffectiveMetaSet set1a = EffectiveMetaSet.builder("FEED1", "Reference")
                .add(101, 100)
                .add(102, 200)
                .build();

        assertThat(set1a.isEmpty())
                .isFalse();
        assertThat(set1a.size())
                .isEqualTo(2);
    }

    @Test
    void testFindLatestBefore() {
        final EffectiveMetaSet set = EffectiveMetaSet.builder("FEED1", "Reference")
                .add(104, 400)
                .add(102, 200)
                .add(101, 100)
                .add(103, 300)
                .build();

        assertThat(set.findLatestBefore(0))
                .isEmpty();

        assertThat(set.findLatestBefore(99))
                .isEmpty();

        assertThat(set.findLatestBefore(100).orElseThrow())
                .extracting(this::metaToIdAndTimeEntry)
                .isEqualTo(entry(101L, 100L));

        assertThat(set.findLatestBefore(200).orElseThrow())
                .extracting(this::metaToIdAndTimeEntry)
                .isEqualTo(entry(102L, 200L));

        assertThat(set.findLatestBefore(201).orElseThrow())
                .extracting(this::metaToIdAndTimeEntry)
                .isEqualTo(entry(102L, 200L));

        assertThat(set.findLatestBefore(300).orElseThrow())
                .extracting(this::metaToIdAndTimeEntry)
                .isEqualTo(entry(103L, 300L));

        assertThat(set.findLatestBefore(Long.MAX_VALUE).orElseThrow())
                .extracting(this::metaToIdAndTimeEntry)
                .isEqualTo(entry(104L, 400L));
    }

    private Entry<Long, Long> metaToIdAndTimeEntry(final EffectiveMeta effectiveMeta) {
        return entry(effectiveMeta.getId(), effectiveMeta.getEffectiveMs());
    }

    private Entry<Long, Long> entry(final long metaId, final long effectiveTimeMs) {
        return Map.entry(metaId, effectiveTimeMs);
    }
}
