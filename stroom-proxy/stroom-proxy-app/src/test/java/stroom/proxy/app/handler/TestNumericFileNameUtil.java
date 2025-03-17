package stroom.proxy.app.handler;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestNumericFileNameUtil {

    @TestFactory
    Stream<DynamicTest> testCreate() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(long.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(NumericFileNameUtil::create)
                .withSimpleEqualityAssertion()
                .addCase(0L, "0000000000")
                .addCase(1L, "0000000001")
                .addCase(10L, "0000000010")
                .addCase(101L, "0000000101")
                .addCase(9_999_999_999L, "9999999999")
                .addThrowsCase(Long.MAX_VALUE, IllegalArgumentException.class)
                .build();
    }
}
