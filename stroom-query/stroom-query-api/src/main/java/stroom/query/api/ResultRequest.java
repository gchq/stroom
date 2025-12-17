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

package stroom.query.api;

import stroom.query.api.SearchRequestSource.SourceType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({
        "componentId",
        "searchRequestSource",
        "mappings",
        "requestedRange",
        "openGroups",
        "resultStyle",
        "fetch",
        "groupSelection",
        "tableName"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A definition for how to return the raw results of the query in the SearchResponse, " +
                      "e.g. sorted, grouped, limited, etc.")
public final class ResultRequest {

    @Schema(description = "The ID of the component that will receive the results corresponding to this ResultRequest")
    @JsonProperty
    private final String componentId;

    @JsonProperty
    private final SearchRequestSource searchRequestSource;

    @JsonProperty
    private final String tableName;

    @Schema
    @JsonProperty
    private final List<TableSettings> mappings;

    @Schema
    @JsonProperty
    private final OffsetRange requestedRange;

    @Schema(description = "If the data includes time in the key then you can apply a time filter when retrieving rows")
    @JsonProperty
    private final TimeFilter timeFilter;

    /**
     * @deprecated Use {@link GroupSelection#openGroups} instead.
     */
    @Schema(description = "A set of group keys of parent rows we want to display children for")
    @JsonProperty
    @Deprecated
    private final Set<String> openGroups;

    @Schema(description = "Contains a set of group keys of parent rows we want to show (or not show) children for")
    @JsonProperty
    private final GroupSelection groupSelection;

    @Schema(description = "The style of results required. FLAT will provide a FlatResult object, while TABLE will " +
                          "provide a TableResult object")
    @JsonProperty
    private final ResultStyle resultStyle;

    @JsonPropertyDescription("The fetch mode for the query. NONE means fetch no data, ALL means fetch all known " +
                             "results, CHANGES means fetch only those records not see in previous requests")
    @JsonProperty
    private final Fetch fetch;

    @JsonCreator
    public ResultRequest(@JsonProperty("componentId") final String componentId,
                         @JsonProperty("searchRequestSource") final SearchRequestSource searchRequestSource,
                         @JsonProperty("mappings") final List<TableSettings> mappings,
                         @JsonProperty("requestedRange") final OffsetRange requestedRange,
                         @JsonProperty("timeFilter") final TimeFilter timeFilter,
                         @JsonProperty("openGroups") final Set<String> openGroups,
                         @JsonProperty("resultStyle") final ResultStyle resultStyle,
                         @JsonProperty("fetch") final Fetch fetch,
                         @JsonProperty("groupSelection") final GroupSelection groupSelection,
                         @JsonProperty("tableName") final String tableName) {
        this.componentId = componentId;
        this.searchRequestSource = searchRequestSource;
        this.mappings = mappings;
        this.requestedRange = requestedRange;
        this.timeFilter = timeFilter;
        this.openGroups = openGroups;
        this.resultStyle = resultStyle;
        this.fetch = fetch;
        this.groupSelection = groupSelection == null
                ?
                GroupSelection.builder().openGroups(openGroups).build()
                : groupSelection;
        this.tableName = tableName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getComponentId() {
        return componentId;
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public List<TableSettings> getMappings() {
        return mappings;
    }

    public OffsetRange getRequestedRange() {
        return requestedRange;
    }

    public TimeFilter getTimeFilter() {
        return timeFilter;
    }

    public Set<String> getOpenGroups() {
        return openGroups;
    }

    public ResultStyle getResultStyle() {
        return resultStyle;
    }

    public GroupSelection getGroupSelection() {
        return groupSelection;
    }

    public String getTableName() {
        return tableName;
    }

    /**
     * The fetch type determines if the request actually wants data returned or if it only wants data if the data has
     * changed since the last request was made.
     *
     * @return The fetch type.
     */
    public Fetch getFetch() {
        return fetch;
    }

    @JsonIgnore
    public String getSourceComponentId() {
        if (searchRequestSource != null) {
            if (SourceType.DASHBOARD_UI.equals(searchRequestSource.getSourceType())) {
                return componentId;
            }
            return searchRequestSource.getComponentId();
        }

        return null;
    }

    @JsonIgnore
    public String getSourceComponentName() {
        if (searchRequestSource != null) {
            if (SourceType.DASHBOARD_UI.equals(searchRequestSource.getSourceType())) {
                return tableName;
            }
            return searchRequestSource.getComponentName();
        }

        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ResultRequest that = (ResultRequest) o;
        return Objects.equals(componentId, that.componentId) &&
               Objects.equals(searchRequestSource, that.searchRequestSource) &&
               Objects.equals(mappings, that.mappings) &&
               Objects.equals(requestedRange, that.requestedRange) &&
               Objects.equals(timeFilter, that.timeFilter) &&
               Objects.equals(openGroups, that.openGroups) &&
               resultStyle == that.resultStyle &&
               fetch == that.fetch &&
               Objects.equals(groupSelection, that.groupSelection) &&
               Objects.equals(tableName, that.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId, searchRequestSource, mappings, requestedRange, timeFilter, openGroups,
                resultStyle, fetch, groupSelection, tableName);
    }

    @Override
    public String toString() {
        return "ResultRequest{" +
               "componentId='" + componentId + '\'' +
               ", searchRequestSource=" + searchRequestSource +
               ", mappings=" + mappings +
               ", requestedRange=" + requestedRange +
               ", timeFilter=" + timeFilter +
               ", openGroups=" + openGroups +
               ", resultStyle=" + resultStyle +
               ", fetch=" + fetch +
               ", groupSelection=" + groupSelection +
               ", tableName=" + tableName +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public enum ResultStyle {
        FLAT,
        TABLE,
        VIS,
        QL_VIS
    }


    // --------------------------------------------------------------------------------


    public enum Fetch {
        NONE,
        CHANGES,
        ALL
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for constructing a {@link ResultRequest}
     */
    public static final class Builder {

        private String componentId;
        private SearchRequestSource searchRequestSource;
        private List<TableSettings> mappings;
        private OffsetRange requestedRange;
        private TimeFilter timeFilter;
        private Set<String> openGroups;
        private ResultRequest.ResultStyle resultStyle;
        private ResultRequest.Fetch fetch;
        private GroupSelection groupSelection;
        private String tableName;

        private Builder() {
        }

        private Builder(final ResultRequest resultRequest) {
            componentId = resultRequest.componentId;
            searchRequestSource = resultRequest.searchRequestSource;
            mappings = resultRequest.mappings;
            requestedRange = resultRequest.requestedRange;
            timeFilter = resultRequest.timeFilter;
            openGroups = resultRequest.openGroups;
            resultStyle = resultRequest.resultStyle;
            fetch = resultRequest.fetch;
            groupSelection = resultRequest.groupSelection;
            tableName = resultRequest.tableName;
        }

        /**
         * @param value The ID of the component that will receive the results corresponding to this ResultRequest
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder componentId(final String value) {
            this.componentId = value;
            return this;
        }

        public Builder searchRequestSource(final SearchRequestSource value) {
            this.searchRequestSource = value;
            return this;
        }

        /**
         * @param value Set the requested range of the results.
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder requestedRange(final OffsetRange value) {
            this.requestedRange = value;
            return this;
        }

        public Builder timeFilter(final TimeFilter timeFilter) {
            this.timeFilter = timeFilter;
            return this;
        }

        public Builder openGroups(final Set<String> openGroups) {
            this.openGroups = openGroups;
            return this;
        }

        public Builder groupSelection(final GroupSelection groupSelection) {
            this.groupSelection = groupSelection;
            return this;
        }

        public Builder tableName(final String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder mappings(final List<TableSettings> mappings) {
            this.mappings = mappings;
            return this;
        }

        /**
         * @param values Adding a set of TableSettings which are used to map the raw results to the output
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addMappings(final TableSettings... values) {
            if (values.length > 0 && mappings == null) {
                mappings = new ArrayList<>();
            }

            this.mappings.addAll(Arrays.asList(values));
            return this;
        }

        /**
         * @param groups TODO
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addOpenGroups(final String... groups) {
            if (groups.length > 0 && openGroups == null) {
                openGroups = new HashSet<>();
            }

            this.openGroups.addAll(Arrays.asList(groups));
            return this;
        }

        /**
         * @param value The style of results required.
         *              FLAT will provide a FlatResult object,
         *              while TABLE will provide a TableResult object
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder resultStyle(final ResultRequest.ResultStyle value) {
            this.resultStyle = value;
            return this;
        }

        /**
         * @param value The fetch mode for the query.
         *              NONE means fetch no data,
         *              ALL means fetch all known results,
         *              CHANGES means fetch only those records not see in previous requests
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder fetch(final ResultRequest.Fetch value) {
            this.fetch = value;
            return this;
        }

        public ResultRequest build() {
            return new ResultRequest(componentId, searchRequestSource, mappings, requestedRange, timeFilter,
                    openGroups, resultStyle, fetch, groupSelection, tableName);
        }
    }
}
