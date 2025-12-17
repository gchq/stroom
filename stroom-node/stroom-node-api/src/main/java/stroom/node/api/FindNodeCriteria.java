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

package stroom.node.api;

import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.FindNamedEntityCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.StringCriteria;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindNodeCriteria extends FindNamedEntityCriteria {

    public static final String FIELD_ID = "Id";
    public static final String FIELD_BUILD_VERSION = "BuildVersion";
    public static final String FIELD_LAST_BOOT_MS = "LastBootMs";

    @JsonProperty("enabled")
    private Boolean enabled = null;

    public FindNodeCriteria() {
    }

    public static FindNodeCriteria allEnabled() {
        final FindNodeCriteria criteria = new FindNodeCriteria();
        criteria.setEnabled(true);
        return criteria;
    }

    @JsonCreator
    public FindNodeCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                            @JsonProperty("name") final StringCriteria name,
                            @JsonProperty("enabled") final Boolean enabled) {
        super(pageRequest, sortList, name);
        this.enabled = enabled;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }
}
