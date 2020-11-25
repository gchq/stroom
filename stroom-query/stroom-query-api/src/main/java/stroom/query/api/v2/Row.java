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

@JsonPropertyOrder({
        "groupKey",
        "values",
        "depth",
        "backgroundColor",
        "textColor"})
@JsonInclude(Include.NON_NULL)
@ApiModel(description = "A row of data in a result set")
public final class Row {
    @ApiModelProperty(
            value = "TODO",
            required = true)
    @JsonProperty
    private final String groupKey;

    @ApiModelProperty(
            value = "The value for this row of data. The values in the list are in the same order as the fields in " +
                    "the ResultRequest",
            required = true)
    @JsonProperty
    private final List<String> values;

    @ApiModelProperty(
            value = "The grouping depth, where 0 is the top level of grouping, or where there is no grouping",
            example = "0",
            required = true)
    @JsonProperty
    private final Integer depth;

    @JsonProperty
    private final String backgroundColor;

    @JsonProperty
    private final String textColor;

    @JsonCreator
    public Row(@JsonProperty("groupKey") final String groupKey,
               @JsonProperty("values") final List<String> values,
               @JsonProperty("depth") final Integer depth,
               @JsonProperty("backgroundColor") final String backgroundColor,
               @JsonProperty("textColor") final String textColor) {
        this.groupKey = groupKey;
        this.values = values;
        this.depth = depth;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public List<String> getValues() {
        return values;
    }

    public Integer getDepth() {
        return depth;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Row row = (Row) o;
        return Objects.equals(groupKey, row.groupKey) &&
                Objects.equals(values, row.values) &&
                Objects.equals(depth, row.depth) &&
                Objects.equals(backgroundColor, row.backgroundColor) &&
                Objects.equals(textColor, row.textColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey, values, depth, backgroundColor, textColor);
    }

    @Override
    public String toString() {
        return "Row{" +
                "groupKey='" + groupKey + '\'' +
                ", values=" + values +
                ", depth=" + depth +
                ", backgroundColor='" + backgroundColor + '\'' +
                ", textColor='" + textColor + '\'' +
                '}';
    }

    /**
     * Builder for constructing a {@link Row}
     */
    public static class Builder {
        private String groupKey;
        private List<String> values;
        private Integer depth;
        private String backgroundColor;
        private String textColor;

        /**
         * @param value TODO
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder groupKey(final String value) {
            this.groupKey = value;
            return this;
        }

        /**
         * @param values The value for this row of data.
         *               The values in the list are in the same order as the fields in the ResultRequest
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder values(final List<String> values) {
            this.values = values;
            return this;
        }

        /**
         * @param value The grouping depth, where 0 is the top level of grouping, or where there is no grouping
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder depth(final Integer value) {
            this.depth = value;
            return this;
        }

        public Builder backgroundColor(final String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder textColor(final String textColor) {
            this.textColor = textColor;
            return this;
        }

        public Row build() {
            return new Row(groupKey, values, depth, backgroundColor, textColor);
        }
    }
}