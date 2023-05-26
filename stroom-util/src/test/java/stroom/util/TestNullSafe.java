package stroom.util;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import org.apache.commons.lang3.mutable.MutableLong;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

class TestNullSafe {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestNullSafe.class);

    private final Level1 nullLevel1 = null;
    private final Level1 nonNullLevel1 = new Level1();
    private final long other = 99L;

    private long getOther() {
        return other;
    }

    @Test
    void testEquals1() {
        // Null parent
        Assertions.assertThat(NullSafe.equals(nullLevel1, Level1::getNonNullLevel2, nonNullLevel1.getNonNullLevel2()))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1, Level1::getNullLevel2, nonNullLevel1.getNonNullLevel2()))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1, Level1::getNonNullLevel2, "foobar"))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1, Level1::getNonNullLevel2, null))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1,
                        Level1::getNonNullLevel2,
                        nonNullLevel1.getNonNullLevel2()))
                .isTrue();
    }

    @Test
    void testEquals2() {
        // Null parent
        Assertions.assertThat(NullSafe.equals(
                        nullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getNonNullLevel3,
                        nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNullLevel3,
                        nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        "foobar"))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        null))
                .isFalse();
        Assertions.assertThat(NullSafe.equals(nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isTrue();
    }

    @TestFactory
    Stream<DynamicTest> testCoalesce_twoValues() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withWrappedOutputType(new TypeLiteral<Optional<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.coalesce(testCase.getInput()._1, testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), Optional.empty())
                .addCase(Tuple.of("foo", null), Optional.of("foo"))
                .addCase(Tuple.of(null, "foo"), Optional.of("foo"))
                .addCase(Tuple.of("foo", "bar"), Optional.of("foo"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCoalesce_threeValues() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class, String.class)
                .withWrappedOutputType(new TypeLiteral<Optional<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.coalesce(
                                testCase.getInput()._1,
                                testCase.getInput()._2,
                                testCase.getInput()._3))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null, null), Optional.empty())
                .addCase(Tuple.of("foo", null, null), Optional.of("foo"))
                .addCase(Tuple.of(null, "foo", null), Optional.of("foo"))
                .addCase(Tuple.of(null, null, "foo"), Optional.of("foo"))
                .addCase(Tuple.of("one", "two", "three"), Optional.of("one"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCoalesce_fourValues() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple4<String, String, String, String>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Optional<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.coalesce(
                                testCase.getInput()._1,
                                testCase.getInput()._2,
                                testCase.getInput()._3,
                                testCase.getInput()._4))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null, null, null), Optional.empty())
                .addCase(Tuple.of("foo", null, null, null), Optional.of("foo"))
                .addCase(Tuple.of(null, "foo", null, null), Optional.of("foo"))
                .addCase(Tuple.of(null, null, "foo", null), Optional.of("foo"))
                .addCase(Tuple.of(null, null, null, "foo"), Optional.of("foo"))
                .addCase(Tuple.of("one", "two", "three", "four"), Optional.of("one"))
                .build();
    }

    @Test
    void testGet1Null() {
        Assertions.assertThat(NullSafe.get(
                        nullLevel1,
                        Level1::getLevel))
                .isNull();

        Assertions.assertThat(NullSafe.getOrElse(
                        nullLevel1,
                        Level1::getLevel,
                        other))
                .isEqualTo(other);

        Assertions.assertThat(NullSafe.getOrElseGet(
                        nullLevel1,
                        Level1::getLevel,
                        this::getOther))
                .isEqualTo(other);

        Assertions.assertThat(NullSafe.get(
                        nullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getLevel))
                .isNull();

        Assertions.assertThat(NullSafe.get(
                        nullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getLevel))
                .isNull();

        Assertions.assertThat(NullSafe.get(
                        nullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getNonNullLevel4,
                        Level4::getLevel))
                .isNull();
    }

    @Test
    void testGet1NonNull() {
        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getLevel))
                .isEqualTo(1L);
    }

    @Test
    void testGet2Null() {
        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getLevel))
                .isNull();

        Assertions.assertThat(NullSafe.getOrElse(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getLevel,
                        other))
                .isEqualTo(other);

        Assertions.assertThat(NullSafe.getOrElseGet(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getLevel,
                        this::getOther))
                .isEqualTo(other);

        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getLevel))
                .isNull();

        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getNonNullLevel4,
                        Level4::getLevel))
                .isNull();
    }

    @Test
    void testGet2NonNull() {
        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getLevel))
                .isEqualTo(2L);
    }

    @Test
    void testGet3Null() {
        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNullLevel3,
                        Level3::getLevel))
                .isNull();

        Assertions.assertThat(NullSafe.getOrElse(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getNullLevel3,
                        Level3::getLevel,
                        other))
                .isEqualTo(other);

        Assertions.assertThat(NullSafe.getOrElseGet(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getLevel,
                        this::getOther))
                .isEqualTo(other);

        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNullLevel3,
                        Level3::getNonNullLevel4,
                        Level4::getLevel))
                .isNull();
    }

    @Test
    void testGet3NonNull() {
        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getLevel))
                .isEqualTo(3L);
    }

    @Test
    void testGet4Null() {
        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getNullLevel4,
                        Level4::getLevel))
                .isNull();

        Assertions.assertThat(NullSafe.getOrElse(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getNullLevel4,
                        Level4::getLevel,
                        other))
                .isEqualTo(other);

        Assertions.assertThat(NullSafe.getOrElseGet(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getNullLevel4,
                        Level4::getLevel,
                        this::getOther))
                .isEqualTo(other);
    }

    @Test
    void testGet4NonNull() {
        Assertions.assertThat(NullSafe.get(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getNonNullLevel3,
                        Level3::getNonNullLevel4,
                        Level4::getLevel))
                .isEqualTo(4L);
    }

    @Test
    void testTest0NonNullTrue() {
        Assertions.assertThat(
                        NullSafe.test(
                                "foo",
                                str -> str.equals("foo")))
                .isTrue();
    }

    @Test
    void testTest0NonNullFalse() {
        Assertions.assertThat(NullSafe.test(
                        "foo",
                        str -> str.equals("bar")))
                .isFalse();
    }

    @Test
    void testTest0Null() {
        Assertions.assertThat(NullSafe.test(
                        null,
                        str -> str.equals("foo")))
                .isFalse();
    }

    @Test
    void testTest1NonNullTrue() {
        Assertions.assertThat(NullSafe.test(
                        nonNullLevel1,
                        Level1::getLevel,
                        level -> level == 1L))
                .isTrue();
    }

    @Test
    void testTest1NonNullFalse() {
        Assertions.assertThat(NullSafe.test(
                        nonNullLevel1,
                        Level1::getLevel,
                        level -> level != 1L))
                .isFalse();
    }

    @Test
    void testTest1Null() {
        Assertions.assertThat(NullSafe.test(
                        nullLevel1,
                        Level1::getLevel,
                        level -> level == 1L))
                .isFalse();
    }

    @Test
    void testTest2NonNullTrue() {
        Assertions.assertThat(NullSafe.test(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getLevel,
                        level -> level == 2L))
                .isTrue();
    }

    @Test
    void testTest2NonNullFalse() {
        Assertions.assertThat(NullSafe.test(
                        nonNullLevel1,
                        Level1::getNonNullLevel2,
                        Level2::getLevel,
                        level -> level != 2L))
                .isFalse();
    }

    @Test
    void testTest2Null() {
        Assertions.assertThat(NullSafe.test(
                        nonNullLevel1,
                        Level1::getNullLevel2,
                        Level2::getLevel,
                        level -> level == 2L))
                .isFalse();
    }

    @TestFactory
    Stream<DynamicTest> testIsEmptyCollection() {
        final List<String> emptyList = Collections.emptyList();
        final List<String> nonEmptyList = List.of("foo", "bar");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isEmptyCollection(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, true)
                .addCase(emptyList, true)
                .addCase(nonEmptyList, false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasItems() {
        final List<String> emptyList = Collections.emptyList();
        final List<String> nonEmptyList = List.of("foo", "bar");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.hasItems(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(emptyList, false)
                .addCase(nonEmptyList, true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSize_collection() {
        final List<String> emptyList = Collections.emptyList();
        final List<String> nonEmptyList = List.of("foo", "bar");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(int.class)
                .withTestFunction(testCase ->
                        NullSafe.size(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, 0)
                .addCase(emptyList, 0)
                .addCase(nonEmptyList, 2)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsEmptyCollection2() {
        final ListWrapper nullListWrapper = null;
        final ListWrapper nonNullListWrapper = new ListWrapper();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<ListWrapper, Function<ListWrapper, List<Integer>>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final ListWrapper listWrapper = testCase.getInput()._1;
                    final Function<ListWrapper, List<Integer>> getter = testCase.getInput()._2;
                    return NullSafe.isEmptyCollection(listWrapper, getter);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullListWrapper, ListWrapper::getNullList), true)
                .addCase(Tuple.of(nullListWrapper, ListWrapper::getNonNullEmptyList), true)
                .addCase(Tuple.of(nullListWrapper, ListWrapper::getNonNullNonEmptyList), true)
                .addCase(Tuple.of(nonNullListWrapper, ListWrapper::getNullList), true)
                .addCase(Tuple.of(nonNullListWrapper, ListWrapper::getNonNullEmptyList), true)
                .addCase(Tuple.of(nonNullListWrapper, ListWrapper::getNonNullNonEmptyList), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasItems2() {
        final ListWrapper nullListWrapper = null;
        final ListWrapper nonNullListWrapper = new ListWrapper();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<ListWrapper, Function<ListWrapper, List<Integer>>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final ListWrapper listWrapper = testCase.getInput()._1;
                    final Function<ListWrapper, List<Integer>> getter = testCase.getInput()._2;
                    return NullSafe.hasItems(listWrapper, getter);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullListWrapper, ListWrapper::getNullList), false)
                .addCase(Tuple.of(nullListWrapper, ListWrapper::getNonNullEmptyList), false)
                .addCase(Tuple.of(nullListWrapper, ListWrapper::getNonNullNonEmptyList), false)
                .addCase(Tuple.of(nonNullListWrapper, ListWrapper::getNullList), false)
                .addCase(Tuple.of(nonNullListWrapper, ListWrapper::getNonNullEmptyList), false)
                .addCase(Tuple.of(nonNullListWrapper, ListWrapper::getNonNullNonEmptyList), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsEmptyMap() {
        final Map<String, String> emptyMap = Collections.emptyMap();
        final Map<String, String> nonEmptyMap = Map.of("foo", "bar");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Map<String, String>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isEmptyMap(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, true)
                .addCase(emptyMap, true)
                .addCase(nonEmptyMap, false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasEntries() {
        final Map<String, String> emptyMap = Collections.emptyMap();
        final Map<String, String> nonEmptyMap = Map.of("foo", "bar");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Map<String, String>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.hasEntries(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(emptyMap, false)
                .addCase(nonEmptyMap, true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSize_map() {
        final Map<String, String> emptyMap = Collections.emptyMap();
        final Map<String, String> nonEmptyMap = Map.of("foo", "bar");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Map<String, String>>() {
                })
                .withOutputType(int.class)
                .withTestFunction(testCase ->
                        NullSafe.size(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, 0)
                .addCase(emptyMap, 0)
                .addCase(nonEmptyMap, 1)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsEmptyMap2() {
        final MapWrapper nullMapWrapper = null;
        final MapWrapper nonNullMapWrapper = new MapWrapper();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(
                        new TypeLiteral<Tuple2<MapWrapper, Function<MapWrapper, Map<Integer, Integer>>>>() {
                        })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    var mapWrapper = testCase.getInput()._1;
                    var getter = testCase.getInput()._2;
                    return NullSafe.isEmptyMap(mapWrapper, getter);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullMapWrapper, MapWrapper::getNullMap), true)
                .addCase(Tuple.of(nullMapWrapper, MapWrapper::getNonNullEmptyMap), true)
                .addCase(Tuple.of(nullMapWrapper, MapWrapper::getNonNullNonEmptyMap), true)
                .addCase(Tuple.of(nonNullMapWrapper, MapWrapper::getNullMap), true)
                .addCase(Tuple.of(nonNullMapWrapper, MapWrapper::getNonNullEmptyMap), true)
                .addCase(Tuple.of(nonNullMapWrapper, MapWrapper::getNonNullNonEmptyMap), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasEntries2() {
        final MapWrapper nullMapWrapper = null;
        final MapWrapper nonNullMapWrapper = new MapWrapper();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(
                        new TypeLiteral<Tuple2<MapWrapper, Function<MapWrapper, Map<Integer, Integer>>>>() {
                        })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    var mapWrapper = testCase.getInput()._1;
                    var getter = testCase.getInput()._2;
                    return NullSafe.hasEntries(mapWrapper, getter);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullMapWrapper, MapWrapper::getNullMap), false)
                .addCase(Tuple.of(nullMapWrapper, MapWrapper::getNonNullEmptyMap), false)
                .addCase(Tuple.of(nullMapWrapper, MapWrapper::getNonNullNonEmptyMap), false)
                .addCase(Tuple.of(nonNullMapWrapper, MapWrapper::getNullMap), false)
                .addCase(Tuple.of(nonNullMapWrapper, MapWrapper::getNonNullEmptyMap), false)
                .addCase(Tuple.of(nonNullMapWrapper, MapWrapper::getNonNullNonEmptyMap), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsEmptyString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isEmptyString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, true)
                .addCase("", true)
                .addCase(" ", false)
                .addCase("\n", false)
                .addCase("\t", false)
                .addCase("foo", false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsBlankString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isBlankString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, true)
                .addCase("", true)
                .addCase(" ", true)
                .addCase("\n", true)
                .addCase("\t", true)
                .addCase("foo", false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContains() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    var str = testCase.getInput()._1;
                    var subStr = testCase.getInput()._2;
                    return NullSafe.contains(str, subStr);
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
    Stream<DynamicTest> testIsEmptyString2() {
        final StringWrapper nullStringWrapper = null;
        final StringWrapper nonNullStringWrapper = new StringWrapper();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<StringWrapper, Function<StringWrapper, String>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    var stringWrapper = testCase.getInput()._1;
                    var getter = testCase.getInput()._2;
                    return NullSafe.isEmptyString(stringWrapper, getter);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNullString), true)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullEmptyString), true)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullBlankString), true)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullNonEmptyString), true)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNullString), true)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullEmptyString), true)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullBlankString), false)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullNonEmptyString), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsBlankString2() {
        final StringWrapper nullStringWrapper = null;
        final StringWrapper nonNullStringWrapper = new StringWrapper();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<StringWrapper, Function<StringWrapper, String>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    var stringWrapper = testCase.getInput()._1;
                    var getter = testCase.getInput()._2;
                    return NullSafe.isBlankString(stringWrapper, getter);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNullString), true)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullEmptyString), true)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullBlankString), true)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullNonEmptyString), true)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNullString), true)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullEmptyString), true)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullBlankString), true)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullNonEmptyString), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testStream_collection() {
        final AtomicInteger counter = new AtomicInteger(0);
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Tuple2<Integer, List<String>>>() {
                })
                .withTestFunction(testCase -> {
                    final List<String> newList = NullSafe.stream(testCase.getInput())
                            .peek(item -> counter.incrementAndGet())
                            .toList();
                    return Tuple.of(counter.get(), newList);
                })
                .withSimpleEqualityAssertion()
                .addCase(null, Tuple.of(0, Collections.emptyList()))
                .addCase(Collections.emptyList(), Tuple.of(0, Collections.emptyList()))
                .addCase(List.of("foo", "bar"), Tuple.of(2, List.of("foo", "bar")))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testStream_array() {
        final AtomicInteger counter = new AtomicInteger(0);
        return TestUtil.buildDynamicTestStream()
                .withInputType(String[].class)
                .withWrappedOutputType(new TypeLiteral<Tuple2<Integer, List<String>>>() {
                })
                .withTestFunction(testCase -> {
                    final List<String> newList = NullSafe.stream(testCase.getInput())
                            .peek(item -> counter.incrementAndGet())
                            .toList();
                    return Tuple.of(counter.get(), newList);
                })
                .withSimpleEqualityAssertion()
                .addCase(null, Tuple.of(0, Collections.emptyList()))
                .addCase(new String[0], Tuple.of(0, Collections.emptyList()))
                .addCase(new String[]{"foo", "bar"}, Tuple.of(2, List.of("foo", "bar")))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testList() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.list(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase(Collections.emptyList(), Collections.emptyList())
                .addCase(List.of("foo", "bar"), List.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSet() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<Set<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.set(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptySet())
                .addCase(Collections.emptySet(), Collections.emptySet())
                .addCase(Set.of("foo", "bar"), Set.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMap() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<Map<String, String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.map(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyMap())
                .addCase(Collections.emptyMap(), Collections.emptyMap())
                .addCase(Map.of("foo", "bar"), Map.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testString() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        NullSafe.string(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase("foo", "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testStroomDuration() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(StroomDuration.class)
                .withTestFunction(testCase ->
                        NullSafe.duration(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, StroomDuration.ZERO)
                .addCase(StroomDuration.ZERO, StroomDuration.ZERO)
                .addCase(StroomDuration.ofSeconds(5), StroomDuration.ofSeconds(5))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsTrue() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Boolean.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> NullSafe.isTrue(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(false, false)
                .addCase(true, true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testDuration() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(Duration.class)
                .withTestFunction(testCase ->
                        NullSafe.duration(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Duration.ZERO)
                .addCase(Duration.ZERO, Duration.ZERO)
                .addCase(Duration.ofSeconds(5), Duration.ofSeconds(5))
                .build();
    }

    @Test
    void testConsume1() {
        doConsumeTest(consumer ->
                        NullSafe.consume(123L, consumer),
                123L);
    }

    @Test
    void testConsume1_null() {
        doConsumeTest(consumer ->
                        NullSafe.consume(null, consumer),
                -1);
    }

    @Test
    void testConsume2() {
        doConsumeTest(consumer ->
                        NullSafe.consume(nonNullLevel1, Level1::getLevel, consumer),
                1L);
    }

    @Test
    void testConsume2_null() {
        doConsumeTest(consumer ->
                        NullSafe.consume(nullLevel1, Level1::getLevel, consumer),
                -1L);
    }

    @TestFactory
    Stream<DynamicTest> testConsume3() {
        final var inputType = new TypeLiteral<Tuple3<
                Level1,
                Function<Level1, Level2>,
                Function<Level2, Long>>>() {
        };

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(inputType)
                .withOutputType(Long.class)
                .withTestFunction(testCase -> {
                    final AtomicLong val = new AtomicLong(-1);
                    NullSafe.consume(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3,
                            val::set);
                    // Return the potentially touched by the consumer
                    return val.get();
                })
                .withSimpleEqualityAssertion()
                .addNamedCase("All non null",
                        Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, Level2::getLevel),
                        2L)
                .addNamedCase("Level 1 null",
                        Tuple.of(nullLevel1, Level1::getNonNullLevel2, Level2::getLevel),
                        -1L)
                .addNamedCase("Level 2 null",
                        Tuple.of(nonNullLevel1, Level1::getNullLevel2, Level2::getLevel),
                        -1L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testConsume4() {
        final var inputType = new TypeLiteral<Tuple4<
                Level1,
                Function<Level1, Level2>,
                Function<Level2, Level3>,
                Function<Level3, Long>>>() {
        };

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(inputType)
                .withOutputType(Long.class)
                .withTestFunction(testCase -> {
                    final AtomicLong val = new AtomicLong(-1);
                    NullSafe.consume(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3,
                            testCase.getInput()._4,
                            val::set);
                    // Return the potentially touched by the consumer
                    return val.get();
                })
                .withSimpleEqualityAssertion()
                .addNamedCase("All non null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevel),
                        3L)
                .addNamedCase("Level 1 null",
                        Tuple.of(
                                nullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevel),
                        -1L)
                .addNamedCase("Level 2 null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevel),
                        -1L)
                .addNamedCase("Level 3 null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNullLevel3,
                                Level3::getLevel),
                        -1L)
                .build();
    }

    private void doConsumeTest(final Consumer<Consumer<Long>> action, final long expectedValue) {
        final AtomicLong val = new AtomicLong(-1);
        final Consumer<Long> consumer = val::set;

        action.accept(consumer);

        Assertions.assertThat(val)
                .hasValue(expectedValue);
    }

    @Test
    @Disabled
    void testPerformanceVsOptional() {
        final int iterations = 100_000_000;
        final Level5 otherLevel5 = new Level5(-1);
        LOGGER.info("Iterations: {}", iterations);
        MutableLong totalNanosNullSafe = new MutableLong(0);
        MutableLong totalNanosOptional = new MutableLong(0);
        for (int i = 0; i < 3; i++) {
            totalNanosNullSafe.setValue(0L);

            LOGGER.logDurationIfInfoEnabled(() -> {
                for (int j = 0; j < iterations; j++) {
                    // Ensure we have a different object each time
                    final Level1 level1 = new Level1(j);
                    final long startNanos = System.nanoTime();
                    final Level5 val = NullSafe.getOrElse(
                            level1,
                            Level1::getNonNullLevel2,
                            Level2::getNonNullLevel3,
                            Level3::getNonNullLevel4,
                            Level4::getNonNullLevel5,
                            otherLevel5);
                    totalNanosNullSafe.add(System.nanoTime() - startNanos);
                    Objects.requireNonNull(val);
                    if (!val.toString().equals(j + ":" + 5)) {
                        throw new RuntimeException("Invalid: " + val);
                    }
                }
            }, i + " NullSafe");
            LOGGER.info("{} NullSafe nanos per iteration: {}", i, (double) totalNanosNullSafe.getValue() / iterations);

            totalNanosOptional.setValue(0L);

            LOGGER.logDurationIfInfoEnabled(() -> {
                for (int j = 0; j < iterations; j++) {
                    final Level1 level1 = new Level1(j);
                    final long startNanos = System.nanoTime();
                    final Level5 val = Optional.ofNullable(level1)
                            .map(Level1::getNonNullLevel2)
                            .map(Level2::getNonNullLevel3)
                            .map(Level3::getNonNullLevel4)
                            .map(Level4::getNonNullLevel5)
                            .orElse(otherLevel5);
                    totalNanosOptional.add(System.nanoTime() - startNanos);
                    Objects.requireNonNull(val);
                    if (!val.toString().equals(j + ":" + 5)) {
                        throw new RuntimeException("Invalid: " + val);
                    }
                }
            }, i + " Optional");
            LOGGER.info("{} Optional nanos per iteration: {}", i, (double) totalNanosOptional.getValue() / iterations);

            LOGGER.info("NullSafe/Optional : {}",
                    (double) totalNanosNullSafe.getValue() / totalNanosOptional.getValue());
            LOGGER.info("--------------------------------");
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class Level1 {

        private final long id;
        private final Level2 nullLevel2 = null;
        private final Level2 nonNullLevel2;
        private final Long level = 1L;

        private Level1() {
            id = 0L;
            nonNullLevel2 = new Level2(id);
        }

        private Level1(final long id) {
            this.id = id;
            nonNullLevel2 = new Level2(id);
        }

        public Level2 getNullLevel2() {
            return nullLevel2;
        }

        public Level2 getNonNullLevel2() {
            return nonNullLevel2;
        }

        public Long getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return id + ":" + level;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Level1 level1 = (Level1) o;
            return id == level1.id && Objects.equals(nullLevel2, level1.nullLevel2) && Objects.equals(
                    nonNullLevel2,
                    level1.nonNullLevel2) && Objects.equals(level, level1.level);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, nullLevel2, nonNullLevel2, level);
        }
    }

    private static class Level2 {

        private final long id;
        private final Level3 nullLevel3 = null;
        private final Level3 nonNullLevel3;
        private final Long level = 2L;

        private Level2(final long id) {
            this.id = id;
            nonNullLevel3 = new Level3(id);
        }

        public Level3 getNullLevel3() {
            return nullLevel3;
        }

        public Level3 getNonNullLevel3() {
            return nonNullLevel3;
        }

        public Long getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return id + ":" + level;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Level2 level2 = (Level2) o;
            return id == level2.id && Objects.equals(nullLevel3, level2.nullLevel3) && Objects.equals(
                    nonNullLevel3,
                    level2.nonNullLevel3) && Objects.equals(level, level2.level);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, nullLevel3, nonNullLevel3, level);
        }
    }

    private static class Level3 {

        private final long id;
        private final Level4 nullLevel4 = null;
        private final Level4 nonNullLevel4;
        private final Long level = 3L;

        private Level3(final long id) {
            this.id = id;
            nonNullLevel4 = new Level4(id);
        }

        public Level4 getNullLevel4() {
            return nullLevel4;
        }

        public Level4 getNonNullLevel4() {
            return nonNullLevel4;
        }

        public Long getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return id + ":" + level;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Level3 level3 = (Level3) o;
            return id == level3.id && Objects.equals(nullLevel4, level3.nullLevel4) && Objects.equals(
                    nonNullLevel4,
                    level3.nonNullLevel4) && Objects.equals(level, level3.level);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, nullLevel4, nonNullLevel4, level);
        }
    }

    private static class Level4 {

        private final long id;
        private final Level5 nullLevel5 = null;
        private final Level5 nonNullLevel5;
        private final Long level = 4L;

        private Level4(final long id) {
            this.id = id;
            nonNullLevel5 = new Level5(id);
        }

        public Level5 getNullLevel5() {
            return nullLevel5;
        }

        public Level5 getNonNullLevel5() {
            return nonNullLevel5;
        }

        public Long getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return id + ":" + level;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Level4 level4 = (Level4) o;
            return id == level4.id && Objects.equals(nullLevel5, level4.nullLevel5) && Objects.equals(
                    nonNullLevel5,
                    level4.nonNullLevel5) && Objects.equals(level, level4.level);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, nullLevel5, nonNullLevel5, level);
        }
    }

    private static class Level5 {

        private final long id;
        private final Long level = 5L;

        private Level5(final long id) {
            this.id = id;
        }

        public Long getLevel() {
            return level;
        }

        @Override
        public String toString() {
            return id + ":" + level;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Level5 level5 = (Level5) o;
            return id == level5.id && Objects.equals(level, level5.level);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, level);
        }
    }

    private static class ListWrapper {

        private final List<Integer> nullList = null;
        private final List<Integer> nonNullEmptyList = new ArrayList<>();
        private final List<Integer> nonNullNonEmptyList = List.of(1, 2, 3);

        public List<Integer> getNullList() {
            return nullList;
        }

        public List<Integer> getNonNullEmptyList() {
            return nonNullEmptyList;
        }

        public List<Integer> getNonNullNonEmptyList() {
            return nonNullNonEmptyList;
        }
    }

    private static class MapWrapper {

        private final Map<Integer, Integer> nullMap = null;
        private final Map<Integer, Integer> nonNullEmptyMap = new HashMap<>();
        private final Map<Integer, Integer> nonNullNonEmptyMap = Map.of(
                1, 11,
                2, 22,
                3, 33);

        public Map<Integer, Integer> getNullMap() {
            return nullMap;
        }

        public Map<Integer, Integer> getNonNullEmptyMap() {
            return nonNullEmptyMap;
        }

        public Map<Integer, Integer> getNonNullNonEmptyMap() {
            return nonNullNonEmptyMap;
        }
    }

    private static class StringWrapper {

        private final String nullString = null;
        private final String nonNullEmptyString = "";
        private final String nonNullBlankString = " ";
        private final String nonNullNonEmptyString = "foobar";

        public String getNullString() {
            return nullString;
        }

        public String getNonNullEmptyString() {
            return nonNullEmptyString;
        }

        public String getNonNullBlankString() {
            return nonNullBlankString;
        }

        public String getNonNullNonEmptyString() {
            return nonNullNonEmptyString;
        }
    }
}
