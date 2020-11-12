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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"componentId", "structure", "values", "size", "error"})
@JsonInclude(Include.NON_NULL)
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(
        description = "A result structure used primarily for visualisation data",
        parent = Result.class)
public final class FlatResult extends Result {

    private static final long serialVersionUID = 3826654996795750099L;

    @XmlElement
    @JsonProperty
    private List<Field> structure;

    @XmlElement
    @ApiModelProperty(value = "The 2 dimensional array containing the result set. The positions in the inner array " +
            "correspond to the positions in the 'structure' property")
    @JsonProperty
    private List<List<Object>> values;

    @XmlElement
    @ApiModelProperty(value = "The size of the result set being returned")
    @JsonProperty
    private Long size;

    public FlatResult() {
    }

    public FlatResult(final String componentId,
                      final List<Field> structure,
                      final List<List<Object>> values,
                      final String error) {
        super(componentId, error);
        this.structure = structure;
        this.values = values;
        this.size = (long) values.size();
    }

    @JsonCreator
    public FlatResult(@JsonProperty("componentId") final String componentId,
                      @JsonProperty("structure") final List<Field> structure,
                      @JsonProperty("values") final List<List<Object>> values,
                      @JsonProperty("size") final Long size,
                      @JsonProperty("error") final String error) {
        super(componentId, error);
        this.structure = structure;
        this.values = values;
        this.size = size;
    }

    public List<Field> getStructure() {
        return structure;
    }

    public void setStructure(final List<Field> structure) {
        this.structure = structure;
    }

    public List<List<Object>> getValues() {
        return values;
    }

    public void setValues(final List<List<Object>> values) {
        this.values = values;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(final Long size) {
        this.size = size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FlatResult that = (FlatResult) o;
        return Objects.equals(structure, that.structure) &&
                Objects.equals(values, that.values) &&
                Objects.equals(size, that.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), structure, values, size);
    }

    @Override
    public String toString() {
        return size + " rows";
    }

    /**
     * Builder for constructing a {@link FlatResult}
     */
    public static class Builder
            extends Result.Builder<FlatResult, Builder> {
        private final List<Field> structure = new ArrayList<>();

        private final List<List<Object>> values = new ArrayList<>();

        private Long overriddenSize = null;

        /**
         * Add headings to our data
         *
         * @param fields the fields which act as headings for our data
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addFields(final Field... fields) {
            structure.addAll(Arrays.asList(fields));
            return this;
        }

        /**
         * Singular Add headings to our data
         *
         * @param field the field which act as headings for our data
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addField(final Field field) {
            return addFields(field);
        }

//        /**
//         * Singular Add headings to our data
//         *
//         * @param name Name of the field
//         * @param expression Expression to use for the field
//         *
//         * @return The {@link Builder}, enabling method chaining
//         */
//        public Builder addField(final String name, final String expression) {
//            return addFields(new Field.Builder().name(name).expression(expression).build());
//        }

        /**
         * @param values A collection of 'rows' to add to our values
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addValues(final List<Object> values) {
            this.values.add(values);
            return this;
        }

        /**
         * Fix the reported size of the result set.
         *
         * @param value The size to use
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder size(final Long value) {
            this.overriddenSize = value;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        public FlatResult build() {
            if (null != overriddenSize) {
                return new FlatResult(getComponentId(), structure, values, overriddenSize, getError());
            } else {
                return new FlatResult(getComponentId(), structure, values, getError());
            }
        }
    }
}