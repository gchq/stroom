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

import stroom.logging.StreamEventLog;
import stroom.pipeline.shared.PipelineStepAction;
import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.shared.SteppingResult;
import stroom.pipeline.stepping.SteppingTask;
import stroom.task.TaskManager;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class PipelineStepActionHandler extends AbstractTaskHandler<PipelineStepAction, SteppingResult> {
    private final TaskManager taskManager;
    private final StreamEventLog streamEventLog;

    @Inject
    PipelineStepActionHandler(final TaskManager taskManager,
                              final StreamEventLog streamEventLog) {
        this.taskManager = taskManager;
        this.streamEventLog = streamEventLog;
    }

    @Override
    public SteppingResult exec(final PipelineStepAction action) {
        SteppingResult result = null;
        StepLocation stepLocation = action.getStepLocation();

        try {
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
            result = taskManager.exec(task);
            if (result.getStepLocation() != null) {
                stepLocation = result.getStepLocation();
            }

            if (stepLocation != null) {
                streamEventLog.stepStream(stepLocation.getEventId(), null, action.getChildStreamType(), action.getPipeline(), null);
            }
        } catch (final RuntimeException e) {
            if (stepLocation != null) {
                streamEventLog.stepStream(stepLocation.getEventId(), null, action.getChildStreamType(), action.getPipeline(), e);
            }
        }

        return result;
    }
}
