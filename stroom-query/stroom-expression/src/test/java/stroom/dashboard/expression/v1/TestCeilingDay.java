package stroom.dashboard.expression.v1;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

class TestCeilingDay extends AbstractFunctionTest<CeilingDay> {

    @Override
    Class<CeilingDay> getFunctionType() {
        return CeilingDay.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final Instant time = LocalDateTime.of(2021, 1,20,6,30, 55)
                .toInstant(ZoneOffset.UTC);

        final Instant truncated = time.truncatedTo(ChronoUnit.DAYS)
                .plus(1, ChronoUnit.DAYS);

        return Stream.of(
                TestCase.of(
                        "long date",
                        ValLong.create(truncated.toEpochMilli()),
                        ValLong.create(time.toEpochMilli())),
                TestCase.of(
                        "string date",
                        ValLong.create(truncated.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(time.toEpochMilli())))
        );
    }
}
