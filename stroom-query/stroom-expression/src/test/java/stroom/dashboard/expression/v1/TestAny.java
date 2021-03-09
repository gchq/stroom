package stroom.dashboard.expression.v1;

import java.util.List;
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
                        "ascending",
                        ValLong.create(1),
                        List.of(
                                ValLong.create(1),
                                ValLong.create(2),
                                ValLong.create(3))),
                TestCase.ofAggregate(
                        "descending",
                        ValLong.create(3),
                        List.of(
                                ValLong.create(3),
                                ValLong.create(2),
                                ValLong.create(1))),
                TestCase.ofAggregate(
                        "null",
                        ValLong.create(2),
                        List.of(
                                ValNull.INSTANCE,
                                ValLong.create(2),
                                ValLong.create(3)))
        );
    }
}
