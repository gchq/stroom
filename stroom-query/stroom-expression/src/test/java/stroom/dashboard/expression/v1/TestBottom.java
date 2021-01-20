package stroom.dashboard.expression.v1;

import java.util.List;
import java.util.stream.Stream;

class TestBottom extends AbstractFunctionTest<Bottom> {

    @Override
    Class<Bottom> getFunctionType() {
        return Bottom.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.ofAggregate(
                        "1",
                        ValString.create("4,5"),
                        List.of(ValLong.create(1),
                                ValLong.create(2),
                                ValLong.create(3),
                                ValLong.create(4),
                                ValLong.create(5)),
                        ValString.create(","),
                        ValLong.create(2))
        );
    }
}