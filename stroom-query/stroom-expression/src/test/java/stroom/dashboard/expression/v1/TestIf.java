package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestIf extends AbstractFunctionTest<If> {

    @Override
    Class<If> getFunctionType() {
        return If.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "static 1",
                        ValLong.create(100),
                        ValBoolean.TRUE,
                        ValLong.create(100),
                        ValString.create("abc")),
                TestCase.of(
                        "static 2",
                        ValString.create("abc"),
                        ValBoolean.FALSE,
                        ValLong.create(100),
                        ValString.create("abc"))
        );
    }
}