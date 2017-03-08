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

import stroom.entity.server.MarshalOptions;
import stroom.pipeline.shared.FetchPipelineXMLAction;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedString;

import javax.annotation.Resource;

@TaskHandlerBean(task = FetchPipelineXMLAction.class)
public class FetchPipelineXMLHandler extends AbstractTaskHandler<FetchPipelineXMLAction, SharedString> {
    @Resource
    private PipelineEntityService pipelineEntityService;
    @Resource
    private MarshalOptions marshalOptions;

    @Override
    public SharedString exec(final FetchPipelineXMLAction action) {
        SharedString result = null;

        marshalOptions.setDisabled(true);

        final PipelineEntity pipelineEntity = pipelineEntityService.loadById(action.getPipelineId());

        if (pipelineEntity != null) {
            result = SharedString.wrap(pipelineEntity.getData());
        }

        return result;
    }
}
