package stroom.query.language.functions;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.util.stream.Stream;

class TestValString {

    @TestFactory
    Stream<DynamicTest> testHasNumericValue() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ValString::hasNumericValue)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), false)
                .addCase(ValString.create("10 foo"), false)
                .addCase(ValString.create(null), false)
                .addCase(ValString.create(""), false)
                .addCase(ValString.create(" 1.1"), false)

                .addCase(ValString.create("0"), true)
                .addCase(ValString.create("1"), true)
                .addCase(ValString.create("-1"), true)
                .addCase(ValString.create("1.1"), true)
                .addCase(ValString.create("1234"), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasFractionalPart() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValString.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ValString::hasFractionalPart)
                .withSimpleEqualityAssertion()
                .addCase(ValString.create("foo"), false)
                .addCase(ValString.create("10 foo"), false)
                .addCase(ValString.create(null), false)
                .addCase(ValString.create(""), false)
                .addCase(ValString.create(" 1.1"), false)
                .addCase(ValString.create("1"), false)
                .addCase(ValString.create("1234"), false)

                .addCase(ValString.create("1.1"), true)
                .addCase(ValString.create("1.0000000000001"), true)
                .addCase(ValString.create(new BigDecimal(Long.toString(Long.MAX_VALUE)).toString()),
                        false)
                .addCase(ValString.create(Double.toString(Double.parseDouble(Long.toString(Long.MAX_VALUE)))),
                        false)
                .build();
    }
}
