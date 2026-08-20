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

package stroom.analytics.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Criteria class.
 */
@JsonInclude(Include.NON_NULL)
public class FindAnalyticDataShardCriteria extends BaseCriteria {

    @JsonProperty
    private String analyticDocUuid;

    public FindAnalyticDataShardCriteria() {
    }

    @JsonCreator
    public FindAnalyticDataShardCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                         @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                         @JsonProperty("analyticDocUuid") final String analyticDocUuid) {
        super(pageRequest, sortList);
        this.analyticDocUuid = analyticDocUuid;
    }

    public String getAnalyticDocUuid() {
        return analyticDocUuid;
    }

    public void setAnalyticDocUuid(final String analyticDocUuid) {
        this.analyticDocUuid = analyticDocUuid;
    }
}
