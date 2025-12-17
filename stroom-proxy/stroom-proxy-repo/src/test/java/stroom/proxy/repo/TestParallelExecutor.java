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

package stroom.proxy.repo;

import stroom.test.common.TestUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestParallelExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestParallelExecutor.class);

    @Test
    void startStop() throws Exception {
        final int taskCount = 10;

        final BlockingQueue<Integer> inputQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> outputQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < taskCount; i++) {
            inputQueue.add(i);
        }
        final Supplier<Runnable> taskSupplier = () -> {
            return () -> {
                try {
                    final Integer item = inputQueue.take();
                    outputQueue.add(item);
                    LOGGER.debug("item: {}, inputQueue.size: {}, outputQueue.size: {}",
                            item, inputQueue.size(), outputQueue.size());
                } catch (final InterruptedException e) {
                    throw new UncheckedInterruptedException(e);
                }
            };
        };

        final ParallelExecutor parallelExecutor = new ParallelExecutor(
                "test-thread", taskSupplier, 4);

        assertThat(parallelExecutor.isStopped())
                .isTrue();
        assertThat(parallelExecutor.isPaused())
                .isFalse();

        parallelExecutor.start();

        assertThat(parallelExecutor.isStopped())
                .isFalse();
        assertThat(parallelExecutor.isPaused())
                .isFalse();

        TestUtil.waitForIt(
                outputQueue::size,
                taskCount,
                "output queue size to reach " + taskCount);

        parallelExecutor.stop();

        TestUtil.waitForIt(
                parallelExecutor::isStopped,
                true,
                "isStopped==true");

        assertThat(parallelExecutor.isStopped())
                .isTrue();
        assertThat(parallelExecutor.isPaused())
                .isFalse();
    }

    @Test
    void pause() throws Exception {
        final int taskCount = 50;
        final int threadCount = 4;

        final BlockingQueue<Integer> inputQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> outputQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < taskCount; i++) {
            inputQueue.add(i);
        }
        final AtomicBoolean areTasksSlow = new AtomicBoolean(true);
        final Supplier<Runnable> taskSupplier = () -> {
            return () -> {
                try {
                    final Integer item = inputQueue.take();
                    // Slow down the tasks while we are pausing/releasing
                    if (areTasksSlow.get()) {
                        ThreadUtil.sleep(1_000);
                    }
                    outputQueue.add(item);
                    LOGGER.debug("item: {}, inputQueue.size: {}, outputQueue.size: {}",
                            item, inputQueue.size(), outputQueue.size());
                } catch (final InterruptedException e) {
                    throw new UncheckedInterruptedException(e);
                }
            };
        };

        final ParallelExecutor parallelExecutor = new ParallelExecutor(
                "test-thread", taskSupplier, threadCount);

        assertThat(parallelExecutor.isStopped())
                .isTrue();
        assertThat(parallelExecutor.isPaused())
                .isFalse();

        parallelExecutor.start();

        TestUtil.waitForIt(
                parallelExecutor::isStopped,
                false,
                "isStopped==false");

        assertThat(parallelExecutor.isStopped())
                .isFalse();
        assertThat(parallelExecutor.isPaused())
                .isFalse();

        // Give some tasks a chance to have run
        TestUtil.waitForIt(
                () -> outputQueue.size() >= threadCount,
                true,
                () -> "output queue size to be >= " + threadCount,
                Duration.ofSeconds(20),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        // Do it twice to make sure it is happy with that
        LOGGER.debug("Pausing ----------------------------------------------------------------");
        parallelExecutor.pause();
        parallelExecutor.pause();

        assertThat(parallelExecutor.isPaused())
                .isTrue();

        final int outputsAfterPause1 = outputQueue.size();

        ThreadUtil.sleep(5_000);

        // There may have been up to threadCount tasks run at the time we paused,
        // so up to that many may have completed

        final int outputsAfterPause2 = outputQueue.size();
        LOGGER.debug("outputsAfterPause1: {}, outputsAfterPause2: {}", outputsAfterPause1, outputsAfterPause2);
        assertThat(outputsAfterPause2 - outputsAfterPause1)
                .isLessThanOrEqualTo(threadCount);

        LOGGER.debug("Resuming ----------------------------------------------------------------");
        // Do it twice to make sure it is happy with that
        parallelExecutor.resume();
        parallelExecutor.resume();

        ThreadUtil.sleep(1_000);

        // Pause/resume again
        parallelExecutor.pause();

        ThreadUtil.sleep(1_000);

        parallelExecutor.resume();

        // Now let it run at full speed to the end
        areTasksSlow.set(false);

        // Wait for all tasks to complete
        TestUtil.waitForIt(
                outputQueue::size,
                taskCount,
                () -> "output queue size to reach " + taskCount,
                Duration.ofSeconds(20),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        parallelExecutor.stop();

        TestUtil.waitForIt(
                parallelExecutor::isStopped,
                true,
                () -> "isStopped==true",
                Duration.ofSeconds(20),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        assertThat(parallelExecutor.isStopped())
                .isTrue();
        assertThat(parallelExecutor.isPaused())
                .isFalse();
    }

    @Test
    void stopWhilePaused() throws Exception {
        final int taskCount = 50;
        final int threadCount = 4;

        final BlockingQueue<Integer> inputQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> outputQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < taskCount; i++) {
            inputQueue.add(i);
        }
        final AtomicBoolean areTasksSlow = new AtomicBoolean(true);
        final Supplier<Runnable> taskSupplier = () -> {
            return () -> {
                try {
                    final Integer item = inputQueue.take();
                    // Slow down the tasks while we are pausing/releasing
                    if (areTasksSlow.get()) {
                        ThreadUtil.sleep(1_000);
                    }
                    outputQueue.add(item);
                    LOGGER.debug("item: {}, inputQueue.size: {}, outputQueue.size: {}",
                            item, inputQueue.size(), outputQueue.size());
                } catch (final InterruptedException e) {
                    throw new UncheckedInterruptedException(e);
                }
            };
        };

        final ParallelExecutor parallelExecutor = new ParallelExecutor(
                "test-thread", taskSupplier, threadCount);

        assertThat(parallelExecutor.isStopped())
                .isTrue();
        assertThat(parallelExecutor.isPaused())
                .isFalse();

        parallelExecutor.start();

        TestUtil.waitForIt(
                parallelExecutor::isStopped,
                false,
                "isStopped==false");

        assertThat(parallelExecutor.isStopped())
                .isFalse();
        assertThat(parallelExecutor.isPaused())
                .isFalse();

        // Give some tasks a chance to have run
        TestUtil.waitForIt(
                () -> outputQueue.size() >= threadCount,
                true,
                () -> "output queue size to be >= " + threadCount,
                Duration.ofSeconds(20),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        // Do it twice to make sure it is happy with that
        LOGGER.debug("Pausing ----------------------------------------------------------------");
        parallelExecutor.pause();

        ThreadUtil.sleep(1_000);

        parallelExecutor.stop();

        TestUtil.waitForIt(
                parallelExecutor::isStopped,
                true,
                () -> "isStopped==true",
                Duration.ofSeconds(20),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        assertThat(parallelExecutor.isStopped())
                .isTrue();
        assertThat(parallelExecutor.isPaused())
                .isFalse(); // Un-paused by calling stop
    }
}
