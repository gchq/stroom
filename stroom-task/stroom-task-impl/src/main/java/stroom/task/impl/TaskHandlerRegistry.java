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

package stroom.task.impl;

import stroom.task.api.TaskHandler;
import stroom.task.api.TaskType;
import stroom.task.shared.Task;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class TaskHandlerRegistry {
    private final Map<TaskType, Provider<TaskHandler>> taskHandlerMap;

    @Inject
    TaskHandlerRegistry(final Map<TaskType, Provider<TaskHandler>> taskHandlerMap) {
        this.taskHandlerMap = taskHandlerMap;
    }

    @SuppressWarnings("unchecked")
    public <R, H extends TaskHandler<Task<R>, R>> H findHandler(final Task<R> task) {
        final Provider<TaskHandler> taskHandlerProvider = taskHandlerMap.get(new TaskType(task.getClass()));
        if (taskHandlerProvider == null) {
            throw new RuntimeException("No handler for " + task.getClass().getName());
        }
        return (H) taskHandlerProvider.get();
    }
}
