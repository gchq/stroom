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

package stroom.state;

import stroom.docref.DocRef;
import stroom.pipeline.refdata.store.StringValue;
import stroom.state.impl.CqlSessionFactory;
import stroom.state.impl.ScyllaDbDocStore;
import stroom.state.impl.StateDocStore;
import stroom.state.impl.dao.TemporalState;
import stroom.state.impl.dao.TemporalStateDao;
import stroom.state.impl.dao.TemporalStateRequest;
import stroom.state.shared.StateDoc;
import stroom.state.shared.StateType;
import stroom.test.AbstractCoreIntegrationTest;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestStateDao extends AbstractCoreIntegrationTest {

    @Inject
    private CqlSessionFactory cqlSessionFactory;
    @Inject
    private ScyllaDbDocStore scyllaDbDocStore;
    @Inject
    private StateDocStore stateDocStore;

    @Test
    void testDao() {
        final DocRef scyllaDbDocRef = scyllaDbDocStore.createDocument("test");

        final DocRef stateDocRef = stateDocStore.createDocument("test_state");
        StateDoc stateDoc = stateDocStore.readDocument(stateDocRef);
        stateDoc.setScyllaDbRef(scyllaDbDocRef);
        stateDoc.setStateType(StateType.STATE);
        stateDoc = stateDocStore.writeDocument(stateDoc);

        final Provider<CqlSession> sessionProvider = cqlSessionFactory.getSessionProvider(stateDocRef.getName());
        final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider);
        stateDao.dropTables();
        stateDao.createTables();

        final ByteBuffer byteBuffer = ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8));
        final TemporalState state = new TemporalState(
                "TEST_KEY",
                Instant.ofEpochMilli(0),
                StringValue.TYPE_ID,
                byteBuffer);
        stateDao.insert(Collections.singletonList(state));

        final TemporalStateRequest stateRequest = new TemporalStateRequest("TEST_MAP",
                "TEST_KEY",
                Instant.ofEpochSecond(10));
        final Optional<TemporalState> optional = stateDao.getState(stateRequest);
        assertThat(optional).isNotEmpty();
        final TemporalState res = optional.get();
        assertThat(res.key()).isEqualTo("TEST_KEY");
        assertThat(res.effectiveTime()).isEqualTo(Instant.ofEpochMilli(0));
        assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
        assertThat(res.getValueAsString()).isEqualTo("test");
    }
}
