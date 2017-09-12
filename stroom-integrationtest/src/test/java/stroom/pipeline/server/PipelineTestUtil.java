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

package stroom.pipeline.server;

import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v1.DocRef;

public final class PipelineTestUtil {
    private static final PipelineMarshaller pipelineMarshaller = new PipelineMarshaller();

    private PipelineTestUtil() {
    }

    public static PipelineEntity createBasicPipeline(final String data) {
        PipelineEntity pipelineEntity = new PipelineEntity();
        pipelineEntity.setName("test");
        pipelineEntity.setDescription("test");
        if (data != null) {
            pipelineEntity.setData(data);
            pipelineEntity = pipelineMarshaller.unmarshal(pipelineEntity);
        }
        return pipelineEntity;
    }


    public static PipelineEntity createTestPipeline(final PipelineService pipelineService, final String data) {
        return createTestPipeline(pipelineService, "test", "test", data);
    }

    public static PipelineEntity createTestPipeline(final PipelineService pipelineService, final String name,
                                                    final String description, final String data) {
        PipelineEntity pipelineEntity = pipelineService.create(name);
        pipelineEntity.setName(name);
        pipelineEntity.setDescription(description);
        if (data != null) {
            pipelineEntity.setData(data);
            pipelineEntity = pipelineMarshaller.unmarshal(pipelineEntity);
        }
        return pipelineService.save(pipelineEntity);
    }

    public static PipelineEntity loadPipeline(final PipelineEntity pipeline) {
        return pipelineMarshaller.unmarshal(pipeline);
    }

    public static PipelineEntity savePipeline(final PipelineEntity pipeline) {
        return pipelineMarshaller.marshal(pipeline);
    }
}
