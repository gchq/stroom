package stroom.query.language.functions;

import java.util.stream.Stream;

class TestAnd extends AbstractFunctionTest<And> {

    @Override
    Class<And> getFunctionType() {
        return And.class;
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
                        "one false",
                        ValBoolean.FALSE,
                        ValBoolean.FALSE,
                        ValBoolean.TRUE),
                TestCase.of(
                        "both true",
                        ValBoolean.TRUE,
                        ValBoolean.TRUE,
                        ValBoolean.TRUE));
    }
}
