package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import java.util.function.Supplier;

public class TestFloorYear extends AbstractFunctionTest<FloorYear> {

    @Override
    Class<FloorYear> getFunctionType() {
        return FloorYear.class;
    }

    @Override
    protected Supplier<FloorYear> getFunctionSupplier() {
        return () -> new FloorYear("floorYear", new ExpressionContext());
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant timeUTC = LocalDateTime.of(2025, 4, 7, 10, 30, 30)
                .toInstant(ZoneOffset.UTC);
        final Instant truncatedUTC = LocalDateTime.ofInstant(timeUTC, ZoneOffset.UTC)
                .withMonth(1)
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
        final Instant truncatedNY = LocalDateTime.of(2024, 12, 31, 19, 0, 0)
                .atZone(newYorkZone)
                .toInstant();

        final ZoneId tokyoZone = ZoneId.of("Asia/Tokyo");
        final Instant timeTokyo = LocalDateTime.of(2025, 4, 7, 10, 30, 30)
                .atZone(tokyoZone)
                .toInstant();
        final Instant truncatedTokyo = LocalDateTime.of(2025, 1, 1, 9, 0, 0)
                .atZone(tokyoZone)
                .toInstant();


        return Stream.of(
                TestCase.of(
                        "long date UTC",
                        ValDate.create(truncatedUTC.toEpochMilli()),
                        ValLong.create(timeUTC.toEpochMilli())),
                TestCase.of(
                        "string date UTC",
                        ValDate.create(truncatedUTC.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(timeUTC.toEpochMilli()))),

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