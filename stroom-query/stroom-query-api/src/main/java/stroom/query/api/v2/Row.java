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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"groupKey", "values", "depth"})
@JsonInclude(Include.NON_NULL)
@XmlType(name = "Row", propOrder = {"groupKey", "values", "depth"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(description = "A row of data in a result set")
public final class Row implements Serializable {
    private static final long serialVersionUID = 4379892306375080112L;

    @XmlElement
    @ApiModelProperty(
            value = "TODO",
            required = true)
    @JsonProperty
    private String groupKey;

    @XmlElementWrapper(name = "values")
    @XmlElement(name = "value")
    @ApiModelProperty(
            value = "The value for this row of data. The values in the list are in the same order as the fields in " +
                    "the ResultRequest",
            required = true)
    @JsonProperty
    private List<String> values;

    @XmlElement
    @ApiModelProperty(
            value = "The grouping depth, where 0 is the top level of grouping, or where there is no grouping",
            example = "0",
            required = true)
    @JsonProperty
    private Integer depth;

    public Row() {
    }

    @JsonCreator
    public Row(@JsonProperty("groupKey") final String groupKey,
               @JsonProperty("values") final List<String> values,
               @JsonProperty("depth") final Integer depth) {
        this.groupKey = groupKey;
        this.values = values;
        this.depth = depth;
    }


    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(final String groupKey) {
        this.groupKey = groupKey;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(final List<String> values) {
        this.values = values;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(final Integer depth) {
        this.depth = depth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Row row = (Row) o;
        return Objects.equals(groupKey, row.groupKey) &&
                Objects.equals(values, row.values) &&
                Objects.equals(depth, row.depth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupKey, values, depth);
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
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder groupKey(final String value) {
            this.groupKey = value;
            return this;
        }

        /**
         * @param values The value for this row of data.
         *               The values in the list are in the same order as the fields in the ResultRequest
         *
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addValues(final String...values) {
            this.values.addAll(Arrays.asList(values));
            return this;
        }

        /**
         * @param value The grouping depth, where 0 is the top level of grouping, or where there is no grouping
         *
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