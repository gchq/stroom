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
                        "1000 (long)",
                        ValErr.create(ValDurationUtil.PARSE_ERROR_MESSAGE),
                        Val.create(1000L)),
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
