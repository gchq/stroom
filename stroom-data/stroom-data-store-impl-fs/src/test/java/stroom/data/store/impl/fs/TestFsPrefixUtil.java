package stroom.data.store.impl.fs;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestFsPrefixUtil {

    @TestFactory
    Stream<DynamicTest> testPadId() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        FsPrefixUtil.padId(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, "000")
                .addCase(0L, "000")
                .addCase(1L, "001")
                .addCase(999L, "999")
                .addCase(1_000L, "001000")
                .addCase(999_999L, "999999")
                .addCase(1_000_000L, "001000000")
                .addCase(999_999_999L, "999999999")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testDePadId() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(long.class)
                .withTestFunction(testCase ->
                        FsPrefixUtil.dePadId(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, -1L)
                .addCase("", -1L)
                .addCase("0", 0L)
                .addCase("000", 0L)
                .addCase("1", 1L)
                .addCase("001", 1L)
                .addCase("999", 999L)
                .addCase("001000", 1_000L)
                .addCase("999999", 999_999L)
                .addCase("001000000", 1_000_000L)
                .addCase("999999999", 999_999_999L)
                .addCase("000ABC", -1L)
                .addCase("ABC", -1L)
                .build();
    }
}
