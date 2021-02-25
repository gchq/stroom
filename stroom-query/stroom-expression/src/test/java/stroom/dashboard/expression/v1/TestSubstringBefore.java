package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestSubstringBefore extends AbstractFunctionTest<SubstringBefore> {

    @Override
    Class<SubstringBefore> getFunctionType() {
        return SubstringBefore.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "simple",
                        "key",
                        "key=>value",
                        "=>"),
                TestCase.of(
                        "not found",
                        "",
                        "key:value",
                        "=>")
        );
    }
}
