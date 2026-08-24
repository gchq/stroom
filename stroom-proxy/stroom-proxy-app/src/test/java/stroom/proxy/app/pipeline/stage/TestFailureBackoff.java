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

package stroom.proxy.app.pipeline.stage;

import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An item that can never succeed must not be retried in a hot loop.
 * <p>
 * {@code item.fail()} returns the message to the queue and the consumer loop
 * previously picked it straight back up with no delay, so a permanently failing item
 * spun a core at thousands of attempts per second. In most stages that is only
 * wasteful, but the forward stage copies the file group to every healthy destination
 * on each attempt, so one unreachable destination produced a flood of duplicate
 * deliveries downstream and an orphaned copy per attempt on disk.
 * </p>
 */
class TestFailureBackoff extends StroomUnitTest {

    private static final String QUEUE_NAME = "forwardingInput";

    @Test
    void testPermanentlyFailingItemIsNotRetriedInAHotLoop() throws Exception {
        final LocalFileGroupQueue queue =
                new LocalFileGroupQueue(QUEUE_NAME, getCurrentTestDir().resolve("queue"));
        queue.publish(message("fg-1"));

        final AtomicInteger attempts = new AtomicInteger();
        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.FORWARD,
                new FileGroupQueueWorker(queue, item -> {
                    attempts.incrementAndGet();
                    throw new IOException("destination is down");
                }),
                1,
                Duration.ofMillis(10),
                Duration.ofMillis(10),
                Duration.ofMillis(50),
                Duration.ofMillis(200));

        runner.start();
        try {
            Thread.sleep(1000);
        } finally {
            runner.stop(Duration.ofSeconds(5));
        }

        // With backoff (50ms doubling to a 200ms cap) roughly 10 attempts fit in a
        // second. Without it the loop managed well over a thousand.
        assertThat(attempts.get())
                .as("attempts in 1s for a permanently failing item")
                .isLessThan(100);
        assertThat(attempts.get())
                .as("it must still be retrying, just not hot-looping")
                .isGreaterThan(1);
    }

    @Test
    void testBackoffResetsAfterASuccess() throws Exception {
        final LocalFileGroupQueue queue =
                new LocalFileGroupQueue(QUEUE_NAME, getCurrentTestDir().resolve("queue"));
        for (int i = 0; i < 20; i++) {
            queue.publish(message("fg-" + i));
        }

        // Fail the first item once, then succeed for everything else. If the backoff
        // did not reset, the remaining items would drain far more slowly.
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();

        final PipelineStageRunner runner = new PipelineStageRunner(
                PipelineStageName.FORWARD,
                new FileGroupQueueWorker(queue, item -> {
                    if (failures.get() == 0) {
                        failures.incrementAndGet();
                        throw new IOException("one transient failure");
                    }
                    processed.incrementAndGet();
                }),
                1,
                Duration.ofMillis(10),
                Duration.ofMillis(10),
                Duration.ofMillis(200),
                Duration.ofMillis(200));

        runner.start();
        try {
            Thread.sleep(2000);
        } finally {
            runner.stop(Duration.ofSeconds(5));
        }

        // One 200ms backoff was served, then the rest drained without further delay.
        // Had the backoff not reset, 20 items at 200ms each would need 4s.
        assertThat(failures.get()).isEqualTo(1);
        assertThat(processed.get())
                .as("remaining items drain at full speed once an item succeeds")
                .isGreaterThanOrEqualTo(19);
    }

    private static FileGroupQueueMessage message(final String fileGroupId) {
        return FileGroupQueueMessage.create(
                QUEUE_NAME,
                fileGroupId,
                FileStoreLocation.localFileSystem("aggregateStore", Path.of("/tmp/store/0000000001")),
                "aggregate",
                "test-node",
                null,
                Map.of());
    }
}
