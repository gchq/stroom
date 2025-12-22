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
class TestTemporalStateDao {
//
//    @Test
//    void testDao() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider, tableName);
//
//            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
//            insertData(stateDao, refTime, "TEST_KEY", "test", 100, 10);
//
//            final TemporalStateRequest stateRequest =
//                    new TemporalStateRequest("TEST_MAP", "TEST_KEY", refTime);
//            final Optional<TemporalState> optional = stateDao.getState(stateRequest);
//            assertThat(optional).isNotEmpty();
//            final TemporalState res = optional.get();
//            assertThat(res.key()).isEqualTo("TEST_KEY");
//            assertThat(res.effectiveTime()).isEqualTo(refTime);
//            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
//            assertThat(res.getValueAsString()).isEqualTo("test");
//
//            final FieldIndex fieldIndex = new FieldIndex();
//            fieldIndex.create(TemporalStateFields.KEY);
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
//            final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider, tableName);
//
//            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
//            insertData(stateDao, refTime, "TEST_KEY", "test", 100, 10);
//            insertData(stateDao, refTime, "TEST_KEY", "test", 10, -10);
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
//            insertData(stateDao, refTime, "TEST_KEY", "test", 100, 10);
//            insertData(stateDao, refTime, "TEST_KEY", "test", 10, -10);
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
//    @Test
//    void testCondense2() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider, tableName);
//
//            Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
//            insertData(stateDao, refTime, "TEST_KEY", "test", 100, 60 * 60 * 24);
//            insertData(stateDao, refTime, "TEST_KEY2", "test2", 100, 60 * 60 * 24);
//            insertData(stateDao, refTime, "TEST_KEY", "test", 10, -60 * 60 * 24);
//            insertData(stateDao, refTime, "TEST_KEY2", "test2", 10, -60 * 60 * 24);
//
//            assertThat(stateDao.count()).isEqualTo(218);
//
//            stateDao.condense(refTime);
//            assertThat(stateDao.count()).isEqualTo(200);
//
//            stateDao.condense(Instant.parse("2000-01-10T00:00:00.000Z"));
//            assertThat(stateDao.count()).isEqualTo(182);
//        });
//    }
//
//    private void insertData(final TemporalStateDao stateDao,
//                            final Instant refTime,
//                            final String key,
//                            final String value,
//                            final int rows,
//                            final long deltaSeconds) {
//        final ByteBuffer byteBuffer = ByteBuffer.wrap((value).getBytes(StandardCharsets.UTF_8));
//        for (int i = 0; i < rows; i++) {
//            final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
//            final TemporalState state = new TemporalState(
//                    key,
//                    effectiveTime,
//                    StringValue.TYPE_ID,
//                    byteBuffer);
//            stateDao.insert(Collections.singletonList(state));
//        }
//    }
}
