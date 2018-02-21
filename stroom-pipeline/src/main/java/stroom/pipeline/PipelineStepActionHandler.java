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

import stroom.pipeline.server.stepping.SteppingTask;
import stroom.pipeline.shared.PipelineStepAction;
import stroom.pipeline.shared.SteppingResult;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;

import javax.inject.Inject;

@TaskHandlerBean(task = PipelineStepAction.class)
class PipelineStepActionHandler extends AbstractTaskHandler<PipelineStepAction, SteppingResult> {
    private final TaskManager taskManager;

    @Inject
    PipelineStepActionHandler(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public SteppingResult exec(final PipelineStepAction action) {
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
    }
}
