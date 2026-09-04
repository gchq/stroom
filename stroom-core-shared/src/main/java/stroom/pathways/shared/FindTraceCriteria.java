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

package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.pathways.shared.pathway.Pathway;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldFields;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.util.shared.time.SimpleDuration;

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
public class FindTraceCriteria extends BaseCriteria {

    public static final CriteriaFieldSort DEFAULT_SORT =
            new CriteriaFieldSort(FieldFields.NAME, false, true);
    public static final List<CriteriaFieldSort> DEFAULT_SORT_LIST =
            Collections.singletonList(DEFAULT_SORT);

    public static final FilterFieldDefinition FIELD_DEF_OPERATION =
            FilterFieldDefinition.defaultField("Operation");
    public static final FilterFieldDefinition FIELD_DEF_TRACE_ID =
            FilterFieldDefinition.defaultField("Trace Id", "traceid");
    public static final FilterFieldDefinition FIELD_DEF_IS_ERROR =
            FilterFieldDefinition.qualifiedField("Is Error", "iserror");
    public static final List<FilterFieldDefinition> FIELD_DEFINITIONS =
            List.of(FIELD_DEF_OPERATION, FIELD_DEF_TRACE_ID, FIELD_DEF_IS_ERROR);

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String filter;
    @JsonProperty
    private final Pathway pathway;
    @JsonProperty
    private SimpleDuration temporalOrderingTolerance;
    @JsonProperty
    private final TimeRange timeRange;

    public FindTraceCriteria(final PageRequest pageRequest,
                             final List<CriteriaFieldSort> sortList,
                             final DocRef dataSourceRef,
                             final SimpleDuration temporalOrderingTolerance) {
        this(pageRequest, sortList, dataSourceRef, null, null, temporalOrderingTolerance, null);
    }

    public FindTraceCriteria(final PageRequest pageRequest,
                             final List<CriteriaFieldSort> sortList,
                             final DocRef dataSourceRef,
                             final String filter,
                             final Pathway pathway,
                             final SimpleDuration temporalOrderingTolerance) {
        this(pageRequest, sortList, dataSourceRef, filter, pathway, temporalOrderingTolerance, null);
    }

    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public FindTraceCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                             @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                             @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                             @JsonProperty("filter") final String filter,
                             @JsonProperty("pathway") final Pathway pathway,
                             @JsonProperty("temporalOrderingTolerance") final SimpleDuration temporalOrderingTolerance,
                             @JsonProperty("timeRange") final TimeRange timeRange) {
        super(pageRequest, sortList);
        this.dataSourceRef = dataSourceRef;
        this.filter = filter;
        this.pathway = pathway;
        this.temporalOrderingTolerance = temporalOrderingTolerance;
        this.timeRange = timeRange;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getFilter() {
        return filter;
    }

    public Pathway getPathway() {
        return pathway;
    }

    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindTraceCriteria that = (FindTraceCriteria) o;
        return Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(filter, that.filter) &&
               Objects.equals(pathway, that.pathway) &&
               Objects.equals(temporalOrderingTolerance, that.temporalOrderingTolerance) &&
               Objects.equals(timeRange, that.timeRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSourceRef, filter, pathway, temporalOrderingTolerance, timeRange);
    }

    @Override
    public String toString() {
        return "FindTraceCriteria{" +
               "dataSourceRef=" + dataSourceRef +
               ", filter='" + filter + '\'' +
               ", pathway=" + pathway +
               ", temporalOrderingTolerance=" + temporalOrderingTolerance +
               ", timeRange=" + timeRange +
               '}';
    }
}
