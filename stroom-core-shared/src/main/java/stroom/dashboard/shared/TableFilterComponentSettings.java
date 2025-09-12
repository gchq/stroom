/*
 * Copyright 2017 Crown Copyright
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

import stroom.query.api.ColumnRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "tableId",
        "columns"})
@JsonInclude(Include.NON_NULL)
public final class TableFilterComponentSettings implements ComponentSettings {

    @JsonProperty
    private final String tableId;
    @JsonProperty
    private final List<ColumnRef> columns;

    @JsonCreator
    public TableFilterComponentSettings(@JsonProperty("tableId") final String tableId,
                                        @JsonProperty("columns") final List<ColumnRef> columns) {
        this.tableId = tableId;
        this.columns = columns;
    }

    public String getTableId() {
        return tableId;
    }

    public List<ColumnRef> getColumns() {
        return columns;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TableFilterComponentSettings that = (TableFilterComponentSettings) o;
        return Objects.equals(tableId, that.tableId) &&
               Objects.equals(columns, that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, columns);
    }

    @Override
    public String toString() {
        return "VisComponentSettings{" +
               "tableId='" + tableId + '\'' +
               ", columns=" + columns +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder
            extends AbstractBuilder<TableFilterComponentSettings, TableFilterComponentSettings.Builder> {

        private String tableId;
        private List<ColumnRef> columns;

        private Builder() {
        }

        private Builder(final TableFilterComponentSettings visComponentSettings) {
            this.tableId = visComponentSettings.tableId;
            this.columns = visComponentSettings.columns;
        }

        public Builder tableId(final String tableId) {
            this.tableId = tableId;
            return self();
        }

        public Builder columns(final List<ColumnRef> columns) {
            this.columns = columns;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TableFilterComponentSettings build() {
            return new TableFilterComponentSettings(tableId, columns);
        }
    }
}
