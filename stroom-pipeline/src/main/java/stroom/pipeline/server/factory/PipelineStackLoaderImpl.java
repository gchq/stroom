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

import stroom.query.api.DocRef;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class PipelineStackLoaderImpl implements PipelineStackLoader {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(PipelineStackLoaderImpl.class);

    @Resource
    private PipelineEntityService pipelineEntityService;

    /**
     * Loads and returns a stack of pipelines representing the inheritance
     * chain. The first pipeline in the chain is at the start of the list and
     * the last pipeline (the one we have supplied) is at the end.
     * <p>
     * This method will prevent circular pipeline references by ensuring only
     * unique items are added to the list.
     *
     * @param pipelineEntity The pipeline that we want to load the inheritance chain for.
     * @return The inheritance chain for the supplied pipeline. The supplied
     * pipeline will be the last element in the list.
     */
    @Override
    public List<PipelineEntity> loadPipelineStack(final PipelineEntity pipelineEntity) {
        // Load the pipeline.
        final List<PipelineEntity> pipelineList = new ArrayList<>();
        PipelineEntity parent = pipelineEntityService.load(pipelineEntity);

        if (parent == null) {
            throw new RuntimeException("Unable to load pipeline: " + pipelineEntity);
        }

        pipelineList.add(0, parent);

        boolean circular = false;
        while (parent.getParentPipeline() != null && !circular) {
            final DocRef parentRef = parent.getParentPipeline();
            parent = pipelineEntityService.loadByUuid(parentRef.getUuid());

            if (parent == null) {
                throw new RuntimeException("Unable to load parent pipeline: " + parentRef);
            }

            // Ensure all items in the list are unique to prevent circular
            // references.
            if (!pipelineList.contains(parent)) {
                pipelineList.add(0, parent);
            } else {
                circular = true;
                LOGGER.warn("Circular reference detected in pipeline: " + pipelineEntity.toString());
            }
        }
        return pipelineList;
    }
}
