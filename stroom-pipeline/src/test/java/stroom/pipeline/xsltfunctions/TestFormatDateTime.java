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

import stroom.pipeline.state.MetaHolder;
import stroom.test.common.TestOutcome;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.TypeLiteral;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.DateTimeValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestFormatDateTime extends AbstractXsltFunctionTest<FormatDateTime> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFormatDateTime.class);

    @SuppressWarnings("unused") // Used by @InjectMocks
    @Mock
    private MetaHolder mockMetaHolder;
    @InjectMocks
    private FormatDateTime formatDateTime;

    @TestFactory
    Stream<DynamicTest> testFormatExamples() throws IOException {
        // Dump each test case to a file, so we can add it to the stroom-docs easily
        // content/en/docs/user-guide/pipelines/xslt/xslt-functions.md#format-dateTime
        final Path tempFile = Files.createTempFile("format-dateTime-", ".md");
//        Files.writeString(tempFile, """
//                Function Call | Output | Notes
//                ------------- | ------ | -----
//                """, StandardOpenOption.APPEND);

        LOGGER.info("Outputting examples to {}", tempFile.toAbsolutePath());

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<Object>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final List<Object> strArgs = testCase.getInput();
                    final Object[] args = strArgs.toArray(new Object[0]);
                    final Sequence sequence = callFunctionWithSimpleArgs(args);
                    final Optional<String> optVal = getAsStringValue(sequence);
                    return optVal.orElseThrow();
                })
                .withAssertions(outcome -> {
                    final String actualOutput = writeFunctionCallStr(outcome, tempFile);
                    assertThat(actualOutput)
                            .isEqualTo(outcome.getExpectedOutput());
                })
                .addNamedCase(
                        "Default format zero millis",
                        List.of(DateTimeValue.parse("2024-08-29T00:00:00Z")),
                        "2024-08-29T00:00:00.000Z")
                .addNamedCase(
                        "Default format nanos",
                        List.of(DateTimeValue.parse("2010-01-01T23:59:59.123456Z")),
                        "2010-01-01T23:59:59.123Z")
                .addNamedCase(
                        "Default format millis",
                        List.of(DateTimeValue.parse("2010-01-01T23:59:59.123Z")),
                        "2010-01-01T23:59:59.123Z")
                .addNamedCase(
                        "Default format Zulu/UTC",
                        List.of(DateTimeValue.parse("2001-08-01T18:45:59.123+00:00")),
                        "2001-08-01T18:45:59.123Z")
                .addNamedCase(
                        "Default format +2hr zone offset",
                        List.of(DateTimeValue.parse("2001-08-01T18:45:59.123+02:00")),
                        "2001-08-01T16:45:59.123Z")
                .addNamedCase(
                        "Default format +2hr30min zone offset",
                        List.of(DateTimeValue.parse("2001-08-01T18:45:59.123+02:30")),
                        "2001-08-01T16:15:59.123Z")
                .addNamedCase(
                        "Default format -3hr zone offset",
                        List.of(DateTimeValue.parse("2001-08-01T18:45:59.123-03:00")),
                        "2001-08-01T21:45:59.123Z")
                .addNamedCase(
                        "Simple date format UK style date",
                        List.of(DateTimeValue.parse("2024-08-29T00:00:00Z"), "dd/MM/yy"),
                        "29/08/24")
                .addNamedCase(
                        "Simple date format US style date",
                        List.of(DateTimeValue.parse("2024-08-29T00:00:00Z"), "MM/dd/yy"),
                        "08/29/24")
                .addNamedCase(
                        "With no delimiters",
                        List.of(DateTimeValue.parse("2001-08-01T18:45:59Z"), "yyyyMMddHHmmss"),
                        "20010801184559")
                .addNamedCase(
                        "Standard output, no TZ",
                        List.of(DateTimeValue.parse("2001-08-01T18:45:59Z"), "yyyy/MM/dd HH:mm:ss"),
                        "2001/08/01 18:45:59")
                .addNamedCase(
                        "Format with nanos",
                        List.of(DateTimeValue.parse("2010-01-01T23:59:59.123456Z"), "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXX"),
                        "2010-01-01T23:59:59.123456Z")
                .addNamedCase(
                        "Standard output, date only, with TZ",
                        List.of(DateTimeValue.parse("2001-08-01T07:00:00Z"), "yyyy/MM/dd", "-07:00"),
                        "2001/08/01")
                .addNamedCase(
                        "Standard output, with TZ",
                        List.of(DateTimeValue.parse("2001-08-01T09:00:00Z"), "yyyy/MM/dd HH:mm:ss", "-08:00"),
                        "2001/08/01 01:00:00")
                .addNamedCase(
                        "Standard output, with TZ",
                        List.of(DateTimeValue.parse("2001-08-01T00:00:00Z"), "yyyy/MM/dd HH:mm:ss", "+01:00"),
                        "2001/08/01 01:00:00")
                .addNamedCase(
                        "Single digit day and month, no padding",
                        List.of(DateTimeValue.parse("2001-08-01T00:00:00Z"), "yyyy M d"),
                        "2001 8 1")
                .addNamedCase(
                        "Double digit day and month, no padding",
                        List.of(DateTimeValue.parse("2001-12-28T00:00:00Z"), "yyyy MM dd"),
                        "2001 12 28")
                .addNamedCase(
                        "Single digit day and month, with optional padding",
                        List.of(DateTimeValue.parse("2001-08-01T00:00:00Z"), "yyyy ppM ppd"),
                        "2001  8  1")
                .addNamedCase(
                        "Double digit day and month, with optional padding",
                        List.of(DateTimeValue.parse("2001-12-31T00:00:00Z"), "yyyy ppMM ppdd"),
                        "2001 12 31")
                .addNamedCase(
                        "With abbreviated day of week month",
                        List.of(DateTimeValue.parse("2024-08-14T00:00:00Z"), "EEE MMM dd yyyy"),
                        "Wed Aug 14 2024")
                .addNamedCase(
                        "With long form day of week and month",
                        List.of(DateTimeValue.parse("2024-08-14T00:00:00Z"), "EEEE MMMM dd yyyy"),
                        "Wednesday August 14 2024")
                .addNamedCase(
                        "With 12 hour clock, AM",
                        List.of(DateTimeValue.parse("2024-08-14T10:32:58Z"), "E MMM dd yyyy hh:mm:ss a"),
                        "Wed Aug 14 2024 10:32:58 AM")
                .addNamedCase(
                        "With 12 hour clock, PM",
                        List.of(DateTimeValue.parse("2024-08-14T22:32:58Z"), "E MMM dd yyyy hh:mm:ss a"),
                        "Wed Aug 14 2024 10:32:58 PM")
                .addNamedCase("Using minimal symbols",
                        List.of(DateTimeValue.parse("2001-12-31T22:58:32.123Z"), "y M d H:m:s.S"),
                        "2001 12 31 22:58:32.1")
                .addNamedCase("Optional time portion, with time",
                        List.of(DateTimeValue.parse("2001-12-31T22:58:32.123Z"), "yyyy/MM/dd[ HH:mm:ss.SSS]"),
                        "2001/12/31 22:58:32.123")
                .addNamedCase("Optional millis portion, with millis",
                        List.of(DateTimeValue.parse("2001-12-31T22:58:32.123Z"), "yyyy/MM/dd HH:mm:ss[.SSS]"),
                        "2001/12/31 22:58:32.123")
                .addNamedCase("Optional millis portion, without millis",
                        List.of(DateTimeValue.parse("2001-12-31T22:58:32Z"), "yyyy/MM/dd HH:mm:ss[.SSS]"),
                        "2001/12/31 22:58:32.000")
                .addNamedCase("Optional millis/nanos portion, with nanos",
                        List.of(DateTimeValue.parse("2001-12-31T22:58:32.123456Z"), "yyyy/MM/dd HH:mm:ss[.SSSSSS]"),
                        "2001/12/31 22:58:32.123456")
                .addNamedCase("Fixed text",
                        List.of(DateTimeValue.parse("2001-12-31T22:58:32.123Z"),
                                "'Date: 'yyyy/MM/dd 'Time: 'HH:mm:ss.SSS"),
                        "Date: 2001/12/31 Time: 22:58:32.123")
                .addNamedCase("GMT/BST date that is BST",
                        List.of(DateTimeValue.parse("2009-06-01T11:34:11Z"), "yyyy/MM/dd HH:mm:ss", "GMT/BST"),
                        "2009/06/01 12:34:11")
                .addNamedCase("GMT/BST date that is GMT",
                        List.of(DateTimeValue.parse("2009-02-01T12:34:11Z"), "yyyy/MM/dd HH:mm:ss", "GMT/BST"),
                        "2009/02/01 12:34:11")
                .addNamedCase("Time zone offset",
                        List.of(DateTimeValue.parse("2009-02-01T11:34:11Z"), "yyyy/MM/dd HH:mm:ss", "+01:00"),
                        "2009/02/01 12:34:11")
                .addNamedCase("Named timezone",
                        List.of(DateTimeValue.parse("2009-02-02T04:34:11Z"), "yyyy/MM/dd HH:mm:ss", "US/Eastern"),
                        "2009/02/01 23:34:11")
                .build();
    }

    private static String writeFunctionCallStr(final TestOutcome<List<Object>, String> outcome, final Path tempFile) {
        final String name = NullSafe.getOrElse(
                outcome.getName(),
                n -> "<!-- " + n + " -->\n",
                "");
        final String actualOutput = outcome.getActualOutput();
        final List<Object> args = outcome.getInput();
        NullSafe.consume(argsToFuncCallStr(args), funcCallStr -> {
            final String line = LogUtil.message("""

                    ```xml
                    {}{}
                    -> '{}'
                    ```
                    """, name, funcCallStr, actualOutput);
            try {
                Files.writeString(tempFile, line, StandardOpenOption.APPEND);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        });
        return actualOutput;
    }

    @Override
    FormatDateTime getXsltFunction() {
        return formatDateTime;
    }

    @Override
    String getFunctionName() {
        return FormatDateTime.FUNCTION_NAME;
    }

    private static String argsToFuncCallStr(final List<Object> args) {
        if (NullSafe.hasItems(args)) {
            return "stroom:format-dateTime(" + args.stream()
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
