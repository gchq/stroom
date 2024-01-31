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
                        "null",
                        ValNull.INSTANCE,
                        Val.create(null)),
                TestCase.of(
                        "",
                        ValNull.INSTANCE,
                        Val.create("")),
                TestCase.of(
                        "foo",
                        ValErr.create(ValDurationUtil.PARSE_ERROR_MESSAGE),
                        Val.create("foo")),
                TestCase.of(
                        "P1D foo",
                        ValErr.create(ValDurationUtil.PARSE_ERROR_MESSAGE),
                        Val.create("P1D foo")),
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
