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

import stroom.pipeline.server.factory.PipelineDataValidator;
import stroom.pipeline.server.factory.PipelineStackLoader;
import stroom.pipeline.shared.FetchPipelineDataAction;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.SourcePipeline;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@TaskHandlerBean(task = FetchPipelineDataAction.class)
public class FetchPipelineDataHandler extends AbstractTaskHandler<FetchPipelineDataAction, SharedList<PipelineData>> {
    private final PipelineEntityService pipelineEntityService;
    private final PipelineStackLoader pipelineStackLoader;
    private final PipelineDataValidator pipelineDataValidator;

    @Inject
    public FetchPipelineDataHandler(final PipelineEntityService pipelineEntityService,
            final PipelineStackLoader pipelineStackLoader, final PipelineDataValidator pipelineDataValidator) {
        this.pipelineEntityService = pipelineEntityService;
        this.pipelineStackLoader = pipelineStackLoader;
        this.pipelineDataValidator = pipelineDataValidator;
    }

    @Override
    public SharedList<PipelineData> exec(final FetchPipelineDataAction action) {
        final PipelineEntity pipelineEntity = pipelineEntityService.loadByUuid(action.getPipeline().getUuid());
        final List<PipelineEntity> pipelines = pipelineStackLoader.loadPipelineStack(pipelineEntity);
        final SharedList<PipelineData> result = new SharedList<>(pipelines.size());

        final Map<String, PipelineElementType> elementMap = PipelineDataMerger.createElementMap();
        for (final PipelineEntity pipe : pipelines) {
            final PipelineData pipelineData = pipe.getPipelineData();

            // Validate the pipeline data and add element and property type
            // information.
            final SourcePipeline source = new SourcePipeline(pipe);
            pipelineDataValidator.validate(source, pipelineData, elementMap);
            result.add(pipelineData);
        }

        return result;
    }
}
