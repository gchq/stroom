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
import stroom.planb.impl.db.rangedstate.RangedState;
import stroom.planb.impl.db.rangedstate.RangedState.Key;
import stroom.planb.impl.db.rangedstate.RangedStateDb;
import stroom.planb.impl.db.rangedstate.RangedStateFields;
import stroom.planb.impl.db.rangedstate.RangedStateRequest;
import stroom.planb.shared.RangedStateSettings;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestRangedStateDb {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final RangedStateSettings BASIC_SETTINGS = RangedStateSettings
            .builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();

    @Test
    void test(@TempDir Path tempDir) {
        testWrite(tempDir);

        try (final RangedStateDb db = RangedStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                BASIC_SETTINGS,
                true)) {
            assertThat(db.count()).isEqualTo(1);
            testGet(db);

            checkState(db, 9, false);
            for (int i = 10; i <= 30; i++) {
                checkState(db, i, true);
            }
            checkState(db, 31, false);

            final RangedState state = getState(db, 11);
            assertThat(state).isNotNull();
            assertThat(state.key().getKeyStart()).isEqualTo(10);
            assertThat(state.key().getKeyEnd()).isEqualTo(30);
            assertThat(state.val().type()).isEqualTo(Type.STRING);
            assertThat(state.val().toString()).isEqualTo("test99");
//
//            final FieldIndex fieldIndex = new FieldIndex();
//            fieldIndex.create(RangedStateFields.KEY_START);
//            final AtomicInteger count = new AtomicInteger();
//            rangedStateDao.search(new ExpressionCriteria(ExpressionOperator.builder().build()), fieldIndex, null,
//                    v -> count.incrementAndGet());
//            assertThat(count.get()).isEqualTo(1);


            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(RangedStateFields.KEY_START);
            fieldIndex.create(RangedStateFields.KEY_END);
            fieldIndex.create(RangedStateFields.VALUE_TYPE);
            fieldIndex.create(RangedStateFields.VALUE);
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

    private RangedState getState(final RangedStateDb db, final long key) {
        final RangedStateRequest request =
                new RangedStateRequest(key);
        return db.getState(request);
    }

    private void checkState(final RangedStateDb db,
                            final long key,
                            final boolean expected) {
        final RangedState state = getState(db, key);
        final boolean actual = state != null;
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1);
        testWrite(dbPath2);

        try (final RangedStateDb db = RangedStateDb.create(dbPath1, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            db.merge(dbPath2);
        }
    }

    private void testWrite(final Path dbDir) {
        try (final RangedStateDb db = RangedStateDb.create(dbDir, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            insertData(db, 100);
        }
    }

    private void testGet(final RangedStateDb db) {
        final Key k = Key.builder().keyStart(10).keyEnd(30).build();
        final Val value = db.get(k);
        assertThat(value).isNotNull();
        assertThat(value.type()).isEqualTo(Type.STRING);
        assertThat(value.toString()).isEqualTo("test99");
    }

//    @Test
//    void testRemoveOldData() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final RangedStateDao stateDao = new RangedStateDao(sessionProvider, tableName);
//
//            insertData(stateDao, 100);
//            insertData(stateDao, 10);
//
//            assertThat(stateDao.count()).isEqualTo(1);
//
//            stateDao.removeOldData(Instant.parse("2000-01-01T00:00:00.000Z"));
//            assertThat(stateDao.count()).isEqualTo(1);
//
//            stateDao.removeOldData(Instant.now());
//            assertThat(stateDao.count()).isEqualTo(0);
//        });
//    }

    private void insertData(final RangedStateDb db,
                            final int rows) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Key k = Key.builder().keyStart(10).keyEnd(30).build();
                final Val v = ValString.create("test" + i);
                db.insert(writer, new RangedState(k, v));
            }
        });
    }
}
