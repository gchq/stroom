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

package stroom.cluster.task.api;

import stroom.task.api.TaskIdFactory;
import stroom.task.api.SimpleThreadPool;
import stroom.task.shared.Task;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;

import java.io.Serializable;
import java.util.Objects;

public abstract class ClusterTask<R> implements Task<R>, Serializable {
    private static final long serialVersionUID = 4730274660149532350L;

    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(5);

    private String taskName;

    private transient TaskId id;

    public ClusterTask(final String taskName) {
        this.taskName = taskName;
    }

    @Override
    public Task<?> getParentTask() {
        return null;
    }

    @Override
    public TaskId getId() {
        if (id == null) {
            throw new UnsupportedOperationException(
                    "You cannot get the id for a cluster task before it is executed on the worker node");
        }

        return id;
    }

    /**
     * Called by the worker node to fix the id for the task prior to execution.
     */
    public void assignId(final TaskId sourceTaskId) {
        this.id = TaskIdFactory.create(sourceTaskId);
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(final String taskName) {
        this.taskName = taskName;
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ClusterTask<?> that = (ClusterTask<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
