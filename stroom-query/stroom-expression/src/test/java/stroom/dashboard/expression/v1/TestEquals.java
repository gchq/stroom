package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestEquals extends AbstractFunctionTest<Equals> {

    @Override
    Class<Equals> getFunctionType() {
        return Equals.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "string true",
                        ValBoolean.TRUE,
                        ValString.create("abc"),
                        ValString.create("abc")),
                TestCase.of(
                        "string false",
                        ValBoolean.FALSE,
                        ValString.create("abc"),
                        ValString.create("def")),
                TestCase.of(
                        "int true",
                        ValBoolean.TRUE,
                        ValInteger.create(123),
                        ValInteger.create(123)),
                TestCase.of(
                        "int false",
                        ValBoolean.FALSE,
                        ValInteger.create(123),
                        ValInteger.create(456)),
                TestCase.of(
                        "long true",
                        ValBoolean.TRUE,
                        ValLong.create(123L),
                        ValLong.create(123L)),
                TestCase.of(
                        "long false",
                        ValBoolean.FALSE,
                        ValLong.create(123L),
                        ValLong.create(456L)),
                TestCase.of(
                        "double true",
                        ValBoolean.TRUE,
                        ValDouble.create(1.230),
                        ValDouble.create(1.23)),
                TestCase.of(
                        "double false",
                        ValBoolean.FALSE,
                        ValDouble.create(1.23),
                        ValDouble.create(1.2301)),
                TestCase.of(
                        "boolean true",
                        ValBoolean.TRUE,
                        ValBoolean.TRUE,
                        ValBoolean.TRUE),
                TestCase.of(
                        "boolean false",
                        ValBoolean.FALSE,
                        ValBoolean.TRUE,
                        ValBoolean.FALSE),
                TestCase.of(
                        "mixed double true",
                        ValBoolean.TRUE,
                        ValString.create("1.230000"),
                        ValDouble.create(1.23)),
                TestCase.of(
                        "mixed boolean true",
                        ValBoolean.TRUE,
                        ValString.create("true"),
                        ValBoolean.TRUE),
                TestCase.of(
                        "mixed boolean 2 true",
                        ValBoolean.TRUE,
                        ValBoolean.TRUE,
                        ValLong.create(1))
        );
    }
}
