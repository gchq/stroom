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

package stroom.process.server;

import org.springframework.context.annotation.Scope;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.process.shared.CreateProcessorAction;
import stroom.security.Secured;
import stroom.streamtask.server.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = CreateProcessorAction.class)
@Scope(StroomScope.TASK)
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class CreateProcessorHandler extends AbstractTaskHandler<CreateProcessorAction, StreamProcessorFilter> {
    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private PipelineService pipelineService;

    @Override
    public StreamProcessorFilter exec(final CreateProcessorAction action) {
        final PipelineEntity pipelineEntity = pipelineService.loadByUuid(action.getPipeline().getUuid());
        return streamProcessorFilterService.createNewFilter(pipelineEntity, action.getQueryData(),
                action.isEnabled(), action.getPriority());
    }
}
