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
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "groupKey",
        "values",
        "depth",
        "formattingType",
        "formattingStyle",
        "customStyle"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A row of data in a result set")
public final class Row {

    @JsonProperty
    private final String groupKey;

    @Schema(description = "The value for this row of data. The values in the list are in the same order as the " +
                          "fields in the ResultRequest"
    )
    @JsonProperty
    private final List<String> values;

    @Schema(description = "The grouping depth, where 0 is the top level of grouping, or where there is no grouping",
            example = "0")
    @JsonProperty
    private final Integer depth;

    @JsonProperty
    private final ConditionalFormattingType formattingType;
    @JsonProperty
    private final ConditionalFormattingStyle formattingStyle;
    @JsonProperty
    private final CustomConditionalFormattingStyle customStyle;

    @JsonCreator
    public Row(@JsonProperty("groupKey") final String groupKey,
               @JsonProperty("values") final List<String> values,
               @JsonProperty("depth") final Integer depth,
               @JsonProperty("formattingType") final ConditionalFormattingType formattingType,
               @JsonProperty("formattingStyle") final ConditionalFormattingStyle formattingStyle,
               @JsonProperty("customStyle") final CustomConditionalFormattingStyle customStyle) {
        this.groupKey = groupKey;
        this.values = values;
        this.depth = depth;
        this.formattingType = formattingType;
        this.formattingStyle = formattingStyle;
        this.customStyle = customStyle;
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

    public ConditionalFormattingType getFormattingType() {
        return formattingType;
    }

    public ConditionalFormattingStyle getFormattingStyle() {
        return formattingStyle;
    }

    public CustomConditionalFormattingStyle getCustomStyle() {
        return customStyle;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Row row = (Row) o;
        return Objects.equals(groupKey, row.groupKey) &&
               Objects.equals(values, row.values) &&
               Objects.equals(depth, row.depth) &&
               Objects.equals(formattingType, row.formattingType) &&
               Objects.equals(formattingStyle, row.formattingStyle) &&
               Objects.equals(customStyle, row.customStyle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey, values, depth, formattingType, formattingStyle, customStyle);
    }

    @Override
    public String toString() {
        return "Row{" +
               "groupKey='" + groupKey + '\'' +
               ", values=" + values +
               ", depth=" + depth +
               ", formattingType='" + formattingType + '\'' +
               ", formattingStyle='" + formattingStyle + '\'' +
               ", customStyle='" + customStyle + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link Row}
     */
    public static final class Builder {

        private String groupKey;
        private List<String> values;
        private Integer depth = 0;
        private ConditionalFormattingType formattingType;
        private ConditionalFormattingStyle formattingStyle;
        private CustomConditionalFormattingStyle customStyle;

        private Builder() {
        }

        private Builder(final Row row) {
            groupKey = row.groupKey;
            values = row.values;
            depth = row.depth;
            formattingType = row.formattingType;
            formattingStyle = row.formattingStyle;
            customStyle = row.customStyle;
        }

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

        public Builder formattingType(final ConditionalFormattingType formattingType) {
            this.formattingType = formattingType;
            return this;
        }

        public Builder formattingStyle(final ConditionalFormattingStyle formattingStyle) {
            this.formattingStyle = formattingStyle;
            return this;
        }

        public Builder customStyle(final CustomConditionalFormattingStyle customStyle) {
            this.customStyle = customStyle;
            return this;
        }

        public Row build() {
            return new Row(groupKey, values, depth, formattingType, formattingStyle, customStyle);
        }
    }
}
