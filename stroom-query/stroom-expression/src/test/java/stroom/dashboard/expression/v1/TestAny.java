package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestAny extends AbstractFunctionTest<Any> {

    @Override
    Class<Any> getFunctionType() {
        return Any.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "1",
                        ValLong.create(3),
                        ValLong.create(1),
                        ValLong.create(2),
                        ValLong.create(3)),
                TestCase.ofAggregate(
                        "2",
                        ValLong.create(1),
                        ValLong.create(3),
                        ValLong.create(2),
                        ValLong.create(1))
        );
    }
}