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


import org.junit.jupiter.api.Test;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.pipeline.PipelineSerialiser;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.common.StroomPipelineTestFileUtil;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestPipelineFactory extends AbstractProcessIntegrationTest {
    private final MockPipelineElementRegistryFactory elementRegistryFactory = new MockPipelineElementRegistryFactory();
    private final PipelineSerialiser pipelineSerialiser = new PipelineSerialiser(new Serialiser2FactoryImpl());

    @Test
    void testSingle() {
        final PipelineDoc pipelineDoc = PipelineTestUtil.createBasicPipeline(
                StroomPipelineTestFileUtil.getString("TestPipelineFactory/EventDataPipeline.Pipeline.data.xml"));

        final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
        final PipelineDataValidator pipelineDataValidator = new PipelineDataValidator(elementRegistryFactory);
        pipelineDataValidator.validate(null, pipelineDoc.getPipelineData(), elementMap);

        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(pipelineDoc.getPipelineData());
        final PipelineData mergedPipelineData = pipelineDataMerger.createMergedData();

        final PipelineFactory pipelineFactory = new PipelineFactory(
                elementRegistryFactory,
                elementRegistryFactory,
                new SimpleProcessorFactory());
        final Pipeline pipeline = pipelineFactory.create(mergedPipelineData);

        System.out.println(pipeline);
    }

    @Test
    void testOverride() {
        final PipelineFactory pipelineFactory = new PipelineFactory(
                elementRegistryFactory,
                elementRegistryFactory,
                new SimpleProcessorFactory());

        final String data1 = StroomPipelineTestFileUtil
                .getString("TestPipelineFactory/EventDataPipeline.Pipeline.data.xml");
        final String data2 = StroomPipelineTestFileUtil.getString("TestPipelineFactory/OverridePipeline.Pipeline.data.xml");
        final String data3 = StroomPipelineTestFileUtil.getString("TestPipelineFactory/CombinedPipeline.Pipeline.data.xml");

        final PipelineDoc pipeline1 = PipelineTestUtil.createBasicPipeline(data1);
        final PipelineDoc pipeline2 = PipelineTestUtil.createBasicPipeline(data2);

        assertThat(pipelineSerialiser.getXmlFromPipelineData(pipeline1.getPipelineData())).isEqualTo(data1);
        assertThat(pipelineSerialiser.getXmlFromPipelineData(pipeline2.getPipelineData())).isEqualTo(data2);

        // Now merge the pipeline data into a single config.
        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(pipeline1.getPipelineData(), pipeline2.getPipelineData());
        final PipelineData pipelineData3 = pipelineDataMerger.createMergedData();
        PipelineDataUtil.normalise(pipelineData3);

        // Take a look at the merged config.
        final PipelineDoc pipeline3 = new PipelineDoc();
        pipeline3.setPipelineData(pipelineData3);

        assertThat(pipelineSerialiser.getXmlFromPipelineData(pipeline3.getPipelineData())).isEqualTo(data3);

        // Create a parser with the merged config.
        pipelineFactory.create(pipelineData3);
    }
}
