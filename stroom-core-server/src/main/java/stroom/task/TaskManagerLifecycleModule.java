/*
 * Copyright 2018 Crown Copyright
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

package stroom.task;

import stroom.lifecycle.api.AbstractLifecycleModule;
import stroom.lifecycle.api.RunnableWrapper;
import stroom.task.api.TaskManager;

import javax.inject.Inject;

public class TaskManagerLifecycleModule extends AbstractLifecycleModule {
    @Override
    protected void configure() {
        super.configure();

        // Make sure the first thing to start and the last thing to stop is the task manager.
        bindStartup().priority(Integer.MAX_VALUE).to(TaskManagerStartup.class);
        bindShutdown().priority(Integer.MAX_VALUE).to(TaskManagerShutdown.class);
    }

    private static class TaskManagerStartup extends RunnableWrapper {
        @Inject
        TaskManagerStartup(final TaskManager taskManager) {
            super(taskManager::startup);
        }
    }

    private static class TaskManagerShutdown extends RunnableWrapper {
        @Inject
        TaskManagerShutdown(final TaskManager taskManager) {
            super(taskManager::shutdown);
        }
    }
}