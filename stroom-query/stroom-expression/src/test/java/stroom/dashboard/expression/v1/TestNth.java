package stroom.dashboard.expression.v1;

import java.util.List;
import java.util.stream.Stream;

class TestNth extends AbstractFunctionTest<Nth> {

    @Override
    Class<Nth> getFunctionType() {
        return Nth.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "1",
                        ValLong.create(4),
                        List.of(ValLong.create(1),
                                ValLong.create(2),
                                ValLong.create(3),
                                ValLong.create(4),
                                ValLong.create(5)),
                        ValLong.create(4))
        );
    }
}
