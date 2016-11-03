/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.factory;

import stroom.AbstractProcessIntegrationTest;
import stroom.pipeline.server.PipelineMarshaller;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.test.StroomProcessTestFileUtil;
import stroom.test.PipelineTestUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.Map;

public class TestPipelineFactory extends AbstractProcessIntegrationTest {
    private final MockPipelineElementRegistryFactory elementRegistryFactory = new MockPipelineElementRegistryFactory();
    @Resource
    private PipelineMarshaller pipelineMarshaller;

    @Test
    public void testSingle() throws Exception {
        final PipelineEntity pipelineEntity = PipelineTestUtil.createBasicPipeline(pipelineMarshaller,
                StroomProcessTestFileUtil.getString("TestPipelineFactory/EventDataPipeline.Pipeline.data.xml"));

        final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
        final PipelineDataValidator pipelineDataValidator = new PipelineDataValidator(elementRegistryFactory);
        pipelineDataValidator.validate(null, pipelineEntity.getPipelineData(), elementMap);

        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(pipelineEntity.getPipelineData());
        final PipelineData mergedPipelineData = pipelineDataMerger.createMergedData();

        final PipelineFactory pipelineFactory = new PipelineFactory(elementRegistryFactory, elementRegistryFactory,
                new SimpleProcessorFactory(), null);
        final Pipeline pipeline = pipelineFactory.create(mergedPipelineData);

        System.out.println(pipeline);
    }

    @Test
    public void testOverride() throws Exception {
        final PipelineFactory pipelineFactory = new PipelineFactory(elementRegistryFactory, elementRegistryFactory,
                new SimpleProcessorFactory(), null);

        final String data1 = StroomProcessTestFileUtil
                .getString("TestPipelineFactory/EventDataPipeline.Pipeline.data.xml");
        final String data2 = StroomProcessTestFileUtil.getString("TestPipelineFactory/OverridePipeline.Pipeline.data.xml");
        final String data3 = StroomProcessTestFileUtil.getString("TestPipelineFactory/CombinedPipeline.Pipeline.data.xml");

        PipelineEntity pipeline1 = PipelineTestUtil.createBasicPipeline(pipelineMarshaller, data1);
        PipelineEntity pipeline2 = PipelineTestUtil.createBasicPipeline(pipelineMarshaller, data2);

        // Read the pipelines.
        pipeline1 = savePipeline(pipeline1, false);
        pipeline2 = savePipeline(pipeline2, false);

        Assert.assertEquals(data1, pipeline1.getData());
        Assert.assertEquals(data2, pipeline2.getData());

        // Now merge the pipeline data into a single config.
        final PipelineDataMerger pipelineDataMerger = new PipelineDataMerger();
        pipelineDataMerger.merge(pipeline1.getPipelineData(), pipeline2.getPipelineData());
        final PipelineData pipelineData3 = pipelineDataMerger.createMergedData();
        PipelineDataUtil.normalise(pipelineData3);

        // Take a look at the merged config.
        PipelineEntity pipeline3 = new PipelineEntity();
        pipeline3.setPipelineData(pipelineData3);
        pipeline3 = savePipeline(pipeline3, false);

        Assert.assertEquals(data3, pipeline3.getData());

        // Create a parser with the merged config.
        pipelineFactory.create(pipelineData3);

        // Now try and serialize pipeline data for import export (external
        // form).
        pipeline1 = savePipeline(pipeline1, true);
        pipeline2 = savePipeline(pipeline2, true);

        // Read the external form back in and make sure it is unchanged.
        pipeline1 = loadPipeline(pipeline1, true);
        pipeline2 = loadPipeline(pipeline2, true);

        Assert.assertEquals(data1, pipeline1.getData());
        Assert.assertEquals(data2, pipeline2.getData());
    }

    private PipelineEntity loadPipeline(final PipelineEntity pipeline, final boolean external) {
        return pipelineMarshaller.unmarshal(pipeline, external, false);
    }

    private PipelineEntity savePipeline(final PipelineEntity pipeline, final boolean external) {
        return pipelineMarshaller.marshal(pipeline, external, false);
    }
}
