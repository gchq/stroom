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

package stroom.task.shared;

import stroom.docref.SharedObject;

import java.io.Serializable;

public abstract class Action<R extends SharedObject> implements Task<R>, Serializable {
    private static final long serialVersionUID = 4730274660149532350L;

    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(4);
    private TaskId id;
    private String applicationInstanceId;
    private String userToken;

    public String getApplicationInstanceId() {
        return applicationInstanceId;
    }

    public void setApplicationInstanceId(final String applicationInstanceId) {
        this.applicationInstanceId = applicationInstanceId;
    }

    @Override
    public String getUserToken() {
        return userToken;
    }

    public void setUserToken(final String userToken) {
        this.userToken = userToken;
    }

    @Override
    public TaskId getId() {
        return id;
    }

    public void setId(final TaskId id) {
        this.id = id;
    }

    @Override
    public Task<?> getParentTask() {
        return null;
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
        return id != null && task.id != null && id.equals(task.id);
    }

    @Override
    public ThreadPool getThreadPool() {
        return THREAD_POOL;
    }
}
