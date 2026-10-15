/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.api.datasource;

import stroom.docref.DocRef;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.QuickFilterCriteria;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FindFieldCriteria extends QuickFilterCriteria {

    public static final CriteriaFieldSort DEFAULT_SORT =
            new CriteriaFieldSort(FieldFields.NAME, false, true);
    public static final List<CriteriaFieldSort> DEFAULT_SORT_LIST =
            Collections.singletonList(DEFAULT_SORT);

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final Boolean queryable;

    public FindFieldCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                             @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                             @JsonProperty("dataSourceRef") final DocRef dataSourceRef) {
        this(pageRequest, sortList, dataSourceRef, null, null);
    }

    @JsonCreator
    public FindFieldCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                             @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                             @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                             @JsonProperty("quickFilter") final String quickFilter,
                             @JsonProperty("queryable") final Boolean queryable) {
        super(pageRequest, sortList, quickFilter);
        this.dataSourceRef = dataSourceRef;
        this.queryable = queryable;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }


    public Boolean getQueryable() {
        return queryable;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FindFieldCriteria)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindFieldCriteria that = (FindFieldCriteria) o;
        return Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(getQuickFilter(), that.getQuickFilter()) &&
               Objects.equals(queryable, that.queryable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSourceRef, getQuickFilter(), queryable);
    }
}
