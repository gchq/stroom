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

package stroom.util.concurrent;

import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestUniqueIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestUniqueIdGenerator.class);

    @Test
    void testBadNodeId() {
        Assertions.assertThatThrownBy(
                        () -> {
                            new UniqueIdGenerator(NodeType.PROXY, "foo bar");
                        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match the pattern");
    }

    @Test
    void simple() {
        final UniqueIdGenerator generator = new UniqueIdGenerator(NodeType.PROXY, "node1");
        ThreadUtil.sleep(20);
        final UniqueId uniqueId = generator.generateId();

        final String str = uniqueId.toString();
        LOGGER.info("uniqueId: {}", uniqueId);
        assertThat(str)
                .contains(String.valueOf(uniqueId.getEpochMs()));
        assertThat(str)
                .contains(String.valueOf(uniqueId.getSequenceNo()));
        assertThat(str)
                .contains(uniqueId.getNodeId());
    }

    @Test
    void parse() {
        final UniqueIdGenerator generator = new UniqueIdGenerator(NodeType.PROXY, "node1");
        final UniqueId uniqueId1 = generator.generateId();

        final String str = uniqueId1.toString();

        final UniqueId uniqueId2 = UniqueId.parse(str);

        assertThat(uniqueId2)
                .isEqualTo(uniqueId1);
    }

    @Test
    void multiThreadUniqueness() {
        final int cores = Runtime.getRuntime().availableProcessors();
        final ExecutorService executorService = Executors.newFixedThreadPool(cores);

        final CountDownLatch startLatch = new CountDownLatch(cores);
        final CountDownLatch completionLatch = new CountDownLatch(cores);
        final UniqueIdGenerator generator = new UniqueIdGenerator(NodeType.PROXY, "node1");

        final int iterations = 100_000;
        final List<UniqueId>[] lists = new List[cores];
        final DurationTimer timer = DurationTimer.start();

        for (int i = 0; i < cores; i++) {
            final int coreIdx = i;
            CompletableFuture.runAsync(() -> {
                final List<UniqueId> uniqueIdList = new ArrayList<>();
                startLatch.countDown();
                ThreadUtil.await(startLatch);
//                LOGGER.info("Thread {} starting", coreIdx);

                try {
                    for (int iteration = 0; iteration < iterations; iteration++) {
                        final UniqueId uniqueId = generator.generateId();
                        assertThat(uniqueId)
                                .isNotNull();
                        uniqueIdList.add(uniqueId);
                    }
                    lists[coreIdx] = uniqueIdList;
                    assertThat(uniqueIdList)
                            .hasSize(iterations);
                } catch (final Exception e) {
                    LOGGER.error("Error", e);
                } finally {
//                    LOGGER.info("Thread {} completing", coreIdx);
                    completionLatch.countDown();
//                    LOGGER.info("Thread {} complete, completionLatch {}",
//                            coreIdx, completionLatch.getCount());
                }
            }, executorService);
        }

        ThreadUtil.await(completionLatch);

        final int totalCount = iterations * cores;
        final Duration duration = timer.get();
        LOGGER.info("Generated {} uniqueIds in {}, " +
                    "uniqueIds/sec: {}, " +
                    "millis per iter: {}",
                ModelStringUtil.formatCsv(totalCount),
                duration,
                totalCount / (double) duration.toMillis() * 1000,
                duration.toMillis() / (double) totalCount);

        final List<UniqueId> allIds = new ArrayList<>(totalCount);
        for (final List<UniqueId> list : lists) {
            allIds.addAll(list);
        }
        assertThat(allIds)
                .hasSize(totalCount);

        final Set<UniqueId> uniqueIds = new HashSet<>(allIds);
        // No dupes should have dropped out, so size is unchanged
        assertThat(uniqueIds)
                .hasSize(totalCount);
    }

    /**
     * On a 12 core/24 thread cpu, I get about 4mil ops/sec
     */
    @Disabled // Manual perf test only
    @Test
    void testPerf() {
        final UniqueIdGenerator uniqueIdGenerator = new UniqueIdGenerator(NodeType.STROOM, "stroom1");

        final int iterations = 10_000_000;
        final String[] arr = new String[iterations];

        final DurationTimer timer = DurationTimer.start();
        IntStream.range(0, iterations)
                .parallel()
                .forEach(i -> {
                    final String str = uniqueIdGenerator.generateId().toString();
                    arr[i] = str;
                });

        final Duration duration = timer.get();
        LOGGER.info("time: {}, {} ops/sec",
                duration,
                iterations / ((double) duration.toMillis() / 1000));

        for (int i = 0; i < iterations; i++) {
            Assertions.assertThat(arr[i])
                    .isNotNull()
                    .isNotBlank();
        }
    }
}
