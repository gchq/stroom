package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

class TestIsWeekend extends AbstractFunctionTest<IsWeekend> {

    @Override
    Class<IsWeekend> getFunctionType() {
        return IsWeekend.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant weekday = LocalDateTime.of(2025, 5, 8, 15, 30, 55)
                .toInstant(ZoneOffset.UTC);
        final Instant weekend = LocalDateTime.of(2025, 5, 11, 15, 30, 55)
                .toInstant(ZoneOffset.UTC);

        return Stream.of(
                TestCase.of(
                        "long date",
                        ValBoolean.create(false),
                        ValDate.create(weekday.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValBoolean.create(true),
                        ValDate.create(weekend.toEpochMilli()
        )));
    }
}
