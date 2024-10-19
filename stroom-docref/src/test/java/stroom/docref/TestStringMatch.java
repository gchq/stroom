package stroom.docref;

import stroom.test.common.TestUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestStringMatch {

    @TestFactory
    Stream<DynamicTest> testIsCaseSensitive() {
        final String pattern = "foo";

        return TestUtil.buildDynamicTestStream()
                .withInputType(StringMatch.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(StringMatch::isCaseSensitive)
                .withAssertions(outcome -> {
                    final StringMatch stringMatch = outcome.getInput();
                    Assertions.assertThat(outcome.getActualOutput())
                            .isEqualTo(outcome.getExpectedOutput());
                    Assertions.assertThat(stringMatch.getPattern())
                            .isEqualTo(pattern);
                })

                .addCase(StringMatch.equals(pattern), true)
                .addCase(StringMatch.equals(pattern, false), false)
                .addCase(StringMatch.equalsIgnoreCase(pattern), false)

                .addCase(StringMatch.notEquals(pattern), true)
                .addCase(StringMatch.notEquals(pattern, false), false)
                .addCase(StringMatch.notEqualsIgnoreCase(pattern), false)

                .addCase(StringMatch.contains(pattern), true)
                .addCase(StringMatch.contains(pattern, false), false)
                .addCase(StringMatch.containsIgnoreCase(pattern), false)

                .addCase(StringMatch.regex(pattern), true)
                .addCase(StringMatch.regex(pattern, false), false)
                .addCase(StringMatch.regexIgnoreCase(pattern), false)

                .build();
    }
}
