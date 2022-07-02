package stroom.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.lang3.mutable.MutableLong;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class TestNullSafe {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestNullSafe.class);

    private final Level1 nullLevel1 = null;
    private final Level1 nonNullLevel1 = new Level1();
    private final long other = 99L;

    private long getOther() {
        return other;
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

    @Test
    void testIsEmpty_Null() {
        final List<String> list = null;
        Assertions.assertThat(NullSafe.isEmptyCollection(list))
                .isTrue();
        Assertions.assertThat(NullSafe.hasItems(list))
                .isFalse();
    }

    @Test
    void testIsEmpty_Empty() {
        final List<String> list = Collections.emptyList();
        Assertions.assertThat(NullSafe.isEmptyCollection(list))
                .isTrue();
        Assertions.assertThat(NullSafe.hasItems(list))
                .isFalse();
    }

    @Test
    void testIsEmpty_NotEmpty() {
        final List<String> list = List.of("X");
        Assertions.assertThat(NullSafe.isEmptyCollection(list))
                .isFalse();
        Assertions.assertThat(NullSafe.hasItems(list))
                .isTrue();
    }

    @Test
    void testIsEmpty2_Null() {
        final ListWrapper nullListWrapper = null;
        Assertions.assertThat(NullSafe.isEmptyCollection(nullListWrapper, ListWrapper::getNonNullNonEmptyList))
                .isTrue();
        Assertions.assertThat(NullSafe.hasItems(nullListWrapper, ListWrapper::getNonNullNonEmptyList))
                .isFalse();

        final ListWrapper nonNullListWrapper = new ListWrapper();
        Assertions.assertThat(NullSafe.isEmptyCollection(nonNullListWrapper, ListWrapper::getNullList))
                .isTrue();
        Assertions.assertThat(NullSafe.hasItems(nonNullListWrapper, ListWrapper::getNullList))
                .isFalse();
    }

    @Test
    void testIsEmptyMap_Null() {
        final Map<String, String> map = null;
        Assertions.assertThat(NullSafe.isEmptyMap(map))
                .isTrue();
        Assertions.assertThat(NullSafe.hasEntries(map))
                .isFalse();
    }

    @Test
    void testIsEmptyMap_Empty() {
        final Map<String, String> map = Collections.emptyMap();
        Assertions.assertThat(NullSafe.isEmptyMap(map))
                .isTrue();
        Assertions.assertThat(NullSafe.hasEntries(map))
                .isFalse();
    }

    @Test
    void testIsEmptyMap_NotEmpty() {
        final Map<String, String> map = Map.of("foo", "bar");
        Assertions.assertThat(NullSafe.isEmptyMap(map))
                .isFalse();
        Assertions.assertThat(NullSafe.hasEntries(map))
                .isTrue();
    }

    @Test
    void testIsEmptyMap2_Null() {
        final MapWrapper nullMapWrapper = null;
        Assertions.assertThat(NullSafe.isEmptyMap(nullMapWrapper, MapWrapper::getNonNullNonEmptyMap))
                .isTrue();
        Assertions.assertThat(NullSafe.hasEntries(nullMapWrapper, MapWrapper::getNonNullNonEmptyMap))
                .isFalse();

        final MapWrapper nonNullMapWrapper = new MapWrapper();
        Assertions.assertThat(NullSafe.isEmptyMap(nonNullMapWrapper, MapWrapper::getNullMap))
                .isTrue();
        Assertions.assertThat(NullSafe.hasEntries(nonNullMapWrapper, MapWrapper::getNullMap))
                .isFalse();
    }

    @Test
    void testIsEmptyMap2_Empty() {
        final MapWrapper nonNullMapWrapper = new MapWrapper();
        Assertions.assertThat(NullSafe.isEmptyMap(nonNullMapWrapper, MapWrapper::getNonNullEmptyMap))
                .isTrue();
        Assertions.assertThat(NullSafe.hasEntries(nonNullMapWrapper, MapWrapper::getNonNullEmptyMap))
                .isFalse();
    }

    @Test
    void testIsEmptyMap2_NotEmpty() {
        final MapWrapper nonNullMapWrapper = new MapWrapper();
        Assertions.assertThat(NullSafe.isEmptyMap(nonNullMapWrapper, MapWrapper::getNonNullNonEmptyMap))
                .isFalse();
        Assertions.assertThat(NullSafe.hasEntries(nonNullMapWrapper, MapWrapper::getNonNullNonEmptyMap))
                .isTrue();
    }

    @Test
    void testIsEmptyString_Null() {
        final String str = null;
        Assertions.assertThat(NullSafe.isEmptyString(str))
                .isTrue();
        Assertions.assertThat(NullSafe.isBlankString(str))
                .isTrue();
    }

    @Test
    void testIsEmptyString_Empty() {
        final String str = "";
        Assertions.assertThat(NullSafe.isEmptyString(str))
                .isTrue();
        Assertions.assertThat(NullSafe.isBlankString(str))
                .isTrue();
    }

    @Test
    void testIsEmptyString_Blank() {
        final String str = " ";
        Assertions.assertThat(NullSafe.isEmptyString(str))
                .isFalse();
        Assertions.assertThat(NullSafe.isBlankString(str))
                .isTrue();
    }

    @Test
    void testIsEmptyString_NotEmpty() {
        final String str = "foobar";
        Assertions.assertThat(NullSafe.isEmptyString(str))
                .isFalse();
        Assertions.assertThat(NullSafe.isBlankString(str))
                .isFalse();
    }

    @Test
    void testIsEmptyString2() {
        final StringWrapper nullStringWrapper = null;
        Assertions.assertThat(NullSafe.isEmptyString(nullStringWrapper, StringWrapper::getNonNullNonEmptyString))
                .isTrue();
        Assertions.assertThat(NullSafe.isBlankString(nullStringWrapper, StringWrapper::getNonNullNonEmptyString))
                .isTrue();

        final StringWrapper nonNullStringWrapper = new StringWrapper();
        Assertions.assertThat(NullSafe.isEmptyString(nonNullStringWrapper, StringWrapper::getNullString))
                .isTrue();
        Assertions.assertThat(NullSafe.isBlankString(nonNullStringWrapper, StringWrapper::getNullString))
                .isTrue();

        Assertions.assertThat(NullSafe.isEmptyString(nonNullStringWrapper, StringWrapper::getNonNullEmptyString))
                .isTrue();
        Assertions.assertThat(NullSafe.isBlankString(nonNullStringWrapper, StringWrapper::getNonNullEmptyString))
                .isTrue();

        Assertions.assertThat(NullSafe.isEmptyString(nonNullStringWrapper, StringWrapper::getNonNullBlankString))
                .isFalse();
        Assertions.assertThat(NullSafe.isBlankString(nonNullStringWrapper, StringWrapper::getNonNullBlankString))
                .isTrue();

        Assertions.assertThat(NullSafe.isEmptyString(nonNullStringWrapper, StringWrapper::getNonNullNonEmptyString))
                .isFalse();
        Assertions.assertThat(NullSafe.isBlankString(nonNullStringWrapper, StringWrapper::getNonNullNonEmptyString))
                .isFalse();
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
