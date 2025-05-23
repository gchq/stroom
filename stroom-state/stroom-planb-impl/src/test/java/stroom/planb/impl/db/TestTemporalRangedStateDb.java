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

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.refdata.store.StringValue;
import stroom.planb.impl.db.TemporalRangedState.Key;
import stroom.planb.shared.TemporalRangedStateSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemporalRangedStateDb {

    @Test
    void test(@TempDir Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalRangedStateDb db = new TemporalRangedStateDb(
                tempDir,
                byteBufferFactory,
                TemporalRangedStateSettings.builder().build(),
                true)) {
            assertThat(db.count()).isEqualTo(100);
            testGet(db);

            // Check exact time states.
            checkState(db, 9, refTime.toEpochMilli(), false);
            for (int i = 10; i <= 30; i++) {
                checkState(db, i, refTime.toEpochMilli(), true);
            }
            checkState(db, 31, refTime.toEpochMilli(), false);

            // Check before time states.
            for (int i = 9; i <= 31; i++) {
                checkState(db, i, refTime.toEpochMilli() - 1, false);
            }

            // Check after time states.
            checkState(db, 9, refTime.toEpochMilli() + 1, false);
            for (int i = 10; i <= 30; i++) {
                checkState(db, i, refTime.toEpochMilli() + 1, true);
            }
            checkState(db, 31, refTime.toEpochMilli() + 1, false);

            final TemporalRangedStateRequest stateRequest =
                    new TemporalRangedStateRequest(11, refTime.toEpochMilli());
            final TemporalRangedState state = db.getState(stateRequest);
            assertThat(state).isNotNull();
            assertThat(state.key().getKeyStart()).isEqualTo(10);
            assertThat(state.key().getKeyEnd()).isEqualTo(30);
            assertThat(state.key().getEffectiveTime()).isEqualTo(refTime.toEpochMilli());
            assertThat(state.val().getTypeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(state.val().toString()).isEqualTo("test");

//            final TemporalRangedStateRequest stateRequest =
//                    new TemporalRangedStateRequest("TEST_MAP", 11, refTime);
//            final Optional<TemporalState> optional = stateDao.getState(stateRequest);
//            assertThat(optional).isNotEmpty();
//            final TemporalState res = optional.get();
//            assertThat(res.key()).isEqualTo("11");
//            assertThat(res.effectiveTime()).isEqualTo(refTime);
//            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
//            assertThat(res.getValueAsString()).isEqualTo("test");
//
//            final FieldIndex fieldIndex = new FieldIndex();
//            fieldIndex.create(RangedStateFields.KEY_START);
//            final AtomicInteger count = new AtomicInteger();
//            stateDao.search(new ExpressionCriteria(ExpressionOperator.builder().build()), fieldIndex, null,
//                    v -> count.incrementAndGet());
//            assertThat(count.get()).isEqualTo(100);


            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(TemporalRangedStateFields.KEY_START);
            fieldIndex.create(TemporalRangedStateFields.KEY_END);
            fieldIndex.create(TemporalRangedStateFields.EFFECTIVE_TIME);
            fieldIndex.create(TemporalRangedStateFields.VALUE_TYPE);
            fieldIndex.create(TemporalRangedStateFields.VALUE);
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
            assertThat(results.getFirst()[3].toString()).isEqualTo("String");
            assertThat(results.getFirst()[4].toString()).isEqualTo("test");
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

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalRangedStateDb db = new TemporalRangedStateDb(dbPath1, byteBufferFactory)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalRangedStateDb db = new TemporalRangedStateDb(dbPath, byteBufferFactory)) {
            assertThat(db.count()).isEqualTo(100);
            db.condense(System.currentTimeMillis(), 0);
            assertThat(db.count()).isEqualTo(1);
            db.condense(System.currentTimeMillis(), System.currentTimeMillis());
            assertThat(db.count()).isEqualTo(0);
        }
    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalRangedStateDb db =
                new TemporalRangedStateDb(dbDir, byteBufferFactory)) {
            insertData(db, refTime, "test", 100, 10);
        }
    }

    private void testGet(final TemporalRangedStateDb db) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final Key k = Key.builder().keyStart(10).keyEnd(30).effectiveTime(refTime).build();
        final StateValue value = db.get(k);
        assertThat(value).isNotNull();
        assertThat(value.getTypeId()).isEqualTo(StringValue.TYPE_ID);
        assertThat(value.toString()).isEqualTo("test");
    }

    private void checkState(final TemporalRangedStateDb db,
                            final long key,
                            final long effectiveTime,
                            final boolean expected) {
        final TemporalRangedStateRequest request =
                new TemporalRangedStateRequest(key, effectiveTime);
        final TemporalRangedState state = db.getState(request);
        assertThat(state != null).isEqualTo(expected);
    }

    //
//    @Test
//    void testRemoveOldData() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final TemporalRangedStateDao stateDao = new TemporalRangedStateDao(sessionProvider, tableName);
//
//            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
//            insertData(stateDao, refTime, "test", 100, 10);
//            insertData(stateDao, refTime, "test", 10, -10);
//
//            assertThat(stateDao.count()).isEqualTo(109);
//
//            stateDao.removeOldData(refTime);
//            assertThat(stateDao.count()).isEqualTo(100);
//
//            stateDao.removeOldData(Instant.now());
//            assertThat(stateDao.count()).isEqualTo(0);
//        });
//    }
//
//    @Test
//    void testCondense() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final TemporalRangedStateDao stateDao = new TemporalRangedStateDao(sessionProvider, tableName);
//
//            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
//            insertData(stateDao, refTime, "test", 100, 10);
//            insertData(stateDao, refTime, "test", 10, -10);
//
//            assertThat(stateDao.count()).isEqualTo(109);
//
//            stateDao.condense(refTime);
//            assertThat(stateDao.count()).isEqualTo(100);
//
//            stateDao.condense(Instant.now());
//            assertThat(stateDao.count()).isEqualTo(1);
//        });
//    }
//
    private void insertData(final TemporalRangedStateDb db,
                            final Instant refTime,
                            final String value,
                            final int rows,
                            final long deltaSeconds) {
        db.write(writer -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap((value).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < rows; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
                final Key k = Key.builder().keyStart(10).keyEnd(30).effectiveTime(effectiveTime).build();
                final StateValue v = StateValue
                        .builder()
                        .typeId(StringValue.TYPE_ID)
                        .byteBuffer(byteBuffer.duplicate())
                        .build();
                db.insert(writer, k, v);
            }
        });
    }
}
