/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.state.impl;

import org.junit.jupiter.api.Disabled;

@Disabled
class TestTemporalRangedStateDao {
//
//    @Test
//    void testDao() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final TemporalRangedStateDao stateDao = new TemporalRangedStateDao(sessionProvider, tableName);
//
//            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
//            insertData(stateDao, refTime, "test", 100, 10);
//
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
//        });
//    }
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
//    private void insertData(final TemporalRangedStateDao stateDao,
//                            final Instant refTime,
//                            final String value,
//                            final int rows,
//                            final long deltaSeconds) {
//        final ByteBuffer byteBuffer = ByteBuffer.wrap((value).getBytes(StandardCharsets.UTF_8));
//        for (int i = 0; i < rows; i++) {
//            final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
//            final TemporalRangedState state = new TemporalRangedState(
//                    10,
//                    30,
//                    effectiveTime,
//                    StringValue.TYPE_ID,
//                    byteBuffer);
//            stateDao.insert(Collections.singletonList(state));
//        }
//    }
}
