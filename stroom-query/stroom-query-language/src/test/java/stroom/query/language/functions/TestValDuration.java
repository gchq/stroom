package stroom.query.language.functions;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.stream.Stream;

class TestValDuration {

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
