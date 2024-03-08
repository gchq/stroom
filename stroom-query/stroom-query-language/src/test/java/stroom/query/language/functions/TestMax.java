package stroom.query.language.functions;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

class TestMax extends AbstractFunctionTest<Max> {

    @Override
    Class<Max> getFunctionType() {
        return Max.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "basic int",
                        ValInteger.create(100),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.ofAggregate(
                        "aggregate int",
                        ValInteger.create(100),
                        ValInteger.create(1),
                        ValInteger.create(100),
                        ValInteger.create(10)),
                TestCase.of(
                        "basic double",
                        ValDouble.create(1.3),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.ofAggregate(
                        "aggregate double",
                        ValDouble.create(1.3),
                        ValDouble.create(1.3),
                        ValDouble.create(1.2),
                        ValDouble.create(-3.3)),
                TestCase.of(
                        "basic durations",
                        Val.create(Duration.ofDays(2)),
                        Val.create(Duration.ofDays(2)),
                        Val.create(Duration.ofSeconds(1)),
                        Val.create(Duration.ofHours(3))),
                TestCase.of(
                        "basic dates",
                        Val.create(Instant.ofEpochMilli(3)),
                        Val.create(Instant.ofEpochMilli(2)),
                        Val.create(Instant.ofEpochMilli(1)),
                        Val.create(Instant.ofEpochMilli(3)))
        );
    }
}
