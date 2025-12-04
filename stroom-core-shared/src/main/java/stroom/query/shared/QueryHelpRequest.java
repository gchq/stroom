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

package stroom.query.shared;

import stroom.docref.DocRef;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpRequest extends BaseCriteria {

    @JsonProperty
    private final String query;
    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String parentPath;
    @JsonProperty
    private final String filter;
    @JsonProperty
    private final Set<QueryHelpType> includedTypes;

    @JsonCreator
    public QueryHelpRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                            @JsonProperty("query") final String query,
                            @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                            @JsonProperty("parentPath") final String parentPath,
                            @JsonProperty("filter") final String filter,
                            @JsonProperty("includedTypes") final Set<QueryHelpType> includedTypes) {
        super(pageRequest, sortList);
        this.query = query;
        this.dataSourceRef = dataSourceRef;
        this.parentPath = parentPath;
        this.filter = filter;
        this.includedTypes = includedTypes;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getQuery() {
        return query;
    }

    public String getParentPath() {
        return parentPath;
    }

    public String getFilter() {
        return filter;
    }

    public Set<QueryHelpType> getIncludedTypes() {
        return includedTypes;
    }

    public boolean isTypeIncluded(final QueryHelpType queryHelpType) {
        if (queryHelpType == null || NullSafe.isEmptyCollection(includedTypes)) {
            return false;
        } else {
            return includedTypes.contains(queryHelpType);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueryHelpRequest)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final QueryHelpRequest request = (QueryHelpRequest) o;
        return includedTypes == request.includedTypes &&
               Objects.equals(query, request.query) &&
               Objects.equals(dataSourceRef, request.dataSourceRef) &&
               Objects.equals(parentPath, request.parentPath) &&
               Objects.equals(filter, request.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), query, dataSourceRef, parentPath, filter, includedTypes);
    }
}
