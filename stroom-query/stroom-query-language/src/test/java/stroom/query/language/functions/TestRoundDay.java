package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

class TestRoundDay extends AbstractFunctionTest<RoundDay> {

    @Override
    Class<RoundDay> getFunctionType() {
        return RoundDay.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 7, 12, 0, 1)
                .toInstant(ZoneOffset.UTC);

        final Instant truncatedUp = time.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);

        final Instant timeT = LocalDateTime.of(2025, 4, 7, 12, 0, 0)
                .toInstant(ZoneOffset.UTC);

        final Instant truncated = time.truncatedTo(ChronoUnit.DAYS);

        final Instant timeWithZone = LocalDateTime.of(2025, 4, 7, 10, 0, 1)
                .toInstant(ZoneOffset.ofHours(-2));

        final Instant truncatedUpWithZone = timeWithZone.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);

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
                        "long date down",
                        ValDate.create(truncated.toEpochMilli()),
                        ValLong.create(timeT.toEpochMilli())),
                TestCase.of(
                        "string date down",
                        ValDate.create(truncated.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(timeT.toEpochMilli()))),
                TestCase.of(
                        "long date with timezone",
                        ValDate.create(truncatedUpWithZone.toEpochMilli()),
                        ValLong.create(timeWithZone.toEpochMilli()))
        );
    }


}
