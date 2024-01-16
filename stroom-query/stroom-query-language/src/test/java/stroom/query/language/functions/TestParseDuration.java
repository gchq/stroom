package stroom.query.language.functions;

import java.time.Duration;
import java.util.stream.Stream;

public class TestParseDuration extends AbstractFunctionTest<ParseDuration> {

    @Override
    Class<ParseDuration> getFunctionType() {
        return ParseDuration.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "1h",
                        ValDuration.create(Duration.ofHours(1).toMillis()),
                        ValString.create("1h")),
                TestCase.of(
                        "22h",
                        ValDuration.create(Duration.ofHours(22).toMillis()),
                        ValString.create("22h")),
                TestCase.of(
                        "5m",
                        ValDuration.create(Duration.ofMinutes(5).toMillis()),
                        ValString.create("5m"))
        );
    }
}
