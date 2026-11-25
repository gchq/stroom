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


import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDateUtil {

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-30T10:00:00Z", "2023-02-29T10:00:00.000Z", "2024-04-31T00:00:00+01:00",
            "2024-04-31T00:00:00+0100", "2024-04-31T23:59:59-18", "2024-02-30T24:00:00Z", "2024-06-31T10:00Z"})
    void dayThatTheMonthDoesNotHave(final String text) {
        assertThatThrownBy(() -> DateUtil.parseNormalDateTimeString(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse date: \"" + text + "\": ")
                .hasMessageContaining("Invalid date '");
        assertThatThrownBy(() -> DateUtil.parseNormalDateTimeStringToInstant(text))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateUtil.parseUnknownString(text))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateUtil.normaliseDate(text, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date '");
        assertThat(DateUtil.normaliseDate(text, true)).isEqualTo(text);
    }

    @ParameterizedTest
    @CsvSource({
            "2024-02-29T10:00:00Z,2024-02-29T10:00:00Z",
            "2023-02-28T24:00:00Z,2023-03-01T00:00:00Z",
            "2024-02-29T24:00:00Z,2024-03-01T00:00:00Z",
            "2023-12-31T24:00:00Z,2024-01-01T00:00:00Z",
            "2024-03-01T00:00:00Z,2024-03-01T00:00:00Z",
            "2024-01-31T23:00:00-18:00,2024-02-01T17:00:00Z",
            "2024-04-30T00:30:00+01:00,2024-04-29T23:30:00Z"})
    void dayThatTheMonthHas(final String text, final Instant expected) {
        assertThat(DateUtil.parseNormalDateTimeStringToInstant(text)).isEqualTo(expected);
        assertThat(DateUtil.parseUnknownString(text)).isEqualTo(expected.toEpochMilli());
        assertThat(DateUtil.parseNormalDateTimeStringToInstant(DateUtil.normaliseDate(text, false)))
                .isEqualTo(expected);
    }

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

    @TestFactory
    Stream<DynamicTest> testSimpleParsing() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(dateStr -> {
                    final Instant instant = DateUtil.parseNormalDateTimeStringToInstant(dateStr);
                    final String normalDateTimeString = DateUtil.createNormalDateTimeString(instant);
                    final Instant instant2 = DateUtil.parseNormalDateTimeStringToInstant(normalDateTimeString);
                    // Make sure that we can reverse the parsing/formatting.
                    // Truncate to millis because our formatter only outputs up to millis
                    assertThat(instant2)
                            .isEqualTo(instant.truncatedTo(ChronoUnit.MILLIS));
                    return instant.toString();
                })
                .withSimpleEqualityAssertion()
                .addCase("2010-01-01T23:59:59.1Z", "2010-01-01T23:59:59.100Z")
                .addCase("2010-01-01T23:59:59.12Z", "2010-01-01T23:59:59.120Z")
                .addCase("2010-01-01T23:59:59.123Z", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.1234Z", "2010-01-01T23:59:59.123400Z")
                .addCase("2010-01-01T23:59:59.12345Z", "2010-01-01T23:59:59.123450Z")
                .addCase("2010-01-01T23:59:59.123456Z", "2010-01-01T23:59:59.123456Z")
                .addCase("2010-01-01T23:59:59.000123Z", "2010-01-01T23:59:59.000123Z")
                .addCase("2010-01-01T23:59:59.0Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59:59.00Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59:59.000Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59Z", "2010-01-01T23:59:00Z")
                .addCase("2010-01-01T23:59:59Z", "2010-01-01T23:59:59Z")
                .addCase("2010-01-01T23:59:59+02:00", "2010-01-01T21:59:59Z")
                .addCase("2010-01-01T23:59:59.123+02", "2010-01-01T21:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+00:00", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+02:00", "2010-01-01T21:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+0200", "2010-01-01T21:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123-03:00", "2010-01-02T02:59:59.123Z")
//                .addCase("2010-01-01T23:59:59.123+02:00:00", "2010-01-01T21:59:59.123Z")
                .addThrowsCase("2010-01-01T23:59:59", IllegalArgumentException.class) // No zone
                .addThrowsCase("2010-01-01T23:59:59.123+2", IllegalArgumentException.class) // No short offsets
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testParseUnknown() {
        final Instant instant = Instant.parse("2010-01-01T23:59:59.123Z");
        final long millis = instant.toEpochMilli();

        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(dateStr ->
                        Instant.ofEpochMilli(DateUtil.parseUnknownString(dateStr)).toString())
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
                .addCase(Long.toString(millis), "2010-01-01T23:59:59.123Z")
                .addCase(Long.toString(0L), "1970-01-01T00:00:00Z")
                .addThrowsCase("2010-01-01T23:59:59", IllegalArgumentException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNormaliseDate_withErrors() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(date -> DateUtil.normaliseDate(date, false))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase(" ", " ")
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
                .addCase("2010-01-01T23:59:59+02:00", "2010-01-01T23:59:59.000+0200")
                .addCase("2010-01-01T23:59:59.123+02", "2010-01-01T23:59:59.123+0200")
                .addCase("2010-01-01T23:59:59.123+00:00", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+02:00", "2010-01-01T23:59:59.123+0200")
                .addCase("2010-01-01T23:59:59.123-03:00", "2010-01-01T23:59:59.123-0300")
//                .addCase("2010-01-01T23:59:59.123+02:00:00", "2010-01-01T23:59:59.123+0200")
                .addCase(String.valueOf(Instant.EPOCH.toEpochMilli()), "1970-01-01T00:00:00.000Z")
                .addCase(
                        String.valueOf(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        "2010-01-01T23:59:59.123Z")
                .addThrowsCase("2010-01-01T23:59:59", IllegalArgumentException.class) // No zone
                .addThrowsCase("2010-01-01T23:59:59.123+2", IllegalArgumentException.class) // No short offsets
                .addThrowsCase("foo", IllegalArgumentException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNormaliseDate_ignoreErrors() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(date -> DateUtil.normaliseDate(date, true))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", "")
                .addCase(" ", " ")
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
                .addCase("2010-01-01T23:59:59+02:00", "2010-01-01T23:59:59.000+0200")
                .addCase("2010-01-01T23:59:59.123+02", "2010-01-01T23:59:59.123+0200")
                .addCase("2010-01-01T23:59:59.123+00:00", "2010-01-01T23:59:59.123Z")
                .addCase("2010-01-01T23:59:59.123+02:00", "2010-01-01T23:59:59.123+0200")
                .addCase("2010-01-01T23:59:59.123-03:00", "2010-01-01T23:59:59.123-0300")
//                .addCase("2010-01-01T23:59:59.123+02:00:00", "2010-01-01T23:59:59.123+0200")
                .addCase(String.valueOf(Instant.EPOCH.toEpochMilli()), "1970-01-01T00:00:00.000Z")
                .addCase(
                        String.valueOf(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        "2010-01-01T23:59:59.123Z")
                // Unchanged cases
                .addCase("2010-01-01T23:59:59", "2010-01-01T23:59:59") // No zone
                .addCase("2010-01-01T23:59:59.123+2", "2010-01-01T23:59:59.123+2") // No short offsets
                .addCase("foo", "foo")
                .build();
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
