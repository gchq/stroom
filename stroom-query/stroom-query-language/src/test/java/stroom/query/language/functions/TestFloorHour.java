package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TestFloorHour extends AbstractFunctionTest<FloorHour> {

    @Override
    Class<FloorHour> getFunctionType() {
        return FloorHour.class;
    }

    @Override
    protected Supplier<FloorHour> getFunctionSupplier() {
        return () -> new FloorHour(new ExpressionContext(), "floorHour");
    }


    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 7, 10, 30, 30)
                .toInstant(ZoneOffset.UTC);

        final Instant truncated = time.truncatedTo(ChronoUnit.HOURS);


        final ZoneId newYorkZone = ZoneId.of("America/New_York");
        final Instant timeNY = LocalDateTime.of(2025, 4, 7, 1, 30, 30)
                .atZone(newYorkZone)
                .toInstant();
        final Instant truncatedNY = LocalDateTime.of(2025, 4, 7, 1, 0, 0)
                .atZone(newYorkZone)
                .toInstant();

        final ZoneId tokyoZone = ZoneId.of("Asia/Tokyo");
        final Instant timeTokyo = LocalDateTime.of(2025, 4, 7, 10, 30, 30)
                .atZone(tokyoZone)
                .toInstant();
        final Instant truncatedTokyo = LocalDateTime.of(2025, 4, 7, 10, 0, 0)
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
