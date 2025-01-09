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
import stroom.planb.impl.io.State.Key;
import stroom.planb.impl.io.StateFields;
import stroom.planb.impl.io.StateReader;
import stroom.planb.impl.io.StateValue;
import stroom.planb.impl.io.StateWriter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestState {

    @Test
    void test(@TempDir Path tempDir) {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final StateWriter writer = new StateWriter(tempDir, byteBufferFactory, false)) {
            insertData(writer, 100);
        }

        try (final StateReader reader = new StateReader(tempDir, byteBufferFactory)) {
            assertThat(reader.count()).isEqualTo(1);
            final Key key = Key.builder().name("TEST_KEY").build();
            final Optional<StateValue> optional = reader.get(key);
            assertThat(optional).isNotEmpty();
            final StateValue res = optional.get();
            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.toString()).isEqualTo("test99");

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(StateFields.KEY);
            fieldIndex.create(StateFields.VALUE_TYPE);
            fieldIndex.create(StateFields.VALUE);
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory(null);
            reader.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.getFirst()[0].toString()).isEqualTo("TEST_KEY");
            assertThat(results.getFirst()[1].toString()).isEqualTo("String");
            assertThat(results.getFirst()[2].toString()).isEqualTo("test99");
        }
    }

//    @Test
//    void testRemoveOldData() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final StateDao stateDao = new StateDao(sessionProvider, tableName);
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

    private void insertData(final StateWriter writer,
                            final int rows) {
        for (int i = 0; i < rows; i++) {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test" + i).getBytes(StandardCharsets.UTF_8));
            final Key k = Key.builder().name("TEST_KEY").build();
            final StateValue v = StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
            writer.insert(k, v);
        }
    }
}
