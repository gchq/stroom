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

import org.springframework.context.annotation.Scope;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.SavePipelineXMLAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = SavePipelineXMLAction.class)
@Scope(value = StroomScope.TASK)
class SavePipelineXMLHandler extends AbstractTaskHandler<SavePipelineXMLAction, VoidResult> {
    private final PipelineService pipelineService;

    @Inject
    SavePipelineXMLHandler(final PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public VoidResult exec(final SavePipelineXMLAction action) {
        final PipelineEntity pipelineEntity = pipelineService.loadByUuid(action.getPipeline().getUuid());

        if (pipelineEntity != null) {
            pipelineEntity.setData(action.getXml());
            pipelineService.saveWithoutMarshal(pipelineEntity);
        }

        return VoidResult.INSTANCE;
    }
}
