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
import stroom.planb.impl.dao.TemporalState;
import stroom.planb.impl.dao.TemporalStateFields;
import stroom.planb.impl.dao.TemporalStateReader;
import stroom.planb.impl.dao.TemporalStateWriter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemporalState {

    @Test
    void testDao(@TempDir Path tempDir) {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateWriter writer = new TemporalStateWriter(tempDir, byteBufferFactory, false)) {
            insertData(writer, refTime, "test", 100, 10);
        }

        try (final TemporalStateReader reader = new TemporalStateReader(tempDir, byteBufferFactory)) {
            assertThat(reader.count()).isEqualTo(100);
//            final TemporalStateRequest stateRequest =
//                    new TemporalStateRequest("TEST_MAP", "TEST_KEY", refTime);
            final TemporalState.Key key = TemporalState.Key.builder().name("TEST_KEY").effectiveTime(refTime).build();
            final Optional<TemporalState.Value> optional = reader.get(key);
            assertThat(optional).isNotEmpty();
            final TemporalState.Value res = optional.get();
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
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory(null);
            reader.search(
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

    private void insertData(final TemporalStateWriter writer,
                            final Instant refTime,
                            final String value,
                            final int rows,
                            final long deltaSeconds) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap((value).getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < rows; i++) {
            final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
            final TemporalState.Key k = TemporalState.Key
                    .builder()
                    .name("TEST_KEY")
                    .effectiveTime(effectiveTime)
                    .build();

            final TemporalState.Value v = TemporalState.Value
                    .builder()
                    .typeId(StringValue.TYPE_ID)
                    .byteBuffer(byteBuffer)
                    .build();

            writer.insert(k, v);
        }
    }
}
