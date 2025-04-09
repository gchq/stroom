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
        final LocalDateTime input = LocalDateTime.of(2025, 4, 7, 10, 46, 30, 550_000_000);
        final long inputMillis = input.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        //expected rounded time millis
        final LocalDateTime rounded = LocalDateTime.of(2025, 4, 7, 10, 45, 0);
        final long expectedMillis = rounded.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

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
                )
        );
    }
}