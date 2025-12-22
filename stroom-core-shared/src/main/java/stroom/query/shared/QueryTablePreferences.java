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

import stroom.query.api.Column;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TableSettings;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "columns",
        "pageSize",
        "conditionalFormattingRules",
        "applyValueFilters",
        "selectionFilter"})
@JsonInclude(Include.NON_NULL)
public class QueryTablePreferences {

    @JsonProperty
    private final List<Column> columns;
    @JsonPropertyDescription("Defines the maximum number of rows to display in the table at once (default 100).")
    @JsonProperty
    private final Integer pageSize;
    @JsonProperty("conditionalFormattingRules")
    private final List<ConditionalFormattingRule> conditionalFormattingRules;
    @JsonProperty
    private final Boolean applyValueFilters;
    @JsonProperty
    private final ExpressionOperator selectionFilter;

    @JsonCreator
    public QueryTablePreferences(
            @JsonProperty("columns") final List<Column> columns,
            @JsonProperty("pageSize") final Integer pageSize,
            @JsonProperty("conditionalFormattingRules") final List<ConditionalFormattingRule>
                    conditionalFormattingRules,
            @JsonProperty("applyValueFilters") final Boolean applyValueFilters,
            @JsonProperty("selectionFilter") final ExpressionOperator selectionFilter) {
        this.columns = columns;
        this.pageSize = pageSize;
        this.conditionalFormattingRules = conditionalFormattingRules;
        this.applyValueFilters = applyValueFilters;
        this.selectionFilter = selectionFilter;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public List<ConditionalFormattingRule> getConditionalFormattingRules() {
        return conditionalFormattingRules;
    }

    public Boolean getApplyValueFilters() {
        return applyValueFilters;
    }

    public boolean applyValueFilters() {
        return applyValueFilters == Boolean.TRUE;
    }

    public ExpressionOperator getSelectionFilter() {
        return selectionFilter;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryTablePreferences that = (QueryTablePreferences) o;
        return Objects.equals(columns, that.columns) &&
               Objects.equals(pageSize, that.pageSize) &&
               Objects.equals(conditionalFormattingRules, that.conditionalFormattingRules) &&
               Objects.equals(applyValueFilters, that.applyValueFilters) &&
               Objects.equals(selectionFilter, that.selectionFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                columns,
                pageSize,
                conditionalFormattingRules,
                applyValueFilters,
                selectionFilter);
    }

    @Override
    public String toString() {
        return "TableSettings{" +
               "columns=" + columns +
               ", pageSize=" + pageSize +
               ", conditionalFormattingRules=" + conditionalFormattingRules +
               ", applyValueFilters='" + applyValueFilters + '\'' +
               ", selectionFilter='" + selectionFilter + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder copy(final QueryTablePreferences queryTablePreferences) {
        if (queryTablePreferences != null) {
            return queryTablePreferences.copy();
        } else {
            return builder();
        }
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link TableSettings tableSettings}
     */
    public static final class Builder extends AbstractBuilder<QueryTablePreferences, Builder> {

        private List<Column> columns;
        private Integer pageSize;
        private List<ConditionalFormattingRule> conditionalFormattingRules;
        private Boolean applyValueFilters;
        private ExpressionOperator selectionFilter;

        private Builder() {
        }

        private Builder(final QueryTablePreferences tableSettings) {
            this.columns = tableSettings.columns == null
                    ? null
                    : new ArrayList<>(tableSettings.columns);
            this.pageSize = tableSettings.pageSize;
            this.conditionalFormattingRules = tableSettings.conditionalFormattingRules == null
                    ? null
                    : new ArrayList<>(tableSettings.conditionalFormattingRules);
            this.applyValueFilters = tableSettings.applyValueFilters;
            this.selectionFilter = tableSettings.selectionFilter;
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

        public Builder pageSize(final Integer pageSize) {
            this.pageSize = pageSize;
            return self();
        }

        public Builder conditionalFormattingRules(final List<ConditionalFormattingRule> conditionalFormattingRules) {
            this.conditionalFormattingRules = conditionalFormattingRules;
            return self();
        }

        public Builder applyValueFilters(final Boolean applyValueFilters) {
            this.applyValueFilters = applyValueFilters;
            return self();
        }

        public Builder selectionFilter(final ExpressionOperator selectionFilter) {
            this.selectionFilter = selectionFilter;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public QueryTablePreferences build() {
            return new QueryTablePreferences(
                    columns,
                    pageSize,
                    conditionalFormattingRules,
                    applyValueFilters,
                    selectionFilter);
        }
    }
}
