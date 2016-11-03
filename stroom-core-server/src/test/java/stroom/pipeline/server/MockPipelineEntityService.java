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

package stroom.pipeline.server;

import stroom.entity.server.MockDocumentEntityService;
import stroom.entity.shared.DocRef;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.util.spring.StroomSpringProfiles;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Very simple mock that keeps everything in memory.
 * </p>
 * <p>
 * <p>
 * You can call clear at any point to clear everything down.
 * </p>
 */
@Component("pipelineEntityService")
@Profile(StroomSpringProfiles.TEST)
public class MockPipelineEntityService extends MockDocumentEntityService<PipelineEntity, FindPipelineEntityCriteria>
        implements PipelineEntityService {
    /**
     * Loads and returns a stack of pipelines representing the inheritance
     * chain. The first pipeline in the chain is at the start of the list and
     * the last pipeline (the one we have supplied) is at the end.
     *
     * @param pipelineEntity The pipeline that we want to load the inheritance chain for.
     * @return The inheritance chain for the supplied pipeline. The supplied
     * pipeline will be the last element in the list.
     */
    public List<PipelineEntity> getPipelines(final PipelineEntity pipelineEntity) {
        // Load the pipeline.
        final List<PipelineEntity> pipelineList = new ArrayList<>();
        PipelineEntity parent = load(pipelineEntity);
        pipelineList.add(0, parent);
        while (parent.getParentPipeline() != null) {
            final DocRef parentRef = parent.getParentPipeline();
            parent = loadByUuid(parentRef.getUuid());
            pipelineList.add(0, parent);
        }
        return pipelineList;
    }

    @Override
    public Class<PipelineEntity> getEntityClass() {
        return PipelineEntity.class;
    }
}
