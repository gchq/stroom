package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestSum extends AbstractFunctionTest<Sum> {

    @Override
    Class<Sum> getFunctionType() {
        return Sum.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "basic int",
                        ValDouble.create(111),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.ofAggregate(
                        "aggregate int",
                        ValDouble.create(111),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.of(
                        "basic double",
                        ValDouble.create(-0.8),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.ofAggregate(
                        "aggregate double",
                        ValDouble.create(-0.8),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3))
        );
    }
}
