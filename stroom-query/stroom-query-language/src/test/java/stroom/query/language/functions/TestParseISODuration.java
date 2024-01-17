package stroom.query.language.functions;

import java.time.Duration;
import java.util.stream.Stream;

public class TestParseISODuration extends AbstractFunctionTest<ParseISODuration> {

    @Override
    Class<ParseISODuration> getFunctionType() {
        return ParseISODuration.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "PT1H",
                        ValDuration.create(Duration.ofHours(1).toMillis()),
                        ValString.create("PT1H")),
                TestCase.of(
                        "PT22H",
                        ValDuration.create(Duration.ofHours(22).toMillis()),
                        ValString.create("PT22H")),
                TestCase.of(
                        "PT5M",
                        ValDuration.create(Duration.ofMinutes(5).toMillis()),
                        ValString.create("PT5M"))
        );
    }
}
