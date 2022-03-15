package stroom.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.lang3.mutable.MutableLong;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
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
    void testTest1NonNull() {
        Assertions.assertThat(NullSafe.test(
                nonNullLevel1,
                Level1::getLevel,
                level -> level == 1L))
                .isTrue();
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
    void testTest2NonNull() {
        Assertions.assertThat(NullSafe.test(
                nonNullLevel1,
                Level1::getNonNullLevel2,
                Level2::getLevel,
                level -> level == 2L))
                .isTrue();
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
        Assertions.assertThat(NullSafe.isEmpty(list))
                .isTrue();
        Assertions.assertThat(NullSafe.isNotEmpty(list))
                .isFalse();
    }

    @Test
    void testIsEmpty_Empty() {
        final List<String> list = Collections.emptyList();
        Assertions.assertThat(NullSafe.isEmpty(list))
                .isTrue();
        Assertions.assertThat(NullSafe.isNotEmpty(list))
                .isFalse();
    }

    @Test
    void testIsEmpty_NotEmpty() {
        final List<String> list = List.of("X");
        Assertions.assertThat(NullSafe.isEmpty(list))
                .isFalse();
        Assertions.assertThat(NullSafe.isNotEmpty(list))
                .isTrue();
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
}
