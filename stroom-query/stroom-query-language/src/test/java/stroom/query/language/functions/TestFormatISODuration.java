package stroom.query.language.functions;

import java.time.Duration;
import java.util.stream.Stream;

public class TestFormatISODuration extends AbstractFunctionTest<FormatISODuration> {

    @Override
    Class<FormatISODuration> getFunctionType() {
        return FormatISODuration.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "PT1H",
                        ValString.create("PT1H"),
                        ValDuration.create(Duration.ofHours(1).toMillis())),
                TestCase.of(
                        "PT22H",
                        ValString.create("PT22H"),
                        ValDuration.create(Duration.ofHours(22).toMillis())),
                TestCase.of(
                        "PT5M",
                        ValString.create("PT5M"),
                        ValDuration.create(Duration.ofMinutes(5).toMillis()))
        );
    }
}
