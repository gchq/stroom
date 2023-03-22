/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.date;


import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDateUtil {

    @TestFactory
    Stream<DynamicTest> testSimpleZuluTimes() {
        return Stream.of("2008-11-18T09:47:50.548Z",
                        "2008-11-18T09:47:00.000Z",
                        "2008-11-18T13:47:00.000Z",
                        "2008-01-01T13:47:00.000Z",
                        "2008-08-01T13:47:00.000Z")
                .map(dateStr -> DynamicTest.dynamicTest(dateStr, () -> {
                    doTest(dateStr);
                }));
    }


    private void doTest(final String dateString) {
        final long epochMs = DateUtil.parseNormalDateTimeString(dateString);
        final Instant instant = DateUtil.parseNormalDateTimeStringToInstant(dateString);

        assertThat(epochMs)
                .isEqualTo(instant.toEpochMilli());

        // Convert Back to string
        assertThat(DateUtil.createNormalDateTimeString(epochMs))
                .isEqualTo(dateString)
                .isEqualTo(DateUtil.createNormalDateTimeString(Instant.ofEpochMilli(epochMs)));
    }

    @Test
    void testSimple() {
        assertThat(DateUtil.createNormalDateTimeString(DateUtil.parseNormalDateTimeString("2010-01-01T23:59:59.000Z")))
                .isEqualTo("2010-01-01T23:59:59.000Z");

    }

    @Test
    void testSimpleFileFormat() {
        final long timeMs = DateUtil.parseNormalDateTimeString("2010-01-01T23:59:59.000Z");
        assertThat(DateUtil.createFileDateTimeString(timeMs))
                .isEqualTo("2010-01-01T23#59#59,000Z")
                .isEqualTo(DateUtil.createFileDateTimeString(Instant.ofEpochMilli(timeMs)));
    }

    @Test
    void testRoundDown() {
        Instant time = DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T23:59:59.000Z");
        Instant rounded = DateUtil.roundDown(time, Duration.parse("PT30M"));
        assertThat(rounded).isEqualTo(DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T23:30:00.000Z"));

        time = DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T23:29:59.000Z");
        rounded = DateUtil.roundDown(time, Duration.parse("PT30M"));
        assertThat(rounded).isEqualTo(DateUtil.parseNormalDateTimeStringToInstant("2010-01-01T23:00:00.000Z"));
    }
}
