package stroom.util;

import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestPredicateUtil {

    private static final Predicate<String> APPLE = "apple"::equalsIgnoreCase;
    private static final Predicate<String> ORANGE = "orange"::equalsIgnoreCase;
    private static final Predicate<String> PEAR = "pear"::equalsIgnoreCase;

    private static final List<String> ALL_FRUITS = List.of(
            "apple",
            "orange",
            "pear",
            "banana",
            "kiwi"
    );

    @TestFactory
    Stream<DynamicTest> andPredicates() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<Predicate<String>>>() {
                })
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final Predicate<String> predicate = PredicateUtil.andPredicates(
                            testCase.getInput(), val -> true);
                    return ALL_FRUITS.stream()
                            .filter(predicate)
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, ALL_FRUITS)
                .addCase(Collections.emptyList(), ALL_FRUITS)
                .addNamedCase("apple", List.of(APPLE), List.of("apple"))
                .addNamedCase("apple+orange", List.of(APPLE, ORANGE), Collections.emptyList())
                .addNamedCase(
                        "2 predicates",
                        List.of(v -> v.endsWith("e"),
                                v -> v.contains("p")
                        ),
                        List.of("apple"))
                .addNamedCase(
                        "3 predicates",
                        List.of(v -> v.contains("e"),
                                v -> v.contains("a"),
                                v -> v.contains("p")
                        ),
                        List.of("apple", "pear"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> orPredicates() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<Predicate<String>>>() {
                })
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase -> {
                    final Predicate<String> predicate = PredicateUtil.orPredicates(
                            testCase.getInput(), val -> true);
                    return ALL_FRUITS.stream()
                            .filter(predicate)
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, ALL_FRUITS)
                .addCase(Collections.emptyList(), ALL_FRUITS)
                .addCase(Collections.emptyList(), ALL_FRUITS)
                .addCase(List.of(APPLE), List.of("apple"))
                .addCase(List.of(APPLE, ORANGE), List.of("apple", "orange"))
                .addCase(
                        List.of(v -> v.endsWith("e"),
                                v -> v.contains("k")
                        ),
                        List.of("apple", "orange", "kiwi"))
                .addCase(
                        List.of(v -> v.contains("e"),
                                v -> v.contains("a"),
                                v -> v.contains("p")
                        ),
                        List.of("apple", "orange", "pear", "banana"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCaseSensitiveContainsPredicate() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    var str = testCase.getInput()._1;
                    var subStr = testCase.getInput()._2;
                    final Predicate<String> predicate = PredicateUtil.caseSensitiveContainsPredicate(subStr);
                    return predicate.test(str);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of("foorbar", null), false)
                .addCase(Tuple.of(null, "foobar"), false)
                .addCase(Tuple.of("foobar", "foo"), true)
                .addCase(Tuple.of("foobar", "ob"), true)
                .addCase(Tuple.of("foobar", "foobar"), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCaseInsensitiveContainsPredicate() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    var str = testCase.getInput()._1;
                    var subStr = testCase.getInput()._2;
                    final Predicate<String> predicate = PredicateUtil.caseInsensitiveContainsPredicate(subStr);
                    return predicate.test(str);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of("FOORBAR", null), false)
                .addCase(Tuple.of(null, "foobar"), false)
                .addCase(Tuple.of("FOOBAR", "foo"), true)
                .addCase(Tuple.of("FOOBAR", "ob"), true)
                .addCase(Tuple.of("FOOBAR", "foobar"), true)
                .build();
    }
}
