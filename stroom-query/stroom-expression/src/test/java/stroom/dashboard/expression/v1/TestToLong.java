package stroom.dashboard.expression.v1;

import java.time.Instant;
import java.util.stream.Stream;

class TestToLong extends AbstractFunctionTest<ToLong> {

    @Override
    Class<ToLong> getFunctionType() {
        return ToLong.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        Instant now = Instant.now();

        return Stream.of(
                TestCase.of(
                        "string",
                        ValLong.create(100),
                        ValString.create("100")),
                TestCase.of(
                        "date",
                        ValLong.create(now.toEpochMilli()),
                        ValString.create(DateUtil.createNormalDateTimeString(now.toEpochMilli()))),
                TestCase.of(
                        "double",
                        ValLong.create(100),
                        ValDouble.create(100.123))
        );
    }
}