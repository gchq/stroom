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

package stroom.util.logging;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.SimpleMetrics.LocalMetrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestSimpleMetrics {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSimpleMetrics.class);

    @Test
    void testLocalMetrics_Disabled() {
        final LocalMetrics localMetrics = SimpleMetrics.createLocalMetrics(false);
        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        localMetrics.measure("x", () -> wasCalled.set(true));
        assertThat(wasCalled)
                .isTrue();

        final Boolean result = localMetrics.measure("y", () -> {
            wasCalled.set(true);
            return wasCalled.get();
        });

        assertThat(wasCalled)
                .isTrue();
        assertThat(result)
                .isTrue();

        localMetrics.toString();

        localMetrics.reset();
    }

    @Test
    void testLocalMetrics_Enabled() {
        final LocalMetrics localMetrics = SimpleMetrics.createLocalMetrics(LOGGER.isInfoEnabled());
        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        localMetrics.measure("x", () -> wasCalled.set(true));
        assertThat(wasCalled)
                .isTrue();
        localMetrics.measure("x", () -> {
            ThreadUtil.sleep(1_000);
            wasCalled.set(true);
        });
        localMetrics.measure("x", () -> wasCalled.set(true));

        final Boolean result = localMetrics.measure("y", () -> {
            wasCalled.set(true);
            return wasCalled.get();
        });

        assertThat(wasCalled)
                .isTrue();
        assertThat(result)
                .isTrue();

        final String output = localMetrics.toString();
        LOGGER.info("toString:\n{}", output);

        localMetrics.reset();
    }
}
