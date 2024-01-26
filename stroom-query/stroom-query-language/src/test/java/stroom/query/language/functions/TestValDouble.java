package stroom.query.language.functions;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.util.stream.Stream;

class TestValDouble {

    @TestFactory
    Stream<DynamicTest> testHasFractionalPart() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(ValDouble.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(ValDouble::hasFractionalPart)
                .withSimpleEqualityAssertion()
                .addCase(ValDouble.create(0D), false)
                .addCase(ValDouble.create(1.1D), true)
                .addCase(ValDouble.create(1D), false)
                .addCase(ValDouble.create(1234D), false)
                .addCase(ValDouble.create(1.0000000000001D), true)
                .addCase(ValDouble.create(new BigDecimal(Long.toString(Long.MAX_VALUE)).doubleValue()),
                        false)
                .build();
    }
}
