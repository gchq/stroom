package stroom.proxy.repo;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestCSVFormatter {

    @TestFactory
    Stream<DynamicTest> testEscape() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withSingleArgTestFunction(CSVFormatter::escape)
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase("\"", "\"\"")
                .addCase("foo", "foo")
                .addCase("foo,bar", "foo,bar")
                .build();
    }
}
