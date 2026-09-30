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

package stroom.util.collections;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestBatchingCollector {

    @Test
    void testSequentialStreamWithMultipleBatches() {
        final List<List<Integer>> batches = new ArrayList<>();
        IntStream.range(0, 10)
                .boxed()
                .collect(new BatchingCollector<>(3, batches::add));

        assertThat(batches).hasSize(4);
        assertThat(batches.get(0)).isEqualTo(List.of(0, 1, 2));
        assertThat(batches.get(1)).isEqualTo(List.of(3, 4, 5));
        assertThat(batches.get(2)).isEqualTo(List.of(6, 7, 8));
        assertThat(batches.get(3)).isEqualTo(List.of(9));
    }

    @Test
    void testParallelStreamProcessing() {
        final List<List<Integer>> batches = new ArrayList<>();
        IntStream.range(0, 100)
                .parallel()
                .boxed()
                .collect(new BatchingCollector<>(10, batch -> {
                    synchronized (batches) {
                        batches.add(batch);
                    }
                }));

        // Verify total element count
        final long totalElements = batches.stream()
                .mapToLong(List::size)
                .sum();
        assertThat(totalElements).isEqualTo(100);

        // Verify all batches except possibly the last have correct size
        for (int i = 0; i < batches.size(); i++) {
            assertThat(batches.get(i).size()).isLessThanOrEqualTo(10);
        }

        // Verify all elements are present
        final List<Integer> allElements = batches.stream()
                .flatMap(List::stream)
                .sorted()
                .collect(Collectors.toList());
        assertThat(allElements).hasSize(100);
        for (int i = 0; i < 100; i++) {
            assertThat(allElements).contains(i);
        }
    }

    @Test
    void testEmptyStream() {
        final List<List<Integer>> batches = new ArrayList<>();
        Stream.<Integer>empty()
                .collect(new BatchingCollector<>(5, batches::add));

        assertThat(batches).isEmpty();
    }

    @Test
    void testSingleElementStream() {
        final List<List<String>> batches = new ArrayList<>();
        Stream.of("single")
                .collect(new BatchingCollector<>(10, batches::add));

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).isEqualTo(List.of("single"));
    }

    @Test
    void testExactBatchSize() {
        final List<List<Integer>> batches = new ArrayList<>();
        IntStream.range(0, 15)
                .boxed()
                .collect(new BatchingCollector<>(5, batches::add));

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).isEqualTo(List.of(0, 1, 2, 3, 4));
        assertThat(batches.get(1)).isEqualTo(List.of(5, 6, 7, 8, 9));
        assertThat(batches.get(2)).isEqualTo(List.of(10, 11, 12, 13, 14));
    }

    @Test
    void testBatchSizeOfOne() {
        final List<List<Integer>> batches = new ArrayList<>();
        IntStream.range(0, 5)
                .boxed()
                .collect(new BatchingCollector<>(1, batches::add));

        assertThat(batches).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(batches.get(i)).isEqualTo(List.of(i));
        }
    }

    @Test
    void testLargeBatchSize() {
        final List<List<Integer>> batches = new ArrayList<>();
        IntStream.range(0, 10)
                .boxed()
                .collect(new BatchingCollector<>(100, batches::add));

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).hasSize(10);
    }

    @Test
    void testBatchOrderPreservation() {
        final List<List<String>> batches = new ArrayList<>();
        Stream.of("a", "b", "c", "d", "e", "f", "g")
                .collect(new BatchingCollector<>(3, batches::add));

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).isEqualTo(List.of("a", "b", "c"));
        assertThat(batches.get(1)).isEqualTo(List.of("d", "e", "f"));
        assertThat(batches.get(2)).isEqualTo(List.of("g"));
    }
}
