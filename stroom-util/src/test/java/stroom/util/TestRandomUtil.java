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

package stroom.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.util.RandomUtil.getRandomItem;

class TestRandomUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestRandomUtil.class);

    @Test
    void testArray() {
        final String[] items = {"one", "two", "three"};
        final Map<String, LongAdder> map = new ConcurrentHashMap<>(3);
        for (final String item : items) {
            map.put(item, new LongAdder());
        }

        IntStream.rangeClosed(1, 10_000)
                .parallel()
                .forEach(ignored -> {
                    final String key = getRandomItem(items);
                    map.get(key).increment();
                });

        map.forEach((item, counter) -> {
            final long count = counter.sum();
            LOGGER.debug("{} => {}", item, count);
            assertThat(count)
                    .isGreaterThan(0);
        });
    }

    @Test
    void testList() {
        final List<String> items = List.of("one", "two", "three");
        final Map<String, LongAdder> map = new ConcurrentHashMap<>(3);
        for (final String item : items) {
            map.put(item, new LongAdder());
        }

        IntStream.rangeClosed(1, 10_000)
                .parallel()
                .forEach(ignored -> {
                    final String key = getRandomItem(items);
                    map.get(key).increment();
                });

        map.forEach((item, counter) -> {
            final long count = counter.sum();
            LOGGER.debug("{} => {}", item, count);
            assertThat(count)
                    .isGreaterThan(0);
        });
    }

    @Test
    void testNull() {
        assertThat(getRandomItem((String[]) null))
                .isNull();
    }

    @Test
    void testNull2() {
        assertThat(getRandomItem((List<?>) null))
                .isNull();
    }

    @Test
    void testEmpty() {
        assertThat(getRandomItem(new String[0]))
                .isNull();
    }

    @Test
    void testEmpty2() {
        final List<String> list = List.of();
        assertThat(getRandomItem(list))
                .isNull();
    }

    @Test
    void testSingle() {
        final List<String> list = List.of("foo");
        assertThat(getRandomItem(list))
                .isEqualTo("foo");
    }

    @Test
    void testSingle2() {
        final List<String> list = List.of("foo");
        assertThat(getRandomItem(list))
                .isEqualTo("foo");
    }

    @Disabled // Checking value distribution of legacy code
    @Test
    void testRandom() {
        final Map<Integer, AtomicInteger> values = new HashMap<>();
        final int size = 5;
        for (int i = 0; i < 100_000; i++) {
            final double random = Math.random();
            final int index = (int) (random * size);
            values.computeIfAbsent(index, AtomicInteger::new).incrementAndGet();
        }
        values.forEach((idx, atomicInteger) ->
                LOGGER.debug("{} => {}", idx, atomicInteger.get()));
    }

    @Disabled // Checking value distribution of legacy code
    @Test
    void testRandom2() {
        final Map<Integer, AtomicInteger> values = new HashMap<>();
        final int size = 5;
        for (int i = 0; i < 100_000; i++) {
            final int index = (int) Math.round(Math.random() * (size - 1));
            values.computeIfAbsent(index, AtomicInteger::new).incrementAndGet();
        }
        values.forEach((idx, atomicInteger) ->
                LOGGER.debug("{} => {}", idx, atomicInteger.get()));
    }
}
