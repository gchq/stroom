package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestModulo extends AbstractFunctionTest<Modulo> {

    @Override
    Class<Modulo> getFunctionType() {
        return Modulo.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "100 % 30",
                        ValDouble.create(10),
                        ValInteger.create(100),
                        ValInteger.create(30)),
                TestCase.of(
                        "(100 % 30) % 4",
                        ValDouble.create(2),
                        ValInteger.create(100),
                        ValInteger.create(30),
                        ValInteger.create(4))
        );
    }
}
