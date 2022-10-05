package stroom.test.common;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class TestTestUtil {

    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_sameInputAsOutput() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestAction(testCase -> {
                    final String output = testCase.getInput().toUpperCase();
                    Assertions.assertThat(output)
                            .isEqualTo(testCase.getExpectedOutput());
                })
                .addCase("a", "A")
                .addCase("z", "Z")
                .build();
    }


    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_simpleTypes() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Integer.class)
                .withOutputType(String.class)
                .withTestAction(testCase -> {

                    final String output = Month.of(testCase.getInput()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                    Assertions.assertThat(output)
                            .isEqualTo(testCase.getExpectedOutput());
                })
                .addCase(1, "January")
                .addCase(12, "December")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testBuildDynamicTestStream_collection() {

        final List<String> months = IntStream.rangeClosed(1, 12)
                .boxed()
                .map(i ->
                        Month.of(i).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .collect(Collectors.toList());

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Integer.class, Integer.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>(){})
                .withTestAction(testCase -> {
                    final List<String> subList = months.subList(testCase.getInput()._1, testCase.getInput()._2);
                    Assertions.assertThat(subList)
                            .containsExactlyElementsOf(testCase.getExpectedOutput());
                })
                .addCase(Tuple.of(0, 2), List.of("January", "February"))
                .build();
    }
}
