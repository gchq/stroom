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

package stroom.query.common.v2.format;

import stroom.query.api.NumberFormatSettings;
import stroom.query.language.functions.Val;
import stroom.test.common.TestCase;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

class TestNumberFormatter {

    private static final Instant INSTANT = Instant.parse("2015-02-03T01:22:33.056Z");
    private static final long INSTANT_MS = INSTANT.toEpochMilli();

    @TestFactory
    Stream<DynamicTest> testFormat_nullSettings() {
        final NumberFormatSettings formatSettings = null;
        final NumberFormatter numberFormatter = NumberFormatter.create(formatSettings);

        return TestUtil.buildDynamicTestStream()
                .withInputType(Val.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(numberFormatter::format)
                .withSimpleEqualityAssertion()
                .withNameFunction(TestNumberFormatter::getTestName)
                .addCase(null, null)
                .addCase(Val.create(""), "")
                .addCase(Val.create("123456789"), "123456789")
                .addCase(Val.create("1.23456789"), "1.23456789")
                .addCase(Val.create(123456789), "123456789")
                .addCase(Val.create(123456789L), "123456789")
                .addCase(Val.create(123456789D), "123456789")
                .addCase(Val.create(1.23456789D), "1.23456789")
                .addCase(Val.create(1.23456789F), "1.2345678806304932")
                .addCase(Val.create(Duration.ofSeconds(5)), "5000")
                .addCase(Val.create(INSTANT), "" + INSTANT_MS)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormat_defaultSettings() {
        // Default is no 000s separator and 0 decimal places
        final NumberFormatSettings formatSettings = NumberFormatSettings.builder().build();
        final NumberFormatter numberFormatter = NumberFormatter.create(formatSettings);

        return TestUtil.buildDynamicTestStream()
                .withInputType(Val.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(numberFormatter::format)
                .withSimpleEqualityAssertion()
                .withNameFunction(TestNumberFormatter::getTestName)
                .addCase(null, null)
                .addCase(Val.create(""), "")
                .addCase(Val.create("123456789"), "123456789")
                .addCase(Val.create("12.3456789"), "12")
                .addCase(Val.create(123456789), "123456789")
                .addCase(Val.create(123456789L), "123456789")
                .addCase(Val.create(123456789D), "123456789")
                .addCase(Val.create(12.3456789D), "12")
                .addCase(Val.create(12.3456789F), "12")
                .addCase(Val.create(Duration.ofSeconds(5)), "5000")
                .addCase(Val.create(INSTANT), "" + INSTANT_MS)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFormat_customSettings() {
        final NumberFormatSettings formatSettings = NumberFormatSettings.builder()
                .useSeparator(true)
                .decimalPlaces(2)
                .build();
        final NumberFormatter numberFormatter = NumberFormatter.create(formatSettings);

        return TestUtil.buildDynamicTestStream()
                .withInputType(Val.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(numberFormatter::format)
                .withSimpleEqualityAssertion()
                .withNameFunction(TestNumberFormatter::getTestName)
                .addCase(null, null)
                .addCase(Val.create(""), "")
                .addCase(Val.create("123456789"), "123,456,789.00")
                .addCase(Val.create("12.3456789"), "12.35")
                .addCase(Val.create(123456789), "123,456,789.00")
                .addCase(Val.create(123456789L), "123,456,789.00")
                .addCase(Val.create(123456789D), "123,456,789.00")
                .addCase(Val.create(12.3456789D), "12.35")
                .addCase(Val.create(12.3456789F), "12.35")
                .addCase(Val.create(Duration.ofSeconds(5)), "5,000.00")
                .addCase(Val.create(INSTANT), "1,422,926,553,056.00")
                .build();
    }

    private static String getTestName(final TestCase<?, ?> testCase) {
        final Object input = testCase.getInput();
        if (input == null) {
            return "null";
        } else {
            return input.getClass().getSimpleName() + "(" + input + ")";
        }
    }

}
