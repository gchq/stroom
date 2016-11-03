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

import java.io.Serializable;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.Monitor;
import stroom.util.shared.SharedObject;
import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.ThreadPool;
import stroom.util.task.HasMonitor;
import stroom.util.task.MonitorImpl;
import stroom.util.task.TaskIdFactory;
import jdk.nashorn.internal.objects.NativeDebug;

public abstract class ClusterTask<R extends SharedObject> implements Task<R>, Serializable, HasMonitor {
    private static final long serialVersionUID = 4730274660149532350L;

    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(5);

    private final String sessionId;
    private final String userId;
    private String taskName;

    private transient TaskId id;
    private transient MonitorImpl monitor;

    public ClusterTask(final Task<?> parentTask, final String taskName) {
        if (parentTask != null) {
            this.sessionId = parentTask.getSessionId();
            this.userId = parentTask.getUserId();
        } else {
            this.sessionId = null;
            this.userId = null;
        }

        this.taskName = taskName;
    }

    public ClusterTask(final String sessionId, final String userName, final String taskName) {
        this.sessionId = sessionId;
        this.userId = userName;
        this.taskName = taskName;
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
        this.monitor = new MonitorImpl();
    }

    @Override
    public final int hashCode() {
        if (id == null) {
            return super.hashCode();
        }
        return id.hashCode();
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getUserId() {
        return userId;
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
    public Monitor getMonitor() {
        if (monitor == null) {
            throw new UnsupportedOperationException(
                        "You cannot get the monitor for a cluster task before it is executed on the worker node");
        }

        return monitor;
    }

    @Override
    public boolean isTerminated() {
        return getMonitor().isTerminated();
    }

    @Override
    public void terminate() {
        getMonitor().terminate();
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }
}
