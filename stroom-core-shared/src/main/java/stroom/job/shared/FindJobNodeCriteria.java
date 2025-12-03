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

package stroom.job.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.StringCriteria;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Criteria object used to fetch a job that matches the parameters specified.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FindJobNodeCriteria extends BaseCriteria {

    public static final String FIELD_ID_ID = "Id";
    public static final String FIELD_ADVANCED = "Advanced";
    public static final String FIELD_JOB_NAME = "Job Name";
    public static final String FIELD_ID_ENABLED = "Enabled";
    public static final String FIELD_ID_NODE = "Node";
    public static final String FIELD_ID_LAST_EXECUTED = "Last Executed";

    @JsonProperty
    private final StringCriteria jobName;
    @JsonProperty
    private final StringCriteria nodeName;
    @JsonProperty
    private Boolean jobNodeEnabled;

    public FindJobNodeCriteria() {
        jobName = new StringCriteria();
        nodeName = new StringCriteria();
        jobNodeEnabled = null;
    }

    public FindJobNodeCriteria(final String nodeName) {
        this.jobName = new StringCriteria();
        this.nodeName = new StringCriteria(nodeName);
        this.jobNodeEnabled = null;
    }

    @JsonCreator
    public FindJobNodeCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                               @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                               @JsonProperty("jobName") final StringCriteria jobName,
                               @JsonProperty("nodeName") final StringCriteria nodeName,
                               @JsonProperty("jobNodeEnabled") final Boolean jobNodeEnabled) {
        super(pageRequest, sortList);
        this.jobName = jobName;
        this.nodeName = nodeName;
        this.jobNodeEnabled = jobNodeEnabled;
    }

    public StringCriteria getJobName() {
        return jobName;
    }

    public StringCriteria getNodeName() {
        return nodeName;
    }

    public Boolean getJobNodeEnabled() {
        return jobNodeEnabled;
    }

    public void setJobNodeEnabled(final Boolean jobNodeEnabled) {
        this.jobNodeEnabled = jobNodeEnabled;
    }

    @Override
    public String toString() {
        return "FindJobNodeCriteria{" +
                "jobName=" + jobName +
                ", nodeName=" + nodeName +
                ", jobNodeEnabled=" + jobNodeEnabled +
                '}';
    }
}
