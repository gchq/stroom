package stroom.query.language.functions;

import stroom.test.common.TestUtil;
import stroom.util.date.DateUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestFormatterCache {

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(
                        String.class,
                        String.class,
                        String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final long epochMs = FormatterCache.parse(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3);
                    return DateUtil.createNormalDateTimeString(epochMs);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("2010-01-01T23:59:59.123Z", null, null), "2010-01-01T23:59:59.123Z")
                .addCase(Tuple.of("2010-01-01T23:59:59.1+02:00", null, null), "2010-01-01T21:59:59.100Z")
                TODO - finish me
                .build();
    }
}
