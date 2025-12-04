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

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder({
        "id",
        "name",
        "expression",
        "sort",
        "filter",
        "format",
        "group",
        "width",
        "visible",
        "special",
        "columnFilter"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "Describes a field in a result set. The field can have various expressions applied to it, " +
                      "e.g. SUM(), along with sorting, filtering, formatting and grouping")
public final class Column implements HasDisplayValue {

    @JsonPropertyDescription("The internal id of the field for equality purposes")
    @JsonProperty
    private final String id;

    @JsonPropertyDescription("The name of the field for display purposes")
    @JsonProperty
    private final String name;

    @Schema(description = "The expression to use to generate the value for this field",
            example = "SUM(${count})")
    @JsonProperty
    private final String expression;

    @JsonProperty
    private final Sort sort;

    @JsonProperty
    private final IncludeExcludeFilter filter;

    @JsonProperty
    private final Format format;

    @JsonPropertyDescription("If this field is to be grouped then this defines the level of grouping, with 0 being " +
                             "the top level of grouping, 1 being the next level down, etc.")
    @JsonProperty
    private final Integer group;

    // Settings for visible table only.
    @Schema(description = "IGNORE: UI use only",
            hidden = true)
    @JsonProperty
    private final Integer width;
    @Schema(description = "IGNORE: UI use only",
            hidden = true)
    @JsonProperty
    private final Boolean visible;
    @Schema(description = "IGNORE: UI use only",
            hidden = true)
    @JsonProperty
    private final Boolean special;
    @JsonProperty
    private ColumnFilter columnFilter;
    @JsonProperty
    private ColumnValueSelection columnValueSelection;

    @JsonCreator
    public Column(@JsonProperty("id") final String id,
                  @JsonProperty("name") final String name,
                  @JsonProperty("expression") final String expression,
                  @JsonProperty("sort") final Sort sort,
                  @JsonProperty("filter") final IncludeExcludeFilter filter,
                  @JsonProperty("format") final Format format,
                  @JsonProperty("group") final Integer group,
                  @JsonProperty("width") final Integer width,
                  @JsonProperty("visible") final Boolean visible,
                  @JsonProperty("special") final Boolean special,
                  @JsonProperty("columnFilter") final ColumnFilter columnFilter,
                  @JsonProperty("columnValueSelection") final ColumnValueSelection columnValueSelection) {
        this.id = id;
        this.name = name;
        this.expression = expression;
        this.sort = sort;
        this.filter = filter;
        this.format = format;
        this.group = group;
        if (width != null) {
            this.width = width;
        } else {
            this.width = 200;
        }
        if (visible != null) {
            this.visible = visible;
        } else {
            this.visible = true;
        }
        if (special != null) {
            this.special = special;
        } else {
            this.special = false;
        }
        this.columnFilter = columnFilter;
        this.columnValueSelection = columnValueSelection;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getExpression() {
        return expression;
    }

    public Sort getSort() {
        return sort;
    }

    public IncludeExcludeFilter getFilter() {
        return filter;
    }

    public Format getFormat() {
        return format;
    }

    public Integer getGroup() {
        return group;
    }

    public Integer getWidth() {
        return width;
    }

    public Boolean isVisible() {
        return visible;
    }

    public Boolean isSpecial() {
        return special;
    }

    public ColumnFilter getColumnFilter() {
        return columnFilter;
    }

    // Required to replace column filter in place without re-rendering table columns.
    public void setColumnFilter(final ColumnFilter columnFilter) {
        this.columnFilter = columnFilter;
    }

    public ColumnValueSelection getColumnValueSelection() {
        return columnValueSelection;
    }

    public void setColumnValueSelection(final ColumnValueSelection columnValueSelection) {
        this.columnValueSelection = columnValueSelection;
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Column column = (Column) o;
        return Objects.equals(id, column.id) &&
               Objects.equals(name, column.name) &&
               Objects.equals(expression, column.expression) &&
               Objects.equals(sort, column.sort) &&
               Objects.equals(filter, column.filter) &&
               Objects.equals(format, column.format) &&
               Objects.equals(group, column.group) &&
               Objects.equals(width, column.width) &&
               Objects.equals(visible, column.visible) &&
               Objects.equals(special, column.special) &&
               Objects.equals(columnFilter, column.columnFilter) &&
               Objects.equals(columnValueSelection, column.columnValueSelection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id,
                name,
                expression,
                sort,
                filter,
                format,
                group,
                width,
                visible,
                special,
                columnFilter,
                columnValueSelection);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link Column}
     */
    public static final class Builder {

        private String id;
        private String name;
        private String expression;
        private Sort sort;
        private IncludeExcludeFilter filter;
        private Format format;
        private Integer group;
        private Integer width;
        private Boolean visible;
        private Boolean special;
        private ColumnFilter columnFilter;
        private ColumnValueSelection columnValueSelection;

//        /**
//         * @param name       The name of the field for display purposes
//         * @param expression The expression to use to generate the value for this field
//         */
//        private Builder(final String name,
//                       final String expression) {
//            this.name = name;
//            this.expression = expression;
//        }

        /**
         * No args constructor, allow all building using chained methods
         */
        private Builder() {
        }

        private Builder(final Column column) {
            this.id = column.id;
            this.name = column.name;
            this.expression = column.expression;
            this.sort = column.sort;
            this.filter = column.filter;
            this.format = column.format;
            this.group = column.group;
            this.width = column.width;
            this.visible = column.visible;
            this.special = column.special;
            this.columnFilter = column.columnFilter;
            this.columnValueSelection = column.columnValueSelection;
        }

        /**
         * @param value The internal id of the field for equality purposes
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder id(final String value) {
            this.id = value;
            return this;
        }

        /**
         * @param value The name of the field for display purposes
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder name(final String value) {
            this.name = value;
            return this;
        }

        /**
         * @param value The expression to use to generate the value for this field
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder expression(final String value) {
            this.expression = value;
            return this;
        }

        /**
         * @param value The sorting configuration to use
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder sort(final Sort value) {
            this.sort = value;
            return this;
        }

        /**
         * @param value Any regex filtering to apply to the values
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder filter(final IncludeExcludeFilter value) {
            this.filter = value;
            return this;
        }

        /**
         * @param value Formatting to apply to the value
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder format(final Format value) {
            this.format = value;
            return this;
        }

        /**
         * Set the group level
         *
         * @param group The group level to apply to this field
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder group(final Integer group) {
            this.group = group;
            return this;
        }

        public Builder width(final Integer width) {
            this.width = width;
            return this;
        }

        public Builder visible(final Boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder special(final Boolean special) {
            this.special = special;
            return this;
        }

        public Builder columnFilter(final ColumnFilter columnFilter) {
            this.columnFilter = columnFilter;
            return this;
        }

        public Builder columnValueSelection(final ColumnValueSelection columnValueSelection) {
            this.columnValueSelection = columnValueSelection;
            return this;
        }

        public Column build() {
            return new Column(
                    id,
                    name,
                    expression,
                    sort,
                    filter,
                    format,
                    group,
                    width,
                    visible,
                    special,
                    columnFilter,
                    columnValueSelection);
        }
    }
}
