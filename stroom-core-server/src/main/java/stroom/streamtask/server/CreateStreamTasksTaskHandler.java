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

package stroom.streamtask.server;

import javax.annotation.Resource;

import org.springframework.context.annotation.Scope;

import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

@TaskHandlerBean(task = CreateStreamTasksTask.class)
@Scope(value = StroomScope.TASK)
public class CreateStreamTasksTaskHandler extends AbstractTaskHandler<CreateStreamTasksTask, VoidResult> {
    @Resource
    private StreamTaskCreator streamTaskCreator;
    @Resource
    private TaskMonitor taskMonitor;

    @Override
    public VoidResult exec(final CreateStreamTasksTask task) {
        streamTaskCreator.createTasks(taskMonitor);
        return new VoidResult();
    }
}
