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

package stroom.legacy.model_6_1;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"groupKey", "values", "depth"})
@XmlType(name = "Row", propOrder = {"groupKey", "values", "depth"})
@Schema(description = "A row of data in a result set")
@Deprecated
public final class Row implements Serializable {

    private static final long serialVersionUID = 4379892306375080112L;

    @XmlElement
    @Schema(description = "TODO",
            required = true)
    private String groupKey;

    @XmlElementWrapper(name = "values")
    @XmlElement(name = "value")
    @Schema(description = "The value for this row of data. The values in the list are in the same order as the fields in " +
                          "the ResultRequest",
            required = true)
    private List<String> values;

    @XmlElement
    @Schema(description = "The grouping depth, where 0 is the top level of grouping, or where there is no grouping",
            example = "0",
            required = true)
    private Integer depth;

    private Row() {
    }

    public Row(final String groupKey, final List<String> values, final Integer depth) {
        this.groupKey = groupKey;
        this.values = values;
        this.depth = depth;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final Row row = (Row) o;

        if (!Objects.equals(groupKey, row.groupKey)) {
            return false;
        }
        if (!Objects.equals(values, row.values)) {
            return false;
        }
        return Objects.equals(depth, row.depth);
    }

    @Override
    public int hashCode() {
        int result = groupKey != null
                ? groupKey.hashCode()
                : 0;
        result = 31 * result + (values != null
                ? values.hashCode()
                : 0);
        result = 31 * result + (depth != null
                ? depth.hashCode()
                : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Row{" +
               "groupKey='" + groupKey + '\'' +
               ", values=" + values +
               ", depth=" + depth +
               '}';
    }

    /**
     * Builder for constructing a {@link Row}
     */
    public static class Builder {

        private String groupKey;

        private final List<String> values = new ArrayList<>();

        private Integer depth;

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
        public Builder addValues(final String... values) {
            this.values.addAll(Arrays.asList(values));
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

        public Row build() {
            return new Row(groupKey, values, depth);
        }
    }

}
