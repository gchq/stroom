package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestMax extends AbstractFunctionTest<Max> {

    @Override
    Class<Max> getFunctionType() {
        return Max.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "basic int",
                        ValDouble.create(100),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.ofAggregate(
                        "aggregate int",
                        ValDouble.create(100),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.of(
                        "basic double",
                        ValDouble.create(1.3),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.ofAggregate(
                        "aggregate double",
                        ValDouble.create(1.3),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3))
        );
    }
}