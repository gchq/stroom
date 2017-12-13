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
 *
 */

package stroom.pipeline.server;

import org.springframework.context.annotation.Scope;
import stroom.pipeline.server.factory.PipelineDataValidator;
import stroom.pipeline.server.factory.PipelineStackLoader;
import stroom.pipeline.shared.FetchPipelineDataAction;
import stroom.pipeline.shared.PipelineDataMerger;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.SourcePipeline;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@TaskHandlerBean(task = FetchPipelineDataAction.class)
@Scope(value = StroomScope.TASK)
public class FetchPipelineDataHandler extends AbstractTaskHandler<FetchPipelineDataAction, SharedList<PipelineData>> {
    private final PipelineService pipelineService;
    private final PipelineStackLoader pipelineStackLoader;
    private final PipelineDataValidator pipelineDataValidator;
    private final SecurityContext securityContext;

    @Inject
    public FetchPipelineDataHandler(final PipelineService pipelineService,
                                    final PipelineStackLoader pipelineStackLoader,
                                    final PipelineDataValidator pipelineDataValidator,
                                    final SecurityContext securityContext) {
        this.pipelineService = pipelineService;
        this.pipelineStackLoader = pipelineStackLoader;
        this.pipelineDataValidator = pipelineDataValidator;
        this.securityContext = securityContext;
    }

    @Override
    public SharedList<PipelineData> exec(final FetchPipelineDataAction action) {
        final PipelineEntity pipelineEntity = pipelineService.loadByUuid(action.getPipeline().getUuid());

        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
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
}
