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

package stroom.query.language.functions;

import stroom.test.common.TestUtil;
import stroom.util.exception.ThrowingFunction;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDateUtil {

    @TestFactory
    Stream<DynamicTest> testCreateNormalDateTimeString() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(dateStr -> {
                    final long epochMs = DateUtil.parseNormalDateTimeString(dateStr);
                    return DateUtil.createNormalDateTimeString(epochMs);
                })
                .withSimpleEqualityAssertion()
                .addCase("2010-01-01T23:59:59.1Z", "2010-01-01T23:59:59.100Z")
                .addCase("2010-01-01T23:59:59.12Z", "2010-01-01T23:59:59.120Z")
                .addCase("2010-01-01T23:59:59.123Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.1234Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.12345Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123456Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.000123Z", "2010-01-01T23:59:59.000Z")
                .addCase("2010-01-01T23:59:59.0Z", "2010-01-01T23:59:59.000Z")
                .addCase("2010-01-01T23:59:59.00Z", "2010-01-01T23:59:59.000Z")
                .addCase("2010-01-01T23:59:59.000Z", "2010-01-01T23:59:59.000Z")
                .addCase("2010-01-01T23:59Z", "2010-01-01T23:59:00.000Z")
                .addCase("2010-01-01T23:59:59Z", "2010-01-01T23:59:59.000Z")
                .addCase("2010-01-01T23:59:59+02:00", "2010-01-01T21:59:59.000Z")
                .addCase("2010-01-01T23:59:59.123+02", "2010-01-01T21:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+00:00", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+02:00", "2010-01-01T21:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123-03:00", "2010-01-02T02:59:59.123Z")
//                .addCase("2010-01-01T23:59:59.123+02:00:00", "2010-01-01T21:59:59.123Z")
                .addThrowsCase("2010-01-01T23:59:59", IllegalArgumentException.class) // No zone
                .addThrowsCase("2010-01-01T23:59:59.123+2", IllegalArgumentException.class) // No short offsets
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSimpleParsing() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(dateStr -> {
                    final Instant instant = Instant.ofEpochMilli(DateUtil.parseNormalDateTimeString(dateStr));
                    final String normalDateTimeString = stroom.util.date.DateUtil.createNormalDateTimeString(instant);
                    final Instant instant2 = stroom.util.date.DateUtil.parseNormalDateTimeStringToInstant(
                            normalDateTimeString);
                    // Make sure that we can reverse the parsing/formatting.
                    // Truncate to millis because our formatter only outputs up to millis.
                    // Can't compare normalDateTimeString to dateStr as
                    assertThat(instant2)
                            .isEqualTo(instant.truncatedTo(ChronoUnit.MILLIS));
                    return instant.toString();
                })
                .withSimpleEqualityAssertion()
                .addCase("2010-01-01T23:59:59.1Z", "2010-01-01T23:59:59.100Z")
                .addCase("2010-01-01T23:59:59.12Z", "2010-01-01T23:59:59.120Z")
                .addCase("2010-01-01T23:59:59.123Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.1234Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.12345Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123456Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.000123Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59:59.0Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59:59.00Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59:59.000Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59Z", "2010-01-01T23:59:00Z")
                .addCase("2010-01-01T23:59:59Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59:59+02:00", "2010-01-01T21:59:59Z")
                .addCase("2010-01-01T23:59:59.123+02", "2010-01-01T21:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+00:00", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+02:00", "2010-01-01T21:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123-03:00", "2010-01-02T02:59:59.123Z")
//                .addCase("2010-01-01T23:59:59.123+02:00:00", "2010-01-01T21:59:59.123Z")
                .addThrowsCase("2010-01-01T23:59:59", IllegalArgumentException.class) // No zone
                .addThrowsCase("2010-01-01T23:59:59.123+2", IllegalArgumentException.class) // No short offsets
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParse() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, DateTimeFormatter.class, ZoneId.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final long ms = DateUtil.parse(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3);
                    return Instant.ofEpochMilli(ms).toString();
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("2010-01-01T23:59:59.1Z", null, null), "2010-01-01T23:59:59.100Z")
                .addCase(Tuple.of("2010-01-01T23:59:59.123Z", null, null), "2010-01-01T23:59:59.123Z")
                .addCase(Tuple.of("2010-01-01T23:59:59.123456Z", null, null), "2010-01-01T23:59:59.123Z")
                .addCase(
                        Tuple.of("20100101 235959123", DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSS"), null),
                        "2010-01-01T23:59:59.123Z")
                .addCase(
                        Tuple.of("20100101 235959123+0200", DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSSXX"), null),
                        "2010-01-01T21:59:59.123Z")
                .addCase(
                        Tuple.of("20100101 235959123",
                                DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSS"),
                                ZoneOffset.ofHours(2)),
                        "2010-01-01T21:59:59.123Z")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseLocal() {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, DateTimeFormatter.class, ZoneId.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final LocalDateTime localDateTime = DateUtil.parseLocal(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3);
                    return formatter.format(localDateTime);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("2010-01-01T23:59:59.1Z", null, null), "2010-01-01T23:59:59.100")
                .addCase(Tuple.of("2010-01-01T23:59:59.123Z", null, null), "2010-01-01T23:59:59.123")
                .addCase(Tuple.of("2010-01-01T23:59:59.123456Z", null, null), "2010-01-01T23:59:59.123")
                .addCase(
                        Tuple.of("20100101 235959123", DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSS"), null),
                        "2010-01-01T23:59:59.123")
                .addCase(
                        Tuple.of("20100101 235959123+0200", DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSSXX"), null),
                        "2010-01-01T21:59:59.123")
                .addCase(
                        Tuple.of("20100101 235959123",
                                DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSS"),
                                ZoneOffset.ofHours(2)),
                        "2010-01-01T23:59:59.123")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormat() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, DateTimeFormatter.class, ZoneId.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final long epochMs = DateUtil.parseNormalDateTimeString(testCase.getInput()._1);
                    return DateUtil.format(
                            epochMs,
                            testCase.getInput()._2,
                            testCase.getInput()._3);
                })
                .withSimpleEqualityAssertion()
                // Default formatter and zone
                .addCase(Tuple.of("2010-01-01T23:59:59Z", null, null), "2010-01-01T23:59:59.000Z")
                .addCase(Tuple.of("2010-01-01T23:59:59+00:00", null, null), "2010-01-01T23:59:59.000Z")
                .addCase(Tuple.of("2010-01-01T23:59:59.1Z", null, null), "2010-01-01T23:59:59.100Z")
                .addCase(Tuple.of("2010-01-01T23:59:59.123Z", null, null), "2010-01-01T23:59:59.123Z")
                .addCase(Tuple.of("2010-01-01T23:59:59.123456Z", null, null), "2010-01-01T23:59:59.123Z")

                .addCase(
                        Tuple.of("2010-01-01T23:59:59.123Z", DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSSX"), null),
                        "20100101 235959123Z")
                .addCase(
                        Tuple.of("2010-01-01T23:59:59.123+02",
                                DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSS XX"),
                                null),
                        "20100101 215959123 Z")
                .addCase(
                        Tuple.of("2010-01-01T23:59:59.123Z",
                                DateTimeFormatter.ofPattern("yyyyMMdd HHmmssSSS XXX"),
                                ZoneOffset.ofHours(3)),
                        "20100102 025959123 +03:00")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetTimeZone() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(ZoneId.class)
                .withSingleArgTestFunction(ThrowingFunction.unchecked(DateUtil::getTimeZone))
                .withSimpleEqualityAssertion()
                .addCase(null, ZoneOffset.UTC)
                .addCase("", ZoneOffset.UTC)
                .addCase(" ", ZoneOffset.UTC)
                .addCase("Z", ZoneOffset.UTC)
                .addCase("+2", ZoneOffset.ofHours(2))
                .addCase("+02", ZoneOffset.ofHours(2))
                .addCase("+0200", ZoneOffset.ofHours(2))
                .addCase("+02:00", ZoneOffset.ofHours(2))
                .addCase("+020000", ZoneOffset.ofHours(2))
                .addCase("+02:00:00", ZoneOffset.ofHours(2))
                .addCase("+02:30:10", ZoneOffset.ofHoursMinutesSeconds(2, 30, 10))
                .build();
    }
}
