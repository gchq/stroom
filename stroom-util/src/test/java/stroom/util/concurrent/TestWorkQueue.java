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

import stroom.util.exception.ThrowingRunnable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

class TestWorkQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestWorkQueue.class);

    @Test
    void singleThreadCapacity1() throws InterruptedException {
        final Executor executor = Executors.newFixedThreadPool(20);
        final WorkQueue workQueue = new WorkQueue(executor, 1, 1);
        final int itemCount = 1_000;
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final LongAdder cnt = new LongAdder();

        final HighWaterMarkTracker highWaterMarkTracker = new HighWaterMarkTracker();
        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(() -> {
                highWaterMarkTracker.increment();
                cnt.increment();
                completionLatch.countDown();
                highWaterMarkTracker.decrement();
            });
        }

        completionLatch.await();

        // Work queue has thread count of 1
        assertThat(highWaterMarkTracker.getHighWaterMark())
                .isEqualTo(1);
        assertThat(cnt.longValue())
                .isEqualTo(itemCount);
    }

    @Test
    void singleThreadedCapacity100() throws InterruptedException {
        final Executor executor = Executors.newFixedThreadPool(20);
        final WorkQueue workQueue = new WorkQueue(executor, 1, 100);
        final int itemCount = 1_000;
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final LongAdder cnt = new LongAdder();

        final HighWaterMarkTracker highWaterMarkTracker = new HighWaterMarkTracker();
        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(() -> {
                highWaterMarkTracker.increment();
                cnt.increment();
                completionLatch.countDown();
                highWaterMarkTracker.decrement();
            });
        }

        completionLatch.await();

        // Work queue has thread count of 1
        assertThat(highWaterMarkTracker.getHighWaterMark())
                .isEqualTo(1);
        assertThat(cnt.longValue())
                .isEqualTo(itemCount);
    }

    @Test
    void multiThreadedCapacity100() throws InterruptedException {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        LOGGER.info("availableProcessors: {}", availableProcessors);
        // Have more threads than we have workers, so we have threads ready to go
        final Executor executor = Executors.newFixedThreadPool(availableProcessors * 2);
        final WorkQueue workQueue = new WorkQueue(executor, availableProcessors, 100);
        final int itemCount = 1_000;
        final CountDownLatch startLatch = new CountDownLatch(availableProcessors);
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final LongAdder cnt = new LongAdder();

        final HighWaterMarkTracker highWaterMarkTracker = new HighWaterMarkTracker();
        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(ThrowingRunnable.unchecked(() -> {
                startLatch.countDown();
                // Wait for the expected number of concurrent workers to be executing together
                // before we let them all go
                final boolean success = startLatch.await(10, TimeUnit.SECONDS);
                if (!success) {
                    throw new RuntimeException("Didn't count down to zero");
                }
                highWaterMarkTracker.increment();
                cnt.increment();
                // Tiny sleep to give it more of a chance of running concurrently with other threads
                ThreadUtil.sleepIgnoringInterrupts(5);
                completionLatch.countDown();
                highWaterMarkTracker.decrement();
            }));
        }

        completionLatch.await();

        // Work queue has thread count of 10 so should get 10 items being exec'd at once.
        // If this fails in CI then might need to change the assertion to > 1 && < availableProcessors
        assertThat(highWaterMarkTracker.getHighWaterMark())
                .isEqualTo(availableProcessors);
        assertThat(cnt.longValue())
                .isEqualTo(itemCount);
    }
}
