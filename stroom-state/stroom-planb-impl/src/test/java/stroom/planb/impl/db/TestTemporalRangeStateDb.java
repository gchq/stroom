/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.planb.impl.data.TemporalRangeState;
import stroom.planb.impl.data.TemporalRangeState.Key;
import stroom.planb.impl.db.StateValueTestUtil.ValueFunction;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateDb;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateFields;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateRequest;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangeType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalRangeKeySchema;
import stroom.planb.shared.TemporalRangeStateSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Type;
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

class TestTemporalRangeStateDb {

    private static final int ITERATIONS = 100;
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final TemporalRangeStateSettings BASIC_SETTINGS = new TemporalRangeStateSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();
    private static final PlanBDoc DOC = getDoc(BASIC_SETTINGS);

    @Test
    void test(@TempDir final Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalRangeStateDb db = TemporalRangeStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(100);
            testGet(db);

            // Check exact time states.
            checkState(db, 9, refTime, false);
            for (int i = 10; i <= 30; i++) {
                checkState(db, i, refTime, true);
            }
            checkState(db, 31, refTime, false);

            // Check before time states.
            for (int i = 9; i <= 31; i++) {
                checkState(db, i, refTime.minusMillis(1), false);
            }

            // Check after time states.
            checkState(db, 9, refTime.plusMillis(1), false);
            for (int i = 10; i <= 30; i++) {
                checkState(db, i, refTime.plusMillis(1), true);
            }
            checkState(db, 31, refTime.plusMillis(1), false);

            final TemporalRangeStateRequest stateRequest =
                    new TemporalRangeStateRequest(11, refTime);
            final TemporalRangeState state = db.getState(stateRequest);
            assertThat(state).isNotNull();
            assertThat(state.key().getKeyStart()).isEqualTo(10);
            assertThat(state.key().getKeyEnd()).isEqualTo(30);
            assertThat(state.key().getTime()).isEqualTo(refTime);
            assertThat(state.val().type()).isEqualTo(Type.STRING);
            assertThat(state.val().toString()).isEqualTo("test");

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(TemporalRangeStateFields.KEY_START);
            fieldIndex.create(TemporalRangeStateFields.KEY_END);
            fieldIndex.create(TemporalRangeStateFields.EFFECTIVE_TIME);
            fieldIndex.create(TemporalRangeStateFields.VALUE_TYPE);
            fieldIndex.create(TemporalRangeStateFields.VALUE);
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(100);
            assertThat(results.getFirst()[0].toString()).isEqualTo("10");
            assertThat(results.getFirst()[1].toString()).isEqualTo("30");
            assertThat(results.getFirst()[2].toString()).isEqualTo("2000-01-01T00:00:00.000Z");
            assertThat(results.getFirst()[3].toString()).isEqualTo("string");
            assertThat(results.getFirst()[4].toString()).isEqualTo("test");
        }
    }


    @TestFactory
    Collection<DynamicTest> testMultiWrite() {
        return createMultiKeyTest(1, false);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWritePerformance() {
        return createMultiKeyTest(ITERATIONS, false);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteRead() {
        return createMultiKeyTest(1, true);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteReadPerformance() {
        return createMultiKeyTest(ITERATIONS, true);
    }

    Collection<DynamicTest> createMultiKeyTest(final int iterations, final boolean read) {
        final List<DynamicTest> tests = new ArrayList<>();
        for (final RangeType rangeType : RangeType.values()) {
            for (final ValueFunction valueFunction : StateValueTestUtil.getValueFunctions()) {
                for (final TemporalPrecision temporalPrecision : TemporalPrecision.values()) {
                    tests.add(DynamicTest.dynamicTest("Range type = " + rangeType +
                                                      ", Value type = " + valueFunction +
                                                      ", Temporal precision = " + temporalPrecision,
                            () -> {
                                final TemporalRangeStateSettings settings = new TemporalRangeStateSettings.Builder()
                                        .keySchema(new TemporalRangeKeySchema.Builder()
                                                .rangeType(rangeType)
                                                .temporalPrecision(temporalPrecision)
                                                .build())
                                        .valueSchema(new StateValueSchema.Builder()
                                                .stateValueType(valueFunction.stateValueType())
                                                .build())
                                        .build();

                                final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
                                final Function<Integer, Key> keyFunction = i -> new Key(i, i + 1, refTime);

                                Path path = null;
                                try {
                                    path = Files.createTempDirectory("stroom");

                                    testWrite(path, settings, iterations, keyFunction,
                                            valueFunction.function());
                                    if (read) {
                                        testSimpleRead(path, settings, iterations, keyFunction,
                                                valueFunction.function());
                                    }

                                } catch (final IOException e) {
                                    throw new UncheckedIOException(e);
                                } finally {
                                    if (path != null) {
                                        FileUtil.deleteDir(path);
                                    }
                                }
                            }));
                }
            }
        }
        return tests;
    }


    private void testWrite(final Path dbDir,
                           final TemporalRangeStateSettings settings,
                           final int insertRows,
                           final Function<Integer, Key> keyFunction,
                           final Function<Integer, Val> valueFunction) {
        try (final TemporalRangeStateDb db = TemporalRangeStateDb.create(dbDir,
                BYTE_BUFFERS,
                getDoc(settings),
                false)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void insertData(final TemporalRangeStateDb db,
                            final int rows,
                            final Function<Integer, Key> keyFunction,
                            final Function<Integer, Val> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Key k = keyFunction.apply(i);
                final Val v = valueFunction.apply(i);
                db.insert(writer, new TemporalRangeState(k, v));
            }
        });
    }

    private void testSimpleRead(final Path dbDir,
                                final TemporalRangeStateSettings settings,
                                final int rows,
                                final Function<Integer, Key> keyFunction,
                                final Function<Integer, Val> valueFunction) {
        try (final TemporalRangeStateDb db = TemporalRangeStateDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), true)) {
            for (int i = 0; i < rows; i++) {
                final Key key = keyFunction.apply(i);
                final TemporalRangeState temporalState = db.getState(
                        new TemporalRangeStateRequest(key.getKeyStart(), key.getTime()));
                assertThat(temporalState).isNotNull();
                assertThat(temporalState.val().type()).isEqualTo(valueFunction.apply(i).type());
//                assertThat(value).isEqualTo(expectedVal); // Values will not be the same due to key overwrite.
            }
        }
    }


    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1);
        testWrite(dbPath2);

        try (final TemporalRangeStateDb db = TemporalRangeStateDb
                .create(dbPath1, BYTE_BUFFERS, DOC, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        try (final TemporalRangeStateDb db = TemporalRangeStateDb
                .create(dbPath, BYTE_BUFFERS, DOC, false)) {
            assertThat(db.count()).isEqualTo(100);
            db.condense(Instant.now());
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(1);
            db.condense(Instant.now());
            db.deleteOldData(Instant.now(), true);
            assertThat(db.count()).isEqualTo(0);
        }
    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");

        try (final TemporalRangeStateDb db = TemporalRangeStateDb
                .create(dbDir, BYTE_BUFFERS, DOC, false)) {
            insertData(db, refTime, "test", 100, 10);
        }
    }

    private void testGet(final TemporalRangeStateDb db) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final Key k = Key.builder().keyStart(10).keyEnd(30).time(refTime).build();
        final Val value = db.get(k);
        assertThat(value).isNotNull();
        assertThat(value.type()).isEqualTo(Type.STRING);
        assertThat(value.toString()).isEqualTo("test");
    }

    private void checkState(final TemporalRangeStateDb db,
                            final long key,
                            final Instant effectiveTime,
                            final boolean expected) {
        final TemporalRangeStateRequest request =
                new TemporalRangeStateRequest(key, effectiveTime);
        final TemporalRangeState state = db.getState(request);
        assertThat(state != null).isEqualTo(expected);
    }

    private void insertData(final TemporalRangeStateDb db,
                            final Instant refTime,
                            final String value,
                            final int rows,
                            final long deltaSeconds) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
                final Key k = Key.builder().keyStart(10).keyEnd(30).time(effectiveTime).build();
                final Val v = ValString.create(value);
                db.insert(writer, new TemporalRangeState(k, v));
            }
        });
    }

    private static PlanBDoc getDoc(final TemporalRangeStateSettings settings) {
        return PlanBDoc.builder().uuid(UUID.randomUUID().toString()).name("test").settings(settings).build();
    }
}
