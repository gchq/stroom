package stroom.util.string;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

public class TestStringIdUtil {

    @TestFactory
    Stream<DynamicTest> testIdToString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(long.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(StringIdUtil::idToString)
                .withSimpleEqualityAssertion()
                .addThrowsCase(-1L, IllegalArgumentException.class)
                .addCase(0L, "000")
                .addCase(1L, "001")
                .addCase(12L, "012")
                .addCase(123L, "123")
                .addCase(1_234L, "001234")
                .addCase(12_345L, "012345")
                .addCase(123_456L, "123456")
                .addCase(1_234_567L, "001234567")
                .addCase(12_345_678L, "012345678")
                .addCase(123_456_789L, "123456789")
                .addCase(999L, "999")
                .addCase(1_000L, "001000")
                .addCase(999_999L, "999999")
                .addCase(1_000_000L, "001000000")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsValidIdString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(StringIdUtil::isValidIdString)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", false)
                .addCase("foo", false)
                .addCase("1", false)
                .addCase("12", false)
                .addCase("123", true)
                .addCase("1234", false)
                .addCase("12345", false)
                .addCase("123456", true)
                .build();
    }
}
