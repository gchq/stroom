package stroom.query.language.functions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

public class TestParseDate extends AbstractFunctionTest<ParseDate> {

    @Override
    Class<ParseDate> getFunctionType() {
        return ParseDate.class;
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
                        "0 milli",
                        Val.create(Instant.parse("2010-01-01T23:59:59Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59Z")),
                TestCase.of(
                        "1 milli",
                        Val.create(Instant.parse("2010-01-01T23:59:59.1Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.1Z")),
                TestCase.of(
                        "2 milli",
                        Val.create(Instant.parse("2010-01-01T23:59:59.12Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.12Z")),
                TestCase.of(
                        "3 milli",
                        Val.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123Z")),
                TestCase.of(
                        "4 milli",
                        Val.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.1234Z")),
                TestCase.of(
                        "+0000 offset",
                        Val.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+0000")),
                TestCase.of(
                        "+0200 offset",
                        Val.create(Instant.parse("2010-01-01T21:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+0200")),
                TestCase.of(
                        "+00:00 offset",
                        Val.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+00:00")),
                TestCase.of(
                        "+02:00 offset",
                        Val.create(Instant.parse("2010-01-01T21:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+02:00")),


                TestCase.of(
                        "3 milli + format",
                        Val.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("20100101 235959123Z"),
                        ValString.create("yyyyMMdd HHmmssSSSXX")),

                TestCase.of(
                        "3 milli + format + zone",
                        Val.create(Instant.parse("2010-01-01T23:59:59.123Z")
                                .minus(2, ChronoUnit.HOURS)
                                .toEpochMilli()),
                        Val.create("20100101 235959123"),
                        ValString.create("yyyyMMdd HHmmssSSS"),
                        ValString.create("GMT+2"))
        );
    }
}
