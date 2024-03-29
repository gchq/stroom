package stroom.query.language.functions;

import java.util.stream.Stream;

class TestNotEquals extends AbstractEqualityFunctionTest<NotEquals> {

    @Override
    Class<NotEquals> getFunctionType() {
        return NotEquals.class;
    }

    @Override
    String getOperator() {
        return NotEquals.NAME;
    }

    @Override
    boolean addInverseTest() {
        return false;
    }

    @Override
    Stream<Values> getTestCaseValues() {
        return Stream.of(
                Values.of(2, 1, true),
                Values.of(2, 1L, true),
                Values.of(2, 1D, true),
                Values.of(2, 1F, true),
                Values.of(2, 2, false),
                Values.of(2, 2L, false),
                Values.of(2, 2D, false),
                Values.of(2, 2.0D, false),
                Values.of(2, 2F, false),
                Values.of(2, 2.0F, false),

                Values.of(2L, 1L, true),
                Values.of(2L, 1, true),
                Values.of(2L, 1D, true),
                Values.of(2L, 1F, true),
                Values.of(2L, 2L, false),
                Values.of(2L, 2, false),
                Values.of(2L, 2D, false),
                Values.of(2L, 2.0D, false),
                Values.of(2L, 2F, false),
                Values.of(2L, 2.0F, false),

                Values.of(1.2D, 1.1D, true),
                Values.of(1.2D, 1, true),
                Values.of(1.2D, 1L, true),
                Values.of(1.2D, 1.1F, true),
                Values.of(1.1D, 1.1D, false),
                Values.of(1D, 1D, false),
                Values.of(1D, 1, false),
                Values.of(1D, 1L, false),
                Values.of(1.1D, 1.1F, false),

                Values.of(1.2F, 1.1F, true),
                Values.of(1.2F, 1, true),
                Values.of(1.2F, 1L, true),
                Values.of(1.2F, 1.1D, true),
                Values.of(1.1F, 1.1F, false),
                Values.of(1F, 1, false),
                Values.of(1F, 1L, false),
                Values.of(1.1F, 1.1D, false),

                Values.of(true, false, true),
                Values.of(true, true, false),

                Values.of("dog", "cat", true),
                Values.of("CAT", "cat", true),
                Values.of("cat", "cat", false)
        );
    }
}
