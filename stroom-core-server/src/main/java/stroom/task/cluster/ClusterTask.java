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

package stroom.task.cluster;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.SharedObject;
import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.ThreadPool;
import stroom.task.TaskIdFactory;

import java.io.Serializable;

public abstract class ClusterTask<R extends SharedObject> implements Task<R>, Serializable {
    private static final long serialVersionUID = 4730274660149532350L;

    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(5);

    private final String userToken;
    private String taskName;

    private transient TaskId id;

    public ClusterTask(final String userToken, final String taskName) {
        this.userToken = userToken;
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
    public final int hashCode() {
        if (id == null) {
            return super.hashCode();
        }
        return id.hashCode();
    }

    @Override
    public String getUserToken() {
        return userToken;
    }

    @Override
    public final boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ClusterTask<?>)) {
            return false;
        }

        final ClusterTask<?> clusterTask = (ClusterTask<?>) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(id, clusterTask.id);
        return builder.isEquals();
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
}
