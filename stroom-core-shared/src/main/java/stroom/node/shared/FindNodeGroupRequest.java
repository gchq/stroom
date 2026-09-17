/*
 * Copyright 2026 Crown Copyright
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

package stroom.node.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindNodeGroupRequest extends BaseCriteria {

    public static final String FIELD_ID_NAME = "Name";
    public static final String FIELD_ID_ENABLED = "Enabled";
    public static final CriteriaFieldSort DEFAULT_SORT =
            new CriteriaFieldSort(FIELD_ID_NAME, false, true);
    public static final List<CriteriaFieldSort> DEFAULT_SORT_LIST =
            Collections.singletonList(DEFAULT_SORT);

    @JsonProperty
    private final String filter;

    @JsonCreator
    public FindNodeGroupRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                @JsonProperty("filter") final String filter) {
        super(pageRequest, sortList);
        this.filter = filter;
    }

    public String getFilter() {
        return filter;
    }
}
