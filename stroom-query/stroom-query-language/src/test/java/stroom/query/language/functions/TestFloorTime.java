package stroom.query.language.functions;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

public class TestFloorTime extends AbstractFunctionTest<FloorTime> {

    @Override
    Class<FloorTime> getFunctionType() {
        return FloorTime.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final LocalDateTime input = LocalDateTime.of(2025, 4, 7, 10, 44, 30, 550_000_000);
        final long inputMillis = input.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime rounded = LocalDateTime.of(2025, 4, 7, 10, 30, 0);
        final long expectedMillis = rounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime inputWithZone = LocalDateTime.of(2025, 4, 7, 10, 44, 30, 550_000_000);
        final long inputMillisWithZone = inputWithZone.atZone(ZoneOffset.ofHours(2)).toInstant().toEpochMilli();
        final LocalDateTime roundedWithZone = LocalDateTime.of(2025, 4, 7, 10, 30, 0);
        final long expectedMillisWithZone = roundedWithZone.atZone(ZoneOffset.ofHours(2)).toInstant().toEpochMilli();

        final LocalDateTime hourlyInput = LocalDateTime.of(2025, 4, 7, 10, 29, 30);
        final long hourlyInputMillis = hourlyInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime hourlyRounded = LocalDateTime.of(2025, 4, 7, 10, 0, 0);
        final long hourlyExpectedMillis = hourlyRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime dailyInput = LocalDateTime.of(2025, 4, 7, 11, 44, 30);
        final long dailyInputMillis = dailyInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime dailyRounded = LocalDateTime.of(2025, 4, 7, 0, 0, 0);
        final long dailyExpectedMillis = dailyRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime secondsInput = LocalDateTime.of(2025, 4, 7, 10, 0, 14, 750_000_000);
        final long secondsInputMillis = secondsInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime secondsRounded = LocalDateTime.of(2025, 4, 7, 10, 0, 0);
        final long secondsExpectedMillis = secondsRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime exactInput = LocalDateTime.of(2025, 4, 7, 10, 30, 0);
        final long exactInputMillis = exactInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime exactRounded = LocalDateTime.of(2025, 4, 7, 10, 30, 0);
        final long exactExpectedMillis = exactRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime beforeInput = LocalDateTime.of(2025, 4, 7, 10, 29, 59, 999_000_000);
        final long beforeInputMillis = beforeInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime beforeRounded = LocalDateTime.of(2025, 4, 7, 10, 0, 0);
        final long beforeExpectedMillis = beforeRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime crossDayInput = LocalDateTime.of(2025, 4, 7, 23, 55, 0);
        final long crossDayInputMillis = crossDayInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime crossDayRounded = LocalDateTime.of(2025, 4, 7, 23, 45, 0);
        final long crossDayExpectedMillis = crossDayRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        return Stream.of(
                TestCase.of(
                        "Floor time with valid inputs",
                        ValDate.create(expectedMillis),
                        ValDate.create(inputMillis),
                        ValString.create("PT15M")       //duration
                ),
                TestCase.of(
                        "Floor time with invalid duration",
                        ValErr.create("Invalid duration format: INVALID"),
                        ValDate.create(inputMillis),
                        ValString.create("INVALID")
                ),
                TestCase.of(
                        "Floor to 15 minutes",
                        ValDate.create(expectedMillisWithZone),
                        ValDate.create(inputMillisWithZone),
                        ValString.create("PT15M")
                ),
                TestCase.of(
                        "Floor to hourly",
                        ValDate.create(hourlyExpectedMillis),
                        ValDate.create(hourlyInputMillis),
                        ValString.create("PT1H")
                ),
                TestCase.of(
                        "Floor to daily",
                        ValDate.create(dailyExpectedMillis),
                        ValDate.create(dailyInputMillis),
                        ValString.create("P1D")
                ),
                TestCase.of(
                        "Floor to 15 seconds",
                        ValDate.create(secondsExpectedMillis),
                        ValDate.create(secondsInputMillis),
                        ValString.create("PT15S")
                ),
                TestCase.of(
                        "Null input value",
                        ValNull.INSTANCE,
                        ValNull.INSTANCE,
                        ValString.create("PT15M")
                ),
                TestCase.of(
                        "Exactly on boundary",
                        ValDate.create(exactExpectedMillis),
                        ValDate.create(exactInputMillis),
                        ValString.create("PT30M")
                ),
                TestCase.of(
                        "Just before boundary",
                        ValDate.create(beforeExpectedMillis),
                        ValDate.create(beforeInputMillis),
                        ValString.create("PT30M")
                ),
                TestCase.of(
                        "Cross day boundary",
                        ValDate.create(crossDayExpectedMillis),
                        ValDate.create(crossDayInputMillis),
                        ValString.create("PT15M")
                )
        );
    }
}
