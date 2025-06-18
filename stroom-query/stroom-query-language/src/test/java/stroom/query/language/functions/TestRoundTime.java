package stroom.query.language.functions;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

public class TestRoundTime extends AbstractFunctionTest<RoundTime> {

    @Override
    Class<RoundTime> getFunctionType() {
        return RoundTime.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final LocalDateTime input = LocalDateTime.of(2025, 4, 7, 10, 44, 30, 550_000_000);
        final long inputMillis = input.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime rounded = LocalDateTime.of(2025, 4, 7, 10, 50, 0);
        final long expectedMillis = rounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime inputWithZone = LocalDateTime.of(2025, 4, 7, 10, 44, 30, 550_000_000);
        final long inputMillisWithZone = inputWithZone.atZone(ZoneOffset.ofHours(2)).toInstant().toEpochMilli();
        final LocalDateTime roundedWithZone = LocalDateTime.of(2025, 4, 7, 10, 50, 0);
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
        final LocalDateTime secondsRounded = LocalDateTime.of(2025, 4, 7, 10, 0, 15);
        final long secondsExpectedMillis = secondsRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime exactInput = LocalDateTime.of(2025, 4, 7, 10, 30, 0);
        final long exactInputMillis = exactInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime exactRounded = LocalDateTime.of(2025, 4, 7, 10, 30, 0);
        final long exactExpectedMillis = exactRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime beforeInput = LocalDateTime.of(2025, 4, 7, 10, 29, 59, 999_000_000);
        final long beforeInputMillis = beforeInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime beforeRounded = LocalDateTime.of(2025, 4, 7, 10, 30, 0);
        final long beforeExpectedMillis = beforeRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final LocalDateTime crossDayInput = LocalDateTime.of(2025, 4, 7, 23, 51, 0);
        final long crossDayInputMillis = crossDayInput.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final LocalDateTime crossDayRounded = LocalDateTime.of(2025, 4, 7, 23, 55, 0);
        final long crossDayExpectedMillis = crossDayRounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        return Stream.of(
                TestCase.of(
                        "Round time with valid inputs",
                        ValLong.create(expectedMillis),
                        ValLong.create(inputMillis),
                        ValString.create("PT15M"),       //duration
                        ValString.create("PT5M")        //offset
                ),
                TestCase.of(
                        "Round time with invalid duration",
                        ValErr.create("Invalid duration format: INVALID"),
                        ValLong.create(inputMillis),
                        ValString.create("INVALID"),
                        ValString.create("PT1M")
                ),
                TestCase.of(
                        "Round time with invalid offset",
                        ValErr.create("Invalid offset format: INVALID"),
                        ValLong.create(inputMillis),
                        ValString.create("PT5M"),
                        ValString.create("INVALID")
                ),
                TestCase.of(
                        "Round time with timezone offset",
                        ValLong.create(expectedMillisWithZone),
                        ValLong.create(inputMillisWithZone),
                        ValString.create("PT15M"),
                        ValString.create("PT5M")
                ),
                TestCase.of(
                        "Round to hourly with zero offset",
                        ValLong.create(hourlyExpectedMillis),
                        ValLong.create(hourlyInputMillis),
                        ValString.create("PT1H"),
                        ValString.create("PT0M")
                ),
                TestCase.of(
                        "Round to daily with offset",
                        ValLong.create(dailyExpectedMillis),
                        ValLong.create(dailyInputMillis),
                        ValString.create("P1D"),
                        ValString.create("PT0M")
                ),
                TestCase.of(
                        "Round to nearest 30 seconds",
                        ValLong.create(secondsExpectedMillis),
                        ValLong.create(secondsInputMillis),
                        ValString.create("PT15S"),
                        ValString.create("PT0S")
                ),
                TestCase.of(
                        "Null input value",
                        ValNull.INSTANCE,
                        ValNull.INSTANCE,
                        ValString.create("PT15M"),
                        ValString.create("PT5M")
                ),
                TestCase.of(
                        "Exactly on boundary",
                        ValLong.create(exactExpectedMillis),
                        ValLong.create(exactInputMillis),
                        ValString.create("PT30M"),
                        ValString.create("PT0M")
                ),
                TestCase.of(
                        "Just before boundary",
                        ValLong.create(beforeExpectedMillis),
                        ValLong.create(beforeInputMillis),
                        ValString.create("PT30M"),
                        ValString.create("PT0M")
                ),
                TestCase.of(
                        "Cross day boundary",
                        ValLong.create(crossDayExpectedMillis),
                        ValLong.create(crossDayInputMillis),
                        ValString.create("PT15M"),
                        ValString.create("PT10M")
                )
        );
    }
}
