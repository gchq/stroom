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

import stroom.util.logging.DurationTimer.IterationTimer;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TestDurationTimer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestDurationTimer.class);

    @Test
    void testIterationTimer_empty() {
        final IterationTimer iterationTimer = DurationTimer.newIterationTimer();

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ZERO);
        assertThat(iterationTimer.getMinDuration())
                .isEmpty();
        assertThat(iterationTimer.getMaxDuration())
                .isEmpty();
        assertThat(iterationTimer.getAverageDuration())
                .isEqualTo(Duration.ZERO);
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(0);
    }

    @Test
    void testIterationTimer_one() {
        final IterationTimer iterationTimer = DurationTimer.newIterationTimer();

        iterationTimer.logIteration(Duration.ofSeconds(1));

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(iterationTimer.getMinDuration())
                .hasValue(Duration.ofSeconds(1));
        assertThat(iterationTimer.getMaxDuration())
                .hasValue(Duration.ofSeconds(1));
        assertThat(iterationTimer.getAverageDuration())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(1);

        LOGGER.info(iterationTimer.toString());
    }

    @Test
    void testIterationTimer_multiple() {
        final IterationTimer iterationTimer = DurationTimer.newIterationTimer();

        iterationTimer.logIteration(Duration.ofSeconds(10));

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(iterationTimer.getMinDuration())
                .hasValue(Duration.ofSeconds(10));
        assertThat(iterationTimer.getMaxDuration())
                .hasValue(Duration.ofSeconds(10));
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(1);

        iterationTimer.logIteration(Duration.ofSeconds(20));
        iterationTimer.logIteration(Duration.ofSeconds(5));
        iterationTimer.logIteration(Duration.ofSeconds(5));

        assertThat(iterationTimer.getCumulativeDuration())
                .isEqualTo(Duration.ofSeconds(40));
        assertThat(iterationTimer.getMinDuration())
                .hasValue(Duration.ofSeconds(5));
        assertThat(iterationTimer.getMaxDuration())
                .hasValue(Duration.ofSeconds(20));
        assertThat(iterationTimer.getAverageDuration())
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(iterationTimer.getIterationCount())
                .isEqualTo(4);

        LOGGER.info(iterationTimer.toString());
    }
}
