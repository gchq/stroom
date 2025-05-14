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
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedState;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedState.Key;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedStateDb;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedStateFields;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedStateRequest;
import stroom.planb.shared.TemporalRangedStateSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemporalRangedStateDb {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final TemporalRangedStateSettings BASIC_SETTINGS = TemporalRangedStateSettings
            .builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();

    @Test
    void test(@TempDir Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalRangedStateDb db = TemporalRangedStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                TemporalRangedStateSettings.builder().build(),
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

            final TemporalRangedStateRequest stateRequest =
                    new TemporalRangedStateRequest(11, refTime);
            final TemporalRangedState state = db.getState(stateRequest);
            assertThat(state).isNotNull();
            assertThat(state.key().getKeyStart()).isEqualTo(10);
            assertThat(state.key().getKeyEnd()).isEqualTo(30);
            assertThat(state.key().getEffectiveTime()).isEqualTo(refTime);
            assertThat(state.val().type()).isEqualTo(Type.STRING);
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
            assertThat(results.getFirst()[3].toString()).isEqualTo("string");
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

        try (final TemporalRangedStateDb db = TemporalRangedStateDb
                .create(dbPath1, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        try (final TemporalRangedStateDb db = TemporalRangedStateDb
                .create(dbPath, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            assertThat(db.count()).isEqualTo(100);
            db.condense(System.currentTimeMillis(), 0);
            assertThat(db.count()).isEqualTo(1);
            db.condense(System.currentTimeMillis(), System.currentTimeMillis());
            assertThat(db.count()).isEqualTo(0);
        }
    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");

        try (final TemporalRangedStateDb db = TemporalRangedStateDb
                .create(dbDir, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            insertData(db, refTime, "test", 100, 10);
        }
    }

    private void testGet(final TemporalRangedStateDb db) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final Key k = Key.builder().keyStart(10).keyEnd(30).effectiveTime(refTime).build();
        final Val value = db.get(k);
        assertThat(value).isNotNull();
        assertThat(value.type()).isEqualTo(Type.STRING);
        assertThat(value.toString()).isEqualTo("test");
    }

    private void checkState(final TemporalRangedStateDb db,
                            final long key,
                            final Instant effectiveTime,
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
            for (int i = 0; i < rows; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
                final Key k = Key.builder().keyStart(10).keyEnd(30).effectiveTime(effectiveTime).build();
                final Val v = ValString.create(value);
                db.insert(writer, new TemporalRangedState(k, v));
            }
        });
    }
}
