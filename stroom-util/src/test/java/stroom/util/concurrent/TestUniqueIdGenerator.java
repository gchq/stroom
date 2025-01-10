package stroom.util.concurrent;

import stroom.util.concurrent.UniqueIdGenerator.UniqueId;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import org.assertj.core.api.Assertions;
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

import static org.assertj.core.api.Assertions.assertThat;

class TestUniqueIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestUniqueIdGenerator.class);

    @Test
    void testBadNodeId() {
        Assertions.assertThatThrownBy(() -> {
                    new UniqueIdGenerator("foo bar");
                }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match the pattern");
    }

    @Test
    void simple() {
        UniqueIdGenerator generator = new UniqueIdGenerator("node1");
        ThreadUtil.sleep(20);
        final UniqueId uniqueId = generator.generateId();

        final String str = uniqueId.toString();
        LOGGER.info("uniqueId: {}", uniqueId);
        assertThat(str)
                .contains(String.valueOf(uniqueId.epochMs()));
        assertThat(str)
                .contains(String.valueOf(uniqueId.sequenceNo()));
        assertThat(str)
                .contains(uniqueId.nodeId());
    }

    @Test
    void parse() {
        UniqueIdGenerator generator = new UniqueIdGenerator("node1");
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
        final UniqueIdGenerator generator = new UniqueIdGenerator("node1");

        int iterations = 100_000;
        final List<UniqueId>[] lists = new List[cores];
        DurationTimer timer = DurationTimer.start();

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
                } catch (Exception e) {
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

        int totalCount = iterations * cores;
        final Duration duration = timer.get();
        LOGGER.info("Generated {} uniqueIds in {}, millis per iter: {}",
                ModelStringUtil.formatCsv(totalCount),
                duration,
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
}