package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestNegate extends AbstractFunctionTest<Negate> {

    @Override
    Class<Negate> getFunctionType() {
        return Negate.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "double 1",
                        ValDouble.create(-1.23),
                        ValDouble.create(1.23)),
                TestCase.of(
                        "double 2",
                        ValDouble.create(1.23),
                        ValDouble.create(-1.23)),
                TestCase.of(
                        "long",
                        ValDouble.create(-123),
                        ValLong.create(123)),
                TestCase.of(
                        "long",
                        ValDouble.create(123),
                        ValLong.create(-123))
        );
    }
}
