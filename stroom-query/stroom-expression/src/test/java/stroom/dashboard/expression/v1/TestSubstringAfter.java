package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestSubstringAfter extends AbstractFunctionTest<SubstringAfter> {


    @Override
    Class<SubstringAfter> getFunctionType() {
        return SubstringAfter.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "simple",
                        "value",
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
