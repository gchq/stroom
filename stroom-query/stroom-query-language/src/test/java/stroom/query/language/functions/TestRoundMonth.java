package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.stream.Stream;

public class TestRoundMonth extends AbstractFunctionTest<RoundMonth> {

    @Override
    Class<RoundMonth> getFunctionType() { return RoundMonth.class; }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 16, 12, 31, 0)
                .toInstant(ZoneOffset.UTC);

        // first day of month ~= the floor of the month
        final Instant truncatedUp = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
                .withMonth(5)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        final Instant timeT = LocalDateTime.of(2025, 4, 14, 0, 0, 0)
                .toInstant(ZoneOffset.UTC);

        // first day of month ~= the floor of the month
        final Instant truncated = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
                .withMonth(4)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        // Test with a specific timezone
        final ZoneId zoneId = ZoneId.of("America/New_York");
        final Instant timeWithZone = LocalDateTime.of(2025, 4, 16, 12, 31, 0)
                .atZone(zoneId)
                .toInstant();

        final Instant truncatedWithZone = LocalDateTime.ofInstant(timeWithZone, zoneId)
                .withMonth(5)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        return Stream.of(
                TestCase.of(
                        "long date",
                        ValDate.create(truncatedUp.toEpochMilli()),
                        ValLong.create(time.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValDate.create(truncatedUp.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(time.toEpochMilli()))),
                TestCase.of(
                        "long date",
                        ValDate.create(truncated.toEpochMilli()),
                        ValLong.create(timeT.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValDate.create(truncated.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(timeT.toEpochMilli()))),
                TestCase.of(
                        "timezone test",
                        ValDate.create(truncatedWithZone.toEpochMilli()),
                        ValLong.create(timeWithZone.toEpochMilli()))
        );
    }

}
