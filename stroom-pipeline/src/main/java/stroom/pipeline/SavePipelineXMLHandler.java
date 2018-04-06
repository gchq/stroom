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

package stroom.pipeline;

import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.SavePipelineXMLAction;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = SavePipelineXMLAction.class)
class SavePipelineXMLHandler extends AbstractTaskHandler<SavePipelineXMLAction, VoidResult> {
    private final PipelineService pipelineService;
    private final Security security;

    @Inject
    SavePipelineXMLHandler(final PipelineService pipelineService,
                           final Security security) {
        this.pipelineService = pipelineService;
        this.security = security;
    }

    @Override
    public VoidResult exec(final SavePipelineXMLAction action) {
        return security.secureResult(() -> {
            final PipelineEntity pipelineEntity = pipelineService.loadByUuid(action.getPipeline().getUuid());

            if (pipelineEntity != null) {
                pipelineEntity.setData(action.getXml());
                pipelineService.saveWithoutMarshal(pipelineEntity);
            }

            return VoidResult.INSTANCE;
        });
    }
}
