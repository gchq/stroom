package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TestCeilingMonth extends AbstractFunctionTest<CeilingMonth> {

    @Override
    Class<CeilingMonth> getFunctionType() {
        return CeilingMonth.class;
    }

    @Override
    protected Supplier<CeilingMonth> getFunctionSupplier() {
        return () -> new CeilingMonth("ceilingMonth", new ExpressionContext());
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 7, 10, 30, 30)
                .toInstant(ZoneOffset.UTC);

        // first day of month ~= the floor of the month
        final Instant truncated = LocalDateTime.ofInstant(time, ZoneOffset.UTC)
                .withMonth(5)
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC);

        final ZoneId newYorkZone = ZoneId.of("America/New_York");
        final Instant timeNY = LocalDateTime.of(2025, 4, 7, 1, 30, 30)
                .atZone(newYorkZone)
                .toInstant();
        final Instant truncatedNY = LocalDateTime.of(2025, 4, 30, 20, 0, 0)
                .atZone(newYorkZone)
                .toInstant();

        final ZoneId tokyoZone = ZoneId.of("Asia/Tokyo");
        final Instant timeTokyo = LocalDateTime.of(2025, 4, 7, 10, 30, 30)
                .atZone(tokyoZone)
                .toInstant();
        final Instant truncatedTokyo = LocalDateTime.of(2025, 5, 1, 9, 0, 0)
                .atZone(tokyoZone)
                .toInstant();

        return Stream.of(
                TestCase.of(
                        "long date",
                        ValDate.create(truncated.toEpochMilli()),
                        ValLong.create(time.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValDate.create(truncated.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(time.toEpochMilli()))),
                TestCase.of(
                        "long date New York",
                        ValDate.create(truncatedNY.toEpochMilli()),
                        ValLong.create(timeNY.toEpochMilli())),

                TestCase.of(
                        "long date Tokyo",
                        ValDate.create(truncatedTokyo.toEpochMilli()),
                        ValLong.create(timeTokyo.toEpochMilli()))
        );
    }
}
