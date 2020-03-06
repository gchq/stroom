/*
 * Copyright 2017 Crown Copyright
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

import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskIdFactory;
import stroom.task.shared.TaskId;
import stroom.util.logging.LogExecutionTime;

import java.util.Objects;
import java.util.function.Supplier;

class TaskContextImpl implements TaskContext {
    private final TaskManagerImpl taskManager;
    private final TaskId parentTaskId;
    private final UserIdentity userIdentity;
    private final String taskName;

    TaskContextImpl(final TaskManagerImpl taskManager, final TaskId parentTaskId, final String taskName, final UserIdentity userIdentity) {
        Objects.requireNonNull(taskName, "Task has null name");
        Objects.requireNonNull(userIdentity, "Task has null user identity: " + taskName);

        this.taskManager = taskManager;
        this.parentTaskId = parentTaskId;
        this.userIdentity = userIdentity;
        this.taskName = taskName;
    }

    @Override
    public void setName(final String name) {
        CurrentTaskState.setName(name);
    }

    @Override
    public void info(final Supplier<String> messageSupplier) {
        CurrentTaskState.info(messageSupplier);
    }

    @Override
    public void terminate() {
        CurrentTaskState.terminate();
    }

    @Override
    public <U> Supplier<U> subTask(final Supplier<U> supplier) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // We might need to be able to retrieve the associate task handler
        // right away so associate it here before we execute as the task
        // will run asynchronously.
        final TaskId taskId = TaskIdFactory.create(parentTaskId);
        return taskManager.wrapSupplier(taskId, taskName, userIdentity, supplier, logExecutionTime);
    }

    @Override
    public Runnable subTask(final Runnable runnable) {
        final Supplier<Void> supplierIn = () -> {
            runnable.run();
            return null;
        };
        final Supplier<Void> supplierOut = subTask(supplierIn);
        return supplierOut::get;
    }
}
