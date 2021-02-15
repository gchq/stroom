package stroom.dashboard.expression.v1;

import java.util.List;
import java.util.stream.Stream;

class TestLast extends AbstractFunctionTest<Last> {

    @Override
    Class<Last> getFunctionType() {
        return Last.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "non-null",
                        ValLong.create(5),
                        List.of(ValLong.create(1),
                                ValLong.create(2),
                                ValLong.create(3),
                                ValLong.create(4),
                                ValLong.create(5))),
                TestCase.ofAggregate(
                        "null",
                        ValNull.INSTANCE,
                        List.of(ValLong.create(1),
                                ValLong.create(2),
                                ValLong.create(3),
                                ValLong.create(4),
                                ValNull.INSTANCE))
        );
    }
}
