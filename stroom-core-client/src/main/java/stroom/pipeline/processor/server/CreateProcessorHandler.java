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

package stroom.pipeline.processor.server;

import stroom.pipeline.processor.shared.CreateProcessorAction;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.security.Secured;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;

import javax.annotation.Resource;

@TaskHandlerBean(task = CreateProcessorAction.class)
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class CreateProcessorHandler extends AbstractTaskHandler<CreateProcessorAction, StreamProcessorFilter> {
    @Resource
    private StreamProcessorFilterService streamProcessorFilterService;
    @Resource
    private PipelineEntityService pipelineEntityService;

    @Override
    public StreamProcessorFilter exec(final CreateProcessorAction action) {
        final PipelineEntity pipelineEntity = pipelineEntityService.loadByUuid(action.getPipeline().getUuid());
        return streamProcessorFilterService.createNewFilter(pipelineEntity, action.getFindStreamCriteria(),
                action.isEnabled(), action.getPriority());
    }
}
