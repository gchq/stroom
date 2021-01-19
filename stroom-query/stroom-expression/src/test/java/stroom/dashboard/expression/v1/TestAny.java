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
               // TODO @AT
        );
    }
}