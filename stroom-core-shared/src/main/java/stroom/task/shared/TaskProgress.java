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

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.docref.SharedObject;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.TreeRow;

import java.util.Objects;

public class TaskProgress implements SharedObject, TreeRow {
    private static final long serialVersionUID = 7903893715149262619L;

    private TaskId id;
    private String taskName;
    private String taskInfo;
    private String userName;
    private String threadName;

    private String nodeName;
    private long submitTimeMs;
    private long timeNowMs;

    private Expander expander;

    public TaskId getId() {
        return id;
    }

    public void setId(final TaskId id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(final String taskName) {
        this.taskName = taskName;
    }

    public String getTaskInfo() {
        return taskInfo;
    }

    public void setTaskInfo(final String taskInfo) {
        this.taskInfo = taskInfo;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(final String threadName) {
        this.threadName = threadName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public long getSubmitTimeMs() {
        return submitTimeMs;
    }

    public void setSubmitTimeMs(final long submitTimeMs) {
        this.submitTimeMs = submitTimeMs;
    }

    public long getTimeNowMs() {
        return timeNowMs;
    }

    public void setTimeNowMs(final long timeNowMs) {
        this.timeNowMs = timeNowMs;
    }

    @Override
    public Expander getExpander() {
        return expander;
    }

    public void setExpander(final Expander expander) {
        this.expander = expander;
    }

    @JsonIgnore
    public long getAgeMs() {
        return timeNowMs - submitTimeMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TaskProgress that = (TaskProgress) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return taskName + " " + ModelStringUtil.formatDurationString(getAgeMs());
    }
}
