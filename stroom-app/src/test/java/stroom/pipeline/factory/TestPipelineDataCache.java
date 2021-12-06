package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.StroomPipelineTestFileUtil;

import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineDataCache extends AbstractProcessIntegrationTest {

    @Inject
    PipelineStore pipelineStore;
    @Inject
    PipelineDataCache pipelineDataCache;

    @Test
    void test() {
        final DocRef docRef = PipelineTestUtil.createTestPipeline(pipelineStore,
                StroomPipelineTestFileUtil.getString("TestPipelineFactory/EventDataPipeline.Pipeline.data.xml"));
        final PipelineDoc pipelineDoc1 = pipelineStore.readDocument(docRef);
        final PipelineDoc pipelineDoc2 = pipelineStore.readDocument(docRef);
        final PipelineData pipelineData1 = pipelineDataCache.get(pipelineDoc1);
        final PipelineData pipelineData2 = pipelineDataCache.get(pipelineDoc2);

        assertThat(pipelineData1).isNotNull();
        assertThat(pipelineData2).isNotNull();
        assertThat(pipelineData1 == pipelineData2).isTrue();
    }
}
