/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.test;

import stroom.pipeline.server.PipelineMarshaller;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.query.api.DocRef;

public final class PipelineTestUtil {
    private PipelineTestUtil() {
    }

    public static PipelineEntity createBasicPipeline(final PipelineMarshaller pipelineMarshaller, final String data) {
        PipelineEntity pipelineEntity = new PipelineEntity();
        pipelineEntity.setName("test");
        pipelineEntity.setDescription("test");
        if (data != null) {
            pipelineEntity.setData(data);
            pipelineEntity = pipelineMarshaller.unmarshal(pipelineEntity, true, false);
        }
        return pipelineEntity;
    }


    public static PipelineEntity createTestPipeline(final PipelineEntityService pipelineEntityService, final PipelineMarshaller pipelineMarshaller, final String data) {
        return createTestPipeline(pipelineEntityService, pipelineMarshaller, null, "test", "test", data);
    }

    public static PipelineEntity createTestPipeline(final PipelineEntityService pipelineEntityService, final PipelineMarshaller pipelineMarshaller, final DocRef folder, final String name,
            final String description, final String data) {
        PipelineEntity pipelineEntity = pipelineEntityService.create(folder, name);
        pipelineEntity.setName(name);
        pipelineEntity.setDescription(description);
        if (data != null) {
            pipelineEntity.setData(data);
            pipelineEntity = pipelineMarshaller.unmarshal(pipelineEntity, true, false);
        }
        return pipelineEntityService.save(pipelineEntity);
    }
}
