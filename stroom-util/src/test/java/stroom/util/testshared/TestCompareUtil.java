/*
 * Copyright 2024 Crown Copyright
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

package stroom.util.testshared;


import stroom.util.NullSafe;
import stroom.util.shared.CompareUtil;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestCompareUtil {

    @Test
    void testStringCompare() {
        assertThat(CompareUtil.compareStringIgnoreCase(null, null)).isEqualTo(0);
        assertThat(CompareUtil.compareStringIgnoreCase("A", "A")).isEqualTo(0);
        assertThat(CompareUtil.compareStringIgnoreCase("A", "a")).isEqualTo(0);
        assertThat(CompareUtil.compareStringIgnoreCase("A", "B")).isEqualTo(-1);
        assertThat(CompareUtil.compareStringIgnoreCase("B", "a")).isEqualTo(1);
        assertThat(CompareUtil.compareStringIgnoreCase("B", null)).isEqualTo(1);
        assertThat(CompareUtil.compareStringIgnoreCase(null, "B")).isEqualTo(-1);
    }

    @Test
    void testLongCompare() {
        assertThat(CompareUtil.compareLong(null, null)).isEqualTo(0);
        assertThat(CompareUtil.compareLong(1L, 1L)).isEqualTo(0);
        assertThat(CompareUtil.compareLong(1L, 2L)).isEqualTo(-1);
        assertThat(CompareUtil.compareLong(2L, 1L)).isEqualTo(1);
        assertThat(CompareUtil.compareLong(2L, null)).isEqualTo(1);
        assertThat(CompareUtil.compareLong(null, 2L)).isEqualTo(-1);
    }

    @Test
    void testGetNullSafeCaseInsensitiveComparator() {

        Comparator<AtomicReference<String>> comparator = CompareUtil.getNullSafeCaseInsensitiveComparator(
                AtomicReference::get);

        final List<AtomicReference<String>> list = Stream.of(
                        "A",
                        "b",
                        "a",
                        "c",
                        null,
                        "C",
                        "2",
                        "1",
                        "0")
                .map(AtomicReference::new)
                .sorted(comparator)
                .toList();

        assertThat(list)
                .extracting(AtomicReference::get)
                .containsExactly(
                        null,
                        "0",
                        "1",
                        "2",
                        "A",
                        "a",
                        "b",
                        "c",
                        "C");
    }

    @Test
    void testGetNullSafeCaseInsensitiveComparator2() {

        Comparator<AtomicReference<String>> comparator = CompareUtil.getNullSafeCaseInsensitiveComparator(
                AtomicReference::get);

        final List<AtomicReference<String>> list = Stream.of(
                        new AtomicReference<>("b"),
                        new AtomicReference<>("A"),
                        new AtomicReference<>((String) null),
                        null)
                .sorted(comparator)
                .toList();

        assertThat(list)
                .extracting(ref -> NullSafe.get(ref, AtomicReference::get))
                .containsExactly(
                        null,
                        null,
                        "A",
                        "b");
    }
}
