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
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.stream.Stream;

class TestValDate {

    @TestFactory
    Stream<DynamicTest> testInteger() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDate.class)
                .withOutputType(Integer.class)
                .withSingleArgTestFunction(ValDate::toInteger)
                .withSimpleEqualityAssertion()
                .addCase(ValDate.create(0L), 0)
                .addCase(ValDate.create(1L), 1)
                .addCase(ValDate.create(Integer.MAX_VALUE), Integer.MAX_VALUE)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLong() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDate.class)
                .withOutputType(Long.class)
                .withSingleArgTestFunction(ValDate::toLong)
                .withSimpleEqualityAssertion()
                .addCase(ValDate.create(0L), 0L)
                .addCase(ValDate.create(1L), 1L)
                .addCase(ValDate.create(Long.MAX_VALUE), Long.MAX_VALUE)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFloat() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDate.class)
                .withOutputType(Float.class)
                .withSingleArgTestFunction(ValDate::toFloat)
                .withSimpleEqualityAssertion()
                .addCase(ValDate.create(0L), 0f)
                .addCase(ValDate.create(1L), 1f)
                .addCase(ValDate.create(Long.MAX_VALUE), (float) Long.MAX_VALUE)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBoolean() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDate.class)
                .withOutputType(Boolean.class)
                .withSingleArgTestFunction(ValDate::toBoolean)
                .withSimpleEqualityAssertion()
                .addCase(ValDate.create(0L), false)
                .addCase(ValDate.create(1L), true)
                .addCase(ValDate.create(Long.MAX_VALUE), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testString() {
        final Instant now = Instant.now();

        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDate.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(ValDate::toString)
                .withSimpleEqualityAssertion()
                .addCase(ValDate.create(0L), DateUtil.createNormalDateTimeString(0L))
                .addCase(ValDate.create(now), DateUtil.createNormalDateTimeString(now.toEpochMilli()))
                .build();
    }
}
