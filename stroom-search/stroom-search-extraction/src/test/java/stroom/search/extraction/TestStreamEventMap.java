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

package stroom.search.extraction;

import stroom.search.extraction.StreamEventMap.EventSet;
import stroom.util.concurrent.CompleteException;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class TestStreamEventMap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStreamEventMap.class);
    private static final int TOTAL_EVENTS = 1000000;

    @Test
    void test() {
        final StreamEventMap streamEventMap = new StreamEventMap(100000);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Start a producer.
        final CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < TOTAL_EVENTS; i++) {
                final int streamId = (int) (Math.random() * 10);
                try {
                    streamEventMap.put(new Event(streamId, i, null));
                } catch (final CompleteException e) {
                    throw new RuntimeException(e);
                }
            }
            streamEventMap.complete();
        });
        futures.add(producer);

        // Start 5 consumers
        final AtomicInteger total = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            final CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
                try {
                    while (true) {
                        final EventSet eventSet = streamEventMap.take();
                        if (eventSet != null) {
                            total.addAndGet(eventSet.size());
                        }
                    }
                } catch (final CompleteException e) {
                    LOGGER.debug(() -> "Complete");
                    LOGGER.trace(e::getMessage, e);
                }
            });
            futures.add(consumer);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Make sure we get all the events back.
        assertThat(total.get()).isEqualTo(TOTAL_EVENTS);
    }

    @Test
    void testManyTakersThenComplete() throws InterruptedException {
        final int count = 10;
        final StreamEventMap streamEventMap = new StreamEventMap(100);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(count);
        final CountDownLatch aboutToTakeLatch = new CountDownLatch(count);
        final AtomicBoolean didComplete = new AtomicBoolean(false);

        // Start a consumer.
        for (int i = 0; i < count; i++) {
            final CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
                try {
                    aboutToTakeLatch.countDown();
                    final EventSet eventSet = streamEventMap.take();
                } catch (final CompleteException e) {
                    LOGGER.debug("Completed");
                    didComplete.set(true);
                }
            }, executorService);
            futures.add(consumer);
        }

        aboutToTakeLatch.await();
        ThreadUtil.sleepIgnoringInterrupts(50);

        streamEventMap.complete();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertThat(didComplete)
                .isTrue();
    }

    @Test
    void testManyTakersThenTerminate() throws InterruptedException {
        final int count = 10;
        final StreamEventMap streamEventMap = new StreamEventMap(100);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(count);
        final CountDownLatch aboutToTakeLatch = new CountDownLatch(count);
        final AtomicBoolean didComplete = new AtomicBoolean(false);

        // Start a consumer.
        for (int i = 0; i < count; i++) {
            final CompletableFuture<Void> consumer = CompletableFuture.runAsync(() -> {
                try {
                    aboutToTakeLatch.countDown();
                    final EventSet eventSet = streamEventMap.take();
                } catch (final CompleteException e) {
                    LOGGER.debug("Completed");
                    didComplete.set(true);
                }
            }, executorService);
            futures.add(consumer);
        }

        aboutToTakeLatch.await();
        ThreadUtil.sleepIgnoringInterrupts(50);

        streamEventMap.terminate();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertThat(didComplete)
                .isTrue();
    }

    @Test
    void testManyPuttersThenComplete() throws InterruptedException {
        // More putters than capacity
        final int count = 10;
        final StreamEventMap streamEventMap = new StreamEventMap(2);
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(count);
        final CountDownLatch aboutToPutLatch = new CountDownLatch(count);
        final AtomicBoolean didComplete = new AtomicBoolean(false);

        // Complete it straight away
        streamEventMap.complete();

        // This should pick up the complete item so will complete the queue and release all putters.
        try {
            streamEventMap.take();
        } catch (final CompleteException e) {
            LOGGER.debug("Completed on take");
        }

        // Start the producers, none of which should block as the map is completed already
        for (int i = 0; i < count; i++) {
            final CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> {
                try {
                    aboutToPutLatch.countDown();
                    final int streamId = (int) (Math.random() * 10);
                    final int eventId = (int) (Math.random() * 10);
                    streamEventMap.put(new Event(streamId, eventId, null));
                } catch (final CompleteException e) {
                    LOGGER.debug("Completed on put");
                    didComplete.set(true);
                }
            }, executorService);
            futures.add(producer);
        }

        aboutToPutLatch.await();
        ThreadUtil.sleepIgnoringInterrupts(50);

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertThat(didComplete)
                .isTrue();
    }
}
