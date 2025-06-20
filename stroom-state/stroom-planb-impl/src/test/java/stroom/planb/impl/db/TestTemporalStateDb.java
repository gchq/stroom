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
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.db.StateValueTestUtil.ValueFunction;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateFields;
import stroom.planb.impl.db.temporalstate.TemporalStateRequest;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateKeySchema;
import stroom.planb.shared.TemporalStateSettings;
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
    private static final TemporalStateSettings BASIC_SETTINGS = new TemporalStateSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();

    private final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
    private final List<KeyFunction> keyFunctions = List.of(
            new KeyFunction(KeyType.BOOLEAN.name(), KeyType.BOOLEAN,
                    i -> new TemporalKey(KeyPrefix.create(ValBoolean.create(i > 0)), refTime)),
            new KeyFunction(KeyType.BYTE.name(), KeyType.BYTE,
                    i -> new TemporalKey(KeyPrefix.create(ValByte.create(i.byteValue())), refTime)),
            new KeyFunction(KeyType.SHORT.name(), KeyType.SHORT,
                    i -> new TemporalKey(KeyPrefix.create(ValShort.create(i.shortValue())), refTime)),
            new KeyFunction(KeyType.INT.name(), KeyType.INT,
                    i -> new TemporalKey(KeyPrefix.create(ValInteger.create(i)), refTime)),
            new KeyFunction(KeyType.LONG.name(), KeyType.LONG,
                    i -> new TemporalKey(KeyPrefix.create(ValLong.create(i.longValue())), refTime)),
            new KeyFunction(KeyType.FLOAT.name(), KeyType.FLOAT,
                    i -> new TemporalKey(KeyPrefix.create(ValFloat.create(i.floatValue())), refTime)),
            new KeyFunction(KeyType.DOUBLE.name(), KeyType.DOUBLE,
                    i -> new TemporalKey(KeyPrefix.create(ValDouble.create(i.doubleValue())), refTime)),
            new KeyFunction(KeyType.STRING.name(), KeyType.STRING,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction(KeyType.UID_LOOKUP.name(), KeyType.UID_LOOKUP,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction(KeyType.HASH_LOOKUP.name(), KeyType.HASH_LOOKUP,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction(KeyType.VARIABLE.name(), KeyType.VARIABLE,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction("Variable mid", KeyType.VARIABLE,
                    i -> new TemporalKey(KeyPrefix.create(
                            ValString.create(StateValueTestUtil.makeString(400))), refTime)),
            new KeyFunction("Variable long", KeyType.VARIABLE,
                    i -> new TemporalKey(KeyPrefix.create(
                            ValString.create(StateValueTestUtil.makeString(1000))), refTime)));

    @Test
    void test(@TempDir final Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                BASIC_SETTINGS,
                true)) {
            assertThat(db.count()).isEqualTo(100);

            final KeyPrefix byteKey = KeyPrefix.create("TEST_KEY");
            // Check exact time states.
            checkState(db, byteKey, refTime, true);
            // Check before time states.
            checkState(db, byteKey, refTime.minusMillis(1), false);
            // Check after time states.
            checkState(db, byteKey, refTime.plusMillis(1), true);
            final TemporalKey key = TemporalKey.builder().prefix(byteKey).time(refTime).build();
            final Val value = db.get(key);
            assertThat(value).isNotNull();
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


//            assertThat(count.get()).isEqualTo(100);
        }
    }

    @Test
    void testGetState(@TempDir final Path tempDir) {
        final KeyPrefix name = KeyPrefix.create("test");
        final Instant effectiveTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            db.write(writer -> {
                final TemporalKey k = TemporalKey
                        .builder()
                        .prefix(name)
                        .time(effectiveTime)
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
                for (final TemporalPrecision temporalPrecision : TemporalPrecision.values()) {
                    tests.add(DynamicTest.dynamicTest("key type = " + keyFunction +
                                                      ", Value type = " + valueFunction +
                                                      ", Temporal precision = " + temporalPrecision,
                            () -> {
                                final TemporalStateSettings settings = new TemporalStateSettings
                                        .Builder()
                                        .keySchema(new TemporalStateKeySchema.Builder()
                                                .keyType(keyFunction.keyType)
                                                .temporalPrecision(temporalPrecision)
                                                .build())
                                        .valueSchema(new StateValueSchema.Builder()
                                                .stateValueType(valueFunction.stateValueType())
                                                .build())
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
            db.condense(Instant.now());
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(1);
            db.condense(Instant.now());
            db.deleteOldData(Instant.now(), true);
            assertThat(db.count()).isEqualTo(0);
        }
    }

    @Test
    void testCondense2(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbPath, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            insertData(db, refTime, KeyPrefix.create("TEST_KEY"), "test", 100, 60 * 60 * 24);
            insertData(db, refTime, KeyPrefix.create("TEST_KEY2"), "test2", 100, 60 * 60 * 24);
            insertData(db, refTime, KeyPrefix.create("TEST_KEY"), "test", 10, -60 * 60 * 24);
            insertData(db, refTime, KeyPrefix.create("TEST_KEY2"), "test2", 10, -60 * 60 * 24);

            assertThat(db.count()).isEqualTo(218);

            db.condense(refTime.plusMillis(1));
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(200);

            db.condense(Instant.parse("2000-01-10T00:00:00.000Z").plusMillis(1));
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(182);
        }
    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            insertData(db, refTime, KeyPrefix.create("TEST_KEY"), "test", 100, 10);
        }
    }

    private void testWrite(final Path dbDir,
                           final TemporalStateSettings settings,
                           final int insertRows,
                           final Function<Integer, TemporalKey> keyFunction,
                           final Function<Integer, Val> valueFunction) {
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, settings, false)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void insertData(final TemporalStateDb db,
                            final int rows,
                            final Function<Integer, TemporalKey> keyFunction,
                            final Function<Integer, Val> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final TemporalKey k = keyFunction.apply(i);
                final Val v = valueFunction.apply(i);
                db.insert(writer, new TemporalState(k, v));
            }
        });
    }

    private void testSimpleRead(final Path dbDir,
                                final TemporalStateSettings settings,
                                final int rows,
                                final Function<Integer, TemporalKey> keyFunction,
                                final Function<Integer, Val> valueFunction) {
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, settings, true)) {
            for (int i = 0; i < rows; i++) {
                final TemporalKey key = keyFunction.apply(i);
                final TemporalState temporalState = db.getState(new TemporalStateRequest(key));
                assertThat(temporalState).isNotNull();
                assertThat(temporalState.val().type()).isEqualTo(valueFunction.apply(i).type());
//                assertThat(value).isEqualTo(expectedVal); // Values will not be the same due to key overwrite.
            }
        }
    }

    private void insertData(final TemporalStateDb db,
                            final Instant refTime,
                            final KeyPrefix key,
                            final String value,
                            final int rows,
                            final long deltaSeconds) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
                final TemporalKey k = TemporalKey
                        .builder()
                        .prefix(key)
                        .time(effectiveTime)
                        .build();
                final Val v = ValString.create(value);
                db.insert(writer, new TemporalState(k, v));
            }
        });
    }

    private void checkState(final TemporalStateDb db,
                            final KeyPrefix key,
                            final Instant effectiveTime,
                            final boolean expected) {
        final TemporalStateRequest request =
                new TemporalStateRequest(new TemporalKey(key, effectiveTime));
        final TemporalState state = db.getState(request);
        assertThat(state != null).isEqualTo(expected);
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
