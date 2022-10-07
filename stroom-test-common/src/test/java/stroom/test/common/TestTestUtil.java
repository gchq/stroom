package stroom.test.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.stream.Stream;

class TestTestUtil {

    @TestFactory
    Stream<DynamicTest> buildDynamicTestStream() {
        return TestUtil.buildDynamicTestStream(String.class)
                .withTest(testCase -> {
                    final String output = testCase.getInput().toUpperCase();
                    Assertions.assertThat(output)
                            .isEqualTo(testCase.getExpectedOutput());
                })
                .addCase("a", "A")
                .addCase("z", "Z")
                .build();
    }


    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream() {
        return TestUtil.buildDynamicTestStream(Integer.class, String.class)
                .withTest(testCase -> {

                    final String output = Month.of(testCase.getInput()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                    Assertions.assertThat(output)
                            .isEqualTo(testCase.getExpectedOutput());
                })
                .addCase(1, "January")
                .addCase(12, "December")
                .build();
    }
}
