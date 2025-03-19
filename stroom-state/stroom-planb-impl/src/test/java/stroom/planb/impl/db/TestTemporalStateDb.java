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
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.api.v2.ExpressionOperator;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemporalStateDb {

    @Test
    void test(@TempDir Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalStateDb db = new TemporalStateDb(
                tempDir,
                byteBufferFactory,
                TemporalStateSettings.builder().build(),
                true)) {
            assertThat(db.count()).isEqualTo(100);
//            final TemporalStateRequest stateRequest =
//                    new TemporalStateRequest("TEST_MAP", "TEST_KEY", refTime);
            final TemporalState.Key key = TemporalState.Key.builder().name("TEST_KEY").effectiveTime(refTime).build();
            final Optional<StateValue> optional = db.get(key);
            assertThat(optional).isNotEmpty();
            final StateValue res = optional.get();
//            assertThat(res.key()).isEqualTo("TEST_KEY");
//            assertThat(res.effectiveTime()).isEqualTo(refTime);
            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.toString()).isEqualTo("test");

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
            assertThat(results.getFirst()[2].toString()).isEqualTo("String");
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

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalStateDb db = new TemporalStateDb(dbPath1, byteBufferFactory)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalStateDb db = new TemporalStateDb(dbPath, byteBufferFactory)) {
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

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = new TemporalStateDb(dbPath, byteBufferFactory)) {
            insertData(db, refTime, "TEST_KEY", "test", 100, 60 * 60 * 24);
            insertData(db, refTime, "TEST_KEY2", "test2", 100, 60 * 60 * 24);
            insertData(db, refTime, "TEST_KEY", "test", 10, -60 * 60 * 24);
            insertData(db, refTime, "TEST_KEY2", "test2", 10, -60 * 60 * 24);

            assertThat(db.count()).isEqualTo(218);

            db.condense(refTime.toEpochMilli(), 0);
            assertThat(db.count()).isEqualTo(200);

            db.condense(Instant.parse("2000-01-10T00:00:00.000Z").toEpochMilli(), 0);
            assertThat(db.count()).isEqualTo(182);
        }
    }

    private void testWrite(final Path dbDir) {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = new TemporalStateDb(dbDir, byteBufferFactory)) {
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
            final ByteBuffer byteBuffer = ByteBuffer.wrap((value).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < rows; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
                final TemporalState.Key k = TemporalState.Key
                        .builder()
                        .name(key)
                        .effectiveTime(effectiveTime)
                        .build();

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
