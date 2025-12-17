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

package stroom.pipeline.xsltfunctions;

import stroom.meta.shared.Meta;
import stroom.pipeline.state.MetaHolder;
import stroom.test.common.TestOutcome;
import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestParseDateTime extends AbstractXsltFunctionTest<ParseDateTime> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestParseDateTime.class);

    private static final Instant META_CREATE_TIME = LocalDateTime.of(
                    2010, 3, 1, 12, 45, 22, 643 * 1_000_000)
            .toInstant(ZoneOffset.UTC);

    @Mock
    private Meta mockMeta;
    @SuppressWarnings("unused") // Used by @InjectMocks
    @Mock
    private MetaHolder mockMetaHolder;
    @InjectMocks
    private ParseDateTime parseDateTime;

    @TestFactory
    Stream<DynamicTest> testParseExamples() throws IOException {
        // Dump each test case to a file, so we can add it to the stroom-docs easily
        // content/en/docs/user-guide/pipelines/xslt/xslt-functions.md#parse-dateTime
        final Path tempFile = Files.createTempFile("parse-dateTime-", ".md");
//        Files.writeString(tempFile, """
//                Function Call | Output | Notes
//                ------------- | ------ | -----
//                """, StandardOpenOption.APPEND);

        LOGGER.info("Outputting examples to {}", tempFile.toAbsolutePath());

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final List<String> strArgs = testCase.getInput();
                    final Object[] args = strArgs.toArray(new Object[0]);
                    final Sequence sequence = callFunctionWithSimpleArgs(args);
                    final Optional<String> optVal = getAsDateTimeValue(sequence);
                    return optVal.orElseThrow();
                })
                .withAssertions(outcome -> {
                    final String actualOutput = writeFunctionCallStr(outcome, tempFile);
                    assertThat(actualOutput)
                            .isEqualTo(outcome.getExpectedOutput());
                })
                .addNamedCase(
                        "ISO 8061 no millis",
                        List.of("2024-08-29T00:00:00Z"),
                        "2024-08-29T00:00:00Z")
                .addNamedCase(
                        "ISO 8061 nanos",
                        List.of("2010-01-01T23:59:59.123456Z"),
                        "2010-01-01T23:59:59.123456Z")
                .addNamedCase(
                        "ISO 8061 millis",
                        List.of("2010-01-01T23:59:59.123Z"),
                        "2010-01-01T23:59:59.123Z")
                .addNamedCase(
                        "ISO 8061 Zulu/UTC",
                        List.of("2001-08-01T18:45:59.123+00:00"),
                        "2001-08-01T18:45:59.123Z")
                .addNamedCase(
                        "ISO 8061 +2hr zone offset",
                        List.of("2001-08-01T18:45:59.123+02"),
                        "2001-08-01T16:45:59.123Z")
                .addNamedCase(
                        "ISO 8061 +2hr zone offset",
                        List.of("2001-08-01T18:45:59.123+02:00"),
                        "2001-08-01T16:45:59.123Z")
                .addNamedCase(
                        "ISO 8061 +2hr30min zone offset",
                        List.of("2001-08-01T18:45:59.123+02:30"),
                        "2001-08-01T16:15:59.123Z")
                .addNamedCase(
                        "ISO 8061 -3hr zone offset",
                        List.of("2001-08-01T18:45:59.123-03:00"),
                        "2001-08-01T21:45:59.123Z")
                .addNamedCase(
                        "Simple date UK style date",
                        List.of("29/08/24", "dd/MM/yy"),
                        "2024-08-29T00:00:00Z")
                .addNamedCase(
                        "Simple date US style date",
                        List.of("08/29/24", "MM/dd/yy"),
                        "2024-08-29T00:00:00Z")
                .addNamedCase(
                        "ISO date with no delimiters",
                        List.of("20010801184559", "yyyyMMddHHmmss"),
                        "2001-08-01T18:45:59Z")
                .addNamedCase(
                        "Standard output, no TZ",
                        List.of("2001/08/01 18:45:59", "yyyy/MM/dd HH:mm:ss"),
                        "2001-08-01T18:45:59Z")
                .addNamedCase(
                        "Standard output, date only, with TZ",
                        List.of("2001/08/01", "yyyy/MM/dd", "-07:00"),
                        "2001-08-01T07:00:00Z")
                .addNamedCase(
                        "Standard output, with TZ",
                        List.of("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "-08:00"),
                        "2001-08-01T09:00:00Z")
                .addNamedCase(
                        "Standard output, with TZ",
                        List.of("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "+01:00"),
                        "2001-08-01T00:00:00Z")
                .addNamedCase(
                        "Single digit day and month, no padding",
                        List.of("2001 8 1", "yyyy MM dd"),
                        "2001-08-01T00:00:00Z")
                .addNamedCase(
                        "Double digit day and month, no padding",
                        List.of("2001 12 28", "yyyy MM dd"),
                        "2001-12-28T00:00:00Z")
                .addNamedCase(
                        "Single digit day and month, with optional padding",
                        List.of("2001  8  1", "yyyy ppMM ppdd"),
                        "2001-08-01T00:00:00Z")
                .addNamedCase(
                        "Double digit day and month, with optional padding",
                        List.of("2001 12 31", "yyyy ppMM ppdd"),
                        "2001-12-31T00:00:00Z")
                .addNamedCase(
                        "With abbreviated day of week month",
                        List.of("Wed Aug 14 2024", "EEE MMM dd yyyy"),
                        "2024-08-14T00:00:00Z")
                .addNamedCase(
                        "With long form day of week and month",
                        List.of("Wednesday August 14 2024", "EEEE MMMM dd yyyy"),
                        "2024-08-14T00:00:00Z")
                .addNamedThrowsCase(
                        "With incorrect day of week",
                        List.of("Mon Aug 14 2024", "EEE MMM dd yyyy"), // Should be a Wed
                        NoSuchElementException.class)
                .addNamedCase(
                        "With 12 hour clock, AM",
                        List.of("Wed Aug 14 2024 10:32:58 AM", "E MMM dd yyyy hh:mm:ss a"),
                        "2024-08-14T10:32:58Z")
                .addNamedCase(
                        "With 12 hour clock, PM (lower case)",
                        List.of("Wed Aug 14 2024 10:32:58 pm", "E MMM dd yyyy hh:mm:ss a"),
                        "2024-08-14T22:32:58Z")
                .addNamedCase("Using minimal symbols",
                        List.of("2001 12 31 22:58:32.123", "y M d H:m:s.S"),
                        "2001-12-31T22:58:32.123Z")
                .addNamedCase("Optional time portion, with time",
                        List.of("2001/12/31 22:58:32.123", "yyyy/MM/dd[ HH:mm:ss.SSS]"),
                        "2001-12-31T22:58:32.123Z")
                .addNamedCase("Optional time portion, without time",
                        List.of("2001/12/31", "yyyy/MM/dd[ HH:mm:ss.SSS]"),
                        "2001-12-31T00:00:00Z")
                .addNamedCase("Optional millis portion, with millis",
                        List.of("2001/12/31 22:58:32.123", "yyyy/MM/dd HH:mm:ss[.SSS]"),
                        "2001-12-31T22:58:32.123Z")
                .addNamedCase("Optional millis portion, without millis",
                        List.of("2001/12/31 22:58:32", "yyyy/MM/dd HH:mm:ss[.SSS]"),
                        "2001-12-31T22:58:32Z")
                .addNamedCase("Optional millis/nanos portion, with nanos",
                        List.of("2001/12/31 22:58:32.123456", "yyyy/MM/dd HH:mm:ss[.SSS]"),
                        "2001-12-31T22:58:32.123456Z")
                .addNamedCase("Fixed text",
                        List.of("Date: 2001/12/31 Time: 22:58:32.123", "'Date: 'yyyy/MM/dd 'Time: 'HH:mm:ss.SSS"),
                        "2001-12-31T22:58:32.123Z")
                .addThrowsCase(List.of("2001 12 31", "yyy MMM ddd"), NoSuchElementException.class)
                .addThrowsCase(List.of("2001 12 31", "yyy MM ddd"), NoSuchElementException.class)
                .addThrowsCase(List.of("2001 Dec 31", "y M d"), NoSuchElementException.class)
                .addNamedCase("GMT/BST date that is BST",
                        List.of("2009/06/01 12:34:11", "yyyy/MM/dd HH:mm:ss", "GMT/BST"),
                        "2009-06-01T11:34:11Z")
                .addNamedCase("GMT/BST date that is GMT",
                        List.of("2009/02/01 12:34:11", "yyyy/MM/dd HH:mm:ss", "GMT/BST"),
                        "2009-02-01T12:34:11Z")
                .addNamedCase("Time zone offset",
                        List.of("2009/02/01 12:34:11", "yyyy/MM/dd HH:mm:ss", "+01:00"),
                        "2009-02-01T11:34:11Z")
                .addNamedCase("Named timezone",
                        List.of("2009/02/01 23:34:11", "yyyy/MM/dd HH:mm:ss", "US/Eastern"),
                        "2009-02-02T04:34:11Z")
                .build();
    }

    private static String writeFunctionCallStr(final TestOutcome<List<String>, String> outcome, final Path tempFile) {
        final String name = NullSafe.getOrElse(
                outcome.getName(),
                n -> "<!-- " + n + " -->\n",
                "");
        final String actualOutput = outcome.getActualOutput();
        final List<String> args = outcome.getInput();
        NullSafe.consume(argsToFuncCallStr(args), funcCallStr -> {
            final String line = LogUtil.message("""

                    ```xml
                    {}{}
                    -> '{}'
                    ```
                    """, name, funcCallStr, actualOutput);
//                    "`"
//                    + funcCallStr
//                    + "` | `" + actualOutput
//                    + "` | " + name
//                    + "\n";
            try {
                Files.writeString(tempFile, line, StandardOpenOption.APPEND);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        });
        return actualOutput;
    }

    @Test
    void testDayOfWeekAndWeekAndWeakYear() {
        setMetaCreateTime("2010-03-01T12:45:22.643Z");
        assertThat(callAsUTC("ccc/w/YYYY", "Mon/1/2018")).isEqualTo("2018-01-01T00:00:00Z");
        assertThat(callAsUTC("E/w/YYYY", "Mon/1/2018")).isEqualTo("2018-01-01T00:00:00Z");
    }

    @TestFactory
    Stream<DynamicTest> testDayOfWeekAndWeek() {
        // 2010-03-04 is a Thur
        setMetaCreateTime("2010-03-04T12:45:22.643Z");
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> callAsUTC(testCase.getInput()._1, testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("ccc/w", "Fri/2"), "2010-01-08T00:00:00Z")
                .addCase(Tuple.of("E/w", "Fri/2"), "2010-01-08T00:00:00Z")
                .addCase(Tuple.of("ccc/w", "Fri/40"), "2009-10-02T00:00:00Z")
                .addCase(Tuple.of("E/w", "Fri/40"), "2009-10-02T00:00:00Z")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testDayOfWeek() {
        // 2010-03-04 is a Thur
        setMetaCreateTime("2010-03-04T12:45:22.643Z");
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> callAsUTC(testCase.getInput()._1, testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("ccc", "Mon"), "2010-03-01T00:00:00Z")
                .addCase(Tuple.of("E", "Mon"), "2010-03-01T00:00:00Z")
                .addCase(Tuple.of("ccc", "Fri"), "2010-02-26T00:00:00Z")
                .addCase(Tuple.of("E", "Fri"), "2010-02-26T00:00:00Z")
                .addCase(Tuple.of("eee", "Fri"), "2010-02-26T00:00:00Z")
                .addCase(Tuple.of("eee YYYY", "Fri 2010"), "2010-03-05T00:00:00Z")
                .addCase(Tuple.of("eee dd MMM YYYY", "Fri 05 Mar 2010"), "2010-03-05T00:00:00Z")
                .addCase(Tuple.of("eee E ccc dd MMM YYYY", "Fri Fri Fri 05 Mar 2010"),
                        "2010-03-05T00:00:00Z")
                .build();
    }

    @Test
    void testParseManualTimeZones() {
        String date;

        date = call("2001/08/01", "yyyy/MM/dd", "-07:00");
        assertThat(date).isEqualTo("2001-08-01T07:00:00Z");

        date = call("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "-08:00");
        assertThat(date).isEqualTo("2001-08-01T09:00:00Z");

        date = call("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "+01:00");
        assertThat(date).isEqualTo("2001-08-01T00:00:00Z");
    }

    @Test
    void testParse() {
        String date;

        date = call("2001/01/01", "yyyy/MM/dd", null);
        assertThat(date).isEqualTo("2001-01-01T00:00:00Z");

        date = call("2001/08/01", "yyyy/MM/dd", "GMT");
        assertThat(date).isEqualTo("2001-08-01T00:00:00Z");

        date = call("2001/08/01 00:00:00.000", "yyyy/MM/dd HH:mm:ss.SSS", "GMT");
        assertThat(date).isEqualTo("2001-08-01T00:00:00Z");

        date = call("2001/08/01 00:00:00", "yyyy/MM/dd HH:mm:ss", "Europe/London");
        assertThat(date).isEqualTo("2001-07-31T23:00:00Z");

        date = call("2001/01/01", "yyyy/MM/dd", "GMT");
        assertThat(date).isEqualTo("2001-01-01T00:00:00Z");

        date = call("2008/08/08:00:00:00", "yyyy/MM/dd:HH:mm:ss", "Europe/London");
        assertThat(date).isEqualTo("2008-08-07T23:00:00Z");

        date = call("2008/08/08", "yyyy/MM/dd", "Europe/London");
        assertThat(date).isEqualTo("2008-08-07T23:00:00Z");
    }

    @Test
    void testParseGMTBSTGuess() {
        assertThatThrownBy(() -> {
            doGMTBSTGuessTest(null, "");
        }).isInstanceOf(RuntimeException.class);

        // Winter
        doGMTBSTGuessTest("2011-01-01T00:00:00.999Z", "2011/01/01 00:00:00.999");

        // MID Point Summer Time 1 Aug
        doGMTBSTGuessTest("2001-08-01T03:00:00Z", "2001/08/01 04:00:00.000");
        doGMTBSTGuessTest("2011-08-01T03:00:00Z", "2011/08/01 04:00:00.000");

        // Boundary WINTER TO SUMMER
        doGMTBSTGuessTest("2011-03-26T22:59:59.999Z", "2011/03/26 22:59:59.999");
        doGMTBSTGuessTest("2011-03-26T23:59:59.999Z", "2011/03/26 23:59:59.999");
        doGMTBSTGuessTest("2011-03-27T00:00:00Z", "2011/03/27 00:00:00.000");
        doGMTBSTGuessTest("2011-03-27T00:59:59Z", "2011/03/27 00:59:59.000");
        // Lost an hour!
        doGMTBSTGuessTest("2011-03-27T00:00:00Z", "2011/03/27 00:00:00.000");
        doGMTBSTGuessTest("2011-03-27T01:59:00.999Z", "2011/03/27 01:59:00.999");
        doGMTBSTGuessTest("2011-03-27T02:00:00.999Z", "2011/03/27 03:00:00.999");

        // Boundary SUMMER TO WINTER
        doGMTBSTGuessTest("2011-10-29T23:59:59.999Z", "2011/10/30 00:59:59.999");
    }

    private void doGMTBSTGuessTest(final String expected, final String value) {
        assertThat(call(value, "yyyy/MM/dd HH:mm:ss.SSS", "GMT/BST"))
                .isEqualTo(expected);
    }

    @Test
    void testDateWithNoYear() {
        setMetaCreateTime("2010-03-01T12:45:22.643Z");

        assertThat(callAsUTC("dd/MM", "01/01")).isEqualTo("2010-01-01T00:00:00Z");
        assertThat(callAsUTC("dd/MM", "01/04")).isEqualTo("2009-04-01T00:00:00Z");
        assertThat(callAsUTC("MM", "01")).isEqualTo("2010-01-01T00:00:00Z");
        assertThat(callAsUTC("MM", "04")).isEqualTo("2009-04-01T00:00:00Z");
        assertThat(callAsUTC("dd", "01")).isEqualTo("2010-03-01T00:00:00Z");
        assertThat(callAsUTC("dd", "04")).isEqualTo("2010-02-04T00:00:00Z");
        assertThat(callAsUTC("HH", "12")).isEqualTo("2010-03-01T12:00:00Z");
        assertThat(callAsUTC("HH:mm", "12:30")).isEqualTo("2010-03-01T12:30:00Z");
    }

    @Test
    void testCaseSensitivity_upperCaseMonth() {
        final ZonedDateTime time = callAsUTCToZonedDateTime("dd-MMM-yy", "18-APR-18");
        assertThat(time.getMonth()).isEqualTo(Month.APRIL);
    }

    @Test
    void testCaseSensitivity_sentenceCaseMonth() {
        final ZonedDateTime time = callAsUTCToZonedDateTime("dd-MMM-yy", "18-Apr-18");
        assertThat(time.getMonth()).isEqualTo(Month.APRIL);
    }

    @Test
    void testCaseSensitivity_lowerCaseMonth() {
        final ZonedDateTime time = callAsUTCToZonedDateTime("dd-MMM-yy", "18-apr-18");
        assertThat(time.getMonth()).isEqualTo(Month.APRIL);
    }

    @Test
    void testWithTimeZoneInStr1() {
        final ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss xxx",
                "18-04-18 01:01:01 +00:00");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr2() {
        final ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss Z", "18-04-18 01:01:01 +0000");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr3() {
        final ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss Z", "18-04-18 01:01:01 -0000");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr4() {
        final ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss xxx", "18-04-18 01:01:01 -00:00");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr5a() {
        // Apr is in BST so UTC is one hr less
        final ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss VV", "18-04-18 01:01:01 Europe/London");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T00:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr5b() {
        // Jan is in GMT so matches UTC
        final ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss VV", "18-01-18 01:01:01 Europe/London");
        assertThat(time.toString())
                .isEqualTo("2018-01-18T01:01:01Z");
    }

    @Disabled // Not a test
    @Test
    void dumpAllTimeZones() {
        final String str = Arrays.stream(TimeZone.getAvailableIDs())
                .map(zoneId1 -> {
                    try {
                        return ZoneId.of(zoneId1);
                    } catch (final Exception e) {
                        LOGGER.warn("No ZoneId for '{}'", zoneId1);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(zoneId -> "`" + zoneId.getId() + "` | " + zoneId.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .collect(Collectors.joining("\n"));

        LOGGER.info("ZoneIds:\n{}", str);
    }

    @Test
    void testTimeZones() {
        final String str = Stream.of(
                        "Zulu",
                        "UTC",
                        "+00:00",
                        "-00:00",
                        "+00",
                        "+0",
                        "+3",
                        "+03",
                        "+03:00")
                .map(ZoneId::of)
                .map(zoneId -> "`" + zoneId.getId() + "` | " + zoneId.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .collect(Collectors.joining("\n"));
        LOGGER.info("ZoneIds:\n{}", str);
    }

    @Test
    void testGetBaseTime() {
        final Instant startTime = Instant.now();
        final Instant baseTime = parseDateTime.getBaseTime();
        assertThat(baseTime)
                .isAfter(startTime);
        final Duration delta = Duration.between(startTime, baseTime);
        assertThat(delta)
                .isLessThan(Duration.ofMillis(250));
    }

    @Test
    void testGetBaseTime_nonNullHolder() {
        Mockito.when(mockMetaHolder.getMeta())
                .thenReturn(null);

        final Instant startTime = Instant.now();
        final Instant baseTime = parseDateTime.getBaseTime();
        assertThat(baseTime)
                .isAfter(startTime);
        final Duration delta = Duration.between(startTime, baseTime);
        assertThat(delta)
                .isLessThan(Duration.ofMillis(250));
    }

    @Test
    void testGetBaseTime_nonNullHolderAndMeta() {
        setMetaCreateTime();

        final Instant baseTime = parseDateTime.getBaseTime();
        assertThat(baseTime)
                .isEqualTo(META_CREATE_TIME);
    }

    private ZonedDateTime callAsUTCToZonedDateTime(final String pattern, final String dateStr) {
//        setMetaCreateTime("2010-03-01T12:45:22.643Z");
        final String outputDateStr = call(dateStr, pattern, "UTC");
        final long timeEpochMs = DateUtil.parseNormalDateTimeString(outputDateStr);
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeEpochMs), ZoneOffset.UTC);
    }

    private String callAsUTC(final String pattern, final String date) {
        return call(date, pattern, "UTC");
    }

    private String call(final String date, final String pattern, final String timeZone) {
        final Sequence sequence = callFunctionWithSimpleArgs(date, pattern, timeZone);
        return getAsDateTimeValue(sequence)
                .orElseThrow();
    }

    private void setMetaCreateTime() {
        setMetaCreateTime(META_CREATE_TIME.toEpochMilli());
    }

    private void setMetaCreateTime(final String referenceDate) {
        final long referenceDateEpochMs = DateUtil.parseNormalDateTimeString(referenceDate);
        setMetaCreateTime(referenceDateEpochMs);
    }

    private void setMetaCreateTime(final long referenceDateEpochMs) {
        Mockito.when(mockMeta.getCreateMs())
                .thenReturn(referenceDateEpochMs);
        Mockito.when(mockMetaHolder.getMeta())
                .thenReturn(mockMeta);
    }

    @Override
    ParseDateTime getXsltFunction() {
        return parseDateTime;
    }

    @Override
    String getFunctionName() {
        return ParseDateTime.FUNCTION_NAME;
    }

    private static String argsToFuncCallStr(final List<String> args) {
        if (NullSafe.hasItems(args)) {
            return "stroom:parse-dateTime(" + args.stream()
                    .map(arg ->
                            arg == null
                                    ? "null"
                                    : "'" + arg + "'")
                    .collect(Collectors.joining(", "))
                   + ")";
        } else {
            return null;
        }
    }
}
