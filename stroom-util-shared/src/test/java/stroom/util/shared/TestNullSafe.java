/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TestSetup;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import io.vavr.Tuple5;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableLong;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


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
        assertThat(NullSafe.equals(nullLevel1,
                Level1::getNonNullLevel2,
                nonNullLevel1.getNonNullLevel2()))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1,
                Level1::getNullLevel2,
                nonNullLevel1.getNonNullLevel2()))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1, Level1::getNonNullLevel2, "foobar"))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1, Level1::getNonNullLevel2, null))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                nonNullLevel1.getNonNullLevel2()))
                .isTrue();
    }

    @Test
    void testEquals2() {
        // Null parent
        assertThat(NullSafe.equals(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNonNullLevel3,
                nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNullLevel3,
                nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                "foobar"))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                null))
                .isFalse();
        assertThat(NullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isTrue();
    }

    @TestFactory
    Stream<DynamicTest> testEqualProperties() {
        final AtomicReference<String> val1 = new AtomicReference<>("foo");
        final AtomicReference<String> val1b = new AtomicReference<>("foo");
        final AtomicReference<String> val2 = new AtomicReference<>("bar");
        final AtomicReference<String> valNull = new AtomicReference<>(null);

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(
                        new TypeLiteral<Tuple2<
                                AtomicReference<String>,
                                AtomicReference<String>>>() {
                        })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final AtomicReference<String> v1 = testCase.getInput()._1;
                    final AtomicReference<String> v2 = testCase.getInput()._2;
                    return NullSafe.equalProperties(v1, v2, AtomicReference::get);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), true)
                .addCase(Tuple.of(val1, null), false)
                .addCase(Tuple.of(null, val1), false)
                .addCase(Tuple.of(val1, val2), false)
                .addCase(Tuple.of(val1, valNull), false)
                .addCase(Tuple.of(valNull, val2), false)
                .addCase(Tuple.of(valNull, valNull), true)
                .addCase(Tuple.of(val1, val1), true)
                .addCase(Tuple.of(val1, val1b), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testEqualProperties_multipleGetters() {
        final ABC obj1 = new ABC("a", "b", "c");
        final ABC obj2 = new ABC("A", "b", "c");
        final ABC obj3 = new ABC("a", "B", "c");
        final ABC obj4 = new ABC("a", "b", "C");
        final ABC obj5 = new ABC("a", null, "c");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(
                        new TypeLiteral<Tuple2<ABC, ABC>>() {
                        })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    return NullSafe.equalProperties(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            List.of(ABC::a, ABC::b, ABC::c));
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(obj1, obj1), true)
                .addCase(Tuple.of(obj2, obj2), true)
                .addCase(Tuple.of(obj3, obj3), true)
                .addCase(Tuple.of(obj4, obj4), true)
                .addCase(Tuple.of(obj5, obj5), true)
                .addCase(Tuple.of(obj1, obj2), false)
                .addCase(Tuple.of(obj1, obj3), false)
                .addCase(Tuple.of(obj1, obj4), false)
                .addCase(Tuple.of(obj1, obj5), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAllNull() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String[].class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(NullSafe::allNull)
                .withSimpleEqualityAssertion()
                .addCase(null, true)
                .addCase(new String[]{null}, true)
                .addCase(new String[]{null, null}, true)
                .addCase(new String[]{null, null, null}, true)
                .addCase(new String[]{"foo", null, null}, false)
                .addCase(new String[]{null, "foo", null}, false)
                .addCase(new String[]{null, null, "foo"}, false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAllNonNull() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Object[].class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(NullSafe::allNonNull)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(new String[]{null}, false)
                .addCase(new String[]{null, null}, false)
                .addCase(new String[]{null, null, null}, false)
                .addCase(new String[]{"foo", null, null}, false)
                .addCase(new String[]{null, "foo", null}, false)
                .addCase(new String[]{null, "foo", "foo"}, false)
                .addCase(new String[]{"1", "2", "3"}, true)
                .addCase(new Object[]{"1", 2L, 3.0D}, true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAllNonNull_supplier() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Supplier<Object>[]>() {
                })
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(NullSafe::allNonNull)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(new Supplier[]{null}, false)
                .addCase(new Supplier[]{null, () -> null}, false)
                .addCase(new Supplier[]{null, null, null}, false)
                .addCase(new Supplier[]{() -> null, () -> null, () -> null}, false)
                .addCase(new Supplier[]{() -> "foo", () -> null, () -> null}, false)
                .addCase(new Supplier[]{() -> null, () -> "foo", () -> null}, false)
                .addCase(new Supplier[]{() -> null, () -> "foo", () -> "foo"}, false)
                .addCase(new Supplier[]{() -> "1", () -> "2", () -> "3"}, true)
                .addCase(new Supplier[]{() -> "1", () -> 2L, () -> 3.0D}, true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFirstNonNull() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String[].class)
                .withWrappedOutputType(new TypeLiteral<Optional<String>>() {
                })
                .withSingleArgTestFunction(NullSafe::firstNonNull)
                .withSimpleEqualityAssertion()
                .addCase(null, Optional.empty())
                .addCase(new String[]{null}, Optional.empty())
                .addCase(new String[]{null, null}, Optional.empty())
                .addCase(new String[]{null, null, null}, Optional.empty())
                .addCase(new String[]{"foo", null, null}, Optional.of("foo"))
                .addCase(new String[]{null, "foo", null}, Optional.of("foo"))
                .addCase(new String[]{null, "foo", "bar"}, Optional.of("foo"))
                .addCase(new String[]{"1", "2", "3"}, Optional.of("1"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFirst() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        NullSafe.first(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase(Collections.emptyList(), null)
                .addCase(List.of("bar"), "bar")
                .addCase(List.of("foo", "bar"), "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testLast() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        NullSafe.last(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase(Collections.emptyList(), null)
                .addCase(List.of("bar"), "bar")
                .addCase(List.of("foo", "bar"), "bar")
                .build();
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
        assertThat(NullSafe.get(
                nullLevel1,
                Level1::getLevelNo))
                .isNull();

        assertThat(NullSafe.getOrElse(
                nullLevel1,
                Level1::getLevelNo,
                other))
                .isEqualTo(other);

        assertThat(NullSafe.getOrElseGet(
                nullLevel1,
                Level1::getLevelNo,
                this::getOther))
                .isEqualTo(other);

        assertThat(NullSafe.get(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevelNo))
                .isNull();

        assertThat(NullSafe.get(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getLevelNo))
                .isNull();

        assertThat(NullSafe.get(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevelNo))
                .isNull();
    }

    @Test
    void testGet1NonNull() {
        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getLevelNo))
                .isEqualTo(1L);

        assertThat(
                NullSafe.requireNonNull(
                        nonNullLevel1,
                        Level1::getLevelNo,
                        () -> "foo"))
                .isEqualTo(1L);
    }

    @Test
    void testGet2Null() {
        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevelNo))
                .isNull();

        assertThat(NullSafe.getOrElse(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevelNo,
                other))
                .isEqualTo(other);

        assertThat(NullSafe.getOrElseGet(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevelNo,
                this::getOther))
                .isEqualTo(other);

        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getLevelNo))
                .isNull();

        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevelNo))
                .isNull();
    }

    @Test
    void testGet2NonNull() {
        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevelNo))
                .isEqualTo(2L);
    }

    @Test
    void testGet3Null() {
        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNullLevel3,
                Level3::getLevelNo))
                .isNull();

        assertThat(NullSafe.getOrElse(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNullLevel3,
                Level3::getLevelNo,
                other))
                .isEqualTo(other);

        assertThat(NullSafe.getOrElseGet(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevelNo,
                this::getOther))
                .isEqualTo(other);

        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevelNo))
                .isNull();
    }

    @Test
    void testGet3NonNull() {
        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getLevelNo))
                .isEqualTo(3L);
    }

    @Test
    void testGet4Null() {
        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNullLevel4,
                Level4::getLevelNo))
                .isNull();

        assertThat(NullSafe.getOrElse(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNullLevel4,
                Level4::getLevelNo,
                other))
                .isEqualTo(other);

        assertThat(NullSafe.getOrElseGet(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNullLevel4,
                Level4::getLevelNo,
                this::getOther))
                .isEqualTo(other);
    }

    @Test
    void testGet4NonNull() {
        assertThat(NullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevelNo))
                .isEqualTo(4L);
    }

    @Test
    void testTest0NonNullTrue() {
        assertThat(
                NullSafe.test(
                        "foo",
                        str -> str.equals("foo")))
                .isTrue();
    }

    @Test
    void testTest0NonNullFalse() {
        assertThat(NullSafe.test(
                "foo",
                str -> str.equals("bar")))
                .isFalse();
    }

    @Test
    void testTest0Null() {
        assertThat(NullSafe.test(
                null,
                str -> str.equals("foo")))
                .isFalse();
    }

    @Test
    void testTest1NonNullTrue() {
        assertThat(NullSafe.test(
                nonNullLevel1,
                Level1::getLevelNo,
                level -> level == 1L))
                .isTrue();
    }

    @Test
    void testTest1NonNullFalse() {
        assertThat(NullSafe.test(
                nonNullLevel1,
                Level1::getLevelNo,
                level -> level != 1L))
                .isFalse();
    }

    @Test
    void testTest1Null() {
        assertThat(NullSafe.test(
                nullLevel1,
                Level1::getLevelNo,
                level -> level == 1L))
                .isFalse();
    }

    @Test
    void testTest2NonNullTrue() {
        assertThat(NullSafe.test(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevelNo,
                level -> level == 2L))
                .isTrue();
    }

    @Test
    void testTest2NonNullFalse() {
        assertThat(NullSafe.test(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevelNo,
                level -> level != 2L))
                .isFalse();
    }

    @Test
    void testTest2Null() {
        assertThat(NullSafe.test(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevelNo,
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
    Stream<DynamicTest> testIsEmptyResultPage() {
        final ResultPage<String> emptyList = ResultPage.empty();
        final ResultPage<String> nonEmptyList = ResultPage.createUnboundedList(List.of("foo", "bar"));

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<ResultPage<String>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isEmptyResultPage(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, true)
                .addCase(emptyList, true)
                .addCase(nonEmptyList, false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsEmptyArray() {
        final String[] emptyArr = new String[0];
        final String[] nonEmptyArr = new String[]{"foo", "bar"};

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<String[]>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isEmptyArray(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, true)
                .addCase(emptyArr, true)
                .addCase(nonEmptyArr, false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasItems_collection() {
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
    Stream<DynamicTest> testHasItems_resultPage() {
        final ResultPage<String> emptyList = ResultPage.empty();
        final ResultPage<String> nonEmptyList = ResultPage.createUnboundedList(List.of("foo", "bar"));

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<ResultPage<String>>() {
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
    Stream<DynamicTest> testHasItems_array() {
        final String[] emptyArr = new String[0];
        final String[] nonEmptyArr = new String[]{"foo", "bar"};

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<String[]>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.hasItems(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(emptyArr, false)
                .addCase(nonEmptyArr, true)
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
    Stream<DynamicTest> testHasOneItem() {
        final List<String> emptyList = Collections.emptyList();
        final List<String> nonEmptyList = List.of("foo", "bar");

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.hasOneItem(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(Collections.emptyList(), false)
                .addCase(List.of("foo"), true)
                .addCase(List.of("foo", "bar"), false)
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
    Stream<DynamicTest> testIsNull_and_nonNull2() {
        final Level1 nullLevel1 = null;
        final Level1 nonNullLevel1 = new Level1();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Level1, Function<Level1, Level2>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final Level1 level1 = testCase.getInput()._1;
                    final Function<Level1, Level2> getter = testCase.getInput()._2;
                    final boolean isNull = NullSafe.isNull(level1, getter);
                    final boolean isNonNull = NullSafe.nonNull(level1, getter);
                    assertThat(isNull)
                            .isEqualTo(!isNonNull);
                    return isNull;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullLevel1, Level1::getNullLevel2), true)
                .addCase(Tuple.of(nullLevel1, Level1::getNonNullLevel2), true)
                .addCase(Tuple.of(nonNullLevel1, Level1::getNullLevel2), true)
                .addCase(Tuple.of(nonNullLevel1, Level1::getNonNullLevel2), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsNull_and_nonNull3() {
        final Level1 nullLevel1 = null;
        final Level1 nonNullLevel1 = new Level1();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple3<
                        Level1,
                        Function<Level1, Level2>,
                        Function<Level2, Level3>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final Level1 level1 = testCase.getInput()._1;
                    final Function<Level1, Level2> getter1 = testCase.getInput()._2;
                    final Function<Level2, Level3> getter2 = testCase.getInput()._3;
                    final boolean isNull = NullSafe.isNull(level1, getter1, getter2);
                    final boolean nonNull = NullSafe.nonNull(level1, getter1, getter2);
                    assertThat(isNull)
                            .isEqualTo(!nonNull);
                    return isNull;
                })
                .withSimpleEqualityAssertion()
                .addCase(
                        Tuple.of(nullLevel1, Level1::getNullLevel2, Level2::getNullLevel3),
                        true)
                .addCase(
                        Tuple.of(nullLevel1, Level1::getNullLevel2, Level2::getNonNullLevel3),
                        true)
                .addCase(
                        Tuple.of(nullLevel1, Level1::getNonNullLevel2, Level2::getNullLevel3),
                        true)
                .addCase(
                        Tuple.of(nullLevel1, Level1::getNonNullLevel2, Level2::getNonNullLevel3),
                        true)
                .addCase(
                        Tuple.of(nonNullLevel1, Level1::getNullLevel2, Level2::getNullLevel3),
                        true)
                .addCase(
                        Tuple.of(nonNullLevel1, Level1::getNullLevel2, Level2::getNonNullLevel3),
                        true)
                .addCase(
                        Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, Level2::getNullLevel3),
                        true)
                .addCase(
                        Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, Level2::getNonNullLevel3),
                        false)
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
                    final MapWrapper mapWrapper = testCase.getInput()._1;
                    //noinspection VariableTypeCanBeExplicit
                    final var getter = testCase.getInput()._2;
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
                    final MapWrapper mapWrapper = testCase.getInput()._1;
                    //noinspection VariableTypeCanBeExplicit
                    final var getter = testCase.getInput()._2;
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
                .addCase(" foo ", false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsNonEmptyString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isNonEmptyString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", true)
                .addCase("\n", true)
                .addCase("\t", true)
                .addCase("foo", true)
                .addCase(" foo ", true)
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
                .addCase(" foo ", false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsNonBlankString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.isNonBlankString(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", false)
                .addCase("\n", false)
                .addCase("\t", false)
                .addCase("foo", true)
                .addCase(" foo ", true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNonBlankStringElse() {
        final String other = "bar";
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        NullSafe.nonBlankStringElse(testCase.getInput(), other))
                .withSimpleEqualityAssertion()
                .addCase(null, other)
                .addCase("", other)
                .addCase(" ", other)
                .addCase("\n", other)
                .addCase("\t", other)
                .addCase("foo", "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNonBlankStringElseGet() {
        final String other = "bar";
        final Supplier<String> supplier = () -> other;
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase ->
                        NullSafe.nonBlankStringElseGet(testCase.getInput(), supplier))
                .withSimpleEqualityAssertion()
                .addCase(null, other)
                .addCase("", other)
                .addCase(" ", other)
                .addCase("\n", other)
                .addCase("\t", other)
                .addCase("foo", "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testTrim() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(NullSafe::trim)
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase(" ", "")
                .addCase("\n", "")
                .addCase("\t", "")
                .addCase("foo", "foo")
                .addCase(" foo", "foo")
                .addCase("foo ", "foo")
                .addCase(" foo ", "foo")
                .addCase(" \n\tfoo\n\t ", "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSubString() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, Integer.class, Integer.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> NullSafe.subString(
                        testCase.getInput()._1,
                        testCase.getInput()._2,
                        testCase.getInput()._3))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, 1, 10), "")
                .addCase(Tuple.of("foobar", 0, 3), "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testConsumeNonBlankString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final AtomicBoolean wasConsumed = new AtomicBoolean(false);
                    NullSafe.consumeNonBlankString(testCase.getInput(), str -> {
                        wasConsumed.set(true);
                        assertThat(str)
                                .isEqualTo(testCase.getInput());
                    });
                    return wasConsumed.get();
                })
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", false)
                .addCase("\n", false)
                .addCase("\t", false)
                .addCase("foo", true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testConsumeNonBlankString_noTrim() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final AtomicBoolean wasConsumed = new AtomicBoolean(false);
                    NullSafe.consumeNonBlankString(testCase.getInput(), false, str -> {
                        wasConsumed.set(true);
                        assertThat(str)
                                .isEqualTo(testCase.getInput());
                    });
                    return wasConsumed.get();
                })
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", false)
                .addCase("\n", false)
                .addCase("\t", false)
                .addCase("foo", true)
                .addCase(" foo", true)
                .addCase("foo ", true)
                .addCase(" foo ", true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testConsumeNonBlankString_trim() {
        final String notConsumedStr = "NOT_CONSUMED";
        final AtomicReference<String> consumedStrRef = new AtomicReference<>();
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> {
                    NullSafe.consumeNonBlankString(testCase.getInput(), true, consumedStrRef::set);
                    return consumedStrRef.get();
                })
                .withSimpleEqualityAssertion()
                .withBeforeTestCaseAction(() ->
                        consumedStrRef.set(notConsumedStr))
                .addCase(null, notConsumedStr)
                .addCase("", notConsumedStr)
                .addCase(" ", notConsumedStr)
                .addCase("\n", notConsumedStr)
                .addCase("\t", notConsumedStr)
                .addCase("foo", "foo")
                .addCase(" foo", "foo")
                .addCase("foo ", "foo")
                .addCase(" foo ", "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testJoin() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(CharSequence[].class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(input ->
                        NullSafe.join(", ", input))
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase(new String[]{}, "")
                .addCase(new String[]{""}, "")
                .addCase(new String[]{"a", "b"}, "a, b")
                .addCase(new String[]{"one", "two", "three"}, "one, two, three")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContains() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final String subStr = testCase.getInput()._2;
                    return NullSafe.contains(str, subStr);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of("foorbar", null), false)
                .addCase(Tuple.of(null, "foobar"), false)
                .addCase(Tuple.of("foobar", "foo"), true)
                .addCase(Tuple.of("foobar", "ob"), true)
                .addCase(Tuple.of("foobar", "foobar"), true)
                .addCase(Tuple.of("foobar", "fooBAR"), false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsIgnoringCase() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final String str = testCase.getInput()._1;
                    final String subStr = testCase.getInput()._2;
                    return NullSafe.containsIgnoringCase(str, subStr);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of("foorbar", null), false)
                .addCase(Tuple.of(null, "foobar"), false)
                .addCase(Tuple.of("foobar", "foo"), true)
                .addCase(Tuple.of("foobar", "ob"), true)
                .addCase(Tuple.of("foobar", "foobar"), true)
                .addCase(Tuple.of("foobar", "fooBAR"), true)
                .addCase(Tuple.of("FOOBAR", "fooBAR"), true)
                .addCase(Tuple.of("FOOBAR", "foobar"), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCollectionContains() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Set<String>, String>>() {
                })
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final Set<String> collection = testCase.getInput()._1;
                    final String item = testCase.getInput()._2;
                    return NullSafe.collectionContains(collection, item);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of(null, "foo"), false)
                .addCase(Tuple.of(Set.of(), "foo"), false)
                .addCase(Tuple.of(Set.of("bar"), "foo"), false)
                .addCase(Tuple.of(Set.of("foo", "bar"), "foo"), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testContainsKey() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Map<String, String>, String>>() {
                })
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final Map<String, String> map = testCase.getInput()._1;
                    final String key = testCase.getInput()._2;
                    return NullSafe.containsKey(map, key);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addCase(Tuple.of(null, "foo"), false)
                .addCase(Tuple.of(Map.of(), "foo"), false)
                .addCase(Tuple.of(Map.of("bar", "BAR"), "foo"), false)
                .addCase(Tuple.of(Map.of(
                                "foo", "FOO",
                                "bar", "BAR"),
                        "foo"), true)
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
                    final StringWrapper stringWrapper = testCase.getInput()._1;
                    final Function<StringWrapper, String> getter = testCase.getInput()._2;
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
                    final StringWrapper stringWrapper = testCase.getInput()._1;
                    final Function<StringWrapper, String> getter = testCase.getInput()._2;
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
    Stream<DynamicTest> testConsumeNonBlankString2() {
        final StringWrapper nullStringWrapper = null;
        final StringWrapper nonNullStringWrapper = new StringWrapper();

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<StringWrapper, Function<StringWrapper, String>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final StringWrapper value = testCase.getInput()._1;
                    final Function<StringWrapper, String> getter = testCase.getInput()._2;

                    final AtomicBoolean wasConsumed = new AtomicBoolean(false);
                    NullSafe.consumeNonBlankString(value, getter, str -> {
                        wasConsumed.set(true);
                        assertThat(str)
                                .isEqualTo(getter.apply(value));
                    });
                    return wasConsumed.get();
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNullString), false)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullEmptyString), false)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullBlankString), false)
                .addCase(Tuple.of(nullStringWrapper, StringWrapper::getNonNullNonEmptyString), false)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNullString), false)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullEmptyString), false)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullBlankString), false)
                .addCase(Tuple.of(nonNullStringWrapper, StringWrapper::getNonNullNonEmptyString), true)
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
    Stream<DynamicTest> testForEach_iterable() {
        final AtomicInteger counter = new AtomicInteger(0);

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Iterable<?>>() {
                })
                .withOutputType(int.class)
                .withTestFunction(testCase -> {
                    NullSafe.forEach(testCase.getInput(), item -> {
                        counter.incrementAndGet();
                    });
                    return counter.get();
                })
                .withSimpleEqualityAssertion()
                .withBeforeTestCaseAction(() -> counter.set(0))
                .addCase(null, 0)
                .addCase(Collections.emptyList(), 0)
                .addCase(Set.of(1, 2, 3), 3)
                .addCase(List.of("1", "2", "3"), 3)
                .addCase(Arrays.asList(null, "2", null), 1)
                .addCase(Arrays.asList(null, null, null), 0)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testForEach_array() {
        final AtomicInteger counter = new AtomicInteger(0);

        return TestUtil.buildDynamicTestStream()
                .withInputType(String[].class)
                .withOutputType(int.class)
                .withTestFunction(testCase -> {
                    NullSafe.forEach(testCase.getInput(), item -> {
                        counter.incrementAndGet();
                    });
                    return counter.get();
                })
                .withSimpleEqualityAssertion()
                .withBeforeTestCaseAction(() -> counter.set(0))
                .addCase(null, 0)
                .addCase(new String[0], 0)
                .addCase(new String[]{"1", "2", "3"}, 3)
                .addCase(new String[]{null, "2", null}, 1)
                .addCase(new String[]{null, null, null}, 0)
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
                .withBeforeTestCaseAction(() -> counter.set(0))
                .addCase(null, Tuple.of(0, Collections.emptyList()))
                .addCase(new String[0], Tuple.of(0, Collections.emptyList()))
                .addCase(new String[]{"foo"}, Tuple.of(1, List.of("foo")))
                .addCase(new String[]{"foo", "bar"}, Tuple.of(2, List.of("foo", "bar")))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCollection() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<Collection<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.collection(testCase.getInput()))
                .withAssertions(outcome -> {
                    assertThat(outcome.getActualOutput())
                            .containsExactlyInAnyOrderElementsOf(outcome.getExpectedOutput());
                })
                .addCase(null, Collections.emptyList())
                .addCase(Collections.emptyList(), Collections.emptyList())
                .addCase(Collections.singleton("bar"), List.of("bar"))
                .addCase(List.of("foo", "bar"), List.of("foo", "bar"))
                .addCase(Set.of("foo", "bar"), List.of("foo", "bar"))
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
                .addCase(List.of("bar"), List.of("bar"))
                .addCase(List.of("foo", "bar"), List.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testUnmodifiableList() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.unmodifiableList(testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final List<String> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isEqualTo(testOutcome.getExpectedOutput());
                    assertThat(actual)
                            .isUnmodifiable();
                })
                .addCase(null, Collections.emptyList())
                .addCase(Collections.emptyList(), Collections.emptyList())
                .addCase(List.of("foo", "bar"), List.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMutableList() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.mutableList(testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final List<String> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isEqualTo(testOutcome.getExpectedOutput());
                    assertThat(actual)
                            .isInstanceOf(ArrayList.class);
                })
                .addCase(null, Collections.emptyList())
                .addCase(Collections.emptyList(), Collections.emptyList())
                .addCase(List.of("foo"), List.of("foo"))
                .addCase(List.of("foo", "bar"), List.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMutableSet() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<Set<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.mutableSet(testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final Set<String> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isEqualTo(testOutcome.getExpectedOutput());
                    assertThat(actual)
                            .isInstanceOf(HashSet.class);
                })
                .addCase(null, Collections.emptySet())
                .addCase(Collections.emptySet(), Collections.emptySet())
                .addCase(Set.of("foo"), Set.of("foo"))
                .addCase(Set.of("foo", "bar"), Set.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSingletonList() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withSingleArgTestFunction(NullSafe::singletonList)
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("foo", List.of("foo"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAsList() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String[].class)
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withSingleArgTestFunction(NullSafe::asList)
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase(new String[0], Collections.emptyList())
                .addCase(new String[]{"foo"}, List.of("foo"))
                .addCase(new String[]{"foo", "bar"}, List.of("foo", "bar"))
                .addCase(new String[]{"foo", null}, Arrays.asList("foo", null))
//                .addThrowsCase(new String[]{"foo", null}, NullPointerException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAsSet() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<String[]>() {
                })
                .withWrappedOutputType(new TypeLiteral<Set<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.asSet(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptySet())
                .addCase(new String[]{}, Collections.emptySet())
                .addCase(new String[]{"foo", "bar"}, Set.of("foo", "bar"))
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
                .addCase(Set.of("bar"), Set.of("bar"))
                .addCase(Set.of("foo", "bar"), Set.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testUnmodifiableSet() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<Set<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.unmodifialbeSet(testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final Set<String> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isEqualTo(testOutcome.getExpectedOutput());
                    assertThat(actual)
                            .isUnmodifiable();
                })
                .addCase(null, Collections.emptySet())
                .addCase(Collections.emptySet(), Collections.emptySet())
                .addCase(Set.of("bar"), Set.of("bar"))
                .addCase(Set.of("foo", "bar"), Set.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMutableEnumSet() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<Set<TestEnum>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.mutableEnumSet(TestEnum.class, testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final Set<TestEnum> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isNotNull()
                            .isEqualTo(testOutcome.getExpectedOutput())
                            .isInstanceOf(EnumSet.class);

                    // Make sure the set is mutable
                    actual.clear();
                })
                .addCase(null, Collections.emptySet())
                .addCase(Collections.emptySet(), EnumSet.noneOf(TestEnum.class))
                .addCase(Set.of(TestEnum.A, TestEnum.C), Set.of(TestEnum.A, TestEnum.C))
                .addCase(EnumSet.of(TestEnum.A, TestEnum.B), Set.of(TestEnum.A, TestEnum.B))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testUnmodifiableEnumSet() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<Set<TestEnum>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.unmodifialbeEnumSet(TestEnum.class, testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final Set<TestEnum> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isNotNull()
                            .isEqualTo(testOutcome.getExpectedOutput())
                            .isInstanceOf(Set.class);

                    if (!actual.isEmpty()) {
                        Assertions.assertThatThrownBy(actual::clear)
                                .isInstanceOf(RuntimeException.class);
                    }
                })
                .addCase(null, Collections.emptySet())
                .addCase(Collections.emptySet(), EnumSet.noneOf(TestEnum.class))
                .addCase(Set.of(TestEnum.A, TestEnum.C), Set.of(TestEnum.A, TestEnum.C))
                .addCase(EnumSet.of(TestEnum.A, TestEnum.B), Set.of(TestEnum.A, TestEnum.B))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMutableEnumSetOf() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(TestEnum[].class)
                .withWrappedOutputType(new TypeLiteral<Set<TestEnum>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.mutableEnumSetOf(TestEnum.class, testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final Set<TestEnum> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isNotNull()
                            .isEqualTo(testOutcome.getExpectedOutput())
                            .isInstanceOf(EnumSet.class);

                })
                .addCase(null, Collections.emptySet())
                .addCase(new TestEnum[0], EnumSet.noneOf(TestEnum.class))
                .addCase(new TestEnum[]{null}, EnumSet.noneOf(TestEnum.class))
                .addCase(new TestEnum[]{null, TestEnum.A}, EnumSet.of(TestEnum.A))
                .addCase(new TestEnum[]{TestEnum.B, TestEnum.A}, EnumSet.of(TestEnum.B, TestEnum.A))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testUnmodifiableEnumSetOf() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(TestEnum[].class)
                .withWrappedOutputType(new TypeLiteral<Set<TestEnum>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.unmodifiableEnumSetOf(TestEnum.class, testCase.getInput()))
                .withAssertions(testOutcome -> {
                    final Set<TestEnum> actual = testOutcome.getActualOutput();
                    assertThat(actual)
                            .isNotNull()
                            .isEqualTo(testOutcome.getExpectedOutput())
                            .isInstanceOf(Set.class);

                    if (!actual.isEmpty()) {
                        Assertions.assertThatThrownBy(actual::clear)
                                .isInstanceOf(RuntimeException.class);
                    }
                })
                .addCase(null, Collections.emptySet())
                .addCase(new TestEnum[0], EnumSet.noneOf(TestEnum.class))
                .addCase(new TestEnum[]{null}, EnumSet.noneOf(TestEnum.class))
                .addCase(new TestEnum[]{null, TestEnum.A}, EnumSet.of(TestEnum.A))
                .addCase(new TestEnum[]{TestEnum.B, TestEnum.A}, EnumSet.of(TestEnum.B, TestEnum.A))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSingletonSet() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Set<String>>() {
                })
                .withSingleArgTestFunction(NullSafe::singletonSet)
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptySet())
                .addCase("foo", Set.of("foo"))
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
    Stream<DynamicTest> testValuesOf() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Map<Integer, String>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Collection<String>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.valuesOf(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptySet())
                .addCase(Collections.emptyMap(), Collections.emptySet())
                .addCase(Map.of(1, "foo", 2, "bar"), Set.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testKeySetOf() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Map<Integer, String>>() {
                })
                .withWrappedOutputType(new TypeLiteral<Collection<Integer>>() {
                })
                .withTestFunction(testCase ->
                        NullSafe.keySetOf(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptySet())
                .addCase(Collections.emptyMap(), Collections.emptySet())
                .addCase(Map.of(1, "foo", 2, "bar"), Set.of(1, 2))
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

//    @TestFactory
//    Stream<DynamicTest> testStroomDuration() {
//        return TestUtil.buildDynamicTestStream()
//                .withInputAndOutputType(StroomDuration.class)
//                .withTestFunction(testCase ->
//                        NullSafe.duration(testCase.getInput()))
//                .withSimpleEqualityAssertion()
//                .addCase(null, StroomDuration.ZERO)
//                .addCase(StroomDuration.ZERO, StroomDuration.ZERO)
//                .addCase(StroomDuration.ofSeconds(5), StroomDuration.ofSeconds(5))
//                .build();
//    }

    @TestFactory
    Stream<DynamicTest> testSimpleDuration() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(SimpleDuration.class)
                .withTestFunction(testCase ->
                        NullSafe.duration(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, SimpleDuration.ZERO)
                .addCase(SimpleDuration.ZERO, SimpleDuration.ZERO)
                .addCase(SimpleDuration.builder().timeUnit(TimeUnit.SECONDS).time(5).build(),
                        SimpleDuration.builder().timeUnit(TimeUnit.SECONDS).time(5).build())
                .build();
    }

//    @TestFactory
//    Stream<DynamicTest> testByteSize() {
//        return TestUtil.buildDynamicTestStream()
//                .withInputAndOutputType(ByteSize.class)
//                .withTestFunction(testCase ->
//                        NullSafe.byteSize(testCase.getInput()))
//                .withSimpleEqualityAssertion()
//                .addCase(null, ByteSize.ZERO)
//                .addCase(ByteSize.ZERO, ByteSize.ZERO)
//                .addCase(ByteSize.ofMebibytes(5), ByteSize.ofMebibytes(5))
//                .build();
//    }

    @TestFactory
    Stream<DynamicTest> testIsTrue() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Boolean.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(NullSafe::isTrue)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(false, false)
                .addCase(true, true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsTrue2() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<
                        AtomicReference<Boolean>,
                        Function<AtomicReference<Boolean>, Boolean>>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final AtomicReference<Boolean> ref = testCase.getInput()._1;
                    final Function<AtomicReference<Boolean>, Boolean> func = testCase.getInput()._2;
                    return NullSafe.isTrue(ref, func);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), false)
                .addThrowsCase(Tuple.of(new AtomicReference<>(), null), NullPointerException.class)
                .addThrowsCase(Tuple.of(new AtomicReference<>(false), null), NullPointerException.class)
                .addThrowsCase(Tuple.of(new AtomicReference<>(false), null), NullPointerException.class)
                .addCase(Tuple.of(new AtomicReference<>(false), AtomicReference::get), false)
                .addCase(Tuple.of(new AtomicReference<>(true), AtomicReference::get), true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetInt() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Integer.class)
                .withOutputType(int.class)
                .withSingleArgTestFunction(NullSafe::getInt)
                .withSimpleEqualityAssertion()
                .addCase(null, 0)
                .addCase(-1, -1)
                .addCase(1, 1)
                .addCase(Integer.MIN_VALUE, Integer.MIN_VALUE)
                .addCase(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testGetLong() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(long.class)
                .withSingleArgTestFunction(NullSafe::getLong)
                .withSimpleEqualityAssertion()
                .addCase(null, 0L)
                .addCase(-1L, -1L)
                .addCase(1L, 1L)
                .addCase(Long.MIN_VALUE, Long.MIN_VALUE)
                .addCase(Long.MAX_VALUE, Long.MAX_VALUE)
                .build();
    }

//    @TestFactory
//    Stream<DynamicTest> testDuration() {
//        return TestUtil.buildDynamicTestStream()
//                .withInputAndOutputType(Duration.class)
//                .withTestFunction(testCase ->
//                        NullSafe.duration(testCase.getInput()))
//                .withSimpleEqualityAssertion()
//                .addCase(null, Duration.ZERO)
//                .addCase(Duration.ZERO, Duration.ZERO)
//                .addCase(Duration.ofSeconds(5), Duration.ofSeconds(5))
//                .build();
//    }

    @TestFactory
    Stream<DynamicTest> testSupply() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Supplier<String>>() {
                })
                .withOutputType(String.class)
                .withSingleArgTestFunction(NullSafe::supply)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase(() -> null, null)
                .addCase(() -> "foo", "foo")
                .addCase(() -> "", "")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSupplyAndMap() {
        final Function<String, String> mapToNonNull = str -> str + "XXX";
        final Function<String, String> mapToNull = str -> null;

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Supplier<String>, Function<String, String>>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        NullSafe.supplyAndMap(testCase.getInput()._1, testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, mapToNonNull), null)
                .addCase(Tuple.of(() -> null, mapToNonNull), null)
                .addCase(Tuple.of(() -> null, mapToNull), null)
                .addCase(Tuple.of(() -> "foo", mapToNonNull), "fooXXX")
                .addCase(Tuple.of(() -> "foo", mapToNull), null)
                .addThrowsCase(Tuple.of(() -> "foo", null), NullPointerException.class)
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
                        NullSafe.consume(nonNullLevel1, Level1::getLevelNo, consumer),
                1L);
    }

    @Test
    void testConsume2_null() {
        doConsumeTest(consumer ->
                        NullSafe.consume(nullLevel1, Level1::getLevelNo, consumer),
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
                        Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, Level2::getLevelNo),
                        2L)
                .addNamedCase("Level 1 null",
                        Tuple.of(nullLevel1, Level1::getNonNullLevel2, Level2::getLevelNo),
                        -1L)
                .addNamedCase("Level 2 null",
                        Tuple.of(nonNullLevel1, Level1::getNullLevel2, Level2::getLevelNo),
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
                                Level3::getLevelNo),
                        3L)
                .addNamedCase("Level 1 null",
                        Tuple.of(
                                nullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevelNo),
                        -1L)
                .addNamedCase("Level 2 null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevelNo),
                        -1L)
                .addNamedCase("Level 3 null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNullLevel3,
                                Level3::getLevelNo),
                        -1L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testRun() {
        final MutableBoolean didRun = new MutableBoolean(false);
        final Runnable nonNullRunnable = didRun::setTrue;

        return TestUtil.buildDynamicTestStream()
                .withInputType(Runnable.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(runnable -> {
                    didRun.setFalse();
                    NullSafe.run(runnable);
                    return didRun.getValue();
                })
                .withSimpleEqualityAssertion()
                .addCase(nonNullRunnable, true)
                .addCase(null, false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testToString() {
        final Object obj = new Object() {
            @Override
            public String toString() {
                return "foo";
            }
        };
        return TestUtil.buildDynamicTestStream()
                .withInputType(Object.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(NullSafe::toString)
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase("foo", "foo")
                .addCase(obj, "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testConsumeOr1() {
        final AtomicBoolean consumerCalled = new AtomicBoolean(false);
        final Consumer<Level1> consumer = level1 -> {
            Objects.requireNonNull(level1);
            consumerCalled.set(true);
        };
        final AtomicBoolean runnableCalled = new AtomicBoolean(false);
        final Runnable runnable = () ->
                runnableCalled.set(true);

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(
                        new TypeLiteral<Tuple3<Level1, Consumer<Level1>, Runnable>>() {
                        })
                .withWrappedOutputType(new TypeLiteral<Tuple2<Boolean, Boolean>>() {
                })
                .withTestFunction(testCase -> {
                    NullSafe.consumeOr(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3);
                    return Tuple.of(consumerCalled.get(), runnableCalled.get());
                })
                .withSimpleEqualityAssertion()
                .withBeforeTestCaseAction(() -> {
                    consumerCalled.set(false);
                    runnableCalled.set(false);
                })
                .addCase(Tuple.of(nonNullLevel1, null, null), Tuple.of(false, false))
                .addCase(Tuple.of(nonNullLevel1, consumer, null), Tuple.of(true, false))
                .addCase(Tuple.of(nonNullLevel1, null, runnable), Tuple.of(false, false))
                .addCase(Tuple.of(nonNullLevel1, consumer, runnable), Tuple.of(true, false))
                .addCase(Tuple.of(nullLevel1, null, null), Tuple.of(false, false))
                .addCase(Tuple.of(nullLevel1, consumer, null), Tuple.of(false, false))
                .addCase(Tuple.of(nullLevel1, null, runnable), Tuple.of(false, true))
                .addCase(Tuple.of(nullLevel1, consumer, runnable), Tuple.of(false, true))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testConsumeOr2() {
        final AtomicBoolean consumerCalled = new AtomicBoolean(false);
        final Consumer<Level2> consumer = level2 -> {
            Objects.requireNonNull(level2);
            consumerCalled.set(true);
        };
        final AtomicBoolean runnableCalled = new AtomicBoolean(false);
        final Runnable runnable = () ->
                runnableCalled.set(true);

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(
                        new TypeLiteral<Tuple4<Level1, Function<Level1, Level2>, Consumer<Level2>, Runnable>>() {
                        })
                .withWrappedOutputType(new TypeLiteral<Tuple2<Boolean, Boolean>>() {
                })
                .withTestFunction(testCase -> {
                    NullSafe.consumeOr(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3,
                            testCase.getInput()._4);
                    return Tuple.of(consumerCalled.get(), runnableCalled.get());
                })
                .withSimpleEqualityAssertion()
                .withBeforeTestCaseAction(() -> {
                    consumerCalled.set(false);
                    runnableCalled.set(false);
                })
                .addCase(Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, null, null), Tuple.of(false, false))
                .addCase(Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, consumer, null), Tuple.of(true, false))
                .addCase(Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, null, runnable), Tuple.of(false, false))
                .addCase(Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, consumer, runnable), Tuple.of(true, false))

                .addCase(Tuple.of(nonNullLevel1, Level1::getNullLevel2, null, null), Tuple.of(false, false))
                .addCase(Tuple.of(nonNullLevel1, Level1::getNullLevel2, consumer, null), Tuple.of(false, false))
                .addCase(Tuple.of(nonNullLevel1, Level1::getNullLevel2, null, runnable), Tuple.of(false, true))
                .addCase(Tuple.of(nonNullLevel1, Level1::getNullLevel2, consumer, runnable), Tuple.of(false, true))

                .addCase(Tuple.of(nullLevel1, Level1::getNonNullLevel2, null, null), Tuple.of(false, false))
                .addCase(Tuple.of(nullLevel1, Level1::getNonNullLevel2, consumer, null), Tuple.of(false, false))
                .addCase(Tuple.of(nullLevel1, Level1::getNonNullLevel2, null, runnable), Tuple.of(false, true))
                .addCase(Tuple.of(nullLevel1, Level1::getNonNullLevel2, consumer, runnable), Tuple.of(false, true))

                .addCase(Tuple.of(nullLevel1, Level1::getNullLevel2, null, null), Tuple.of(false, false))
                .addCase(Tuple.of(nullLevel1, Level1::getNullLevel2, consumer, null), Tuple.of(false, false))
                .addCase(Tuple.of(nullLevel1, Level1::getNullLevel2, null, runnable), Tuple.of(false, true))
                .addCase(Tuple.of(nullLevel1, Level1::getNullLevel2, consumer, runnable), Tuple.of(false, true))

                .build();
    }

    @TestFactory
    Stream<DynamicTest> testTest3() {
        final var inputType = new TypeLiteral<Tuple5<
                Level1,
                Function<Level1, Level2>,
                Function<Level2, Level3>,
                Function<Level3, Long>,
                Boolean>>() { // bool allows us to make the predicate pass/fail
        };

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(inputType)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase ->
                        NullSafe.test(
                                testCase.getInput()._1,
                                testCase.getInput()._2,
                                testCase.getInput()._3,
                                testCase.getInput()._4,
                                val -> val > 0L && testCase.getInput()._5))
                .withSimpleEqualityAssertion()
                .addNamedCase("All non null, test => true",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevelNo,
                                true),
                        true)
                .addNamedCase("All non null, test => false",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevelNo,
                                false),
                        false)
                .addNamedCase("Level 1 null",
                        Tuple.of(
                                nullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevelNo,
                                true),
                        false)
                .addNamedCase("Level 2 null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getLevelNo,
                                true),
                        false)
                .addNamedCase("Level 3 null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNullLevel3,
                                Level3::getLevelNo,
                                true),
                        false)
                .addNamedCase("Level number null",
                        Tuple.of(
                                nonNullLevel1,
                                Level1::getNonNullLevel2,
                                Level2::getNonNullLevel3,
                                Level3::getNullLevelNo,
                                true),
                        false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testRequireNonNull2() {
        final var inputType = new TypeLiteral<Tuple2<
                Level1,
                Function<Level1, Level2>
                >>() {
        };

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(inputType)
                .withOutputType(Long.class)
                .withTestFunction(testCase -> {
                    return NullSafe.requireNonNull(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            () -> "Oh dear!").getLevelNo();
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(nonNullLevel1, Level1::getNonNullLevel2), 2L)
                .addThrowsCase(Tuple.of(nonNullLevel1, Level1::getNullLevel2), NullPointerException.class)
                .addThrowsCase(Tuple.of(nullLevel1, Level1::getNonNullLevel2), NullPointerException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testRequireNonNull3() {
        final var inputType = new TypeLiteral<Tuple3<
                Level1,
                Function<Level1, Level2>,
                Function<Level2, Level3>
                >>() {
        };

        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(inputType)
                .withOutputType(Long.class)
                .withTestFunction(testCase -> {
                    return NullSafe.requireNonNull(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3,
                            () -> "Oh dear!").getLevelNo();
                })
                .withSimpleEqualityAssertion()
                .addCase(
                        Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, Level2::getNonNullLevel3),
                        3L)
                .addThrowsCase(
                        Tuple.of(nonNullLevel1, Level1::getNonNullLevel2, Level2::getNullLevel3),
                        NullPointerException.class)
                .addThrowsCase(
                        Tuple.of(nonNullLevel1, Level1::getNullLevel2, Level2::getNonNullLevel3),
                        NullPointerException.class)
                .addThrowsCase(
                        Tuple.of(nullLevel1, Level1::getNonNullLevel2, Level2::getNonNullLevel3),
                        NullPointerException.class)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testPredicate() {
        final String valToTest = "foo";
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<Predicate<String>, Boolean>>() {
                })
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final Predicate<String> predicate = NullSafe.predicate(
                            testCase.getInput()._1,
                            testCase.getInput()._2());
                    return predicate.test(valToTest);
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, true), true)
                .addCase(Tuple.of(null, false), false)
                .addCase(Tuple.of(x -> x.equals("foo"), true), true)
                .addCase(Tuple.of(x -> x.equals("foo"), false), true)
                .addCase(Tuple.of(x -> x.equals("bar"), true), false)
                .addCase(Tuple.of(x -> x.equals("bar"), false), false)
                .build();
    }

    private void doConsumeTest(final Consumer<Consumer<Long>> action, final long expectedValue) {
        final AtomicLong val = new AtomicLong(-1);
        final Consumer<Long> consumer = val::set;

        action.accept(consumer);

        assertThat(val)
                .hasValue(expectedValue);
    }

    @Test
    @Disabled
    void testPerformanceVsOptional() {
        final int iterations = 100_000_000;
        final Level5 otherLevel5 = new Level5(-1);
        LOGGER.info("Iterations: {}", iterations);
        final MutableLong totalNanosNullSafe = new MutableLong(0);
        final MutableLong totalNanosOptional = new MutableLong(0);
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

    @Disabled
    @Test
    void testIsNullPerf() {

        final TimedCase pureJavaCase = TimedCase.of("Pure java", (round, iterations) -> {
            final Level1 nonNullLevel1 = new Level1();
            long i = 0;
            for (i = 0; i < iterations; i++) {
                if (nonNullLevel1 != null
                    && nonNullLevel1.getNonNullLevel2() != null
                    && nonNullLevel1.getNonNullLevel2().getNonNullLevel3() != null) {
                    i++;
                }
            }
            System.out.println(i);
        });
        final TimedCase nullSafeCase = TimedCase.of("NullSafe", (round, iterations) -> {
            final Level1 nonNullLevel1 = new Level1();
            long i = 0;
            for (i = 0; i < iterations; i++) {
                if (NullSafe.nonNull(nonNullLevel1, Level1::getNonNullLevel2, Level2::getNonNullLevel3)) {
                    i++;
                }
            }
        });
        final TimedCase optCase = TimedCase.of("Optional", (round, iterations) -> {
            final Level1 nonNullLevel1 = new Level1();
            long i = 0;
            for (i = 0; i < iterations; i++) {
                if (Optional.ofNullable(nonNullLevel1)
                        .map(Level1::getNonNullLevel2)
                        .map(Level2::getNonNullLevel3)
                        .filter(Objects::nonNull)
                        .isPresent()) {
                    i++;
                }
            }
        });

        TestUtil.comparePerformance(
                4,
                100_000_000L,
                LOGGER::info,
                pureJavaCase,
                nullSafeCase,
                optCase);
    }

    @Disabled // Long-running manual perf test
    @Test
    void testGetPerf() {
        final int iters = 200_000_000;
        final Integer[] vals = new Integer[iters];
        final String[] outputs = new String[iters];
        final Random random = new Random(123456);
        final int bound = 1_000;
        final int threshold = bound / 2;
        int nullsCount = 0;
        for (int i = 0; i < iters; i++) {
            final int val = random.nextInt(bound);
            if (val > threshold) {
                vals[i] = val;
            } else {
                vals[i] = null;
                nullsCount++;
            }
        }
        LOGGER.info("null count: {}", LogUtil.withPercentage(nullsCount, iters));

        final TestSetup testSetup = (rounds, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                outputs[i] = null;
            }
        };

        final TimedCase pureJavaIfCase = TimedCase.of("Pure java if", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final Integer val = vals[i];
                if (val == null) {
                    outputs[i] = null;
                } else {
                    outputs[i] = val.toString();
                }
            }
        });

        final TimedCase pureJavaTernaryCase = TimedCase.of("Pure java ternary", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final Integer val = vals[i];
                outputs[i] = val != null
                        ? val.toString()
                        : null;
            }
        });

        final TimedCase nullSafeCase = TimedCase.of("NullSafe", (round, iterations) -> {
            for (int i = 0; i < iterations; i++) {
                final Integer val = vals[i];
                outputs[i] = NullSafe.get(val, Objects::toString);
            }
        });

        TestUtil.comparePerformance(
                4,
                iters,
                testSetup,
                LOGGER::info,
                pureJavaIfCase,
                pureJavaTernaryCase,
                nullSafeCase);
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

        public Long getLevelNo() {
            return level;
        }

        public Long getNullLevelNo() {
            return null;
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


    // --------------------------------------------------------------------------------


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

        public Long getLevelNo() {
            return level;
        }

        public Long getNullLevelNo() {
            return null;
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


    // --------------------------------------------------------------------------------


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

        public Long getLevelNo() {
            return level;
        }

        public Long getNullLevelNo() {
            return null;
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


    // --------------------------------------------------------------------------------


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

        public Long getLevelNo() {
            return level;
        }

        public Long getNullLevelNo() {
            return null;
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


    // --------------------------------------------------------------------------------


    private static class Level5 {

        private final long id;
        private final Long level = 5L;

        private Level5(final long id) {
            this.id = id;
        }

        public Long getLevelNo() {
            return level;
        }

        public Long getNullLevelNo() {
            return null;
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


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


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


    // --------------------------------------------------------------------------------


    private static enum TestEnum {
        A,
        B,
        C,
        ;
    }


    // --------------------------------------------------------------------------------


    private record ABC(String a, String b, String c) {

    }
}
