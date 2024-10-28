/*
 * Copyright 2024 Crown Copyright
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
 */

package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.refdata.store.StringValue;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.language.functions.FieldIndex;
import stroom.state.impl.ScyllaDbUtil;
import stroom.util.shared.string.CIKeys;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestRangedStateDao {

    @Test
    void testDao() {
        ScyllaDbUtil.test((sessionProvider, tableName) -> {
            final RangedStateDao rangedStateDao = new RangedStateDao(sessionProvider, tableName);

            insertData(rangedStateDao, 100);

            final RangedStateRequest stateRequest =
                    new RangedStateRequest("TEST_MAP", 11);
            final Optional<State> optional = rangedStateDao.getState(stateRequest);
            assertThat(optional).isNotEmpty();
            final State res = optional.get();
            assertThat(res.key()).isEqualTo("11");
            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.getValueAsString()).isEqualTo("test99");

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(CIKeys.KEY_START);
            final AtomicInteger count = new AtomicInteger();
            rangedStateDao.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    v -> count.incrementAndGet());
            assertThat(count.get()).isEqualTo(1);
        });
    }

    @Test
    void testRemoveOldData() {
        ScyllaDbUtil.test((sessionProvider, tableName) -> {
            final RangedStateDao stateDao = new RangedStateDao(sessionProvider, tableName);

            insertData(stateDao, 100);
            insertData(stateDao, 10);

            assertThat(stateDao.count()).isEqualTo(1);

            stateDao.removeOldData(Instant.parse("2000-01-01T00:00:00.000Z"));
            assertThat(stateDao.count()).isEqualTo(1);

            stateDao.removeOldData(Instant.now());
            assertThat(stateDao.count()).isEqualTo(0);
        });
    }

    private void insertData(final RangedStateDao rangedStateDao,
                            final int rows) {
        for (int i = 0; i < rows; i++) {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test" + i).getBytes(StandardCharsets.UTF_8));
            final RangedState state = new RangedState(
                    10,
                    30,
                    StringValue.TYPE_ID,
                    byteBuffer);
            rangedStateDao.insert(Collections.singletonList(state));
        }
    }
}
