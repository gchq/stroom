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
import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.RangeState.Key;
import stroom.planb.impl.db.StateValueTestUtil.ValueFunction;
import stroom.planb.impl.db.rangestate.RangeStateDb;
import stroom.planb.impl.db.rangestate.RangeStateFields;
import stroom.planb.impl.db.rangestate.RangeStateRequest;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangeKeySchema;
import stroom.planb.shared.RangeStateSettings;
import stroom.planb.shared.RangeType;
import stroom.planb.shared.StateValueSchema;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestRangeStateDb {

    private static final int ITERATIONS = 100;
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final RangeStateSettings BASIC_SETTINGS = new RangeStateSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();
    private static final PlanBDoc DOC = getDoc(BASIC_SETTINGS);

    @Test
    void test(@TempDir final Path tempDir) {
        testWrite(tempDir);

        try (final RangeStateDb db = RangeStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(1);
            testGet(db);

            checkState(db, 9, false);
            for (int i = 10; i <= 30; i++) {
                checkState(db, i, true);
            }
            checkState(db, 31, false);

            final RangeState state = getState(db, 11);
            assertThat(state).isNotNull();
            assertThat(state.key().getKeyStart()).isEqualTo(10);
            assertThat(state.key().getKeyEnd()).isEqualTo(30);
            assertThat(state.val().type()).isEqualTo(Type.STRING);
            assertThat(state.val().toString()).isEqualTo("test99");

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(RangeStateFields.KEY_START);
            fieldIndex.create(RangeStateFields.KEY_END);
            fieldIndex.create(RangeStateFields.VALUE_TYPE);
            fieldIndex.create(RangeStateFields.VALUE);
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.getFirst()[0].toString()).isEqualTo("10");
            assertThat(results.getFirst()[1].toString()).isEqualTo("30");
            assertThat(results.getFirst()[2].toString()).isEqualTo("string");
            assertThat(results.getFirst()[3].toString()).isEqualTo("test99");
        }
    }

    private RangeState getState(final RangeStateDb db, final long key) {
        final RangeStateRequest request =
                new RangeStateRequest(key);
        return db.getState(request);
    }

    private void checkState(final RangeStateDb db,
                            final long key,
                            final boolean expected) {
        final RangeState state = getState(db, key);
        final boolean actual = state != null;
        assertThat(actual).isEqualTo(expected);
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
                tests.add(DynamicTest.dynamicTest("Range type = " + rangeType +
                                                  ", Value type = " + valueFunction,
                        () -> {
                            final RangeStateSettings settings = new RangeStateSettings
                                    .Builder()
                                    .keySchema(new RangeKeySchema.Builder()
                                            .rangeType(rangeType)
                                            .build())
                                    .valueSchema(new StateValueSchema.Builder()
                                            .stateValueType(valueFunction.stateValueType())
                                            .build())
                                    .build();

                            final Function<Integer, Key> keyFunction = i -> new Key(i, i + 1);

                            Path path = null;
                            try {
                                path = Files.createTempDirectory("stroom");

                                testWrite(path, settings, iterations, keyFunction, valueFunction.function());
                                if (read) {
                                    testSimpleRead(path, settings, iterations, keyFunction, valueFunction.function());
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
        return tests;
    }


    private void testWrite(final Path dbDir,
                           final RangeStateSettings settings,
                           final int insertRows,
                           final Function<Integer, Key> keyFunction,
                           final Function<Integer, Val> valueFunction) {
        try (final RangeStateDb db = RangeStateDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), false)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void insertData(final RangeStateDb db,
                            final int rows,
                            final Function<Integer, Key> keyFunction,
                            final Function<Integer, Val> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Key k = keyFunction.apply(i);
                final Val v = valueFunction.apply(i);
                db.insert(writer, new RangeState(k, v));
            }
        });
    }

    private void testSimpleRead(final Path dbDir,
                                final RangeStateSettings settings,
                                final int rows,
                                final Function<Integer, Key> keyFunction,
                                final Function<Integer, Val> valueFunction) {
        try (final RangeStateDb db = RangeStateDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), true)) {
            for (int i = 0; i < rows; i++) {
                final Key key = keyFunction.apply(i);
                final RangeState state = db.getState(new RangeStateRequest(key.getKeyStart()));
                assertThat(state).isNotNull();
                assertThat(state.val().type()).isEqualTo(valueFunction.apply(i).type());
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

        try (final RangeStateDb db = RangeStateDb.create(dbPath1, BYTE_BUFFERS, DOC, false)) {
            db.merge(dbPath2);
        }
    }

    private void testWrite(final Path dbDir) {
        try (final RangeStateDb db = RangeStateDb.create(dbDir, BYTE_BUFFERS, DOC, false)) {
            insertData(db, 100);
        }
    }

    private void testGet(final RangeStateDb db) {
        final Key k = Key.builder().keyStart(10).keyEnd(30).build();
        final Val value = db.get(k);
        assertThat(value).isNotNull();
        assertThat(value.type()).isEqualTo(Type.STRING);
        assertThat(value.toString()).isEqualTo("test99");
    }

    private void insertData(final RangeStateDb db,
                            final int rows) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Key k = Key.builder().keyStart(10).keyEnd(30).build();
                final Val v = ValString.create("test" + i);
                db.insert(writer, new RangeState(k, v));
            }
        });
    }

    private static PlanBDoc getDoc(final RangeStateSettings settings) {
        return PlanBDoc.builder().uuid(UUID.randomUUID().toString()).name("test").settings(settings).build();
    }
}
