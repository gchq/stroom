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

import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class TestAsyncReference extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAsyncReference.class);

    @Test
    void test() {
        final ExecutorService executor = Executors.newCachedThreadPool();
        final AsyncReference<Integer> asyncReference = new AsyncReference<>(value -> {
            ThreadUtil.sleep(100);
            if (value == null) {
                return 1;
            }
            return value + 1;
        }, Duration.ofMillis(100), executor);

        // Run for 10 seconds.
        final Instant start = Instant.now();
        int updates = 0;
        int calls = 0;
        while (Instant.now().isBefore(start.plus(Duration.ofSeconds(1)))) {
            updates = asyncReference.get();
            calls++;
        }

        LOGGER.info("Updates = " + updates);
        LOGGER.info("Calls = " + calls);

        assertThat(updates).isGreaterThan(2);
        assertThat(calls).isGreaterThan(1000);
    }
}
