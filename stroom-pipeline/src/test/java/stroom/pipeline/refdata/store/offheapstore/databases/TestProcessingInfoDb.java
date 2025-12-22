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

package stroom.pipeline.refdata.store.offheapstore.databases;


import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.RefStreamDefinitionSerde;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessingInfoDb extends AbstractStoreDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestProcessingInfoDb.class);

    private ProcessingInfoDb processingInfoDb = null;

    @BeforeEach
    void setup() {
        processingInfoDb = new ProcessingInfoDb(
                refDataLmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new RefStreamDefinitionSerde(),
                new RefDataProcessingInfoSerde());
    }

    @Test
    void testPutAndGet() {

        final byte version = 0;
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

        PutOutcome putOutcome;
        putOutcome = processingInfoDb.put(refStreamDefinitionA, refDataProcessingInfoA, false);
        assertThat(putOutcome.isSuccess())
                .isTrue();
        assertThat(putOutcome.isDuplicate())
                .hasValue(false);
        putOutcome = processingInfoDb.put(refStreamDefinitionB, refDataProcessingInfoB, false);
        assertThat(putOutcome.isSuccess())
                .isTrue();
        assertThat(putOutcome.isDuplicate())
                .hasValue(false);

        final Map<String, String> dbInfo = processingInfoDb.getDbInfo();
        LOGGER.debug("DB info: {}", dbInfo);

        final int entries = Optional.ofNullable(dbInfo.get("entries")).map(Integer::parseInt).orElse(-1);
        assertThat(entries).isEqualTo(2);

        final RefDataProcessingInfo refDataProcessingInfoA2 = processingInfoDb.get(refStreamDefinitionA).get();

        assertThat(refDataProcessingInfoA).isEqualTo(refDataProcessingInfoA2);
    }

    @Test
    void updateState() {

        final byte version = 0;
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        RefDataProcessingInfo refDataProcessingInfoBefore = new RefDataProcessingInfo(
                234L,
                123L,
                345L,
                ProcessingState.LOAD_IN_PROGRESS);

        PutOutcome putOutcome;

        // initial put into empty db so will succeed
        putOutcome = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);
        assertThat(putOutcome.isSuccess())
                .isTrue();
        assertThat(putOutcome.isDuplicate())
                .hasValue(false);

        // put the same key/value with overwrite==false so will fail
        putOutcome = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);
        assertThat(putOutcome.isSuccess())
                .isFalse();
        assertThat(putOutcome.isDuplicate())
                .hasValue(true);

        // put the same key/value with overwrite==true so will succeed
        putOutcome = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, true);
        assertThat(putOutcome.isSuccess())
                .isTrue();
        assertThat(putOutcome.isDuplicate())
                .hasValue(true);

        // open a write txn and mutate the value
        lmdbEnv.doWithWriteTxn(writeTxn ->
                processingInfoDb.updateProcessingState(
                        writeTxn, refStreamDefinition, ProcessingState.COMPLETE, true));

        RefDataProcessingInfo refDataProcessingInfoAfter = processingInfoDb.get(refStreamDefinition).get();

        assertThat(refDataProcessingInfoAfter.getProcessingState())
                .isEqualTo(ProcessingState.COMPLETE);
        assertThat(refDataProcessingInfoAfter.getLastAccessedTimeEpochMs())
                .isGreaterThan(refDataProcessingInfoBefore.getLastAccessedTimeEpochMs());

        refDataProcessingInfoBefore = processingInfoDb.get(refStreamDefinition).get();
        // open a write txn and mutate the value
        lmdbEnv.doWithWriteTxn(writeTxn ->
                processingInfoDb.updateProcessingState(
                        writeTxn, refStreamDefinition, ProcessingState.PURGE_IN_PROGRESS, false));

        refDataProcessingInfoAfter = processingInfoDb.get(refStreamDefinition).get();

        assertThat(refDataProcessingInfoAfter.getProcessingState())
                .isEqualTo(ProcessingState.PURGE_IN_PROGRESS);
        assertThat(refDataProcessingInfoAfter.getLastAccessedTimeEpochMs())
                .isEqualTo(refDataProcessingInfoBefore.getLastAccessedTimeEpochMs());
    }

    @Test
    void testUpdateLastAccessTime() {

        final byte version = 0;
        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        final RefDataProcessingInfo refDataProcessingInfoBefore = new RefDataProcessingInfo(
                234L,
                123L,
                345L,
                ProcessingState.LOAD_IN_PROGRESS);

        final PutOutcome putOutcome;

        // initial put into empty db so will succeed
        putOutcome = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);
        assertThat(putOutcome.isSuccess())
                .isTrue();
        assertThat(putOutcome.isDuplicate())
                .hasValue(false);

        processingInfoDb.updateLastAccessedTime(refStreamDefinition);

        final RefDataProcessingInfo refDataProcessingInfoAfter = processingInfoDb.get(refStreamDefinition).get();

        assertThat(refDataProcessingInfoAfter.getProcessingState())
                .isEqualTo(refDataProcessingInfoBefore.getProcessingState());
        assertThat(refDataProcessingInfoAfter.getLastAccessedTimeEpochMs())
                .isGreaterThan(refDataProcessingInfoBefore.getLastAccessedTimeEpochMs());
    }

    @Test
    void testDelete() {

        final RefStreamDefinition refStreamDefinition = buildUniqueRefStreamDefinition();

        final RefDataProcessingInfo refDataProcessingInfoBefore = new RefDataProcessingInfo(
                234L,
                123L,
                345L,
                ProcessingState.LOAD_IN_PROGRESS);

        final PutOutcome putOutcome;

        // initial put into empty db so will succeed
        putOutcome = processingInfoDb.put(refStreamDefinition, refDataProcessingInfoBefore, false);

        assertThat(putOutcome.isSuccess())
                .isTrue();
        assertThat(putOutcome.isDuplicate())
                .hasValue(false);
        assertThat(processingInfoDb.getEntryCount()).isEqualTo(1);

        final boolean didDeleteSucceed = processingInfoDb.delete(refStreamDefinition);

        assertThat(didDeleteSucceed).isTrue();
        assertThat(processingInfoDb.getEntryCount()).isEqualTo(0);
    }

    private RefStreamDefinition buildUniqueRefStreamDefinition() {
        return new RefStreamDefinition(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                123456L);
    }
}
