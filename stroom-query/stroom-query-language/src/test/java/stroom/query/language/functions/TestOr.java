package stroom.query.language.functions;

import java.util.stream.Stream;

class TestOr extends AbstractFunctionTest<Or> {

    @Override
    Class<Or> getFunctionType() {
        return Or.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "both false",
                        ValBoolean.FALSE,
                        ValBoolean.FALSE,
                        ValBoolean.FALSE),
                TestCase.of(
                        "one true",
                        ValBoolean.TRUE,
                        ValBoolean.FALSE,
                        ValBoolean.TRUE),
                TestCase.of(
                        "both true",
                        ValBoolean.TRUE,
                        ValBoolean.TRUE,
                        ValBoolean.TRUE));
    }
}
