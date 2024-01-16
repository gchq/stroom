package stroom.query.language.functions;

import java.time.Duration;
import java.util.stream.Stream;

public class TestFormatDuration extends AbstractFunctionTest<FormatDuration> {

    @Override
    Class<FormatDuration> getFunctionType() {
        return FormatDuration.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "1h",
                        ValString.create("1h"),
                        ValDuration.create(Duration.ofHours(1).toMillis())),
                TestCase.of(
                        "22h",
                        ValString.create("22h"),
                        ValDuration.create(Duration.ofHours(22).toMillis())),
                TestCase.of(
                        "5m",
                        ValString.create("5m"),
                        ValDuration.create(Duration.ofMinutes(5).toMillis()))
        );
    }
}
