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

package stroom.task.impl;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.task.api.TaskManager;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

public class TaskManagerLifecycleModule extends AbstractModule {
    @Override
    protected void configure() {

        // Make sure the first thing to start and the last thing to stop is the task manager.
        LifecycleBinder.create(binder())
                .bindStartupTaskTo(TaskManagerStartup.class, Integer.MAX_VALUE)
                .bindShutdownTaskTo(TaskManagerShutdown.class, Integer.MAX_VALUE);
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