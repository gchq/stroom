package stroom.dashboard.expression.v1;

import java.util.stream.Stream;

class TestTypeOf extends AbstractFunctionTest<TypeOf> {

    @Override
    Class<TypeOf> getFunctionType() {
        return TypeOf.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of("string",
                        "string",
                        ValString.create("x")),
                TestCase.of("long",
                        "long",
                        ValLong.create(1L)),
                TestCase.of("integer",
                        "integer",
                        ValInteger.create(1)),
                TestCase.of("double",
                        "double",
                        ValDouble.create(1.2)),
                TestCase.of("error",
                        "error",
                        ValErr.INSTANCE),
                TestCase.of("null",
                        "null",
                        ValNull.INSTANCE),
                TestCase.of("boolean",
                        "boolean",
                        ValBoolean.TRUE)
        );
    }
}
