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

package stroom.job.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;
import stroom.util.shared.StringCriteria;

import java.util.List;

/**
 * Criteria object used to fetch a job that matches the parameters specified.
 */
@JsonInclude(Include.NON_NULL)
public class FindJobNodeCriteria extends BaseCriteria {
    public static final String FIELD_ID = "Id";

    @JsonProperty
    private StringCriteria jobName;
    @JsonProperty
    private StringCriteria nodeName;

    public FindJobNodeCriteria() {
        jobName = new StringCriteria();
        nodeName = new StringCriteria();
    }

    @JsonCreator
    public FindJobNodeCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                               @JsonProperty("sortList") final List<Sort> sortList,
                               @JsonProperty("jobName") final StringCriteria jobName,
                               @JsonProperty("nodeName") final StringCriteria nodeName) {
        super(pageRequest, sortList);
        this.jobName = jobName;
        this.nodeName = nodeName;
    }

    public StringCriteria getJobName() {
        return jobName;
    }

    public StringCriteria getNodeName() {
        return nodeName;
    }
}
