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

package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

public class TestStringIdUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStringIdUtil.class);

    @TestFactory
    Stream<DynamicTest> testIdToString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(long.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(StringIdUtil::idToString)
                .withSimpleEqualityAssertion()
                .addThrowsCase(-1L, IllegalArgumentException.class)
                .addCase(0L, "000")
                .addCase(1L, "001")
                .addCase(12L, "012")
                .addCase(123L, "123")
                .addCase(1_234L, "001234")
                .addCase(12_345L, "012345")
                .addCase(123_456L, "123456")
                .addCase(1_234_567L, "001234567")
                .addCase(12_345_678L, "012345678")
                .addCase(123_456_789L, "123456789")
                .addCase(999L, "999")
                .addCase(1_000L, "001000")
                .addCase(999_999L, "999999")
                .addCase(1_000_000L, "001000000")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsValidIdString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(StringIdUtil::isValidIdString)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", false)
                .addCase("foo", false)
                .addCase("1", false)
                .addCase("12", false)
                .addCase("123", true)
                .addCase("1234", false)
                .addCase("12345", false)
                .addCase("123456", true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetDigitCountAsId() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(long.class)
                .withOutputType(int.class)
                .withSingleArgTestFunction(StringIdUtil::getDigitCountAsId)
                .withSimpleEqualityAssertion()
                .addCase(1L, 3)
                .addCase(9L, 3)
                .addCase(10L, 3)
                .addCase(99L, 3)
                .addCase(100L, 3)
                .addCase(999L, 3)
                .addCase(1000L, 6)
                .addCase(9999L, 6)
                .addCase(10000L, 6)
                .addCase(99999L, 6)
                .addCase(100000L, 6)
                .addCase(999999L, 6)
                .build();
    }
}
