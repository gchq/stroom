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

package stroom.util.time;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

class TestTimeUtils {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestTimeUtils.class);

    @Test
    void durationToThreshold() {

        final Instant now = ZonedDateTime.of(
                        2020, 1, 1, 17, 30, 0, 0,
                        ZoneOffset.UTC)
                .toInstant();

        final Instant actual = TimeUtils.durationToThreshold(now, Duration.ofMinutes(10));

        LOGGER.info("actual: {}", actual);

        final Instant expected = ZonedDateTime.of(
                        2020, 1, 1, 17, 20, 0, 0,
                        ZoneOffset.UTC)
                .toInstant();

        Assertions.assertThat(actual)
                .isEqualTo(expected);
    }

    @TestFactory
    Stream<DynamicTest> testGreaterThanOrEqualTo() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(StroomDuration.class, StroomDuration.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final StroomDuration stroomDuration1 = testCase.getInput()._1;
                    final StroomDuration stroomDuration2 = testCase.getInput()._2;
                    final boolean result1 = TimeUtils.isGreaterThanOrEqualTo(stroomDuration1, stroomDuration2);

                    // Test for both StroomDuration and Duration
                    final boolean result2 = TimeUtils.isGreaterThanOrEqualTo(
                            stroomDuration1.getDuration(),
                            stroomDuration2.getDuration());

                    Assertions.assertThat(result2)
                            .isEqualTo(result1);
                    return result1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(2)), false)
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(1)), true)
                .addCase(Tuple.of(StroomDuration.ofDays(2), StroomDuration.ofDays(1)), true)
                .addThrowsCase(Tuple.of(StroomDuration.ofDays(2), null), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, StroomDuration.ofDays(1)), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, null), NullPointerException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGreaterThan() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(StroomDuration.class, StroomDuration.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final StroomDuration stroomDuration1 = testCase.getInput()._1;
                    final StroomDuration stroomDuration2 = testCase.getInput()._2;
                    final boolean result1 = TimeUtils.isGreaterThan(stroomDuration1, stroomDuration2);

                    // Test for both StroomDuration and Duration
                    final boolean result2 = TimeUtils.isGreaterThan(
                            stroomDuration1.getDuration(),
                            stroomDuration2.getDuration());

                    Assertions.assertThat(result2)
                            .isEqualTo(result1);
                    return result1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(2)), false)
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(1)), false)
                .addCase(Tuple.of(StroomDuration.ofDays(2), StroomDuration.ofDays(1)), true)
                .addThrowsCase(Tuple.of(StroomDuration.ofDays(2), null), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, StroomDuration.ofDays(1)), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, null), NullPointerException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLessThanOrEqualTo() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(StroomDuration.class, StroomDuration.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final StroomDuration stroomDuration1 = testCase.getInput()._1;
                    final StroomDuration stroomDuration2 = testCase.getInput()._2;
                    final boolean result1 = TimeUtils.isLessThanOrEqualTo(stroomDuration1, stroomDuration2);

                    // Test for both StroomDuration and Duration
                    final boolean result2 = TimeUtils.isLessThanOrEqualTo(
                            stroomDuration1.getDuration(),
                            stroomDuration2.getDuration());

                    Assertions.assertThat(result2)
                            .isEqualTo(result1);
                    return result1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(2)), true)
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(1)), true)
                .addCase(Tuple.of(StroomDuration.ofDays(2), StroomDuration.ofDays(1)), false)
                .addThrowsCase(Tuple.of(StroomDuration.ofDays(2), null), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, StroomDuration.ofDays(1)), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, null), NullPointerException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLessThan() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(StroomDuration.class, StroomDuration.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final StroomDuration stroomDuration1 = testCase.getInput()._1;
                    final StroomDuration stroomDuration2 = testCase.getInput()._2;
                    final boolean result1 = TimeUtils.isLessThan(stroomDuration1, stroomDuration2);

                    // Test for both StroomDuration and Duration
                    final boolean result2 = TimeUtils.isLessThan(
                            stroomDuration1.getDuration(),
                            stroomDuration2.getDuration());

                    Assertions.assertThat(result2)
                            .isEqualTo(result1);
                    return result1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(2)), true)
                .addCase(Tuple.of(StroomDuration.ofDays(1), StroomDuration.ofDays(1)), false)
                .addCase(Tuple.of(StroomDuration.ofDays(2), StroomDuration.ofDays(1)), false)
                .addThrowsCase(Tuple.of(StroomDuration.ofDays(2), null), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, StroomDuration.ofDays(1)), NullPointerException.class)
                .addThrowsCase(Tuple.of(null, null), NullPointerException.class)
                .build();
    }

}
