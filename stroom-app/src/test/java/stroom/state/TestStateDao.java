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

package stroom.state;

import stroom.test.AbstractCoreIntegrationTest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled // ScyllaDB based state store is defunct
@ExtendWith(MockitoExtension.class)
class TestStateDao extends AbstractCoreIntegrationTest {
//
//    @Test
//    void testDao() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider, tableName);
//
//            final ByteBuffer byteBuffer = ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8));
//            final TemporalState state = new TemporalState(
//                    "TEST_KEY",
//                    Instant.ofEpochMilli(0),
//                    StringValue.TYPE_ID,
//                    byteBuffer);
//            stateDao.insert(Collections.singletonList(state));
//
//            final TemporalStateRequest stateRequest = new TemporalStateRequest(tableName,
//                    "TEST_KEY",
//                    Instant.ofEpochSecond(10));
//            final Optional<TemporalState> optional = stateDao.getState(stateRequest);
//            assertThat(optional).isNotEmpty();
//            final TemporalState res = optional.get();
//            assertThat(res.key()).isEqualTo("TEST_KEY");
//            assertThat(res.effectiveTime()).isEqualTo(Instant.ofEpochMilli(0));
//            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
//            assertThat(res.getValueAsString()).isEqualTo("test");
//        });
//    }
}
