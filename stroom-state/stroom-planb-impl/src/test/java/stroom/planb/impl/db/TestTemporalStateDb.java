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
import stroom.planb.impl.db.StateValueTestUtil.ValueFunction;
import stroom.planb.impl.db.temporalstate.TemporalState;
import stroom.planb.impl.db.temporalstate.TemporalState.Key;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateFields;
import stroom.planb.impl.db.temporalstate.TemporalStateRequest;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateKeyType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.TemporalStateSettings;
import stroom.planb.shared.TimePrecision;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValByte;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValShort;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemporalStateDb {

    private static final int ITERATIONS = 100;
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final TemporalStateSettings BASIC_SETTINGS = TemporalStateSettings
            .builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();

    private final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
    private final List<KeyFunction> keyFunctions = List.of(
            new KeyFunction(StateKeyType.BOOLEAN.name(), StateKeyType.BOOLEAN,
                    i -> new Key(ValBoolean.create(i > 0), refTime)),
            new KeyFunction(StateKeyType.BYTE.name(), StateKeyType.BYTE,
                    i -> new Key(ValByte.create(i.byteValue()), refTime)),
            new KeyFunction(StateKeyType.SHORT.name(), StateKeyType.SHORT,
                    i -> new Key(ValShort.create(i.shortValue()), refTime)),
            new KeyFunction(StateKeyType.INT.name(), StateKeyType.INT,
                    i -> new Key(ValInteger.create(i), refTime)),
            new KeyFunction(StateKeyType.LONG.name(), StateKeyType.LONG,
                    i -> new Key(ValLong.create(i.longValue()), refTime)),
            new KeyFunction(StateKeyType.FLOAT.name(), StateKeyType.FLOAT,
                    i -> new Key(ValFloat.create(i.floatValue()), refTime)),
            new KeyFunction(StateKeyType.DOUBLE.name(), StateKeyType.DOUBLE,
                    i -> new Key(ValDouble.create(i.doubleValue()), refTime)),
            new KeyFunction(StateKeyType.STRING.name(), StateKeyType.STRING,
                    i -> new Key(ValString.create("test-" + i), refTime)),
            new KeyFunction(StateKeyType.UID_LOOKUP.name(), StateKeyType.UID_LOOKUP,
                    i -> new Key(ValString.create("test-" + i), refTime)),
            new KeyFunction(StateKeyType.HASH_LOOKUP.name(), StateKeyType.HASH_LOOKUP,
                    i -> new Key(ValString.create("test-" + i), refTime)),
            new KeyFunction(StateKeyType.VARIABLE.name(), StateKeyType.VARIABLE,
                    i -> new Key(ValString.create("test-" + i), refTime)),
            new KeyFunction("Variable mid", StateKeyType.VARIABLE,
                    i -> new Key(ValString.create(StateValueTestUtil.makeString(400)), refTime)),
            new KeyFunction("Variable long", StateKeyType.VARIABLE,
                    i -> new Key(ValString.create(StateValueTestUtil.makeString(1000)), refTime)));

    @Test
    void test(@TempDir Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                BASIC_SETTINGS,
                true)) {
            assertThat(db.count()).isEqualTo(100);

            final Val byteKey = ValString.create("TEST_KEY");
            // Check exact time states.
            checkState(db, byteKey, refTime, true);
            // Check before time states.
            checkState(db, byteKey, refTime.minusMillis(1), false);
            // Check after time states.
            checkState(db, byteKey, refTime.plusMillis(1), true);

//            final TemporalStateRequest stateRequest =
//                    new TemporalStateRequest("TEST_MAP", "TEST_KEY", refTime);
            final TemporalState.Key key = TemporalState.Key.builder().name("TEST_KEY").effectiveTime(refTime).build();
            final Val value = db.get(key);
            assertThat(value).isNotNull();
//            assertThat(res.key()).isEqualTo("TEST_KEY");
//            assertThat(res.effectiveTime()).isEqualTo(refTime);
            assertThat(value.type()).isEqualTo(Type.STRING);
            assertThat(value.toString()).isEqualTo("test");

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(TemporalStateFields.KEY);
            fieldIndex.create(TemporalStateFields.EFFECTIVE_TIME);
            fieldIndex.create(TemporalStateFields.VALUE_TYPE);
            fieldIndex.create(TemporalStateFields.VALUE);
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(100);
            assertThat(results.getFirst()[0].toString()).isEqualTo("TEST_KEY");
            assertThat(results.getFirst()[1].toString()).isEqualTo("2000-01-01T00:00:00.000Z");
            assertThat(results.getFirst()[2].toString()).isEqualTo("string");
            assertThat(results.getFirst()[3].toString()).isEqualTo("test");


//            final AtomicInteger count = new AtomicInteger();
//            stateDao.search(new ExpressionCriteria(ExpressionOperator.builder().build()), fieldIndex, null,
//                    v -> count.incrementAndGet());
//            assertThat(count.get()).isEqualTo(100);
        }
    }

    @Test
    void testGetState(@TempDir Path tempDir) {
        final Val name = ValString.create("test");
        final Instant effectiveTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            db.write(writer -> {
                final TemporalState.Key k = TemporalState.Key
                        .builder()
                        .name(name)
                        .effectiveTime(effectiveTime)
                        .build();
                final Val v = ValString.create("test");
                db.insert(writer, new TemporalState(k, v));
            });
        }

        try (final TemporalStateDb db = TemporalStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                BASIC_SETTINGS,
                true)) {
            assertThat(db.count()).isEqualTo(1);
            checkState(db, name, effectiveTime, true);
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
        for (final KeyFunction keyFunction : keyFunctions) {
            for (final ValueFunction valueFunction : StateValueTestUtil.getValueFunctions()) {
                for (final TimePrecision timePrecision : TimePrecision.values()) {
                    tests.add(DynamicTest.dynamicTest("key type = " + keyFunction +
                                                      ", Value type = " + valueFunction +
                                                      ", Time precision = " + timePrecision,
                            () -> {
                                final TemporalStateSettings settings = TemporalStateSettings
                                        .builder()
                                        .stateKeySchema(StateKeySchema.builder()
                                                .stateKeyType(keyFunction.stateKeyType)
                                                .build())
                                        .stateValueSchema(StateValueSchema.builder()
                                                .stateValueType(valueFunction.stateValueType())
                                                .build())
                                        .timePrecision(timePrecision)
                                        .build();

                                Path path = null;
                                try {
                                    path = Files.createTempDirectory("stroom");

                                    testWrite(path, settings, iterations,
                                            keyFunction.function,
                                            valueFunction.function());
                                    if (read) {
                                        testSimpleRead(path, settings, iterations,
                                                keyFunction.function,
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

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1);
        testWrite(dbPath2);

        try (final TemporalStateDb db = TemporalStateDb.create(dbPath1, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        try (final TemporalStateDb db = TemporalStateDb.create(dbPath, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            assertThat(db.count()).isEqualTo(100);
            db.condense(System.currentTimeMillis(), 0);
            assertThat(db.count()).isEqualTo(1);
            db.condense(System.currentTimeMillis(), System.currentTimeMillis());
            assertThat(db.count()).isEqualTo(0);
        }
    }

    @Test
    void testCondense2(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbPath, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            insertData(db, refTime, "TEST_KEY", "test", 100, 60 * 60 * 24);
            insertData(db, refTime, "TEST_KEY2", "test2", 100, 60 * 60 * 24);
            insertData(db, refTime, "TEST_KEY", "test", 10, -60 * 60 * 24);
            insertData(db, refTime, "TEST_KEY2", "test2", 10, -60 * 60 * 24);

            assertThat(db.count()).isEqualTo(218);

            db.condense(refTime.plusMillis(1), Instant.MIN);
            assertThat(db.count()).isEqualTo(200);

            db.condense(Instant.parse("2000-01-10T00:00:00.000Z").plusMillis(1), Instant.MIN);
            assertThat(db.count()).isEqualTo(182);
        }
    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            insertData(db, refTime, "TEST_KEY", "test", 100, 10);
        }
    }

    private void testWrite(final Path dbDir,
                           final TemporalStateSettings settings,
                           final int insertRows,
                           final Function<Integer, Key> keyFunction,
                           final Function<Integer, Val> valueFunction) {
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, settings, false)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void insertData(final TemporalStateDb db,
                            final int rows,
                            final Function<Integer, Key> keyFunction,
                            final Function<Integer, Val> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Key k = keyFunction.apply(i);
                final Val v = valueFunction.apply(i);
                db.insert(writer, new TemporalState(k, v));
            }
        });
    }

    private void testSimpleRead(final Path dbDir,
                                final TemporalStateSettings settings,
                                final int rows,
                                final Function<Integer, Key> keyFunction,
                                final Function<Integer, Val> valueFunction) {
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, settings, true)) {
            for (int i = 0; i < rows; i++) {
                final Key key = keyFunction.apply(i);
                final TemporalState temporalState = db.getState(new TemporalStateRequest(key));
                assertThat(temporalState).isNotNull();
                assertThat(temporalState.val().type()).isEqualTo(valueFunction.apply(i).type());
//                assertThat(value).isEqualTo(expectedVal); // Values will not be the same due to key overwrite.
            }
        }
    }

    private void insertData(final TemporalStateDb db,
                            final Instant refTime,
                            final String key,
                            final String value,
                            final int rows,
                            final long deltaSeconds) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
                final TemporalState.Key k = TemporalState.Key
                        .builder()
                        .name(key)
                        .effectiveTime(effectiveTime)
                        .build();
                final Val v = ValString.create(value);
                db.insert(writer, new TemporalState(k, v));
            }
        });
    }

    private void checkState(final TemporalStateDb db,
                            final Val key,
                            final Instant effectiveTime,
                            final boolean expected) {
        final TemporalStateRequest request =
                new TemporalStateRequest(new Key(key, effectiveTime));
        final TemporalState state = db.getState(request);
        assertThat(state != null).isEqualTo(expected);
    }

    private record KeyFunction(String description,
                               StateKeyType stateKeyType,
                               Function<Integer, Key> function) {

        @Override
        public String toString() {
            return description;
        }
    }
}
