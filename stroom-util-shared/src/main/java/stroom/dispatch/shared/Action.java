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

package stroom.dispatch.shared;

import stroom.util.shared.SharedObject;
import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.ThreadPool;

import java.io.Serializable;

public abstract class Action<R extends SharedObject> implements Task<R>, Serializable {
    private static final long serialVersionUID = 4730274660149532350L;

    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(4);
    boolean terminate;
    private TaskId id;
    private String sessionId;
    private String applicationInstanceId;
    private String userId;

    @Override
    public void terminate() {
        terminate = true;
    }

    @Override
    public boolean isTerminated() {
        return terminate;
    }

    @Override
    public final String getSessionId() {
        return sessionId;
    }

    public final void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getApplicationInstanceId() {
        return applicationInstanceId;
    }

    public void setApplicationInstanceId(final String applicationInstanceId) {
        this.applicationInstanceId = applicationInstanceId;
    }

    @Override
    public final String getUserId() {
        return userId;
    }

    public final void setUserId(final String userId) {
        this.userId = userId;
    }

    @Override
    public TaskId getId() {
        return id;
    }

    public void setId(final TaskId id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return getTaskName();
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public final boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Action<?>)) {
            return false;
        }

        final Action<?> task = (Action<?>) o;
        if (id != null && task.id != null) {
            return id.equals(task.id);
        }

        return false;
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }
}
