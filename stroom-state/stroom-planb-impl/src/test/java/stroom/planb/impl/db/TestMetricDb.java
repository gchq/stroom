/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.planb.impl.data.TemporalValue;
import stroom.planb.impl.db.metric.MetricDb;
import stroom.planb.impl.db.metric.MetricFields;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.keyprefix.Tag;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.MaxValueSize;
import stroom.planb.shared.MetricKeySchema;
import stroom.planb.shared.MetricSettings;
import stroom.planb.shared.MetricValueSchema;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TemporalResolution;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetricDb {

    private static final int ITERATIONS = 100;
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final MetricSettings BASIC_SETTINGS = new MetricSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .valueSchema(new MetricValueSchema
                    .Builder()
                    .storeLatestValue(true)
                    .storeMin(true)
                    .storeMax(true)
                    .storeCount(true)
                    .storeSum(true)
                    .build())
            .build();
    private static final PlanBDoc DOC = getDoc(BASIC_SETTINGS);

    private final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
    private final List<KeyFunction> keyFunctions = List.of(
            new KeyFunction(KeyType.TAGS.name(), KeyType.TAGS,
                    i -> getKey(refTime)));

    private KeyPrefix getTags() {
        final List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("captain", ValString.create("kirk")));
        tags.add(new Tag("mr", ValString.create("spock")));
        tags.add(new Tag("dr", ValString.create("mccoy")));
        tags.add(new Tag("lieutenant", ValString.create("uhura")));
        return KeyPrefix.create(tags);
    }

    private TemporalKey getKey(final Instant time) {
        return new TemporalKey(getTags(), time);
    }

    @Test
    void test(@TempDir final Path tempDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final MetricDb db = MetricDb.create(tempDir, BYTE_BUFFERS, DOC, false)) {
            for (int i = 0; i < 100; i++) {
                insertData(db, refTime, i + 10, 10, 1);
            }
        }

        try (final MetricDb db = MetricDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(1);

            final KeyPrefix tags = getTags();
            // Check exact time states.
            checkMetric(db, tags, refTime, 109L);

            final TemporalKey key = getKey(refTime);
            final Long value = db.get(key);
            assertThat(value).isNotNull();
            assertThat(value).isEqualTo(109L);

            final FieldIndex fieldIndex = getFieldIndex();
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(3600L);
            assertThat(results.getFirst()[0].toString())
                    .isEqualTo("captain=kirk, dr=mccoy, lieutenant=uhura, mr=spock");
            assertThat(results.getFirst()[1].toString()).isEqualTo("2000-01-01T00:00:00.000Z");
            assertThat(results.getFirst()[2].toString()).isEqualTo("Second");
            assertThat(results.getFirst()[3].toString()).isEqualTo("109");
            assertThat(results.getFirst()[4].toString()).isEqualTo("10");
            assertThat(results.getFirst()[5].toString()).isEqualTo("109");
            assertThat(results.getFirst()[6].toString()).isEqualTo("100");
            assertThat(results.getFirst()[7].toString()).isEqualTo("5950");
            assertThat(results.getFirst()[8].toString()).isEqualTo("59");
        }
    }

    private FieldIndex getFieldIndex() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create(MetricFields.KEY);
        fieldIndex.create(MetricFields.TIME);
        fieldIndex.create(MetricFields.RESOLUTION);
        fieldIndex.create(MetricFields.VALUE);
        fieldIndex.create(MetricFields.MIN);
        fieldIndex.create(MetricFields.MAX);
        fieldIndex.create(MetricFields.COUNT);
        fieldIndex.create(MetricFields.SUM);
        fieldIndex.create(MetricFields.AVERAGE);
        return fieldIndex;
    }

    @Test
    void testGetMetric(@TempDir final Path tempDir) {
        final KeyPrefix tags = getTags();
        final Instant time = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final MetricDb db = MetricDb.create(tempDir, BYTE_BUFFERS, DOC, false)) {
            db.write(writer -> {
                final TemporalKey k = getKey(time);
                db.insert(writer, new TemporalValue(k, 1L));
            });
        }

        try (final MetricDb db = MetricDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(1);
            checkMetric(db, tags, time, 1L);
        }
    }

    @TestFactory
    Collection<DynamicTest> testMultiWrite() {
        return createMultiKeyTest(1, false, 1);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWritePerformance() {
        return createMultiKeyTest(ITERATIONS, false, 1);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteRead() {
        return createMultiKeyTest(1, true, 1);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteReadPerformance() {
        return createMultiKeyTest(ITERATIONS, true, 1);
    }

    Collection<DynamicTest> createMultiKeyTest(final int iterations,
                                               final boolean read,
                                               final long expectedValue) {
        final List<DynamicTest> tests = new ArrayList<>();
        for (final KeyFunction keyFunction : keyFunctions) {
//            for (final ValueFunction valueFunction : MetricValueTestUtil.getValueFunctions()) {
//            for (final MetricPeriod period : MetricPeriod.values()) {

            final TemporalResolution temporalResolution = TemporalResolution.HOUR;
            tests.add(DynamicTest.dynamicTest("key type = " + keyFunction +
//                                                      ", Value type = " + valueFunction +
                                              ", Temporal resolution = " + temporalResolution,
                    () -> {
                        final MetricSettings settings = new MetricSettings
                                .Builder()
                                .keySchema(new MetricKeySchema.Builder()
                                        .keyType(keyFunction.keyType)
                                        .temporalResolution(temporalResolution)
                                        .build())
                                .valueSchema(new MetricValueSchema.Builder()
                                        .valueType(MaxValueSize.TWO)
                                        .build())
                                .build();

                        Path path = null;
                        try {
                            path = Files.createTempDirectory("stroom");

                            testWrite(path, settings, iterations,
                                    keyFunction.function,
                                    i -> 1L);
                            if (read) {
                                testSimpleRead(path, settings, iterations,
                                        keyFunction.function,
                                        i -> expectedValue);
                            }

                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        } finally {
                            if (path != null) {
                                FileUtil.deleteDir(path);
                            }
                        }
                    }));
//            }
//            }
        }
        return tests;
    }

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1);
        testWrite(dbPath2);

        try (final MetricDb db = MetricDb.create(dbPath1, BYTE_BUFFERS, DOC, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        try (final MetricDb db = MetricDb.create(dbPath, BYTE_BUFFERS, DOC, false)) {
            assertThat(db.count()).isEqualTo(1);
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(1);
            db.deleteOldData(Instant.now(), true);
            assertThat(db.count()).isEqualTo(0);
        }
    }

    @Test
    void testDelete2(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final MetricDb db = MetricDb.create(dbPath, BYTE_BUFFERS, DOC, false)) {
            insertData(db, refTime, 1L, 100, 60 * 60 * 24);
            insertData(db, refTime, 1L, 100, 60 * 60 * 24);
            insertData(db, refTime, 1L, 10, -60 * 60 * 24);
            insertData(db, refTime, 1L, 10, -60 * 60 * 24);

            assertThat(db.count()).isEqualTo(109L);

            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(109L);

            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(109L);
        }
    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final MetricDb db = MetricDb.create(dbDir, BYTE_BUFFERS, DOC, false)) {
            insertData(db, refTime, 1, 100, 10);
        }
    }

    private void testWrite(final Path dbDir,
                           final MetricSettings settings,
                           final int insertRows,
                           final Function<Integer, TemporalKey> keyFunction,
                           final Function<Integer, Long> valueFunction) {
        try (final MetricDb db = MetricDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), false)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void insertData(final MetricDb db,
                            final int rows,
                            final Function<Integer, TemporalKey> keyFunction,
                            final Function<Integer, Long> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final TemporalKey k = keyFunction.apply(i);
                final Long v = valueFunction.apply(i);
                db.insert(writer, new TemporalValue(k, v));
            }
        });
    }

    private void testSimpleRead(final Path dbDir,
                                final MetricSettings settings,
                                final int rows,
                                final Function<Integer, TemporalKey> keyFunction,
                                final Function<Integer, Long> valueFunction) {
        try (final MetricDb db = MetricDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), true)) {
            for (int i = 0; i < rows; i++) {
                final TemporalKey key = keyFunction.apply(i);
                final Long value = db.get(key);
                assertThat(value).isNotNull();
                assertThat(value).isEqualTo(valueFunction.apply(i));
            }
        }
    }

    private void insertData(final MetricDb db,
                            final Instant refTime,
                            final long delta,
                            final int rows,
                            final long deltaSeconds) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Instant time = refTime.plusSeconds(i * deltaSeconds);
                final TemporalKey k = getKey(time);
                db.insert(writer, new TemporalValue(k, delta));
            }
        });
    }

    private void checkMetric(final MetricDb db,
                             final KeyPrefix tags,
                             final Instant time,
                             final Long expected) {
        final TemporalKey key = new TemporalKey(tags, time);
        final Long state = db.get(key);
        assertThat(state).isEqualTo(expected);
    }

    private static PlanBDoc getDoc(final MetricSettings settings) {
        return PlanBDoc.builder().uuid(UUID.randomUUID().toString()).name("test").settings(settings).build();
    }

    private record KeyFunction(String description,
                               KeyType keyType,
                               Function<Integer, TemporalKey> function) {

        @Override
        public String toString() {
            return description;
        }
    }
}
