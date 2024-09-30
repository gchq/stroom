/*
 * Copyright 2024 Crown Copyright
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
import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFormatDate extends AbstractXsltFunctionTest<FormatDate> {

    private static final Instant META_CREATE_TIME = LocalDateTime.of(
                    2010, 3, 1, 12, 45, 22, 643 * 1_000_000)
            .toInstant(ZoneOffset.UTC);

    @Mock
    private Meta mockMeta;
    @SuppressWarnings("unused") // Used by @InjectMocks
    @Mock
    private MetaHolder mockMetaHolder;
    @InjectMocks
    private FormatDate formatDate;

    @Test
    void testCall_millis() {
        final Instant now = Instant.now();
        final Sequence sequence = callFunctionWithSimpleArgs(now.toEpochMilli());

        final Optional<String> optVal = getAsStringValue(sequence);

        Assertions.assertThat(optVal)
                .hasValue(DateUtil.createNormalDateTimeString(now.toEpochMilli()));
    }

    @TestFactory
    Stream<DynamicTest> testCall_string() {

//        setMetaCreateTime();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final Object[] args = testCase.getInput().toArray(new Object[0]);
                    final Sequence sequence = callFunctionWithSimpleArgs(args);
                    final Optional<String> optVal = getAsStringValue(sequence);
                    return optVal.orElseThrow();
                })
                .withSimpleEqualityAssertion()
                .addNamedCase(
                        "Standard out fmt with TZ",
                        List.of("2001/08/01", "yyyy/MM/dd", "-07:00"),
                        "2001-08-01T07:00:00.000Z")
                .addNamedCase(
                        "Standard out fmt with TZ",
                        List.of("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "-08:00"),
                        "2001-08-01T09:00:00.000Z")
                .addNamedCase(
                        "Standard out fmt with TZ",
                        List.of("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "+01:00"),
                        "2001-08-01T00:00:00.000Z")
                .addNamedCase(
                        "Standard out fmt no TZ",
                        List.of("2001/08/01 18:45:59", "yyyy/MM/dd HH:mm:ss"),
                        "2001-08-01T18:45:59.000Z")
                .addNamedCase(
                        "Specific out fmt no TZ",
                        List.of("2001/08/01 14:30:59",
                                "yyyy/MM/dd HH:mm:ss",
                                "+01:00",
                                "E dd MMM yyyy HH:mm (s 'secs')"),
                        "Wed 01 Aug 2001 13:30 (59 secs)")
                .addNamedCase(
                        "Specific out fmt with TZ",
                        List.of("2001/08/01 14:30:59",
                                "yyyy/MM/dd HH:mm:ss",
                                "+01:00",
                                "E dd MMM yyyy HH:mm",
                                "+02:00"),
                        "Wed 01 Aug 2001 15:30")
                .addNamedCase(
                        "Single digit day and month",
                        List.of("2001 8 1", "yyyy M d"),
                        "2001-08-01T00:00:00.000Z")
                .addNamedCase(
                        "Single digit day and month, plus padding",
                        List.of("2001  8  1", "yyyy ppM ppd"),
                        "2001-08-01T00:00:00.000Z")
                .addNamedCase(
                        "Single digit day and month, plus padding",
                        List.of("2001 12 31", "yyyy ppM ppd"),
                        "2001-12-31T00:00:00.000Z")
                .addNamedCase(
                        "With day of week",
                        List.of("Wed Aug 14 2024", "E MMM dd yyyy"),
                        "2024-08-14T00:00:00.000Z")
                .addNamedThrowsCase(
                        "With incorrect day of week",
                        List.of("Mon Aug 14 2024", "EEE MMM dd yyyy"), // Should be a Wed
                        NoSuchElementException.class)
                .addCase(List.of("2001 12 31", "y M d"), "2001-12-31T00:00:00.000Z")
                .addCase(List.of("2001 12 31", "yy MM dd"), "2001-12-31T00:00:00.000Z")
                .addThrowsCase(List.of("2001 12 31", "yyy MMM ddd"), NoSuchElementException.class)
                .addThrowsCase(List.of("2001 12 31", "yyy MM ddd"), NoSuchElementException.class)
                .addCase(List.of("2001 12 31", "yyy MMM dd"), "2001-12-31T00:00:00.000Z")
                .addCase(List.of("2001 12 31", "yyy MM dd"), "2001-12-31T00:00:00.000Z")
                .addCase(List.of("2001 12 31", "yyyy MM dd"), "2001-12-31T00:00:00.000Z")
                .addThrowsCase(List.of("2001 Dec 31", "y M d"), NoSuchElementException.class)
                .addCase(List.of("2001 Dec 31", "y MMM d"), "2001-12-31T00:00:00.000Z")
                .addCase(List.of("2001 12 31 22:58:32.123", "y M d H:m:s.S"),
                        "2001-12-31T22:58:32.123Z")
                .addCase(List.of("2001 12 31 22:58:32.123", "y M d HH:mm:ss.SSS"),
                        "2001-12-31T22:58:32.123Z")
                .addCase(List.of("2001 12 31 9:3:5.12 pm", "y M d h:m:s.S a"),
                        "2001-12-31T21:03:05.120Z")
                .build();
    }

    @Test
    void testDayOfWeekAndWeekAndWeakYear() {
        setMetaCreateTime("2010-03-01T12:45:22.643Z");
        assertThat(callAsUTC("ccc/w/YYYY", "Mon/1/2018")).isEqualTo("2018-01-01T00:00:00.000Z");
        assertThat(callAsUTC("E/w/YYYY", "Mon/1/2018")).isEqualTo("2018-01-01T00:00:00.000Z");
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
                .addCase(Tuple.of("ccc/w", "Fri/2"), "2010-01-08T00:00:00.000Z")
                .addCase(Tuple.of("E/w", "Fri/2"), "2010-01-08T00:00:00.000Z")
                .addCase(Tuple.of("ccc/w", "Fri/40"), "2009-10-02T00:00:00.000Z")
                .addCase(Tuple.of("E/w", "Fri/40"), "2009-10-02T00:00:00.000Z")
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
                .addCase(Tuple.of("ccc", "Mon"), "2010-03-01T00:00:00.000Z")
                .addCase(Tuple.of("E", "Mon"), "2010-03-01T00:00:00.000Z")
                .addCase(Tuple.of("ccc", "Fri"), "2010-02-26T00:00:00.000Z")
                .addCase(Tuple.of("E", "Fri"), "2010-02-26T00:00:00.000Z")
                .addCase(Tuple.of("eee", "Fri"), "2010-02-26T00:00:00.000Z")
                .addCase(Tuple.of("eee YYYY", "Fri 2010"), "2010-03-05T00:00:00.000Z")
                .addCase(Tuple.of("eee dd MMM YYYY", "Fri 05 Mar 2010"), "2010-03-05T00:00:00.000Z")
                .addCase(Tuple.of("eee E ccc dd MMM YYYY", "Fri Fri Fri 05 Mar 2010"),
                        "2010-03-05T00:00:00.000Z")
                .build();
    }

    @Test
    void testParseManualTimeZones() {
        String date;

        date = call("2001/08/01", "yyyy/MM/dd", "-07:00");
        assertThat(date).isEqualTo("2001-08-01T07:00:00.000Z");

        date = call("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "-08:00");
        assertThat(date).isEqualTo("2001-08-01T09:00:00.000Z");

        date = call("2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "+01:00");
        assertThat(date).isEqualTo("2001-08-01T00:00:00.000Z");
    }

    @Test
    void testParse() {
        String date;

        date = call("2001/01/01", "yyyy/MM/dd", null);
        assertThat(date).isEqualTo("2001-01-01T00:00:00.000Z");

        date = call("2001/08/01", "yyyy/MM/dd", "GMT");
        assertThat(date).isEqualTo("2001-08-01T00:00:00.000Z");

        date = call("2001/08/01 00:00:00.000", "yyyy/MM/dd HH:mm:ss.SSS", "GMT");
        assertThat(date).isEqualTo("2001-08-01T00:00:00.000Z");

        date = call("2001/08/01 00:00:00", "yyyy/MM/dd HH:mm:ss", "Europe/London");
        assertThat(date).isEqualTo("2001-07-31T23:00:00.000Z");

        date = call("2001/01/01", "yyyy/MM/dd", "GMT");
        assertThat(date).isEqualTo("2001-01-01T00:00:00.000Z");

        date = call("2008/08/08:00:00:00", "yyyy/MM/dd:HH:mm:ss", "Europe/London");
        assertThat(date).isEqualTo("2008-08-07T23:00:00.000Z");

        date = call("2008/08/08", "yyyy/MM/dd", "Europe/London");
        assertThat(date).isEqualTo("2008-08-07T23:00:00.000Z");
    }

    @Test
    void testParseGMTBSTGuess() {
        assertThatThrownBy(() -> {
            doGMTBSTGuessTest(null, "");
        }).isInstanceOf(RuntimeException.class);

        // Winter
        doGMTBSTGuessTest("2011-01-01T00:00:00.999Z", "2011/01/01 00:00:00.999");

        // MID Point Summer Time 1 Aug
        doGMTBSTGuessTest("2001-08-01T03:00:00.000Z", "2001/08/01 04:00:00.000");
        doGMTBSTGuessTest("2011-08-01T03:00:00.000Z", "2011/08/01 04:00:00.000");

        // Boundary WINTER TO SUMMER
        doGMTBSTGuessTest("2011-03-26T22:59:59.999Z", "2011/03/26 22:59:59.999");
        doGMTBSTGuessTest("2011-03-26T23:59:59.999Z", "2011/03/26 23:59:59.999");
        doGMTBSTGuessTest("2011-03-27T00:00:00.000Z", "2011/03/27 00:00:00.000");
        doGMTBSTGuessTest("2011-03-27T00:59:59.000Z", "2011/03/27 00:59:59.000");
        // Lost an hour!
        doGMTBSTGuessTest("2011-03-27T00:00:00.000Z", "2011/03/27 00:00:00.000");
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

        assertThat(callAsUTC("dd/MM", "01/01")).isEqualTo("2010-01-01T00:00:00.000Z");
        assertThat(callAsUTC("dd/MM", "01/04")).isEqualTo("2009-04-01T00:00:00.000Z");
        assertThat(callAsUTC("MM", "01")).isEqualTo("2010-01-01T00:00:00.000Z");
        assertThat(callAsUTC("MM", "04")).isEqualTo("2009-04-01T00:00:00.000Z");
        assertThat(callAsUTC("dd", "01")).isEqualTo("2010-03-01T00:00:00.000Z");
        assertThat(callAsUTC("dd", "04")).isEqualTo("2010-02-04T00:00:00.000Z");
        assertThat(callAsUTC("HH", "12")).isEqualTo("2010-03-01T12:00:00.000Z");
        assertThat(callAsUTC("HH:mm", "12:30")).isEqualTo("2010-03-01T12:30:00.000Z");
    }

    @Test
    void testCaseSensitivity_upperCaseMonth() {
        ZonedDateTime time = callAsUTCToZonedDateTime("dd-MMM-yy", "18-APR-18");
        assertThat(time.getMonth()).isEqualTo(Month.APRIL);
    }

    @Test
    void testCaseSensitivity_sentenceCaseMonth() {
        ZonedDateTime time = callAsUTCToZonedDateTime("dd-MMM-yy", "18-Apr-18");
        assertThat(time.getMonth()).isEqualTo(Month.APRIL);
    }

    @Test
    void testCaseSensitivity_lowerCaseMonth() {
        ZonedDateTime time = callAsUTCToZonedDateTime("dd-MMM-yy", "18-apr-18");
        assertThat(time.getMonth()).isEqualTo(Month.APRIL);
    }

    @Test
    void testWithTimeZoneInStr1() {
        ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss xxx",
                "18-04-18 01:01:01 +00:00");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr2() {
        ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss Z", "18-04-18 01:01:01 +0000");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr3() {
        ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss Z", "18-04-18 01:01:01 -0000");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr4() {
        ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss xxx", "18-04-18 01:01:01 -00:00");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T01:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr5a() {
        // Apr is in BST so UTC is one hr less
        ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss VV", "18-04-18 01:01:01 Europe/London");
        assertThat(time.toString())
                .isEqualTo("2018-04-18T00:01:01Z");
    }

    @Test
    void testWithTimeZoneInStr5b() {
        // Jan is in GMT so matches UTC
        ZonedDateTime time = callAsUTCToZonedDateTime(
                "dd-MM-yy HH:mm:ss VV", "18-01-18 01:01:01 Europe/London");
        assertThat(time.toString())
                .isEqualTo("2018-01-18T01:01:01Z");
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
        return getAsStringValue(sequence)
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
    FormatDate getXsltFunction() {
        return formatDate;
    }

    @Override
    String getFunctionName() {
        return FormatDate.FUNCTION_NAME;
    }
}
