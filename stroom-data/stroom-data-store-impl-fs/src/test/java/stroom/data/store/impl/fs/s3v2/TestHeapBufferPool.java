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

package stroom.data.store.impl.fs.s3v2;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestHeapBufferPool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHeapBufferPool.class);

    @TestFactory
    Stream<DynamicTest> testGetOffset() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Integer.class)
                .withOutputType(Integer.class)
                .withSingleArgTestFunction(HeapBufferPool::getOffset)
                .withSimpleEqualityAssertion()
                .addCase(1, 0)
                .addCase(9, 1)
                .addCase(10, 1)
                .addCase(11, 2)
                .addCase(99, 2)
                .addCase(100, 2)
                .addCase(1000, 3)
                .addCase(10000, 4)
                .addCase(100000, 5)
                .build();
    }

    @Disabled // Manual perf test
    @Test
    void test() {
        final int totalIterations = 100_000_000;
        final int[] values = new int[totalIterations];
        final int[] capacities = new int[9];
        int capacity = 1;
        for (int i = 0; i < 9; i++) {
            capacities[i] = capacity;
            capacity *= 10;
        }

        final TimedCase timedCase1 = TimedCase.of("getOffset1", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                for (int j = 0; j < 9; j++) {
                    getOffset1(capacities[j]);
                }
            }
        });

        final TimedCase timedCase2 = TimedCase.of("getOffset2", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                for (int j = 0; j < 9; j++) {
                    getOffset2(capacities[j]);
                }
            }
        });

        TestUtil.comparePerformance(
                5,
                totalIterations,
                LOGGER::info,
                timedCase1,
                timedCase2);
    }

    static int getOffset1(final int capacity) {
        return switch (capacity) {
            case 1 -> 0;
            case 10 -> 1;
            case 100 -> 2;
            case 1_000 -> 3;
            case 10_000 -> 4;
            case 100_000 -> 5;
            case 1_000_000 -> 6;
            case 10_000_000 -> 7;
            case 100_000_000 -> 8;
            case 1_000_000_000 -> 9;
            default -> getOffset2(capacity);
        };
    }

    static int getOffset2(final int minCapacity) {
        if (minCapacity <= 10) {
            // Optimisation for ints/longs
            return minCapacity <= 1
                    ? 0
                    : 1;
        }
        return (int) Math.ceil(Math.log10(minCapacity));
    }
}
