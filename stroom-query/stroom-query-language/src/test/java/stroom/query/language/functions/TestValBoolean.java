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

import java.util.stream.Stream;

class TestValBoolean {

    @Test
    void testHasNumericValue() {
        Assertions.assertThat(ValBoolean.create(true).hasNumericValue())
                .isTrue();
    }

    @Test
    void testHasFractionalPart() {
        Assertions.assertThat(ValBoolean.create(true).hasFractionalPart())
                .isFalse();
    }

    @TestFactory
    Stream<DynamicTest> testToInteger() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValBoolean.class)
                .withOutputType(Integer.class)
                .withSingleArgTestFunction(ValBoolean::toInteger)
                .withSimpleEqualityAssertion()
                .addCase(ValBoolean.create(true), 1)
                .addCase(ValBoolean.create(false), 0)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToLong() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValBoolean.class)
                .withOutputType(Long.class)
                .withSingleArgTestFunction(ValBoolean::toLong)
                .withSimpleEqualityAssertion()
                .addCase(ValBoolean.create(true), 1L)
                .addCase(ValBoolean.create(false), 0L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToFloat() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValBoolean.class)
                .withOutputType(Float.class)
                .withSingleArgTestFunction(ValBoolean::toFloat)
                .withSimpleEqualityAssertion()
                .addCase(ValBoolean.create(true), 1F)
                .addCase(ValBoolean.create(false), 0F)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToDouble() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValBoolean.class)
                .withOutputType(Double.class)
                .withSingleArgTestFunction(ValBoolean::toDouble)
                .withSimpleEqualityAssertion()
                .addCase(ValBoolean.create(true), 1D)
                .addCase(ValBoolean.create(false), 0D)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToBoolean() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValBoolean.class)
                .withOutputType(Boolean.class)
                .withSingleArgTestFunction(ValBoolean::toBoolean)
                .withSimpleEqualityAssertion()
                .addCase(ValBoolean.create(true), true)
                .addCase(ValBoolean.create(false), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValBoolean.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(ValBoolean::toString)
                .withSimpleEqualityAssertion()
                .addCase(ValBoolean.create(true), "true")
                .addCase(ValBoolean.create(false), "false")
                .build();
    }
}
