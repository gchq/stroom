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
import stroom.planb.impl.db.temporalstate.TemporalState;
import stroom.planb.impl.db.temporalstate.TemporalState.Key;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateFields;
import stroom.planb.impl.db.temporalstate.TemporalStateRequest;
import stroom.planb.shared.TemporalStateSettings;
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

class TestTemporalStateDb {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final TemporalStateSettings BASIC_SETTINGS = TemporalStateSettings
            .builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();

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

//
//    @Test
//    void testRemoveOldData() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider, tableName);
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
//            final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider, tableName);
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
}
