package stroom.query.language.functions;

import java.time.Duration;
import java.util.stream.Stream;

class TestEquals extends AbstractEqualityFunctionTest<Equals> {

    @Override
    Class<Equals> getFunctionType() {
        return Equals.class;
    }

    @Override
    String getOperator() {
        return Equals.NAME;
    }

    @Override
    boolean addInverseTest() {
        return false;
    }

    @Override
    Stream<Values> getTestCaseValues() {
        return Stream.of(
                Values.of(2, 1, false),
                Values.of(2, 1L, false),
                Values.of(2, 1D, false),
                Values.of(2, 1F, false),
                Values.of(2, 2, true),
                Values.of(2, 2L, true),
                Values.of(2, 2D, true),
                Values.of(2, 2.0D, true),
                Values.of(2, 2F, true),
                Values.of(2, 2.0F, true),

                Values.of(2L, 1L, false),
                Values.of(2L, 1, false),
                Values.of(2L, 1D, false),
                Values.of(2L, 1F, false),
                Values.of(2L, 2L, true),
                Values.of(2L, 2, true),
                Values.of(2L, 2D, true),
                Values.of(2L, 2.0D, true),
                Values.of(2L, 2F, true),
                Values.of(2L, 2.0F, true),

                Values.of(1.2D, 1.1D, false),
                Values.of(1.2D, 1, false),
                Values.of(1.2D, 1L, false),
                Values.of(1.2D, 1.1F, false),
                Values.of(1.1D, 1.1D, true),
                Values.of(1D, 1D, true),
                Values.of(1D, 1, true),
                Values.of(1D, 1L, true),
                Values.of(1.1D, 1.1F, true),

                Values.of(1.2F, 1.1F, false),
                Values.of(1.2F, 1, false),
                Values.of(1.2F, 1L, false),
                Values.of(1.2F, 1.1D, false),
                Values.of(1.1F, 1.1F, true),
                Values.of(1F, 1, true),
                Values.of(1F, 1L, true),
                Values.of(1.1F, 1.1D, true),

                Values.of(true, false, false),
                Values.of(true, true, true),

                Values.of("dog", "cat", false),
                Values.of("CAT", "cat", false),
                Values.of("cat", "cat", true),

                Values.of("1", "1", true),
                Values.of("1", "2", false),

                Values.of(true, "true", true),
                Values.of(true, 1, true),
                Values.of(true, 1L, true),
                Values.of(true, 1F, true),
                Values.of(true, 1.0F, true),
                Values.of(true, 1D, true),
                Values.of(true, 1.0D, true),

                Values.of(false, "false", true),
                Values.of(false, 0, true),
                Values.of(false, 0L, true),
                Values.of(false, 0F, true),
                Values.of(false, 0.0F, true),
                Values.of(false, 0.0D, true),

                Values.of(Duration.ofSeconds(2), Duration.ofSeconds(1), false),
                Values.of(Duration.ofSeconds(1), Duration.ofSeconds(1), true),
                Values.of(Duration.ofSeconds(1), 1_000, true)
        );
    }
}
