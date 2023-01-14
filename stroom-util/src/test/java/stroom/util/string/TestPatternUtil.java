package stroom.util.string;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestPatternUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPatternUtil.class);

    @TestFactory
    Stream<DynamicTest> createPatternFromWildCardFilter() {
        final List<String> months = Arrays.stream(Month.values())
                .map(month -> month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .collect(Collectors.toList());

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {})
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    // Case sensitive
                    final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(filter, true);

                    return months.stream()
                            .filter(pattern.asPredicate())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(null, NullPointerException.class)
                .addCase("", Collections.emptyList())
                .addCase("May", List.of("May"))
                .addCase("Jan*", List.of("January"))
                .addCase("Ma*", List.of("March", "May"))
                .addCase("*ry", List.of("January", "February"))
                .addCase("*e*emb*", List.of("September", "December"))
                .addCase("*a*", List.of("January", "February", "March", "May"))
                .addCase("a", Collections.emptyList())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> createPatternFromWildCardFilter_caseInsensitive() {
        final List<String> months = Arrays.stream(Month.values())
                .map(month -> month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .collect(Collectors.toList());

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {})
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(
                            filter, true,  false);

                    return months.stream()
                            .filter(pattern.asPredicate())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(null, NullPointerException.class)
                .addCase("", Collections.emptyList())
                .addCase("MAY", List.of("May"))
                .addCase("JAN*", List.of("January"))
                .addCase("ma*", List.of("March", "May"))
                .addCase("*Ry", List.of("January", "February"))
                .addCase("*e*emB*", List.of("September", "December"))
                .addCase("*a*", List.of("January", "February", "March", "April", "May", "August"))
                .addCase("a", Collections.emptyList())
                .addCase("m*", List.of("March", "May"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> createPatternFromWildCardFilter_funkyChars() {

        final String val1 = "/some/path/file.txt";
        final String val2 = "http://some.domain:1234/some/path?x=1&y=2";
        final String val3 = "C:\\some\\path\\file.txt";
        final String val4 = "[abcd]{1}"; // meant to look like regex but is not

        final List<String> allValues = List.of(val1, val2, val3, val4);

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {})
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(filter, true);

                    LOGGER.info("filter: '{}', pattern: '{}'", filter, pattern);

                    return allValues.stream()
                            .filter(pattern.asMatchPredicate())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase("/*/*/file.txt", List.of(val1))
                .addCase("*\\*\\*\\file.txt", List.of(val3))
                .addCase("[abcd]{*}", List.of(val4))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> createSqlLikeStringFromWildCardFilter() {

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String filter = testCase.getInput();
                    return PatternUtil.createSqlLikeStringFromWildCardFilter(filter);
                })
                .withSimpleEqualityAssertion()
                .addThrowsCase(null, NullPointerException.class)
                .addCase("", "")
                .addCase("May", "May")
                .addCase("Jan*", "Jan%")
                .addCase("*ry", "%ry")
                .addCase("*e*emb*", "%e%emb%")
                .addCase("*a*", "%a%")
                .addCase("a", "a")
                .addCase("%", "\\%")
                .addCase("%_%_", "\\%\\_\\%\\_")
                .addCase("_", "\\_")
                .build();
    }
}
