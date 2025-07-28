package stroom.query.language.functions;

import java.util.stream.Stream;

class TestBetween extends AbstractFunctionTest<Between> {

    @Override
    Class<Between> getFunctionType() {
        return Between.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                //between lower and upper
                TestCase.of("value between bounds",
                        ValBoolean.TRUE, ValDouble.create(5),
                        ValDouble.create(1), ValDouble.create(10)),
                //equal to lower bound
                TestCase.of("value equals lower",
                        ValBoolean.TRUE, ValDouble.create(1),
                        ValDouble.create(1), ValDouble.create(10)),
                //equal to upper bound
                TestCase.of("value equals upper",
                        ValBoolean.TRUE, ValDouble.create(10),
                        ValDouble.create(1), ValDouble.create(10)),
                //below lower bound
                TestCase.of("value below lower",
                        ValBoolean.FALSE, ValDouble.create(0), ValDouble.create(1), ValDouble.create(10)),
                //above upper bound
                TestCase.of("value above upper",
                        ValBoolean.FALSE, ValDouble.create(11), ValDouble.create(1), ValDouble.create(10)),
                //non-numeric
                TestCase.of("non-numeric value",
                        ValErr.create("Parameters must be all numeric or all ISO date strings for between function"),
                        ValString.create("a"), ValDouble.create(1), ValDouble.create(10)),
                //null
                TestCase.of("null value",
                        ValErr.create("Null parameter in between function"),
                        ValNull.INSTANCE, ValDouble.create(1), ValDouble.create(10)),
                //date
                TestCase.of("date between bounds",
                        ValBoolean.TRUE, ValString.create("2023-01-05"),
                        ValString.create("2023-01-01"), ValString.create("2023-01-10"))
        );
    }
}
