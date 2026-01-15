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

package stroom.query.api;

import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class SearchTaskProgress {

    @JsonProperty
    private final String taskName;
    @JsonProperty
    private final String taskInfo;
    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final String threadName;

    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final long submitTimeMs;
    @JsonProperty
    private final long timeNowMs;

    @JsonCreator
    public SearchTaskProgress(@JsonProperty("taskName") final String taskName,
                              @JsonProperty("taskInfo") final String taskInfo,
                              @JsonProperty("userRef") final UserRef userRef,
                              @JsonProperty("threadName") final String threadName,
                              @JsonProperty("nodeName") final String nodeName,
                              @JsonProperty("submitTimeMs") final long submitTimeMs,
                              @JsonProperty("timeNowMs") final long timeNowMs) {
        this.taskName = taskName;
        this.taskInfo = taskInfo;
        this.userRef = userRef;
        this.threadName = threadName;
        this.nodeName = nodeName;
        this.submitTimeMs = submitTimeMs;
        this.timeNowMs = timeNowMs;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getTaskInfo() {
        return taskInfo;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public long getSubmitTimeMs() {
        return submitTimeMs;
    }

    public long getTimeNowMs() {
        return timeNowMs;
    }

    @JsonIgnore
    public long getAgeMs() {
        return timeNowMs - submitTimeMs;
    }

    @Override
    public String toString() {
        return taskName + " " + ModelStringUtil.formatDurationString(getAgeMs());
    }
}
