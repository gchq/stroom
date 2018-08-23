/*
 * Copyright 2018 Crown Copyright
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

package stroom.refdata.store.offheapstore.databases;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.store.offheapstore.lmdb.LmdbUtils;
import stroom.refdata.util.ByteBufferPool;
import stroom.refdata.store.ProcessingState;
import stroom.refdata.store.RefDataProcessingInfo;
import stroom.refdata.store.RefStreamDefinition;
import stroom.refdata.store.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.store.offheapstore.serdes.RefStreamDefinitionSerde;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TestProcessingInfoDb extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProcessingInfoDb.class);

    private ProcessingInfoDb processingInfoDb = null;

    @Before
    @Override
    public void setup() {
        super.setup();

        processingInfoDb = new ProcessingInfoDb(
                lmdbEnv,
                new ByteBufferPool(),
                new RefStreamDefinitionSerde(),
                new RefDataProcessingInfoSerde());
    }

    @Test
    public void testPutAndGet() {

        byte version = 0;
        final RefStreamDefinition refStreamDefinitionA = buildUniqueRefStreamDefinition();
        final RefStreamDefinition refStreamDefinitionB = buildUniqueRefStreamDefinition();

        final RefDataProcessingInfo refDataProcessingInfoA = new RefDataProcessingInfo(
                1234567890L,
                345678901L,
                56789012L,
                ProcessingState.COMPLETE);

        final RefDataProcessingInfo refDataProcessingInfoB = new RefDataProcessingInfo(
                34567890L,
                5678901L,
                789012L,
                ProcessingState.LOAD_IN_PROGRESS);

        boolean didSucceed = false;
        didSucceed = processingInfoDb.put(refStreamDefinitionA, refDataProcessingInfoA, false);
        assertThat(didSucceed).isTrue();
        didSucceed = processingInfoDb.put(refStreamDefinitionB, refDataProcessingInfoB, false);
        assertThat(didSucceed).isTrue();

        Map<String, String> dbInfo = processingInfoDb.getDbInfo();
        LOGGER.debug("DB info: {}", dbInfo);

        int entries = Optional.ofNullable(dbInfo.get("entries")).map(Integer::parseInt).orElse(-1);
        assertThat(entries).isEqualTo(2);

        final RefDataProcessingInfo refDataProcessingInfoA2 = processingInfoDb.get(refStreamDefinitionA).get();

        assertThat(refDataProcessingInfoA).isEqualTo(refDataProcessingInfoA2);
    }

    @Test
    public void updateState() {

        byte version = 0;
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        RefDataProcessingInfo refDataProcessingInfoBefore = new RefDataProcessingInfo(
                234L,
                123L,
                345L,
                ProcessingState.LOAD_IN_PROGRESS);

        boolean didSucceed = false;

        // initial put into empty db so will succeed
        didSucceed = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);
        assertThat(didSucceed).isTrue();

        // put the same key/value with overwrite==false so will fail
        didSucceed = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);
        assertThat(didSucceed).isFalse();

        // put the same key/value with overwrite==true so will succeed
        didSucceed = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, true);
        assertThat(didSucceed).isTrue();

        // open a write txn and mutate the value
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn ->
                processingInfoDb.updateProcessingState(
                        writeTxn, refStreamDefinition, ProcessingState.COMPLETE, true));

        RefDataProcessingInfo refDataProcessingInfoAfter = processingInfoDb.get(refStreamDefinition).get();

        assertThat(refDataProcessingInfoAfter.getProcessingState()).isEqualTo(ProcessingState.COMPLETE);
        assertThat(refDataProcessingInfoAfter.getLastAccessedTimeEpochMs()).isGreaterThan(refDataProcessingInfoBefore.getLastAccessedTimeEpochMs());

        refDataProcessingInfoBefore = processingInfoDb.get(refStreamDefinition).get();
        // open a write txn and mutate the value
        LmdbUtils.doWithWriteTxn(lmdbEnv, writeTxn ->
                processingInfoDb.updateProcessingState(
                        writeTxn, refStreamDefinition, ProcessingState.PURGE_IN_PROGRESS, false));

        refDataProcessingInfoAfter = processingInfoDb.get(refStreamDefinition).get();

        assertThat(refDataProcessingInfoAfter.getProcessingState()).isEqualTo(ProcessingState.PURGE_IN_PROGRESS);
        assertThat(refDataProcessingInfoAfter.getLastAccessedTimeEpochMs()).isEqualTo(refDataProcessingInfoBefore.getLastAccessedTimeEpochMs());
    }

    @Test
    public void testUpdateLastAccessTime() {

        byte version = 0;
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        final RefDataProcessingInfo refDataProcessingInfoBefore = new RefDataProcessingInfo(
                234L,
                123L,
                345L,
                ProcessingState.LOAD_IN_PROGRESS);

        boolean didSucceed = false;

        // initial put into empty db so will succeed
        didSucceed = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);
        assertThat(didSucceed).isTrue();

        processingInfoDb.updateLastAccessedTime(refStreamDefinition);

        final RefDataProcessingInfo refDataProcessingInfoAfter = processingInfoDb.get(refStreamDefinition).get();

        assertThat(refDataProcessingInfoAfter.getProcessingState())
                .isEqualTo(refDataProcessingInfoBefore.getProcessingState());
        assertThat(refDataProcessingInfoAfter.getLastAccessedTimeEpochMs())
                .isGreaterThan(refDataProcessingInfoBefore.getLastAccessedTimeEpochMs());
    }

    @Test
    public void testDelete() {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        final RefDataProcessingInfo refDataProcessingInfoBefore = new RefDataProcessingInfo(
                234L,
                123L,
                345L,
                ProcessingState.LOAD_IN_PROGRESS);

        boolean didSucceed;

        // initial put into empty db so will succeed
        didSucceed = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);

        assertThat(didSucceed).isTrue();
        assertThat(processingInfoDb.getEntryCount()).isEqualTo(1);

        didSucceed = processingInfoDb.delete(refStreamDefinition);

        assertThat(didSucceed).isTrue();
        assertThat(processingInfoDb.getEntryCount()).isEqualTo(0);
    }

    private RefStreamDefinition buildUniqueRefStreamDefinition() {
        return new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
    }
}