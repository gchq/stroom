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

package stroom.refdata.offheapstore.databases;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.api.v2.DocRef;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.serdes.RefDataProcessingInfoSerde;
import stroom.refdata.offheapstore.serdes.RefStreamDefinitionSerde;
import stroom.refdata.saxevents.LmdbUtils;

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
                new RefStreamDefinitionSerde(),
                new RefDataProcessingInfoSerde());
    }

    @Test
    public void testPutAndGet() {

        byte version = 0;
        final RefStreamDefinition refStreamDefinitionA = new RefStreamDefinition(
                new DocRef("MyType", UUID.randomUUID().toString()),
                version,
                123456L,
                1);
        final RefStreamDefinition refStreamDefinitionB = new RefStreamDefinition(
                new DocRef("MyType", UUID.randomUUID().toString()),
                version,
                654321L,
                1);

        final RefDataProcessingInfo refDataProcessingInfoA = new RefDataProcessingInfo(
                1234567890L,
                345678901L,
                56789012L,
                RefDataProcessingInfo.ProcessingState.COMPLETE);

        final RefDataProcessingInfo refDataProcessingInfoB = new RefDataProcessingInfo(
                34567890L,
                5678901L,
                789012L,
                RefDataProcessingInfo.ProcessingState.IN_PROGRESS);

        processingInfoDb.put(refStreamDefinitionA, refDataProcessingInfoA);
        processingInfoDb.put(refStreamDefinitionB, refDataProcessingInfoB);

        Map<String, String> dbInfo = LmdbUtils.getDbInfo(lmdbEnv, processingInfoDb.getLmdbDbi());
        LOGGER.debug("DB info: {}", dbInfo);

        int entries = Optional.ofNullable(dbInfo.get("entries")).map(Integer::parseInt).orElse(-1);
        assertThat(entries).isEqualTo(2);

        final RefDataProcessingInfo refDataProcessingInfoA2 = processingInfoDb.get(refStreamDefinitionA);

        assertThat(refDataProcessingInfoA).isEqualTo(refDataProcessingInfoA2);
    }
}