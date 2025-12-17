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

package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.StroomPipelineTestFileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineDataCache extends AbstractProcessIntegrationTest {

    @Inject
    PipelineStore pipelineStore;
    @Inject
    PipelineDataCache pipelineDataCache;

    @Test
    void test() {
        final DocRef docRef = PipelineTestUtil.createTestPipeline(pipelineStore,
                StroomPipelineTestFileUtil.getString("TestPipelineFactory/EventDataPipeline.Pipeline.json"));
        final PipelineDoc pipelineDoc1 = pipelineStore.readDocument(docRef);
        final PipelineDoc pipelineDoc2 = pipelineStore.readDocument(docRef);
        final PipelineData pipelineData1 = pipelineDataCache.get(pipelineDoc1);
        final PipelineData pipelineData2 = pipelineDataCache.get(pipelineDoc2);

        assertThat(pipelineData1).isNotNull();
        assertThat(pipelineData2).isNotNull();
        assertThat(pipelineData1 == pipelineData2).isTrue();
    }
}
