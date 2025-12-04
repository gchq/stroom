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

package stroom.node.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindNodeStatusCriteria extends BaseCriteria {

    public static final String FIELD_ID_NAME = "Name";
    public static final String FIELD_ID_URL = "URL";
    public static final String FIELD_ID_PRIORITY = "Priority";
    public static final String FIELD_ID_ENABLED = "Enabled";
    public static final String FIELD_ID_BUILD_VERSION = "BuildVersion";
    public static final String FIELD_ID_LAST_BOOT_MS = "LastBootMs";

    public FindNodeStatusCriteria() {
        super();
    }

    @JsonCreator
    public FindNodeStatusCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                  @JsonProperty("sortList") final List<CriteriaFieldSort> sortList) {
        super(pageRequest, sortList);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
