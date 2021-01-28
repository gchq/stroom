package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestNot extends AbstractFunctionTest<Not> {

    @Override
    Class<Not> getFunctionType() {
        return Not.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "same type 1",
                        ValBoolean.TRUE,
                        ValBoolean.FALSE),
                TestCase.of(
                        "mixed type 1",
                        ValBoolean.TRUE,
                        ValString.create("xxx")),
                TestCase.of(
                        "same type 2",
                        ValBoolean.TRUE,
                        ValBoolean.FALSE),
                TestCase.of(
                        "same type 2",
                        ValBoolean.FALSE,
                        ValString.create("true")),
                TestCase.of(
                        "mixed type 3",
                        ValBoolean.TRUE,
                        ValLong.create(0)),
                TestCase.of(
                        "mixed type 4",
                        ValBoolean.FALSE,
                        ValLong.create(1))
        );
    }
}