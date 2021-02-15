package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestCeiling extends AbstractFunctionTest<Ceiling> {

    @Override
    Class<Ceiling> getFunctionType() {
        return Ceiling.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "whole 1",
                        ValDouble.create(9),
                        ValDouble.create(8.1234)),
                TestCase.of(
                        "whole 2",
                        ValDouble.create(9),
                        ValDouble.create(8.9234)),
                TestCase.of(
                        "decimal 1",
                        ValDouble.create(1.224),
                        ValDouble.create(1.22345),
                        ValInteger.create(3))
        );
    }
}
