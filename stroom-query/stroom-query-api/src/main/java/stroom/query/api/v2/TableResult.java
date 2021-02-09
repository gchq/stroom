/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"componentId", "fields", "rows", "resultRange", "totalResults", "error"})
@JsonInclude(Include.NON_NULL)
@ApiModel(
        description = "Object for describing a set of results in a table form that supports grouped data",
        parent = Result.class)
public class TableResult extends Result {
    @ApiModelProperty(required = true)
    @JsonProperty
    private final List<Field> fields;

    @ApiModelProperty(required = true)
    @JsonProperty
    private final List<Row> rows;

    @ApiModelProperty(required = true)
    @JsonProperty
    private final OffsetRange resultRange;

    @ApiModelProperty(value = "The total number of results in this result set")
    @JsonProperty
    private final Integer totalResults;

    @JsonCreator
    public TableResult(@JsonProperty("componentId") final String componentId,
                       @JsonProperty("fields") final List<Field> fields,
                       @JsonProperty("rows") final List<Row> rows,
                       @JsonProperty("resultRange") final OffsetRange resultRange,
                       @JsonProperty("totalResults") final Integer totalResults,
                       @JsonProperty("error") final String error) {
        super(componentId, error);
        this.fields = fields;
        this.rows = rows;
        this.resultRange = resultRange;
        this.totalResults = totalResults;
    }

    public List<Field> getFields() {
        return fields;
    }

    public List<Row> getRows() {
        return rows;
    }

    public OffsetRange getResultRange() {
        return resultRange;
    }

    public Integer getTotalResults() {
        return totalResults;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
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

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link TableResult tableResult}
     */
    public static final class Builder extends Result.Builder<TableResult, Builder> {
        private List<Field> fields;
        private List<Row> rows;
        private OffsetRange resultRange;
        private Integer totalResults;

        private Builder() {
            super();
        }

        private Builder(final TableResult tableResult) {
            super(tableResult);
            fields = tableResult.fields;
            rows = tableResult.rows;
            resultRange = tableResult.resultRange;
            totalResults = tableResult.totalResults;
        }

//        /**
//         * @param values add fields to our table
//         * @return The {@link Builder}, enabling method chaining
//         */
//        public Builder addFields(final Field... values) {
//            this.fields.addAll(Arrays.asList(values));
//            return this;
//        }
//
//        /**
//         * @param values add rows of data to our table
//         * @return The {@link Builder}, enabling method chaining
//         */
//        public Builder addRows(final Row... values) {
//            this.rows.addAll(Arrays.asList(values));
//            return this;
//        }


        public Builder fields(final List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder rows(final List<Row> rows) {
            this.rows = rows;
            return this;
        }

        public Builder resultRange(final OffsetRange resultRange) {
            this.resultRange = resultRange;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TableResult build() {
            Integer totalResults = this.totalResults;
            if (totalResults == null && rows != null) {
                totalResults = rows.size();
            }
            return new TableResult(componentId, fields, rows, resultRange, totalResults, error);
        }
    }
}
