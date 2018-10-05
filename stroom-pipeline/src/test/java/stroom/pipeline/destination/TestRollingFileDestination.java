/*
 * Copyright 2018 Crown Copyright
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

import org.junit.Assert;
import org.junit.Test;
import stroom.util.date.DateUtil;
import stroom.util.scheduler.SimpleCron;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestRollingFileDestination {
    @Test
    public void testFrequency() throws IOException {
        final long time = DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z");
        final Path dir = Files.createTempDirectory("stroom");
        final Path file = dir.resolve("test.log");

        final RollingFileDestination rollingFileDestination = new RollingFileDestination(
                "test",
                60000L,
                null,
                100,
                time,
                "test.tmp",
                "test.log",
                dir,
                file);

        Assert.assertFalse(rollingFileDestination.tryFlushAndRoll(false, time));
        Assert.assertFalse(rollingFileDestination.tryFlushAndRoll(false, time + 60000));
        Assert.assertTrue(rollingFileDestination.tryFlushAndRoll(false, time + 60001));
    }

    @Test
    public void testSchedule() throws IOException {
        final long time = DateUtil.parseNormalDateTimeString("2010-01-01T00:00:00.000Z");
        final Path dir = Files.createTempDirectory("stroom");
        final Path file = dir.resolve("test.log");
        final RollingFileDestination rollingFileDestination = new RollingFileDestination(
                "test",
                null,
                SimpleCron.compile("* * *"),
                100,
                time,
                "test.tmp",
                "test.log",
                dir,
                file);

        Assert.assertFalse(rollingFileDestination.tryFlushAndRoll(false, time));
        Assert.assertFalse(rollingFileDestination.tryFlushAndRoll(false, time + 60000));
        Assert.assertTrue(rollingFileDestination.tryFlushAndRoll(false, time + 60001));
    }
}
