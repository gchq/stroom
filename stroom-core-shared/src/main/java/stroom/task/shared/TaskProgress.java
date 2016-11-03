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

import stroom.node.shared.Node;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SharedObject;
import stroom.util.shared.TaskId;
import stroom.util.shared.TreeRow;

public class TaskProgress implements SharedObject, TreeRow {
    private static final long serialVersionUID = 7903893715149262619L;

    private TaskId id;
    private String taskName;
    private String taskInfo;
    private String sessionId;
    private String userName;
    private String threadName;

    private Node node;
    private long submitTimeMs;
    private long timeNowMs;
    private boolean orphan;

    private Expander expander;

    @Override
    public Expander getExpander() {
        return expander;
    }

    public void setExpander(final Expander expander) {
        this.expander = expander;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(final Node node) {
        this.node = node;
    }

    public long getTimeNowMs() {
        return timeNowMs;
    }

    public void setTimeNowMs(final long timeNowMs) {
        this.timeNowMs = timeNowMs;
    }

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

    public void setTaskInfo(final String progress) {
        this.taskInfo = progress;
    }

    public long getSubmitTimeMs() {
        return submitTimeMs;
    }

    public void setSubmitTimeMs(final long submitTimeMs) {
        this.submitTimeMs = submitTimeMs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }

    public long getAgeMs() {
        return timeNowMs - submitTimeMs;
    }

    public boolean isOrphan() {
        return orphan;
    }

    public void setOrphan(final boolean orphan) {
        this.orphan = orphan;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(final String threadName) {
        this.threadName = threadName;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof TaskProgress)) {
            return false;
        }
        final TaskProgress other = (TaskProgress) obj;
        final EqualsBuilder equalsBuilder = new EqualsBuilder();
        equalsBuilder.append(this.id, other.id);
        return equalsBuilder.isEquals();
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return id.hashCode();
        }
        return 0;
    }

    @Override
    public String toString() {
        return taskName + " " + ModelStringUtil.formatDurationString(getAgeMs());
    }
}
