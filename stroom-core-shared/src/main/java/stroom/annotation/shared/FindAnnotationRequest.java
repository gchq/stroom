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

package stroom.annotation.shared;

import stroom.security.shared.DocumentPermission;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindAnnotationRequest extends BaseCriteria {

    @JsonProperty
    private final String filter;
    @JsonProperty
    private final DocumentPermission requiredPermission;
    @JsonProperty
    private final Long sourceId;
    @JsonProperty
    private final Long destinationId;

    @JsonCreator
    public FindAnnotationRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                 @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                 @JsonProperty("filter") final String filter,
                                 @JsonProperty("requiredPermission") final DocumentPermission requiredPermission,
                                 @JsonProperty("sourceId") final Long sourceId,
                                 @JsonProperty("destinationId") final Long destinationId) {
        super(pageRequest, sortList);
        this.filter = filter;
        this.requiredPermission = requiredPermission;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
    }

    public String getFilter() {
        return filter;
    }

    public DocumentPermission getRequiredPermission() {
        return requiredPermission;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getDestinationId() {
        return destinationId;
    }
}
