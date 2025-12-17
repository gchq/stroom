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

package stroom.security.common.impl;

import stroom.util.authentication.Refreshable;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

class TestRefreshManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestRefreshManager.class);

    private static final Duration REFRESH_BUFFER = Duration.ofMillis(0);

    private final List<String> refreshedItems = new CopyOnWriteArrayList<>();
    private final Map<String, List<String>> valuesMap = new ConcurrentHashMap<>();
    private RefreshManager refreshManager = null;

    @BeforeEach
    void setUp() throws Exception {
        refreshedItems.clear();
        if (refreshManager != null) {
            refreshManager.stop();
            refreshManager = null;
        } else {
            refreshManager = new RefreshManager();
            refreshManager.start();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (refreshManager != null) {
            refreshManager.stop();
            refreshManager = null;
        }
    }

    @Test
    void testWithDelayQueue() throws Exception {
        final Duration duration1 = Duration.ofMillis(500);
        final Duration duration2 = Duration.ofMillis(900);

        final Refreshable item1 = createRefreshableItem("1", duration1);
        final Refreshable item2 = createRefreshableItem("2", duration2);

        refreshManager.addOrUpdate(item2);
        refreshManager.addOrUpdate(item1);

        ThreadUtil.sleepIgnoringInterrupts(1_700);

        refreshManager.stop();

        Assertions.assertThat(refreshedItems)
                .containsExactly(
                        item1.getUuid(),
                        item2.getUuid(),
                        item1.getUuid(),
                        item1.getUuid());
        // there are 3x 500ms in 1700ms
        Assertions.assertThat(valuesMap.get(item1.getUuid()))
                .containsExactly("1", "10", "100");
        // 1x 900ms in 1700ms
        Assertions.assertThat(valuesMap.get(item2.getUuid()))
                .containsExactly("2");

        Assertions.assertThat(refreshManager.size())
                .isLessThanOrEqualTo(2);
    }

    @Test
    void testWithDelayQueue_manualRefresh() throws Exception {
        final Duration duration1 = Duration.ofMillis(500);

        final Refreshable item1 = createRefreshableItem("1", duration1);

        refreshManager.addOrUpdate(item1);
        ThreadUtil.sleepIgnoringInterrupts(400);
        item1.refresh(refreshManager::addOrUpdate);
        ThreadUtil.sleepIgnoringInterrupts(400);
        item1.refresh(refreshManager::addOrUpdate);
        ThreadUtil.sleepIgnoringInterrupts(400);
        item1.refresh(refreshManager::addOrUpdate);
        ThreadUtil.sleepIgnoringInterrupts(400);

        refreshManager.stop();

        // there are 3x 500ms in 1700ms
        Assertions.assertThat(valuesMap.get(item1.getUuid()))
                .containsExactly("1", "10", "100");

        Assertions.assertThat(refreshManager.size())
                .isLessThanOrEqualTo(1);
    }

    private Refreshable createRefreshableItem(final String initialValue,
                                              final Duration timeBeforeExpiry) {
        return new MyRefreshable(timeBeforeExpiry, initialValue);
    }


    // --------------------------------------------------------------------------------


    private class MyRefreshable implements Refreshable {

        private final String uuid;
        private final AtomicReference<String> valueRef;
        private final Duration timeTillExpiry;
        private volatile Instant expiry;

        private MyRefreshable(final Duration timeTillExpiry, final String initialValue) {
            this.timeTillExpiry = timeTillExpiry;
            this.expiry = Instant.now().plus(timeTillExpiry);
            this.uuid = UUID.randomUUID().toString();
            this.valueRef = new AtomicReference<>(initialValue);
        }

        @Override
        public String getUuid() {
            return uuid;
        }

        @Override
        public boolean refresh(final Consumer<Refreshable> onRefreshAction) {
            refreshedItems.add(uuid);
            valuesMap.computeIfAbsent(uuid, k -> new ArrayList<>()).add(valueRef.get());
            valueRef.accumulateAndGet("0", (s, s2) -> s + s2);
            this.expiry = Instant.now().plus(timeTillExpiry);
            LOGGER.info("After refresh value: {}, expiry: {}", valueRef.get(), expiry);
            NullSafe.consume(this, onRefreshAction);
            return true;
        }

        @Override
        public Instant getExpireTime() {
            return expiry;
        }

        @Override
        public long getExpireTimeEpochMs() {
            return expiry.toEpochMilli();
        }
    }
}
