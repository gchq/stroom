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
 */

package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.shared.DocRefUtil;
import stroom.pipeline.PipelineSerialiser;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineLayer;
import stroom.task.api.SimpleTaskContext;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.StroomPipelineTestFileUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestPipelineFactory extends AbstractProcessIntegrationTest {

    private final MockPipelineElementRegistryFactory elementRegistryFactory = new MockPipelineElementRegistryFactory();
    private final PipelineSerialiser pipelineSerialiser = new PipelineSerialiser(new Serialiser2FactoryImpl());

    @Mock
    private ErrorReceiverProxy mockErrorReceiverProxy;

    @Test
    void testSingle() {
        final PipelineDoc pipelineDoc = PipelineTestUtil.createBasicPipeline(
                StroomPipelineTestFileUtil.getString("TestPipelineFactory/EventDataPipeline.Pipeline.json"));
        final DocRef docRef = DocRefUtil.create(pipelineDoc);
        final PipelineData pipelineData = pipelineDoc.getPipelineData();

        final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
        final PipelineDataValidator pipelineDataValidator = new PipelineDataValidator(elementRegistryFactory);
        pipelineDataValidator.validate(pipelineData, elementMap);

        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(new PipelineLayer(docRef, pipelineData));
        final PipelineData mergedPipelineData = pipelineDataMerger.createMergedData();

        final PipelineFactory pipelineFactory = new PipelineFactory(
                elementRegistryFactory,
                elementRegistryFactory,
                new SimpleProcessorFactory(),
                mockErrorReceiverProxy);
        final Pipeline pipeline = pipelineFactory.create(mergedPipelineData, new SimpleTaskContext());

        System.out.println(pipeline);
    }

    @Test
    void testOverride() {
        final PipelineFactory pipelineFactory = new PipelineFactory(
                elementRegistryFactory,
                elementRegistryFactory,
                new SimpleProcessorFactory(),
                mockErrorReceiverProxy);

        final String data1 = StroomPipelineTestFileUtil
                .getString("TestPipelineFactory/EventDataPipeline.Pipeline.json");
        final String data2 = StroomPipelineTestFileUtil.getString(
                "TestPipelineFactory/OverridePipeline.Pipeline.json");
        final String data3 = StroomPipelineTestFileUtil.getString(
                "TestPipelineFactory/CombinedPipeline.Pipeline.json");

        final PipelineDoc pipeline1 = PipelineTestUtil.createBasicPipeline(data1);
        final PipelineDoc pipeline2 = PipelineTestUtil.createBasicPipeline(data2);

        final DocRef docRef1 = DocRefUtil.create(pipeline1);
        final PipelineData pipelineData1 = pipeline1.getPipelineData();
        final DocRef docRef2 = DocRefUtil.create(pipeline2);
        final PipelineData pipelineData2 = pipeline2.getPipelineData();

        assertThat(pipelineSerialiser.getJsonFromPipelineData(pipelineData1)).isEqualTo(data1);
        assertThat(pipelineSerialiser.getJsonFromPipelineData(pipelineData2)).isEqualTo(data2);

        // Now merge the pipeline data into a single config.
        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(
                new PipelineLayer(docRef1, pipelineData1),
                new PipelineLayer(docRef2, pipelineData2));
        PipelineData pipelineData3 = pipelineDataMerger.createMergedData();
        pipelineData3 = PipelineDataUtil.normalise(pipelineData3);

        // Take a look at the merged config.
        final PipelineDoc pipeline3 = PipelineDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .pipelineData(pipelineData3)
                .build();

        assertThat(pipelineSerialiser.getJsonFromPipelineData(pipeline3.getPipelineData())).isEqualTo(data3);

        // Create a parser with the merged config.
        pipelineFactory.create(pipelineData3, new SimpleTaskContext());
    }

    @Test
    void testOverride2() {
        final PipelineFactory pipelineFactory = new PipelineFactory(
                elementRegistryFactory,
                elementRegistryFactory,
                new SimpleProcessorFactory(),
                mockErrorReceiverProxy);

        final String data1 = StroomPipelineTestFileUtil
                .getString("TestPipelineFactory/TestBasePipeline.Pipeline.json");
        final String data2 = StroomPipelineTestFileUtil.getString(
                "TestPipelineFactory/TestChildPipeline.Pipeline.json");
        final String data3 = StroomPipelineTestFileUtil.getString(
                "TestPipelineFactory/TestChildCombinedPipeline.Pipeline.json");

        final PipelineDoc pipeline1 = PipelineTestUtil.createBasicPipeline(data1);
        final PipelineDoc pipeline2 = PipelineTestUtil.createBasicPipeline(data2);

        final DocRef docRef1 = DocRefUtil.create(pipeline1);
        final PipelineData pipelineData1 = pipeline1.getPipelineData();
        final DocRef docRef2 = DocRefUtil.create(pipeline2);
        final PipelineData pipelineData2 = pipeline2.getPipelineData();

        assertThat(pipelineSerialiser.getJsonFromPipelineData(pipelineData1)).isEqualTo(data1);
        assertThat(pipelineSerialiser.getJsonFromPipelineData(pipelineData2)).isEqualTo(data2);

        // Now merge the pipeline data into a single config.
        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(
                new PipelineLayer(docRef1, pipelineData1),
                new PipelineLayer(docRef2, pipelineData2));
        PipelineData pipelineData3 = pipelineDataMerger.createMergedData();
        pipelineData3 = PipelineDataUtil.normalise(pipelineData3);

        // Take a look at the merged config.
        final PipelineDoc pipeline3 = PipelineDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .pipelineData(pipelineData3)
                .build();

        assertThat(pipelineSerialiser.getJsonFromPipelineData(pipeline3.getPipelineData())).isEqualTo(data3);

        // Create a parser with the merged config.
        pipelineFactory.create(pipelineData3, new SimpleTaskContext());
    }
}
