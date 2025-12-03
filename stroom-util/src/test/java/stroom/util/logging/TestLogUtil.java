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

package stroom.util.logging;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.concurrent.DurationAdder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestLogUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLogUtil.class);

    @Test
    void inSeparatorLine() {
        LOGGER.info(LogUtil.inSeparatorLine("Hello World"));
    }

    @Test
    void inBox() {
        final String box = LogUtil.inBox("Hello World");
        LOGGER.info("\n{}", box);

        assertThat(box)
                .isEqualTo("""
                        ┌───────────────┐
                        │  Hello World  │
                        └───────────────┘""");
    }

    @Test
    void inBox_newLine() {
        final String box = LogUtil.inBoxOnNewLine("Hello World");
        LOGGER.info(box);

        assertThat(box)
                .isEqualTo("""

                        ┌───────────────┐
                        │  Hello World  │
                        └───────────────┘""");
    }

    @Test
    void inBoxMultiLine() {
        final String box = LogUtil.inBoxOnNewLine("""
                This is
                an example showing
                multiple lines
                of differing length""");

        LOGGER.info(box);
        assertThat(box)
                .isEqualTo("""

                        ┌───────────────────────┐
                        │  This is              │
                        │  an example showing   │
                        │  multiple lines       │
                        │  of differing length  │
                        └───────────────────────┘""");
    }

    @Test
    void withPercentage_double() {
        assertThat(LogUtil.withPercentage(10.5, 100.0))
                .isEqualTo("10.5 (10.5%)");
    }

    @Test
    void withPercentage_int() {
        assertThat(LogUtil.withPercentage((int) 10, (int) 100))
                .isEqualTo("10 (10%)");

    }

    @Test
    void withPercentage_long() {
        assertThat(LogUtil.withPercentage(10L, 100L))
                .isEqualTo("10 (10%)");
    }

    @Test
    void withPercentage_duration() {
        assertThat(LogUtil.withPercentage(Duration.ofMinutes(12), Duration.ofHours(1)))
                .isEqualTo("PT12M (20%)");
    }

    @Test
    void withPercentage_durationAdder() {
        final DurationAdder durationAdderVal = new DurationAdder(Duration.ofSeconds(30));
        final DurationAdder durationAdderTotal = new DurationAdder(Duration.ofMinutes(1));

        assertThat(LogUtil.withPercentage(durationAdderVal, durationAdderTotal))
                .isEqualTo("PT30S (50%)");
    }

    @Test
    void withPercentage_null() {

        assertThat(LogUtil.withPercentage(null, (int) 100))
                .isNull();
    }

    @Test
    void test() {
        final String output = LogUtil.toPaddedMultiLine(
                "  ",
                List.of("one", "two", "three"),
                String::toUpperCase);
        LOGGER.debug("output:\n{}", output);
        assertThat(output)
                .isEqualTo("  ONE\n" + "  TWO\n" + "  THREE");
    }

    @Test
    void toStringWithoutName() {
        assertThat(LogUtil.toStringWithoutClassName(new MyPojo("abc", 123)))
                .isEqualTo("aString='abc', anInt=123");

    }

    @Test
    void toStringWithoutName_null() {
        assertThat(LogUtil.toStringWithoutClassName(null))
                .isNull();

    }

    @Test
    void toStringWithoutName_empty() {
        assertThat(LogUtil.toStringWithoutClassName(new Object() {
            @Override
            public String toString() {
                return "";
            }
        }))
                .isEqualTo("");

    }

    @Test
    void toStringWithoutName_noName() {
        assertThat(LogUtil.toStringWithoutClassName(new Object() {
            @Override
            public String toString() {
                return "abc";
            }
        }))
                .isEqualTo("abc");
    }

    @TestFactory
    Stream<DynamicTest> testNamedCount() {
        final String name = "apple";
        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(count ->
                        LogUtil.namedCount(name, count))
                .withSimpleEqualityAssertion()
                .addCase(0, "0 apples")
                .addCase(1, "1 apple")
                .addCase(2, "2 apples")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNamedCount2() {
        final String base = "embass";
        final String singular = "y";
        final String plural = "ies";
        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(count ->
                        LogUtil.namedCount(base, singular, plural, count))
                .withSimpleEqualityAssertion()
                .addCase(0, "0 embassies")
                .addCase(1, "1 embassy")
                .addCase(2, "2 embassies")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testExceptionMessage() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Throwable.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(LogUtil::exceptionMessage)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase(new NullPointerException(), NullPointerException.class.getSimpleName())
                .addCase(new IOException(), IOException.class.getSimpleName())
                .addCase(new IOException((String) null), IOException.class.getSimpleName())
                .addCase(
                        new IOException("Bad happened"),
                        IOException.class.getSimpleName() + " 'Bad happened'")
                .build();
    }

    @Disabled
    @Test
    void testTemplatePerformance() {

        final Random random = new Random();

        final TimedCase lambdaOneArg = TimedCase.of("1 arg Lambda", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> "The number is " + num);
            }
        });

        final TimedCase templateOneArg = TimedCase.of("1 arg Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace("The number is {}", num);
            }
        });

        final TimedCase templateLambdaOneArg = TimedCase.of("1 arg Lambda+Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> LogUtil.message("The number is {}", num));
            }
        });

        final TimedCase lambdaTwoArgs = TimedCase.of("2 arg Lambda", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> "The numbers are " + num + " and " + num);
            }
        });

        final TimedCase templateTwoArgs = TimedCase.of("2 arg Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace("The numbers are {} and {}", num, num);
            }
        });

        final TimedCase templateLambdaTwoArgs = TimedCase.of("2 arg Lambda+Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> LogUtil.message("The numbers are {} and {}", num, num));
            }
        });

        final TimedCase lambdaThreeArgs = TimedCase.of("3 arg Lambda", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> "The numbers are " + num + " and " + num + " and " + num);
            }
        });

        final TimedCase templateThreeArgs = TimedCase.of("3 arg Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace("The numbers are {} and {} and {}", num, num, num);
            }
        });

        final TimedCase templateLambdaThreeArgs = TimedCase.of("3 arg Lambda+Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> LogUtil.message("The numbers are {} and {} and {}", num, num, num));
            }
        });

        final TimedCase lambdaSevenArgs = TimedCase.of("7 arg Lambda", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> "The numbers are " + num + " and " + num + " and " + num + " and " +
                                   num + " and " + num + " and " + num + " and " + num);
            }
        });

        final TimedCase templateSevenArgs = TimedCase.of("7 arg Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace("The numbers are {} and {} and {} and {} and {} and {} and {}",
                        num, num, num, num, num, num, num);
            }
        });

        final TimedCase templateLambdaSevenArgs = TimedCase.of("7 arg Lambda+Template", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final int num = random.nextInt();
                LOGGER.trace(() -> LogUtil.message("The numbers are {} and {} and {} and {} and {} and {} and {}",
                        num, num, num, num, num, num, num));
            }
        });

        TestUtil.comparePerformance(
                3,
                10_000_000,
                LOGGER::info,
                lambdaOneArg,
                templateOneArg,
                templateLambdaOneArg,
                lambdaTwoArgs,
                templateTwoArgs,
                templateLambdaTwoArgs,
                lambdaThreeArgs,
                templateThreeArgs,
                templateLambdaThreeArgs,
                lambdaSevenArgs,
                templateSevenArgs,
                templateLambdaSevenArgs);
    }


    // --------------------------------------------------------------------------------


    private static class MyPojo {

        private final String aString;
        private final int anInt;

        private MyPojo(final String aString, final int anInt) {
            this.aString = aString;
            this.anInt = anInt;
        }

        @Override
        public String toString() {
            return "MyPojo{" +
                   "aString='" + aString + '\'' +
                   ", anInt=" + anInt +
                   '}';
        }
    }

    @TestFactory
    Stream<DynamicTest> testPath() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Path.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(LogUtil::path)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase(Path.of("/tmp"), "/tmp")
                .addCase(Path.of("/tmp/../tmp/"), "/tmp")
                .build();
    }

    @Test
    void message_tooManyArgs() {
        System.out.println(LogUtil.message("Hello {}", "world", "foo"));
    }
}
