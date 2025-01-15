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

package stroom.planb.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.refdata.store.StringValue;
import stroom.planb.impl.db.StateValue;
import stroom.planb.impl.db.TemporalRangedState;
import stroom.planb.impl.db.TemporalRangedState.Key;
import stroom.planb.impl.db.TemporalRangedStateDb;
import stroom.planb.impl.db.TemporalRangedStateFields;
import stroom.planb.impl.db.TemporalRangedStateRequest;
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

class TestTemporalRangedStateDb {

    @Test
    void test(@TempDir Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalRangedStateDb db = new TemporalRangedStateDb(
                tempDir,
                byteBufferFactory,
                false,
                true)) {
            assertThat(db.count()).isEqualTo(100);
            testGet(db);


            final TemporalRangedStateRequest stateRequest =
                    new TemporalRangedStateRequest(11, refTime.toEpochMilli());
            final Optional<TemporalRangedState> optional = db.getState(stateRequest);
            assertThat(optional).isNotEmpty();
            final TemporalRangedState res = optional.get();
            assertThat(res.key().keyStart()).isEqualTo(10);
            assertThat(res.key().keyEnd()).isEqualTo(30);
            assertThat(res.key().effectiveTime()).isEqualTo(refTime.toEpochMilli());
            assertThat(res.value().typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.value().toString()).isEqualTo("test");

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
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory(null);
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
        final Path db1 = rootDir.resolve("db1");
        final Path db2 = rootDir.resolve("db2");
        Files.createDirectory(db1);
        Files.createDirectory(db2);

        testWrite(db1);
        testWrite(db2);

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalRangedStateDb writer = new TemporalRangedStateDb(db1, byteBufferFactory)) {
            writer.merge(db2);
        }
    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final TemporalRangedStateDb writer =
                new TemporalRangedStateDb(dbDir, byteBufferFactory)) {
            insertData(writer, refTime, "test", 100, 10);
        }
    }

    private void testGet(final TemporalRangedStateDb db) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        final Key k = Key.builder().keyStart(10).keyEnd(30).effectiveTime(refTime).build();
        final Optional<StateValue> optional = db.get(k);
        assertThat(optional).isNotEmpty();
        final StateValue res = optional.get();
        assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
        assertThat(res.toString()).isEqualTo("test");
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
        final ByteBuffer byteBuffer = ByteBuffer.wrap((value).getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < rows; i++) {
            final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
            final Key k = Key.builder().keyStart(10).keyEnd(30).effectiveTime(effectiveTime).build();
            final StateValue v = StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
            db.insert(k, v);
        }
    }
}
