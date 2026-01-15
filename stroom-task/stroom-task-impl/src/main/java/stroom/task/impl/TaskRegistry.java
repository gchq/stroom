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

package stroom.task.impl;

import stroom.task.shared.TaskId;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class TaskRegistry {

    private final Map<TaskId, TaskContextImpl> currentTasks = new ConcurrentHashMap<>(1024, 0.75F, 1024);

    void put(final TaskId taskId, final TaskContextImpl taskContext) {
        currentTasks.put(taskId, taskContext);
    }

    TaskContextImpl get(final TaskId taskId) {
        return currentTasks.get(taskId);
    }

    TaskContextImpl remove(final TaskId taskId) {
        return currentTasks.remove(taskId);
    }

    List<TaskContextImpl> list() {
        return List.copyOf(currentTasks.values());
    }

    @Override
    public String toString() {
        return TaskThreadInfoUtil.getInfo(list());
    }
}
