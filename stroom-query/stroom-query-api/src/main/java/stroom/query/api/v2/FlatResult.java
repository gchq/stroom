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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"componentId", "structure", "values", "size", "error"})
@JsonInclude(Include.NON_NULL)
@ApiModel(
        description = "A result structure used primarily for visualisation data",
        parent = Result.class)
public final class FlatResult extends Result {
    @JsonProperty
    private final List<Field> structure;

    @ApiModelProperty(value = "The 2 dimensional array containing the result set. The positions in the inner array " +
            "correspond to the positions in the 'structure' property")
    @JsonProperty
    private final List<List<Object>> values;

    @ApiModelProperty(value = "The size of the result set being returned")
    @JsonProperty
    private final Long size;

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

    public List<List<Object>> getValues() {
        return values;
    }

    public Long getSize() {
        return size;
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

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link FlatResult}
     */
    public static final class Builder extends Result.Builder<FlatResult, Builder> {
        private List<Field> structure = Collections.emptyList();
        private List<List<Object>> values = Collections.emptyList();
        private Long overriddenSize;

        private Builder() {
        }

        private Builder(final FlatResult flatResult) {
            this.structure = flatResult.structure;
            this.values = flatResult.values;
        }

        /**
         * Add headings to our data
         *
         * @param structure the fields which act as headings for our data
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder structure(List<Field> structure) {
            this.structure = structure;
            return this;
        }

        /**
         * @param values A collection of 'rows' to add to our values
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder values(final List<List<Object>> values) {
            this.values = values;
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
                return new FlatResult(componentId, structure, values, overriddenSize, error);
            } else {
                return new FlatResult(componentId, structure, values, (long) values.size(), error);
            }
        }
    }
}