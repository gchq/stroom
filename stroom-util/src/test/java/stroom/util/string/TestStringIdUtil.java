package stroom.util.string;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestStringIdUtil {

    @Test
    void test() {
        assertThat(StringIdUtil.idToString(0)).isEqualTo("000");
        assertThat(StringIdUtil.idToString(1)).isEqualTo("001");
        assertThat(StringIdUtil.idToString(999)).isEqualTo("999");
        assertThat(StringIdUtil.idToString(1000)).isEqualTo("001000");
        assertThat(StringIdUtil.idToString(999999)).isEqualTo("999999");
        assertThat(StringIdUtil.idToString(1000000)).isEqualTo("001000000");
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
