package stroom.query.language.functions;

import java.util.stream.Stream;

class TestLessThan extends AbstractEqualityFunctionTest<LessThan> {

    @Override
    Class<LessThan> getFunctionType() {
        return LessThan.class;
    }

    @Override
    String getOperator() {
        return LessThan.NAME;
    }

    @Override
    Stream<Values> getTestCaseValues() {
        return Stream.of(
                Values.of(2, 1, false),
                Values.of(2, 1L, false),
                Values.of(2, 1D, false),
                Values.of(2, 1F, false),
                Values.of(2, 2, false),
                Values.of(2, null, false),

                Values.of(2L, 1L, false),
                Values.of(2L, 1, false),
                Values.of(2L, 1D, false),
                Values.of(2L, 1F, false),
                Values.of(2L, 2L, false),
                Values.of(2L, null, false),

                Values.of(1.2D, 1.1D, false),
                Values.of(1.2D, 1, false),
                Values.of(1.2D, 1L, false),
                Values.of(1.2D, 1.1F, false),
                Values.of(1.1D, 1.1D, false),
                Values.of(1.1D, null, false),

                Values.of(1.2F, 1.1F, false),
                Values.of(1.2F, 1, false),
                Values.of(1.2F, 1L, false),
                Values.of(1.2F, 1.1D, false),
                Values.of(1.1F, 1.1F, false),
                Values.of(1.1F, null, false),

                Values.of(true, false, false),
                Values.of(true, true, false),
                Values.of(true, null, false),

                Values.of(TOMORROW, TODAY, false),
                Values.of(TODAY, TODAY, false),
                Values.of(TODAY, null, false),

                Values.of("dog", "cat", false),
                Values.of("cat", "cat", false),
                Values.of("CAT", "cat", true),
                Values.of("CAT", null, false),

                Values.of(null, null, false)
        );
    }
}
