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
import stroom.util.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.Tuple4;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableLong;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


class TestGwtNullSafe {

    private final Level1 nullLevel1 = null;
    private final Level1 nonNullLevel1 = new Level1();
    private final long other = 99L;

    private long getOther() {
        return other;
    }

    @Test
    void testEquals1() {
        // Null parent
        assertThat(GwtNullSafe.equals(nullLevel1,
                Level1::getNonNullLevel2,
                nonNullLevel1.getNonNullLevel2()))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1,
                Level1::getNullLevel2,
                nonNullLevel1.getNonNullLevel2()))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1, Level1::getNonNullLevel2, "foobar"))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1, Level1::getNonNullLevel2, null))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                nonNullLevel1.getNonNullLevel2()))
                .isTrue();
    }

    @Test
    void testEquals2() {
        // Null parent
        assertThat(GwtNullSafe.equals(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNonNullLevel3,
                nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNullLevel3,
                nonNullLevel1.getNonNullLevel2().getNonNullLevel3()))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                "foobar"))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                null))
                .isFalse();
        assertThat(GwtNullSafe.equals(nonNullLevel1,
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
                    return GwtNullSafe.equalProperties(v1, v2, AtomicReference::get);
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
    Stream<DynamicTest> testAllNull() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String[].class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(GwtNullSafe::allNull)
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
                .withInputType(String[].class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(GwtNullSafe::allNonNull)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase(new String[]{null}, false)
                .addCase(new String[]{null, null}, false)
                .addCase(new String[]{null, null, null}, false)
                .addCase(new String[]{"foo", null, null}, false)
                .addCase(new String[]{null, "foo", null}, false)
                .addCase(new String[]{null, "foo", "foo"}, false)
                .addCase(new String[]{"1", "2", "3"}, true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCoalesce_twoValues() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withWrappedOutputType(new TypeLiteral<Optional<String>>() {
                })
                .withTestFunction(testCase ->
                        GwtNullSafe.coalesce(testCase.getInput()._1, testCase.getInput()._2))
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
                        GwtNullSafe.coalesce(
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
                        GwtNullSafe.coalesce(
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
        assertThat(GwtNullSafe.get(
                nullLevel1,
                Level1::getLevel))
                .isNull();

        assertThat(GwtNullSafe.getOrElse(
                nullLevel1,
                Level1::getLevel,
                other))
                .isEqualTo(other);

        assertThat(GwtNullSafe.getOrElseGet(
                nullLevel1,
                Level1::getLevel,
                this::getOther))
                .isEqualTo(other);

        assertThat(GwtNullSafe.get(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevel))
                .isNull();

        assertThat(GwtNullSafe.get(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getLevel))
                .isNull();

        assertThat(GwtNullSafe.get(
                nullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevel))
                .isNull();
    }

    @Test
    void testGet1NonNull() {
        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getLevel))
                .isEqualTo(1L);
    }

    @Test
    void testGet2Null() {
        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevel))
                .isNull();

        assertThat(GwtNullSafe.getOrElse(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevel,
                other))
                .isEqualTo(other);

        assertThat(GwtNullSafe.getOrElseGet(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevel,
                this::getOther))
                .isEqualTo(other);

        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getLevel))
                .isNull();

        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevel))
                .isNull();
    }

    @Test
    void testGet2NonNull() {
        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevel))
                .isEqualTo(2L);
    }

    @Test
    void testGet3Null() {
        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNullLevel3,
                Level3::getLevel))
                .isNull();

        assertThat(GwtNullSafe.getOrElse(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getNullLevel3,
                Level3::getLevel,
                other))
                .isEqualTo(other);

        assertThat(GwtNullSafe.getOrElseGet(
                nonNullLevel1,
                Level1::getNullLevel2,
                Level2::getLevel,
                this::getOther))
                .isEqualTo(other);

        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevel))
                .isNull();
    }

    @Test
    void testGet3NonNull() {
        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getLevel))
                .isEqualTo(3L);
    }

    @Test
    void testGet4Null() {
        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNullLevel4,
                Level4::getLevel))
                .isNull();

        assertThat(GwtNullSafe.getOrElse(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNullLevel4,
                Level4::getLevel,
                other))
                .isEqualTo(other);

        assertThat(GwtNullSafe.getOrElseGet(
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
        assertThat(GwtNullSafe.get(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getNonNullLevel3,
                Level3::getNonNullLevel4,
                Level4::getLevel))
                .isEqualTo(4L);
    }

    @Test
    void testTest0NonNullTrue() {
        assertThat(
                GwtNullSafe.test(
                        "foo",
                        str -> str.equals("foo")))
                .isTrue();
    }

    @Test
    void testTest0NonNullFalse() {
        assertThat(GwtNullSafe.test(
                "foo",
                str -> str.equals("bar")))
                .isFalse();
    }

    @Test
    void testTest0Null() {
        assertThat(GwtNullSafe.test(
                null,
                str -> str.equals("foo")))
                .isFalse();
    }

    @Test
    void testTest1NonNullTrue() {
        assertThat(GwtNullSafe.test(
                nonNullLevel1,
                Level1::getLevel,
                level -> level == 1L))
                .isTrue();
    }

    @Test
    void testTest1NonNullFalse() {
        assertThat(GwtNullSafe.test(
                nonNullLevel1,
                Level1::getLevel,
                level -> level != 1L))
                .isFalse();
    }

    @Test
    void testTest1Null() {
        assertThat(GwtNullSafe.test(
                nullLevel1,
                Level1::getLevel,
                level -> level == 1L))
                .isFalse();
    }

    @Test
    void testTest2NonNullTrue() {
        assertThat(GwtNullSafe.test(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevel,
                level -> level == 2L))
                .isTrue();
    }

    @Test
    void testTest2NonNullFalse() {
        assertThat(GwtNullSafe.test(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevel,
                level -> level != 2L))
                .isFalse();
    }

    @Test
    void testTest2Null() {
        assertThat(GwtNullSafe.test(
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
                        GwtNullSafe.isEmptyCollection(testCase.getInput()))
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
                        GwtNullSafe.hasItems(testCase.getInput()))
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
                        GwtNullSafe.size(testCase.getInput()))
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
                    return GwtNullSafe.isEmptyCollection(listWrapper, getter);
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
                    return GwtNullSafe.hasItems(listWrapper, getter);
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
                        GwtNullSafe.isEmptyMap(testCase.getInput()))
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
                        GwtNullSafe.hasEntries(testCase.getInput()))
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
                        GwtNullSafe.size(testCase.getInput()))
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
                    return GwtNullSafe.isEmptyMap(mapWrapper, getter);
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
                    return GwtNullSafe.hasEntries(mapWrapper, getter);
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
                        GwtNullSafe.isEmptyString(testCase.getInput()))
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
                        GwtNullSafe.isNonEmptyString(testCase.getInput()))
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
    Stream<DynamicTest> testIsBlankString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase ->
                        GwtNullSafe.isBlankString(testCase.getInput()))
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
                        GwtNullSafe.isNonBlankString(testCase.getInput()))
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
                        GwtNullSafe.nonBlankStringElse(testCase.getInput(), other))
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
                        GwtNullSafe.nonBlankStringElseGet(testCase.getInput(), supplier))
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
    Stream<DynamicTest> testConsumeNonBlankString() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final AtomicBoolean wasConsumed = new AtomicBoolean(false);
                    GwtNullSafe.consumeNonBlankString(testCase.getInput(), str -> {
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
                    GwtNullSafe.consumeNonBlankString(testCase.getInput(), false, str -> {
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
                    GwtNullSafe.consumeNonBlankString(testCase.getInput(), true, consumedStrRef::set);
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
                        GwtNullSafe.join(", ", input))
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
                    var str = testCase.getInput()._1;
                    var subStr = testCase.getInput()._2;
                    return GwtNullSafe.contains(str, subStr);
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
                    return GwtNullSafe.isEmptyString(stringWrapper, getter);
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
                    return GwtNullSafe.isBlankString(stringWrapper, getter);
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
                    GwtNullSafe.consumeNonBlankString(value, getter, str -> {
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
                    final List<String> newList = GwtNullSafe.stream(testCase.getInput())
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
                    final List<String> newList = GwtNullSafe.stream(testCase.getInput())
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
    Stream<DynamicTest> testAsList() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<String[]>() {
                })
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        GwtNullSafe.asList(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase(new String[]{}, Collections.emptyList())
                .addCase(new String[]{"foo", "bar"}, List.of("foo", "bar"))
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
                        GwtNullSafe.asSet(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptySet())
                .addCase(new String[]{}, Collections.emptySet())
                .addCase(new String[]{"foo", "bar"}, Set.of("foo", "bar"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testList() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputAndOutputType(new TypeLiteral<List<String>>() {
                })
                .withTestFunction(testCase ->
                        GwtNullSafe.list(testCase.getInput()))
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
                        GwtNullSafe.set(testCase.getInput()))
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
                        GwtNullSafe.map(testCase.getInput()))
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
                        GwtNullSafe.string(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, "")
                .addCase("", "")
                .addCase("foo", "foo")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testSimpleDuration() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(SimpleDuration.class)
                .withTestFunction(testCase ->
                        GwtNullSafe.duration(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, SimpleDuration.ZERO)
                .addCase(SimpleDuration.ZERO, SimpleDuration.ZERO)
                .addCase(SimpleDuration.builder().timeUnit(TimeUnit.SECONDS).time(5).build(),
                        SimpleDuration.builder().timeUnit(TimeUnit.SECONDS).time(5).build())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsTrue() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Boolean.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> GwtNullSafe.isTrue(testCase.getInput()))
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
                    return GwtNullSafe.isTrue(ref, func);
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

    @Test
    void testConsume1() {
        doConsumeTest(consumer ->
                        GwtNullSafe.consume(123L, consumer),
                123L);
    }

    @Test
    void testConsume1_null() {
        doConsumeTest(consumer ->
                        GwtNullSafe.consume(null, consumer),
                -1);
    }

    @Test
    void testConsume2() {
        doConsumeTest(consumer ->
                        GwtNullSafe.consume(nonNullLevel1, Level1::getLevel, consumer),
                1L);
    }

    @Test
    void testConsume2_null() {
        doConsumeTest(consumer ->
                        GwtNullSafe.consume(nullLevel1, Level1::getLevel, consumer),
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
                    GwtNullSafe.consume(
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
                    GwtNullSafe.consume(
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

    @TestFactory
    Stream<DynamicTest> testRun() {
        final MutableBoolean didRun = new MutableBoolean(false);
        final Runnable nonNullRunnable = didRun::setTrue;

        return TestUtil.buildDynamicTestStream()
                .withInputType(Runnable.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(runnable -> {
                    didRun.setFalse();
                    GwtNullSafe.run(runnable);
                    return didRun.getValue();
                })
                .withSimpleEqualityAssertion()
                .addCase(nonNullRunnable, true)
                .addCase(null, false)
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
        MutableLong totalNanosNullSafe = new MutableLong(0);
        MutableLong totalNanosOptional = new MutableLong(0);
        for (int i = 0; i < 3; i++) {
            totalNanosNullSafe.setValue(0L);

            for (int j = 0; j < iterations; j++) {
                // Ensure we have a different object each time
                final Level1 level1 = new Level1(j);
                final long startNanos = System.nanoTime();
                final Level5 val = GwtNullSafe.getOrElse(
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

            totalNanosOptional.setValue(0L);

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


    // --------------------------------------------------------------------------------


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
}
