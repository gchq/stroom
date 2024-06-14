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

package stroom.state.impl;

import stroom.pipeline.refdata.store.StringValue;

import com.datastax.oss.driver.api.core.CqlSession;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestRangedStateDao {

    @Test
    void testDao() {
        ScyllaDbUtil.test((session, keyspaceName) -> {
            RangedStateDao.dropTables(session);
            RangedStateDao.createTables(session);

            final ByteBuffer byteBuffer = ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8));
            final RangedState state = new RangedState(
                    "TEST_MAP",
                    10,
                    30,
                    Instant.ofEpochMilli(0),
                    StringValue.TYPE_ID,
                    byteBuffer);
            RangedStateDao.insert(session, Collections.singletonList(state));

            final RangedStateRequest stateRequest =
                    new RangedStateRequest("TEST_MAP", 11, Instant.ofEpochSecond(10));
            final Optional<State> optional = RangedStateDao.getState(session, stateRequest);
            assertThat(optional).isNotEmpty();
            final State res = optional.get();
            assertThat(res.map()).isEqualTo("TEST_MAP");
            assertThat(res.key()).isEqualTo("11");
            assertThat(res.effectiveTime()).isEqualTo(Instant.ofEpochMilli(0));
            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.getValueAsString()).isEqualTo("test");
        });
    }
}
