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

import stroom.docref.DocRef;

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

@JsonPropertyOrder({
        "queryId",
        "fields",
        "extractValues",
        "extractionPipeline",
        "maxResults",
        "showDetail",
        "conditionalFormattingRules",
        "modelVersion",
        "visSettings",
        "applyValueFilters",
        "maxStringFieldLength",
        "overrideMaxStringFieldLength"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "An object to describe how the query results should be returned, including which fields " +
        "should be included and what sorting, grouping, filtering, limiting, etc. should be applied")
public final class TableSettings {

    @Schema(description = "TODO")
    @JsonProperty
    private final String queryId;

    @Schema
    @JsonProperty
    private final List<Column> fields;

    @JsonProperty
    private final Window window;

    /**
     * A filter to apply to raw values.
     */
    @Schema
    @JsonProperty
    private final ExpressionOperator valueFilter;

    /**
     * A filter to apply to aggregated values.
     */
    @Schema
    @JsonProperty
    private final ExpressionOperator aggregateFilter;

    @JsonPropertyDescription("TODO")
    @JsonProperty
    private final Boolean extractValues;

    @JsonProperty
    private final DocRef extractionPipeline;

    @Schema(description = "Defines the maximum number of results to return at each grouping level, e.g. '1000,10,1' " +
            "means 1000 results at group level 0, 10 at level 1 and 1 at level 2. In the absence of this field " +
            "system defaults will apply",
            example = "1000,10,1")
    @JsonProperty
    private final List<Long> maxResults;

    @Schema(description = "When grouping is used a value of true indicates that the results will include the full " +
            "detail of any results aggregated into a group as well as their aggregates. A value of false " +
            "will only include the aggregated values for each group. Defaults to false.")
    @JsonProperty
    private final Boolean showDetail;

    @Schema(description = "IGNORE: UI use only",
            hidden = true)
    @JsonProperty("conditionalFormattingRules")
    private final List<ConditionalFormattingRule> conditionalFormattingRules;

    @Schema(description = "IGNORE: UI use only",
            hidden = true)
    @JsonProperty("modelVersion")
    @Deprecated
    private String modelVersion;

    @JsonProperty("visSettings")
    private final QLVisSettings visSettings;
    @JsonProperty
    private final Boolean applyValueFilters;

    @JsonProperty
    private final Integer maxStringFieldLength;

    @JsonProperty
    private final Boolean overrideMaxStringFieldLength;

    public TableSettings(
            final String queryId,
            final List<Column> columns,
            final Window window,
            final ExpressionOperator valueFilter,
            final ExpressionOperator aggregateFilter,
            final Boolean extractValues,
            final DocRef extractionPipeline,
            final List<Long> maxResults,
            final Boolean showDetail,
            final List<ConditionalFormattingRule> conditionalFormattingRules,
            final QLVisSettings visSettings,
            final Boolean applyValueFilters,
            final Integer maxStringFieldLength,
            final Boolean overrideMaxStringFieldLength) {
        this.queryId = queryId;
        this.fields = columns;
        this.window = window;
        this.valueFilter = valueFilter;
        this.aggregateFilter = aggregateFilter;
        this.extractValues = extractValues;
        this.extractionPipeline = extractionPipeline;
        this.maxResults = maxResults;
        this.showDetail = showDetail;
        this.conditionalFormattingRules = conditionalFormattingRules;
        this.visSettings = visSettings;
        this.applyValueFilters = applyValueFilters;
        this.maxStringFieldLength = maxStringFieldLength;
        this.overrideMaxStringFieldLength = overrideMaxStringFieldLength;
    }

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public TableSettings(
            @JsonProperty("queryId") final String queryId,
            @JsonProperty("fields") final List<Column> fields, // Kept as fields for backward compatibility.
            @JsonProperty("window") final Window window,
            @JsonProperty("valueFilter") final ExpressionOperator valueFilter,
            @JsonProperty("aggregateFilter") final ExpressionOperator aggregateFilter,
            @JsonProperty("extractValues") final Boolean extractValues,
            @JsonProperty("extractionPipeline") final DocRef extractionPipeline,
            @JsonProperty("maxResults") final List<Long> maxResults,
            @JsonProperty("showDetail") final Boolean showDetail,
            @JsonProperty("conditionalFormattingRules") final List<ConditionalFormattingRule> conditionalFormattingRules,
            @JsonProperty("modelVersion") final String modelVersion, // deprecated modelVersion.
            @JsonProperty("visSettings") final QLVisSettings visSettings,
            @JsonProperty("applyValueFilters") final Boolean applyValueFilters,
            @JsonProperty("maxStringFieldLength") final Integer maxStringFieldLength,
            @JsonProperty("overrideMaxStringFieldLength") final Boolean overrideMaxStringFieldLength) {
        this.queryId = queryId;
        this.fields = fields;
        this.window = window;
        this.valueFilter = valueFilter;
        this.aggregateFilter = aggregateFilter;
        this.extractValues = extractValues;
        this.extractionPipeline = extractionPipeline;
        this.maxResults = maxResults;
        this.showDetail = showDetail;
        this.conditionalFormattingRules = conditionalFormattingRules;
        this.modelVersion = modelVersion;
        this.visSettings = visSettings;
        this.applyValueFilters = applyValueFilters;
        this.maxStringFieldLength = maxStringFieldLength;
        this.overrideMaxStringFieldLength = overrideMaxStringFieldLength;
    }

    public String getQueryId() {
        return queryId;
    }

    @Deprecated // Kept as fields for backward compatibility.
    public List<Column> getFields() {
        return fields;
    }

    @JsonIgnore // Kept as fields for backward compatibility.
    public List<Column> getColumns() {
        return fields;
    }

    public Window getWindow() {
        return window;
    }

    public ExpressionOperator getValueFilter() {
        return valueFilter;
    }

    public ExpressionOperator getAggregateFilter() {
        return aggregateFilter;
    }

    public Boolean getExtractValues() {
        return extractValues;
    }

    public boolean extractValues() {
        return extractValues != Boolean.FALSE;
    }

    public DocRef getExtractionPipeline() {
        return extractionPipeline;
    }

    public List<Long> getMaxResults() {
        return maxResults;
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

    public QLVisSettings getVisSettings() {
        return visSettings;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TableSettings that = (TableSettings) o;
        return Objects.equals(queryId, that.queryId) &&
                Objects.equals(fields, that.fields) &&
                Objects.equals(window, that.window) &&
                Objects.equals(valueFilter, that.valueFilter) &&
                Objects.equals(aggregateFilter, that.aggregateFilter) &&
                Objects.equals(extractValues, that.extractValues) &&
                Objects.equals(extractionPipeline, that.extractionPipeline) &&
                Objects.equals(maxResults, that.maxResults) &&
                Objects.equals(showDetail, that.showDetail) &&
                Objects.equals(conditionalFormattingRules, that.conditionalFormattingRules) &&
                Objects.equals(modelVersion, that.modelVersion) &&
                Objects.equals(visSettings, that.visSettings) &&
                Objects.equals(applyValueFilters, that.applyValueFilters) &&
                Objects.equals(maxStringFieldLength, that.maxStringFieldLength) &&
                Objects.equals(overrideMaxStringFieldLength, that.overrideMaxStringFieldLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                queryId,
                fields,
                window,
                valueFilter,
                aggregateFilter,
                extractValues,
                extractionPipeline,
                maxResults,
                showDetail,
                conditionalFormattingRules,
                modelVersion,
                visSettings,
                applyValueFilters,
                maxStringFieldLength,
                overrideMaxStringFieldLength);
    }

    @Override
    public String toString() {
        return "TableSettings{" +
                "queryId='" + queryId + '\'' +
                ", columns=" + fields +
                ", window=" + window +
                ", filter=" + aggregateFilter +
                ", extractValues=" + extractValues +
                ", extractionPipeline=" + extractionPipeline +
                ", maxResults=" + maxResults +
                ", showDetail=" + showDetail +
                ", conditionalFormattingRules=" + conditionalFormattingRules +
                ", visSettings=" + visSettings +
                ", applyValueFilters='" + applyValueFilters + '\'' +
                ", maxStringFieldLength=" + maxStringFieldLength +
                ", overrideMaxStringFieldLength=" + overrideMaxStringFieldLength +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link TableSettings tableSettings}
     */
    public static final class Builder {

        private String queryId;
        private List<Column> columns;
        private Window window;
        private ExpressionOperator valueFilter;
        private ExpressionOperator aggregateFilter;
        private Boolean extractValues;
        private DocRef extractionPipeline;
        private List<Long> maxResults;
        private Boolean showDetail;
        private List<ConditionalFormattingRule> conditionalFormattingRules;
        private QLVisSettings visSettings;
        private Boolean applyValueFilters;
        private Integer maxStringFieldLength;
        private Boolean overrideMaxStringFieldLength;

        private Builder() {
        }

        private Builder(final TableSettings tableSettings) {
            this.queryId = tableSettings.getQueryId();
            this.columns = tableSettings.getColumns() == null
                    ? null
                    : new ArrayList<>(tableSettings.getColumns());
            this.window = tableSettings.window;
            this.valueFilter = tableSettings.valueFilter;
            this.aggregateFilter = tableSettings.aggregateFilter;
            this.extractValues = tableSettings.getExtractValues();
            this.extractionPipeline = tableSettings.getExtractionPipeline();
            this.maxResults = tableSettings.getMaxResults() == null
                    ? null
                    : new ArrayList<>(tableSettings.getMaxResults());
            this.showDetail = tableSettings.getShowDetail();
            this.conditionalFormattingRules = tableSettings.getConditionalFormattingRules() == null
                    ? null
                    : new ArrayList<>(tableSettings.getConditionalFormattingRules());
            this.visSettings = tableSettings.visSettings;
            this.applyValueFilters = tableSettings.applyValueFilters;
            this.maxStringFieldLength = tableSettings.maxStringFieldLength;
            this.overrideMaxStringFieldLength = tableSettings.overrideMaxStringFieldLength;
        }

        /**
         * @param value The ID for the query that wants these results
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder queryId(final String value) {
            this.queryId = value;
            return this;
        }

        public Builder window(final Window window) {
            this.window = window;
            return this;
        }

        public Builder columns(final List<Column> columns) {
            this.columns = columns;
            return this;
        }

        public Builder valueFilter(final ExpressionOperator valueFilter) {
            this.valueFilter = valueFilter;
            return this;
        }

        public Builder aggregateFilter(final ExpressionOperator rowFilter) {
            this.aggregateFilter = rowFilter;
            return this;
        }

        /**
         * @param values Add expected columns to the output table
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder addColumns(final Column... values) {
            return addColumns(Arrays.asList(values));
        }

        /**
         * Convenience function for adding multiple columns that are already in a collection.
         *
         * @param values The columns to add
         * @return this builder, with the columns added.
         */
        public Builder addColumns(final Collection<Column> values) {
            if (this.columns == null) {
                this.columns = new ArrayList<>(values);
            } else {
                this.columns.addAll(values);
            }
            return this;
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
            return this;
        }

        /**
         * @param value The reference to the extraction pipeline that will be used on the results
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder extractionPipeline(final DocRef value) {
            this.extractionPipeline = value;
            return this;
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
            return this.extractionPipeline(DocRef.builder().type(type).uuid(uuid).name(name).build());
        }

        public Builder maxResults(final List<Long> maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * @param values The max result value
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder addMaxResults(final Long... values) {
            return addMaxResults(Arrays.asList(values));
        }

        /**
         * Add a collection of max result values
         *
         * @param values The list of max result values
         * @return this builder
         */
        public Builder addMaxResults(final Collection<Long> values) {
            if (this.maxResults == null) {
                this.maxResults = new ArrayList<>(values);
            } else {
                this.maxResults.addAll(values);
            }
            return this;
        }

        /**
         * @param value When grouping is used a value of true indicates that the results will include
         *              the full detail of any results aggregated into a group as well as their aggregates.
         *              A value of false will only include the aggregated values for each group. Defaults to false.
         * @return The {@link TableSettings.Builder}, enabling method chaining
         */
        public Builder showDetail(final Boolean value) {
            this.showDetail = value;
            return this;
        }

        public Builder conditionalFormattingRules(final List<ConditionalFormattingRule> conditionalFormattingRules) {
            this.conditionalFormattingRules = conditionalFormattingRules;
            return this;
        }

        public Builder visSettings(final QLVisSettings visSettings) {
            this.visSettings = visSettings;
            return this;
        }

        public Builder applyValueFilters(final Boolean applyValueFilters) {
            this.applyValueFilters = applyValueFilters;
            return this;
        }

        public Builder maxStringFieldLength(final Integer maxStringFieldLength) {
            this.maxStringFieldLength = maxStringFieldLength;
            return this;
        }

        public Builder overrideMaxStringFieldLength(final Boolean overrideMaxStringFieldLength) {
            this.overrideMaxStringFieldLength = overrideMaxStringFieldLength;
            return this;
        }

        public TableSettings build() {
            return new TableSettings(
                    queryId,
                    columns,
                    window,
                    valueFilter,
                    aggregateFilter,
                    extractValues,
                    extractionPipeline,
                    maxResults,
                    showDetail,
                    conditionalFormattingRules,
                    visSettings,
                    applyValueFilters,
                    maxStringFieldLength,
                    overrideMaxStringFieldLength);
        }
    }
}
