package stroom.util.io;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestByteSizeUnit {

    @Test
    void intBytes() {
    }

    @TestFactory
    Stream<DynamicTest> testIntBytes() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(int.class, ByteSizeUnit.class)
                .withOutputType(int.class)
                .withTestFunction(testCase -> {
                    final int value = testCase.getInput()._1;
                    final ByteSizeUnit byteSizeUnit = testCase.getInput()._2;
                    final int intResult = byteSizeUnit.intBytes(value);
                    final long longResult = byteSizeUnit.longBytes(value);
                    Assertions.assertThat(intResult)
                            .isEqualTo(longResult);
                    return intResult;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(1, ByteSizeUnit.BYTE), 1)
                .addCase(Tuple.of(1, ByteSizeUnit.KIBIBYTE), 1024)
                .addCase(Tuple.of(1, ByteSizeUnit.KILOBYTE), 1000)
                .addCase(Tuple.of(1, ByteSizeUnit.MEBIBYTE), 1024 * 1024)
                .addCase(Tuple.of(1, ByteSizeUnit.MEGABYTE), 1000 * 1000)
                .addCase(Tuple.of(1, ByteSizeUnit.GIBIBYTE), 1024 * 1024 * 1024)
                .addCase(Tuple.of(1, ByteSizeUnit.GIGABYTE), 1000 * 1000 * 1000)
                .addCase(Tuple.of(5, ByteSizeUnit.BYTE), 5)
                .addCase(Tuple.of(5, ByteSizeUnit.KIBIBYTE), 5 * 1024)
                .addCase(Tuple.of(5, ByteSizeUnit.KILOBYTE), 5 * 1000)
                .addCase(Tuple.of(5, ByteSizeUnit.MEBIBYTE), 5 * 1024 * 1024)
                .addCase(Tuple.of(5, ByteSizeUnit.MEGABYTE), 5 * 1000 * 1000)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLongBytes() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(long.class, ByteSizeUnit.class)
                .withOutputType(long.class)
                .withTestFunction(testCase -> {
                    final long value = testCase.getInput()._1;
                    final ByteSizeUnit byteSizeUnit = testCase.getInput()._2;
                    return byteSizeUnit.longBytes(value);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(1L, ByteSizeUnit.BYTE), 1L)
                .addCase(Tuple.of(1L, ByteSizeUnit.KIBIBYTE), 1024L)
                .addCase(Tuple.of(1L, ByteSizeUnit.KILOBYTE), 1000L)
                .addCase(Tuple.of(1L, ByteSizeUnit.MEBIBYTE), 1024L * 1024)
                .addCase(Tuple.of(1L, ByteSizeUnit.MEGABYTE), 1000L * 1000)
                .addCase(Tuple.of(1L, ByteSizeUnit.GIBIBYTE), 1024L * 1024 * 1024)
                .addCase(Tuple.of(1L, ByteSizeUnit.GIGABYTE), 1000L * 1000 * 1000)
                .addCase(Tuple.of(1L, ByteSizeUnit.TEBIBYTE), 1024L * 1024 * 1024 * 1024)
                .addCase(Tuple.of(1L, ByteSizeUnit.TERABYTE), 1000L * 1000 * 1000 * 1000)
                .addCase(Tuple.of(1L, ByteSizeUnit.PEBIBYTE), 1024L * 1024 * 1024 * 1024 * 1024)
                .addCase(Tuple.of(1L, ByteSizeUnit.PETABYTE), 1000L * 1000 * 1000 * 1000 * 1000)
                .addCase(Tuple.of(5L, ByteSizeUnit.BYTE), 5L)
                .addCase(Tuple.of(5L, ByteSizeUnit.KIBIBYTE), 5L * 1024L)
                .addCase(Tuple.of(5L, ByteSizeUnit.KILOBYTE), 5L * 1000L)
                .addCase(Tuple.of(5L, ByteSizeUnit.MEBIBYTE), 5L * 1024L * 1024)
                .addCase(Tuple.of(5L, ByteSizeUnit.MEGABYTE), 5L * 1000L * 1000)
                .addCase(Tuple.of(5L, ByteSizeUnit.GIBIBYTE), 5L * 1024L * 1024 * 1024)
                .addCase(Tuple.of(5L, ByteSizeUnit.GIGABYTE), 5L * 1000L * 1000 * 1000)
                .addCase(Tuple.of(5L, ByteSizeUnit.TEBIBYTE), 5L * 1024L * 1024 * 1024 * 1024)
                .addCase(Tuple.of(5L, ByteSizeUnit.TERABYTE), 5L * 1000L * 1000 * 1000 * 1000)
                .addCase(Tuple.of(5L, ByteSizeUnit.PEBIBYTE), 5L * 1024L * 1024 * 1024 * 1024 * 1024)
                .addCase(Tuple.of(5L, ByteSizeUnit.PETABYTE), 5L * 1000L * 1000 * 1000 * 1000 * 1000)
                .build();
    }
}
