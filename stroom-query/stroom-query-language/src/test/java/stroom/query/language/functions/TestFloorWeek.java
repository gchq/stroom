package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

public class TestFloorWeek extends AbstractFunctionTest<FloorWeek> {

    @Override
    Class<FloorWeek> getFunctionType() {
        return FloorWeek.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2025, 4, 10, 10, 30, 30)
                .toInstant(ZoneOffset.UTC);

        // April 7th 2025 is the Monday
        final Instant floored = LocalDateTime.of(2025, 4, 7, 0, 0)
                .toInstant(ZoneOffset.UTC);

        return Stream.of(
                TestCase.of(
                        "long date",
                        ValDate.create(floored.toEpochMilli()),
                        ValLong.create(time.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValDate.create(floored.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(time.toEpochMilli())))
        );
    }
}
