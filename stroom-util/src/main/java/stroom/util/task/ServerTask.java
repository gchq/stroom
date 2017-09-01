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

package stroom.util.task;

import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.Monitor;
import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.ThreadPool;
import stroom.util.shared.UserTokenUtil;

public abstract class ServerTask<R> implements Task<R>, HasMonitor {
    public static final String INTERNAL_PROCESSING_USER_TOKEN = UserTokenUtil.createInternal();
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(2);
    private final TaskId id;
    private final String userToken;
    private final MonitorImpl monitor;
    private volatile String taskName;

    public ServerTask() {
        this.id = TaskIdFactory.create();
        this.userToken = INTERNAL_PROCESSING_USER_TOKEN;
        this.monitor = new MonitorImpl();
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

        // Rather than remember the parent task I just grab the parent monitor.
        // This avoids some Hessian serialisation issues that can occur
        // unexpectedly when sending parent tasks across the wire.
        if (parentTask != null && parentTask instanceof HasMonitor) {
            final HasMonitor hasMonitor = (HasMonitor) parentTask;
            this.monitor = new MonitorImpl(hasMonitor.getMonitor());
        } else {
            this.monitor = new MonitorImpl();
        }
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
        if (obj == null || !(obj instanceof Task<?>)) {
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
    public Monitor getMonitor() {
        return monitor;
    }

    /**
     * CONVENIENCE METHODS TO SHORTCUT REFERENCES TO MONITOR TO CHECK
     * TERMINATION ETC.
     **/
    @Override
    public boolean isTerminated() {
        return monitor.isTerminated();
    }

    @Override
    public void terminate() {
        monitor.terminate();
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }
}
