/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.TreeRow;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class TaskProgress implements TreeRow {

    @JsonProperty
    private TaskId id;
    @JsonProperty
    private String taskName;
    @JsonProperty
    private String taskInfo;
    @JsonProperty
    private UserRef userRef;
    @JsonProperty
    private String threadName;

    @JsonProperty
    private String nodeName;
    @JsonProperty
    private long submitTimeMs;
    @JsonProperty
    private long timeNowMs;

    @JsonProperty
    private Expander expander;

    @JsonProperty
    private FilterMatchState filterMatchState;

    public TaskProgress() {
    }

    @JsonCreator
    public TaskProgress(@JsonProperty("id") final TaskId id,
                        @JsonProperty("taskName") final String taskName,
                        @JsonProperty("taskInfo") final String taskInfo,
                        @JsonProperty("userRef") final UserRef userRef,
                        @JsonProperty("threadName") final String threadName,
                        @JsonProperty("nodeName") final String nodeName,
                        @JsonProperty("submitTimeMs") final long submitTimeMs,
                        @JsonProperty("timeNowMs") final long timeNowMs,
                        @JsonProperty("expander") final Expander expander,
                        @JsonProperty("filterMatchState") final FilterMatchState filterMatchState) {
        this.id = id;
        this.taskName = taskName;
        this.taskInfo = taskInfo;
        this.userRef = userRef;
        this.threadName = threadName;
        this.nodeName = nodeName;
        this.submitTimeMs = submitTimeMs;
        this.timeNowMs = timeNowMs;
        this.expander = expander;
        this.filterMatchState = filterMatchState;
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

    public void setTaskInfo(final String taskInfo) {
        this.taskInfo = taskInfo;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
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

    public FilterMatchState getFilterMatchState() {
        return filterMatchState;
    }

    public void setFilterMatchState(final FilterMatchState filterMatchState) {
        this.filterMatchState = filterMatchState;
    }

    @JsonIgnore
    public long getAgeMs() {
        return timeNowMs - submitTimeMs;
    }

    @JsonIgnore
    public boolean isMatchedInFilter() {
        return FilterMatchState.MATCHED.equals(filterMatchState);
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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


    // --------------------------------------------------------------------------------


    /**
     * This could be a boolean but due to the issues around (de)serialisation of primitive boolean
     * and their default values this avoids having to have a prop called isFilteredOut and is
     * explicitly clear.
     */
    public enum FilterMatchState {
        MATCHED,
        NOT_MATCHED;

        @JsonIgnore
        public static FilterMatchState fromBoolean(final boolean isMatched) {
            return isMatched
                    ? MATCHED
                    : NOT_MATCHED;
        }
    }
}
