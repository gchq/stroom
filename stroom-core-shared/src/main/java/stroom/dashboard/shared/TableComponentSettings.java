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

package stroom.dashboard.shared;

import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.TableSettings;

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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonPropertyOrder({
        "queryId",
        "fields",
        "extractValues",
        "extractionPipeline",
        "maxResults",
        "pageSize",
        "showDetail",
        "conditionalFormattingRules",
        "modelVersion",
        "applyValueFilters",
        "selectionHandlers",
        "maxStringFieldLength",
        "overrideMaxStringFieldLength"})
@JsonInclude(Include.NON_NULL)
public final class TableComponentSettings implements ComponentSettings, HasSelectionFilter {

    public static final long[] DEFAULT_MAX_RESULTS = {};

    @Schema(description = "TODO")
    @JsonProperty
    private final String queryId;

    @JsonProperty
    private final DocRef dataSourceRef;

    @Schema
    @JsonProperty
    private final List<Column> fields;

    @Schema(description = "TODO")
    @JsonProperty
    private final Boolean extractValues;

    @JsonProperty
    private final Boolean useDefaultExtractionPipeline;

    @JsonProperty
    private final DocRef extractionPipeline;

    @Schema(description = "Defines the maximum number of results to return at each grouping level, e.g. " +
                          "'1000,10,1' means 1000 results at group level 0, 10 at level 1 and 1 at level 2. " +
                          "In the absence of this field system defaults will apply", example = "1000,10,1")
    @JsonPropertyDescription("Defines the maximum number of results to return at each grouping level, e.g. " +
                             "'1000,10,1' means 1000 results at group level 0, 10 at level 1 and 1 at level 2. " +
                             "In the absence of this field system defaults will apply")
    @JsonProperty
    private final List<Long> maxResults;

    @Schema(description = "Defines the maximum number of rows to display in the table at once (default 100).",
            example = "100")
    @JsonPropertyDescription("Defines the maximum number of rows to display in the table at once (default 100).")
    @JsonProperty
    private final Integer pageSize;

    @JsonPropertyDescription("When grouping is used a value of true indicates that the results will include the full " +
                             "detail of any results aggregated into a group as well as their aggregates. A value of " +
                             "false will only include the aggregated values for each group. Defaults to false.")
    @JsonProperty
    private final Boolean showDetail;

    @Schema(description = "IGNORE: UI use only", hidden = true)
    @JsonProperty("conditionalFormattingRules")
    private final List<ConditionalFormattingRule> conditionalFormattingRules;
    @Schema(description = "IGNORE: UI use only", hidden = true)
    @JsonProperty("modelVersion")
    private final String modelVersion;
    @JsonProperty
    private final Boolean applyValueFilters;
    @JsonProperty
    private final List<ComponentSelectionHandler> selectionHandlers;
    @JsonProperty
    private final Integer maxStringFieldLength;
    @JsonProperty
    private final Boolean overrideMaxStringFieldLength;

    @JsonCreator
    public TableComponentSettings(
            @JsonProperty("queryId") final String queryId,
            @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
            @JsonProperty("fields") final List<Column> fields, // Kept as fields for backward compatibility.
            @JsonProperty("extractValues") final Boolean extractValues,
            @JsonProperty("useDefaultExtractionPipeline") final Boolean useDefaultExtractionPipeline,
            @JsonProperty("extractionPipeline") final DocRef extractionPipeline,
            @JsonProperty("maxResults") final List<Long> maxResults,
            @JsonProperty("pageSize") final Integer pageSize,
            @JsonProperty("showDetail") final Boolean showDetail,
            @JsonProperty("conditionalFormattingRules") final List<ConditionalFormattingRule>
                    conditionalFormattingRules,
            @JsonProperty("modelVersion") final String modelVersion,
            @JsonProperty("applyValueFilters") final Boolean applyValueFilters,
            @JsonProperty("selectionHandlers") final List<ComponentSelectionHandler> selectionHandlers,
            @JsonProperty("maxStringFieldLength") final Integer maxStringFieldLength,
            @JsonProperty("overrideMaxStringFieldLength") final Boolean overrideMaxStringFieldLength) {

        // TODO all List props should be set like this
        //  this.fields = NullSafe.unmodifiableList(fields);
        //  so that we can ensure the obj is immutable, however some code is mutating
        //  these lists, e.g.
        //  getTableSettings().getColumns().removeIf(Column::isSpecial);
        //  Too dangerous to fix in 7.5, so best done in 7.7
        this.queryId = queryId;
        this.dataSourceRef = dataSourceRef;
        this.fields = fields;
        this.extractValues = extractValues;
        this.useDefaultExtractionPipeline = useDefaultExtractionPipeline;
        this.extractionPipeline = extractionPipeline;
        this.maxResults = maxResults;
        this.pageSize = pageSize;
        this.showDetail = showDetail;
        this.conditionalFormattingRules = conditionalFormattingRules;
        this.modelVersion = modelVersion;
        this.applyValueFilters = applyValueFilters;
        this.selectionHandlers = selectionHandlers;
        this.maxStringFieldLength = maxStringFieldLength;
        this.overrideMaxStringFieldLength = overrideMaxStringFieldLength;
    }

    public String getQueryId() {
        return queryId;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    @Deprecated // Kept as fields for backward compatibility.
    public List<Column> getFields() {
        return fields;
    }

    @JsonIgnore // Kept as fields for backward compatibility.
    public List<Column> getColumns() {
        return fields;
    }

    public Boolean getExtractValues() {
        return extractValues;
    }

    public boolean extractValues() {
        return extractValues != Boolean.FALSE;
    }

    public Boolean getUseDefaultExtractionPipeline() {
        return useDefaultExtractionPipeline;
    }

    public boolean useDefaultExtractionPipeline() {
        return useDefaultExtractionPipeline == Boolean.TRUE;
    }

    public DocRef getExtractionPipeline() {
        return extractionPipeline;
    }

    public List<Long> getMaxResults() {
        return maxResults;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public Boolean getShowDetail() {
        return showDetail;
    }

    public boolean showDetail() {
        return showDetail == Boolean.TRUE;
    }

    public List<ConditionalFormattingRule> getConditionalFormattingRules() {
        return conditionalFormattingRules;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Boolean getApplyValueFilters() {
        return applyValueFilters;
    }

    public boolean applyValueFilters() {
        return applyValueFilters == Boolean.TRUE;
    }

    public Integer getMaxStringFieldLength() {
        return maxStringFieldLength;
    }

    public Boolean getOverrideMaxStringFieldLength() {
        return overrideMaxStringFieldLength;
    }

    public boolean overrideMaxStringFieldLength() {
        return overrideMaxStringFieldLength == Boolean.TRUE;
    }

    @Deprecated
    public List<ComponentSelectionHandler> getSelectionHandlers() {
        return selectionHandlers;
    }

    @JsonIgnore
    @Override
    public List<ComponentSelectionHandler> getSelectionFilter() {
        return selectionHandlers;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TableComponentSettings that = (TableComponentSettings) o;
        return Objects.equals(queryId, that.queryId) &&
               Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(fields, that.fields) &&
               Objects.equals(extractValues, that.extractValues) &&
               Objects.equals(useDefaultExtractionPipeline, that.useDefaultExtractionPipeline) &&
               Objects.equals(extractionPipeline, that.extractionPipeline) &&
               Objects.equals(maxResults, that.maxResults) &&
               Objects.equals(pageSize, that.pageSize) &&
               Objects.equals(showDetail, that.showDetail) &&
               Objects.equals(conditionalFormattingRules, that.conditionalFormattingRules) &&
               Objects.equals(modelVersion, that.modelVersion) &&
               Objects.equals(applyValueFilters, that.applyValueFilters) &&
               Objects.equals(selectionHandlers, that.selectionHandlers) &&
               Objects.equals(maxStringFieldLength, that.maxStringFieldLength) &&
               Objects.equals(overrideMaxStringFieldLength, that.overrideMaxStringFieldLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                queryId,
                dataSourceRef,
                fields,
                extractValues,
                useDefaultExtractionPipeline,
                extractionPipeline,
                maxResults,
                pageSize,
                showDetail,
                conditionalFormattingRules,
                modelVersion,
                applyValueFilters,
                selectionHandlers,
                maxStringFieldLength,
                overrideMaxStringFieldLength);
    }

    @Override
    public String toString() {
        return "TableSettings{" +
               "queryId='" + queryId + '\'' +
               ", dataSourceRef=" + dataSourceRef +
               ", columns=" + fields +
               ", extractValues=" + extractValues +
               ", useDefaultExtractionPipeline=" + useDefaultExtractionPipeline +
               ", extractionPipeline=" + extractionPipeline +
               ", maxResults=" + maxResults +
               ", pageSize=" + pageSize +
               ", showDetail=" + showDetail +
               ", conditionalFormattingRules=" + conditionalFormattingRules +
               ", modelVersion='" + modelVersion + '\'' +
               ", applyValueFilters='" + applyValueFilters + '\'' +
               ", selectionHandlers='" + selectionHandlers + '\'' +
               ", maxStringFieldLength=" + maxStringFieldLength +
               ", overrideMaxStringFieldLength=" + overrideMaxStringFieldLength +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for constructing a {@link TableSettings tableSettings}
     */
    public static final class Builder
            extends ComponentSettings.AbstractBuilder<TableComponentSettings, TableComponentSettings.Builder>
            implements HasSelectionFilterBuilder<TableComponentSettings, Builder> {

        private String queryId;
        private DocRef dataSourceRef;
        private List<Column> columns;
        private Boolean extractValues;
        private Boolean useDefaultExtractionPipeline = Boolean.TRUE;
        private DocRef extractionPipeline;
        private List<Long> maxResults;
        private Integer pageSize;
        private Boolean showDetail;
        private List<ConditionalFormattingRule> conditionalFormattingRules;
        private String modelVersion;
        private Boolean applyValueFilters;
        private List<ComponentSelectionHandler> selectionFilter;
        private Integer maxStringFieldLength;
        private Boolean overrideMaxStringFieldLength;

        private Builder() {
        }

        private Builder(final TableComponentSettings tableSettings) {
            this.queryId = tableSettings.queryId;
            this.dataSourceRef = tableSettings.dataSourceRef;
            this.columns = tableSettings.fields == null
                    ? null
                    : new ArrayList<>(tableSettings.fields);
            this.extractValues = tableSettings.extractValues;
            this.useDefaultExtractionPipeline = tableSettings.useDefaultExtractionPipeline;
            this.extractionPipeline = tableSettings.extractionPipeline;
            this.maxResults = tableSettings.maxResults == null
                    ? null
                    : new ArrayList<>(tableSettings.maxResults);
            this.pageSize = tableSettings.pageSize;
            this.showDetail = tableSettings.showDetail;
            this.conditionalFormattingRules = tableSettings.conditionalFormattingRules == null
                    ? null
                    : new ArrayList<>(tableSettings.conditionalFormattingRules);
            this.modelVersion = tableSettings.modelVersion;
            this.applyValueFilters = tableSettings.applyValueFilters;
            this.selectionFilter = tableSettings.selectionHandlers == null
                    ? null
                    : new ArrayList<>(tableSettings.selectionHandlers);
            this.maxStringFieldLength = tableSettings.maxStringFieldLength;
            this.overrideMaxStringFieldLength = tableSettings.overrideMaxStringFieldLength;
        }

        private List<ConditionalFormattingRule> copyConditionalFormattingRules(
                final List<ConditionalFormattingRule> rules) {

            if (rules == null) {
                return null;
            } else {
                return rules.stream()
                        .map(col -> col.copy()
                                .build())
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        }

        /**
         * @param value The ID for the query that wants these results
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder queryId(final String value) {
            this.queryId = value;
            return self();
        }

        public Builder dataSourceRef(final DocRef dataSourceRef) {
            this.dataSourceRef = dataSourceRef;
            return self();
        }

        public Builder columns(final List<Column> columns) {
            this.columns = columns;
            return self();
        }

        /**
         * @param values Add expected columns to the output table
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder addColumn(final Column... values) {
            return addColumn(Arrays.asList(values));
        }

        /**
         * Convenience function for adding multiple fields that are already in a collection.
         *
         * @param values The columns to add
         * @return this builder, with the columns added.
         */
        public Builder addColumn(final Collection<Column> values) {
            if (this.columns == null) {
                this.columns = new ArrayList<>(values);
            } else {
                this.columns.addAll(values);
            }
            return self();
        }

        /**
         * @param value TODO - unknown purpose
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder extractValues(final Boolean value) {
            if (value != null && value) {
                this.extractValues = null;
            } else {
                this.extractValues = Boolean.FALSE;
            }
            return self();
        }

        public Builder useDefaultExtractionPipeline(final Boolean value) {
            if (value == null || !value) {
                this.useDefaultExtractionPipeline = null;
            } else {
                this.useDefaultExtractionPipeline = Boolean.TRUE;
            }
            return self();
        }


        /**
         * @param value The reference to the extraction pipeline that will be used on the results
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder extractionPipeline(final DocRef value) {
            this.extractionPipeline = value;
            return self();
        }

        /**
         * Shortcut function for creating the extractionPipeline {@link DocRef} in one go
         *
         * @param type The type of the extractionPipeline
         * @param uuid The UUID of the extractionPipeline
         * @param name The name of the extractionPipeline
         * @return this builder, with the completed extractionPipeline added.
         */
        public Builder extractionPipeline(final String type,
                                          final String uuid,
                                          final String name) {
            return self().extractionPipeline(DocRef.builder()
                    .type(type)
                    .uuid(uuid)
                    .name(name)
                    .build());
        }

        public Builder maxResults(final List<Long> maxResults) {
            this.maxResults = maxResults;
            return self();
        }

        public Builder pageSize(final Integer pageSize) {
            this.pageSize = pageSize;
            return self();
        }

        /**
         * @param value When grouping is used a value of true indicates that the results will include
         *              the full detail of any results aggregated into a group as well as their aggregates.
         *              A value of false will only include the aggregated values for each group. Defaults to false.
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder showDetail(final Boolean value) {
            this.showDetail = value;
            return self();
        }

        public Builder conditionalFormattingRules(final List<ConditionalFormattingRule> conditionalFormattingRules) {
            this.conditionalFormattingRules = conditionalFormattingRules;
            return self();
        }

        public Builder modelVersion(final String modelVersion) {
            this.modelVersion = modelVersion;
            return self();
        }

        public Builder applyValueFilters(final Boolean applyValueFilters) {
            this.applyValueFilters = applyValueFilters;
            return self();
        }

        public Builder selectionFilter(final List<ComponentSelectionHandler> selectionFilter) {
            this.selectionFilter = selectionFilter;
            return self();
        }

        public Builder maxStringFieldLength(final Integer maxStringFieldLength) {
            this.maxStringFieldLength = maxStringFieldLength;
            return self();
        }

        public Builder overrideMaxStringFieldLength(final Boolean overrideMaxStringFieldLength) {
            this.overrideMaxStringFieldLength = overrideMaxStringFieldLength;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TableComponentSettings build() {
            return new TableComponentSettings(
                    queryId,
                    dataSourceRef,
                    columns,
                    extractValues,
                    useDefaultExtractionPipeline,
                    extractionPipeline,
                    maxResults,
                    pageSize,
                    showDetail,
                    conditionalFormattingRules,
                    modelVersion,
                    applyValueFilters,
                    selectionFilter,
                    maxStringFieldLength,
                    overrideMaxStringFieldLength);
        }

        public TableSettings buildTableSettings() {
            return new TableSettings(
                    queryId,
                    columns,
                    null,
                    null,
                    null,
                    extractValues,
                    extractionPipeline,
                    maxResults,
                    showDetail,
                    conditionalFormattingRules,
                    null,
                    applyValueFilters,
                    maxStringFieldLength,
                    overrideMaxStringFieldLength);
        }
    }
}
