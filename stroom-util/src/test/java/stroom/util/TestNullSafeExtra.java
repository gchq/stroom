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

package stroom.util;

import stroom.test.common.TestUtil;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestNullSafeExtra {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestNullSafeExtra.class);

    @TestFactory
    Stream<DynamicTest> testStroomDuration() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(StroomDuration.class)
                .withTestFunction(testCase ->
                        NullSafeExtra.duration(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, StroomDuration.ZERO)
                .addCase(StroomDuration.ZERO, StroomDuration.ZERO)
                .addCase(StroomDuration.ofSeconds(5), StroomDuration.ofSeconds(5))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testByteSize() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(ByteSize.class)
                .withTestFunction(testCase ->
                        NullSafeExtra.byteSize(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, ByteSize.ZERO)
                .addCase(ByteSize.ZERO, ByteSize.ZERO)
                .addCase(ByteSize.ofMebibytes(5), ByteSize.ofMebibytes(5))
                .build();
    }
}
