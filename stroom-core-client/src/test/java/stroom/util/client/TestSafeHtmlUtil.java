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

package stroom.util.client;

import stroom.test.common.TestCase;
import stroom.test.common.TestUtil;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

class TestSafeHtmlUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSafeHtmlUtil.class);

    @TestFactory
    Stream<DynamicTest> testToParagraphs() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        SafeHtmlUtil.toParagraphs(testCase.getInput()).asString())
                .withAssertions(testOutcome -> {
                    LOGGER.info("input [{}], output [{}], expectedOutput [{}]",
                            (testOutcome.getInput() != null
                                    ? testOutcome.getInput().replace("\n", "↵")
                                    : null),
                            testOutcome.getActualOutput(),
                            testOutcome.getExpectedOutput());

                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());

                    if (testOutcome.getExpectedOutput() != null) {
                        Assertions.assertThat(testOutcome.getActualOutput())
                                .doesNotContain("\n");
                    }
                })
                .addCase("hello\nworld", "<p>hello</p><p>world</p>")
                .addCase("\nhello\nworld\n", "<p>hello</p><p>world</p>")
                .addCase("hello\n", "<p>hello</p>")
                .addCase("\nhello", "<p>hello</p>")
                .addCase("", "")
                .addCase(null, "")
                .addCase("hello\nworld\ngoodbye", "<p>hello</p><p>world</p><p>goodbye</p>")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWithLineBreaks() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        SafeHtmlUtil.withLineBreaks(testCase.getInput()).asString())
                .withAssertions(testOutcome -> {
                    LOGGER.info("input [{}], output [{}], expectedOutput [{}]",
                            (testOutcome.getInput() != null
                                    ? testOutcome.getInput().replace("\n", "↵")
                                    : null),
                            testOutcome.getActualOutput(),
                            testOutcome.getExpectedOutput());

                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());

                    if (testOutcome.getExpectedOutput() != null) {
                        Assertions.assertThat(testOutcome.getActualOutput())
                                .doesNotContain("\n");
                    }
                })
                .addCase("hello\nworld", "hello<br>world")
                .addCase("hello\n\nworld", "hello<br><br>world")
                .addCase("hello\n\n\nworld", "hello<br><br><br>world")
                .addCase("\nhello\nworld\n", "hello<br>world")
                .addCase("hello\n", "hello")
                .addCase("hello\n\n", "hello")
                .addCase("\n\nhello", "hello")
                .addCase("", "")
                .addCase(null, "")
                .addCase("hello\nworld\ngoodbye", "hello<br>world<br>goodbye")
                .build();
    }

    private void doParaTest(final TestCase<String, String> testCase) {
        final String input = testCase.getInput();
        final String expectedOutput = testCase.getExpectedOutput();
        final SafeHtml output = SafeHtmlUtil.toParagraphs(input);

    }

    private void doLineBreakTest(final TestCase<String, String> testCase) {
        final String input = testCase.getInput();
        final String expectedOutput = testCase.getExpectedOutput();
        final SafeHtml output = SafeHtmlUtil.withLineBreaks(input);

    }
}
