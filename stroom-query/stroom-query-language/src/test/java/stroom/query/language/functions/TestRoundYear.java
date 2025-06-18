package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

public class TestRoundYear extends AbstractFunctionTest<RoundYear> {

    @Override
    Class<RoundYear> getFunctionType() { return RoundYear.class; }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 7, 1, 0, 0, 1)
                .toInstant(ZoneOffset.UTC);

        final Instant truncatedUp = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
                .plusYears(1)
                .withMonth(1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        final Instant timeT = LocalDateTime.of(2025, 7, 1, 0, 0, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant truncated = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
                .withMonth(1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        final Instant timeWithZone = LocalDateTime.of(2025, 7, 1, 0, 0, 1)
                .toInstant(ZoneOffset.ofHours(-2));

        final Instant truncatedUpWithZone = LocalDateTime.ofInstant(timeWithZone, ZoneOffset.ofHours(2))
                .plusYears(1)
                .withMonth(1)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.ofHours(0));

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
                        "long date with timezone",
                        ValDate.create(truncatedUpWithZone.toEpochMilli()),
                        ValLong.create(timeWithZone.toEpochMilli()))
        );
    }

}
