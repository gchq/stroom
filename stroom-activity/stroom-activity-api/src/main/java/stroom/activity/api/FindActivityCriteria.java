/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.api;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindActivityCriteria extends BaseCriteria {
    @JsonProperty
    private String userId;
    @JsonProperty
    private String filter;

    public FindActivityCriteria() {
    }

    @JsonCreator
    public FindActivityCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<Sort> sortList,
                                @JsonProperty("userId") final String userId,
                                @JsonProperty("name") final String filter) {
        super(pageRequest, sortList);
        this.userId = userId;
        this.filter = filter;
    }

    public static FindActivityCriteria create(final String filter) {
        FindActivityCriteria criteria = new FindActivityCriteria();
        criteria.setFilter(filter);
        return criteria;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(final String filter) {
        this.filter = filter;
    }
}
