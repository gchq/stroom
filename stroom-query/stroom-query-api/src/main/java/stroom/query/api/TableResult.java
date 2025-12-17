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

import stroom.util.shared.ErrorMessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"componentId", "fields", "rows", "resultRange", "totalResults", "errors", "errorMessages"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "Object for describing a set of results in a table form that supports grouped data")
public final class TableResult extends Result {

    @Schema(required = true)
    @JsonProperty
    private final List<Column> fields;

    @Schema(required = true)
    @JsonProperty
    private final List<Row> rows;

    @Schema(required = true)
    @JsonProperty
    private final OffsetRange resultRange;

    @JsonPropertyDescription("The total number of results in this result set")
    @JsonProperty
    private final Long totalResults;

    @JsonCreator
    public TableResult(@JsonProperty("componentId") final String componentId,
                       @JsonProperty("fields") final List<Column> fields, // Kept as fields for backward compatibility.
                       @JsonProperty("rows") final List<Row> rows,
                       @JsonProperty("resultRange") final OffsetRange resultRange,
                       @JsonProperty("totalResults") final Long totalResults,
                       @JsonProperty("errors") final List<String> errors,
                       @JsonProperty("errorMessages") final List<ErrorMessage> errorMessages) {
        super(componentId, errors, errorMessages);
        this.fields = fields;
        this.rows = rows;
        this.resultRange = resultRange;
        this.totalResults = totalResults;
    }

    @Deprecated // Kept as fields for backward compatibility.
    public List<Column> getFields() {
        return fields;
    }

    @JsonIgnore // Kept as fields for backward compatibility.
    public List<Column> getColumns() {
        return fields;
    }

    public List<Row> getRows() {
        return rows;
    }

    public OffsetRange getResultRange() {
        return resultRange;
    }

    public Long getTotalResults() {
        return totalResults;
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
        final TableResult that = (TableResult) o;
        return Objects.equals(fields, that.fields) &&
                Objects.equals(rows, that.rows) &&
                Objects.equals(resultRange, that.resultRange) &&
                Objects.equals(totalResults, that.totalResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fields, rows, resultRange, totalResults);
    }

    @Override
    public String toString() {
        if (rows == null) {
            return "0 rows";
        }

        return rows.size() + " rows";
    }

    public static TableResultBuilderImpl builder() {
        return new TableResultBuilderImpl();
    }

    public TableResultBuilderImpl copy() {
        return new TableResultBuilderImpl(this);
    }

    /**
     * Builder for constructing a {@link TableResult tableResult}
     */
    public static final class TableResultBuilderImpl
            implements TableResultBuilder {

        private String componentId;
        private List<Column> columns;
        private final List<Row> rows;
        private OffsetRange resultRange;
        private Long totalResults;
        private List<ErrorMessage> errorMessages;

        private TableResultBuilderImpl() {
            rows = new ArrayList<>();
            errorMessages = Collections.emptyList();
        }

        private TableResultBuilderImpl(final TableResult tableResult) {
            componentId = tableResult.getComponentId();
            columns = tableResult.fields;
            rows = new ArrayList<>(tableResult.rows);
            resultRange = tableResult.resultRange;
            totalResults = tableResult.totalResults;
            errorMessages = tableResult.getErrorMessages();
        }

        public TableResultBuilderImpl componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        @Override
        public TableResultBuilderImpl columns(final List<Column> columns) {
            this.columns = columns;
            return this;
        }

        @Override
        public TableResultBuilder addRow(final Row row) {
            this.rows.add(row);
            return this;
        }

        @Override
        public TableResultBuilder errorMessages(final List<ErrorMessage> errorMessages) {
            this.errorMessages = errorMessages;
            return this;
        }

        @Override
        public TableResultBuilder resultRange(final OffsetRange resultRange) {
            this.resultRange = resultRange;
            return this;
        }

        @Override
        public TableResultBuilder totalResults(final Long totalResults) {
            this.totalResults = totalResults;
            return this;
        }

        @Override
        public TableResult build() {
            Long totalResults = this.totalResults;
            if (totalResults == null && rows != null) {
                totalResults = (long) rows.size();
            }
            return new TableResult(componentId, columns, rows, resultRange, totalResults,
                    Collections.emptyList(), errorMessages);
        }
    }
}
