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

package stroom.pipeline.destination;


import stroom.util.date.DateUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.scheduler.CronTrigger;
import stroom.util.scheduler.FrequencyTrigger;
import stroom.util.shared.scheduler.CronExpressions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestRollingFileDestination {

    @Test
    void testFrequency(@TempDir final Path tempDir) throws IOException {
        final Instant time = DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T00:00:00.000Z");
        final Path dir = Files.createTempDirectory("stroom");
        final Path file = dir.resolve("test.log");

        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final RollingFileDestination rollingFileDestination = new RollingFileDestination(
                pathCreator,
                "test",
                new FrequencyTrigger(60000L),
                null,
                100,
                time,
                "test.tmp",
                "test.log",
                dir,
                file,
                false,
                null,
                null);

        assertThat(rollingFileDestination.tryFlushAndRoll(false, time)).isFalse();
        assertThat(rollingFileDestination.tryFlushAndRoll(false, time.plusMillis(60000))).isFalse();
        assertThat(rollingFileDestination.tryFlushAndRoll(false, time.plusMillis(60001))).isTrue();
    }

    @Test
    void testSchedule(@TempDir final Path tempDir) throws IOException {
        final Instant time = DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T00:00:00.000Z");
        final Path dir = Files.createTempDirectory("stroom");
        final Path file = dir.resolve("test.log");
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final RollingFileDestination rollingFileDestination = new RollingFileDestination(
                pathCreator,
                "test",
                null,
                new CronTrigger(CronExpressions.EVERY_MINUTE.getExpression()),
                100,
                time,
                "test.tmp",
                "test.log",
                dir,
                file,
                false,
                null,
                null);

        assertThat(rollingFileDestination.tryFlushAndRoll(false, time)).isFalse();
        assertThat(rollingFileDestination.tryFlushAndRoll(false, time.plusMillis(60000))).isFalse();
        assertThat(rollingFileDestination.tryFlushAndRoll(false, time.plusMillis(60001))).isTrue();
    }
}
