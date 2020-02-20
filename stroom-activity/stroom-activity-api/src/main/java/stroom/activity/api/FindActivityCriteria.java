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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;
import stroom.util.shared.StringCriteria;
import stroom.util.shared.StringCriteria.MatchStyle;

import java.util.List;

@JsonInclude(Include.NON_DEFAULT)
public class FindActivityCriteria extends BaseCriteria {
    @JsonProperty
    private String userId;
    @JsonProperty
    private StringCriteria name;

    public FindActivityCriteria() {
        name = new StringCriteria();
    }

    @JsonCreator
    public FindActivityCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<Sort> sortList,
                                @JsonProperty("userId") final String userId,
                                @JsonProperty("name") final StringCriteria name) {
        super(pageRequest, sortList);
        this.userId = userId;
        if (name != null) {
            this.name = name;
        } else {
            this.name = new StringCriteria();
        }
    }

    public static FindActivityCriteria create(final String name) {
        FindActivityCriteria criteria = new FindActivityCriteria();
        criteria.setName(new StringCriteria(name, MatchStyle.WildStandAndEnd));
        return criteria;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public StringCriteria getName() {
        return name;
    }

    public void setName(final StringCriteria name) {
        this.name = name;
    }
}
