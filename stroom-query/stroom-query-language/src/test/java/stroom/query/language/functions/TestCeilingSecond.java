package stroom.query.language.functions;

import net.bytebuddy.asm.Advice.Local;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

public class TestCeilingSecond extends AbstractFunctionTest<CeilingSecond> {

    @Override
    Class<CeilingSecond> getFunctionType() {
        return CeilingSecond.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        final LocalDateTime input = LocalDateTime.of(2025, 4, 7, 10, 30, 30, 550_000_000);
        final LocalDateTime floored = input.withNano(0).plusSeconds(1);

        final long inputMillis = input.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        final long expectedMillis = floored.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();

        final String formattedInput = DateUtil.createNormalDateTimeString(inputMillis);

        return Stream.of(
                TestCase.of(
                        "string date (formatted)",
                        ValDate.create(expectedMillis),
                        ValString.create(formattedInput)
                )
        );
    }
}
