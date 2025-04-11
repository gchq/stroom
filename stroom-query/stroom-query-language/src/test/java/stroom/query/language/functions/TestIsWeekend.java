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
        final Instant weekdayUTC = LocalDateTime.of(2025, 5, 8, 15, 30, 55)
                .toInstant(ZoneOffset.UTC);
        final Instant weekendUTC = LocalDateTime.of(2025, 5, 11, 15, 30, 55)
                .toInstant(ZoneOffset.UTC);

        //test for UTC+5
        final Instant weekdayUTCPlus5 = LocalDateTime.of(2025, 5, 8, 15, 30, 55)
                .toInstant(ZoneOffset.ofHours(5));
        final Instant weekendUTCPlus5 = LocalDateTime.of(2025, 5, 11, 15, 30, 55)
                .toInstant(ZoneOffset.ofHours(5));

        return Stream.of(
                TestCase.of(
                        "long date UTC",
                        ValBoolean.create(false),
                        ValDate.create(weekdayUTC.toEpochMilli())),
                TestCase.of(
                        "string date UTC",
                        ValBoolean.create(true),
                        ValDate.create(weekendUTC.toEpochMilli())),
                TestCase.of(
                        "long date UTC+5",
                        ValBoolean.create(false),
                        ValDate.create(weekdayUTCPlus5.toEpochMilli())),
                TestCase.of(
                        "string date UTC+5",
                        ValBoolean.create(true),
                        ValDate.create(weekendUTCPlus5.toEpochMilli()))
        );
    }
}
