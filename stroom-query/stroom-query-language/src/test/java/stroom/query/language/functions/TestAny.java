package stroom.query.language.functions;

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
                        List.of(ValLong.create(1))),
                TestCase.ofAggregate(
                        "descending",
                        ValLong.create(3),
                        List.of(ValLong.create(3))),
                TestCase.ofAggregate(
                        "null",
                        ValLong.create(2),
                        List.of(ValLong.create(2)))
        );
    }
}
