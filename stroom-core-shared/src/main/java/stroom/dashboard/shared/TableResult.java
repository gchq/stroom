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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.OffsetRange;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"fields", "rows", "resultRange", "totalResults", "error"})
@JsonInclude(Include.NON_NULL)
public class TableResult implements ComponentResult {
    @JsonProperty
    private final List<Field> fields;
    @JsonProperty
    private final List<Row> rows;
    @JsonProperty
    private final OffsetRange<Integer> resultRange;
    @JsonProperty
    private final Integer totalResults;
    @JsonProperty
    private final String error;

    @JsonCreator
    public TableResult(@JsonProperty("fields") final List<Field> fields,
                       @JsonProperty("rows") final List<Row> rows,
                       @JsonProperty("resultRange") final OffsetRange<Integer> resultRange,
                       @JsonProperty("totalResults") final Integer totalResults,
                       @JsonProperty("error") final String error) {
        this.fields = fields;
        this.rows = rows;
        this.resultRange = resultRange;
        this.totalResults = totalResults;
        this.error = error;
    }

    public List<Field> getFields() {
        return fields;
    }

    public List<Row> getRows() {
        return rows;
    }

    public OffsetRange<Integer> getResultRange() {
        return resultRange;
    }

    public Integer getTotalResults() {
        return totalResults;
    }

    public String getError() {
        return error;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TableResult that = (TableResult) o;
        return Objects.equals(fields, that.fields) &&
                Objects.equals(rows, that.rows) &&
                Objects.equals(resultRange, that.resultRange) &&
                Objects.equals(totalResults, that.totalResults) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, rows, resultRange, totalResults, error);
    }

    @Override
    public String toString() {
        if (rows == null) {
            return "";
        }

        return rows.size() + " rows";
    }
}
