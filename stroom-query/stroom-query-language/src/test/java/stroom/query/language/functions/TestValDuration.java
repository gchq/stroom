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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.stream.Stream;

class TestValDuration {

    @Test
    void testHasNumericValue() {
        Assertions.assertThat(ValDuration.create(1000L).hasNumericValue())
                .isTrue();
    }

    @Test
    void testHasFractionalPart() {
        Assertions.assertThat(ValDuration.create(1000L).hasFractionalPart())
                .isFalse();
    }

    @TestFactory
    Stream<DynamicTest> testInteger() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDuration.class)
                .withOutputType(Integer.class)
                .withSingleArgTestFunction(ValDuration::toInteger)
                .withSimpleEqualityAssertion()
                .addCase(ValDuration.create(0L), 0)
                .addCase(ValDuration.create(1L), 1)
                .addCase(ValDuration.create(Integer.MAX_VALUE), Integer.MAX_VALUE)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLong() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDuration.class)
                .withOutputType(Long.class)
                .withSingleArgTestFunction(ValDuration::toLong)
                .withSimpleEqualityAssertion()
                .addCase(ValDuration.create(0L), 0L)
                .addCase(ValDuration.create(1L), 1L)
                .addCase(ValDuration.create(Long.MAX_VALUE), Long.MAX_VALUE)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFloat() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDuration.class)
                .withOutputType(Float.class)
                .withSingleArgTestFunction(ValDuration::toFloat)
                .withSimpleEqualityAssertion()
                .addCase(ValDuration.create(0L), 0f)
                .addCase(ValDuration.create(1L), 1f)
                .addCase(ValDuration.create(Long.MAX_VALUE), (float) Long.MAX_VALUE)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBoolean() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDuration.class)
                .withOutputType(Boolean.class)
                .withSingleArgTestFunction(ValDuration::toBoolean)
                .withSimpleEqualityAssertion()
                .addCase(ValDuration.create(0L), false)
                .addCase(ValDuration.create(1L), true)
                .addCase(ValDuration.create(Long.MAX_VALUE), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDuration.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(ValDuration::toString)
                .withSimpleEqualityAssertion()
                .addCase(ValDuration.create(0L), "0ms")
                .addCase(ValDuration.create(1L), "1ms")
                .addCase(ValDuration.create(1_000L), "1s")
                .addCase(ValDuration.create(60_000L), "1m")
                .addCase(ValDuration.create(Duration.ofDays(1)), "1d")
                .build();
    }
}
