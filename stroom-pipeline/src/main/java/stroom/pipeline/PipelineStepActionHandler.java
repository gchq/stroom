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

package stroom.pipeline;

import stroom.pipeline.shared.PipelineStepAction;
import stroom.pipeline.shared.SteppingResult;
import stroom.pipeline.stepping.SteppingTask;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.task.TaskManager;

import javax.inject.Inject;

@TaskHandlerBean(task = PipelineStepAction.class)
class PipelineStepActionHandler extends AbstractTaskHandler<PipelineStepAction, SteppingResult> {
    private final TaskManager taskManager;
    private final Security security;

    @Inject
    PipelineStepActionHandler(final TaskManager taskManager,
                              final Security security) {
        this.taskManager = taskManager;
        this.security = security;
    }

    @Override
    public SteppingResult exec(final PipelineStepAction action) {
        return security.secureResult(() -> {
            // Copy the action settings to the server task.
            final SteppingTask task = new SteppingTask(action.getUserToken());
            task.setCriteria(action.getCriteria());
            task.setChildStreamType(action.getChildStreamType());
            task.setStepLocation(action.getStepLocation());
            task.setStepFilterMap(action.getStepFilterMap());
            task.setStepType(action.getStepType());
            task.setPipeline(action.getPipeline());
            task.setCode(action.getCode());

            // Make sure stepping can only happen on streams that are visible to
            // the user.
            // FIXME : Constrain available streams.
            // folderValidator.constrainCriteria(task.getCriteria());

            // Execute the stepping task.
            return taskManager.exec(task);
        });
    }
}
