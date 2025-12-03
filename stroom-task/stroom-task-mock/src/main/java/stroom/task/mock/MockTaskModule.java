/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.task.mock;

import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class MockTaskModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(TaskContextFactory.class).to(SimpleTaskContextFactory.class);
        bind(TaskContext.class).to(SimpleTaskContext.class);
    }

    @Provides
    TaskManager getTaskManager() {
        return new TaskManager() {
            @Override
            public void startup() {

            }

            @Override
            public void shutdown() {

            }

            @Override
            public boolean isTerminated(final TaskId taskId) {
                return false;
            }

            @Override
            public void terminate(final TaskId taskId) {

            }

            @Override
            public TaskProgress getTaskProgress(final TaskContext taskContext) {
                return null;
            }
        };
    }
}
