package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

class TestRoundMinute extends AbstractFunctionTest<RoundMinute> {

    @Override
    Class<RoundMinute> getFunctionType() {
        return RoundMinute.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 7, 10, 30, 31)
                .toInstant(ZoneOffset.UTC);

        final Instant truncatedUp = time.truncatedTo(ChronoUnit.MINUTES)
                .plus(1, ChronoUnit.MINUTES);

        final Instant timeT = LocalDateTime.of(2025, 4, 7, 10, 30, 30)
                .toInstant(ZoneOffset.UTC);

        final Instant truncated = time.truncatedTo(ChronoUnit.MINUTES);

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
                        ValString.create(DateUtil.createNormalDateTimeString(timeT.toEpochMilli())))
        );
    }
}
