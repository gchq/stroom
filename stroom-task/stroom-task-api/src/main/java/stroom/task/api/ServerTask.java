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

package stroom.task.api;

import stroom.security.UserTokenUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.task.shared.SimpleThreadPool;
import stroom.task.shared.Task;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;

public abstract class ServerTask<R> implements Task<R> {
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(2);
    private final TaskId id;
    private final String userToken;
    private final Task<?> parentTask;
    private volatile String taskName;

    public ServerTask() {
        this.id = TaskIdFactory.create();
        this.userToken = UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN;
        this.parentTask = null;
    }

    public ServerTask(final Task<?> parentTask) {
        this(parentTask, parentTask.getUserToken());
    }

    public ServerTask(final Task<?> parentTask, final String userToken) {
        if (parentTask == null) {
            this.id = TaskIdFactory.create();
        } else {
            this.id = TaskIdFactory.create(parentTask.getId());
        }
        this.userToken = userToken;
        this.parentTask = parentTask;
    }

    @Override
    public Task<?> getParentTask() {
        return parentTask;
    }

    @Override
    public TaskId getId() {
        return id;
    }

    @Override
    public String getTaskName() {
        if (taskName == null) {
            this.taskName = generateTaskName();
        }
        return taskName;
    }

    protected void setTaskName(final String newTaskName) {
        this.taskName = newTaskName;
    }

    private String generateTaskName() {
        final String name = getClass().getSimpleName();
        ModelStringUtil.toDisplayValue(name);
        return name;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Task<?>)) {
            return false;
        }

        final Task<?> task = (Task<?>) obj;
        return id.equals(task.getId());
    }

    @Override
    public String getUserToken() {
        return userToken;
    }

    @Override
    public String toString() {
        return getTaskName() + " - " + id;
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }
}
