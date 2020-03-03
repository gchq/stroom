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

package stroom.processor.impl;

import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskContext;
import stroom.task.api.VoidResult;

import javax.inject.Inject;


public class CreateStreamTasksTaskHandler extends AbstractTaskHandler<CreateStreamTasksTask, VoidResult> {
    private final ProcessorTaskManager processorTaskManager;
    private final TaskContext taskContext;
    private final SecurityContext securityContext;

    @Inject
    CreateStreamTasksTaskHandler(final ProcessorTaskManager processorTaskManager,
                                 final TaskContext taskContext,
                                 final SecurityContext securityContext) {
        this.processorTaskManager = processorTaskManager;
        this.taskContext = taskContext;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final CreateStreamTasksTask task) {
        return securityContext.secureResult(() -> {
            processorTaskManager.createTasks(taskContext);
            return new VoidResult();
        });
    }
}
