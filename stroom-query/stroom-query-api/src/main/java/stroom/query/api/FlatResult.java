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

import stroom.util.shared.ErrorMessage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "componentId",
        "structure",
        "values",
        "size",
        "errors",
        "errorMessages"})
@JsonInclude(Include.NON_NULL)
@Schema(description = "A result structure used primarily for visualisation data")
public final class FlatResult extends Result {

    @JsonProperty
    private final List<Column> structure;

    @JsonPropertyDescription("The 2 dimensional array containing the result set. The positions in the inner array " +
                             "correspond to the positions in the 'structure' property")
    @JsonProperty
    private final List<List<Object>> values;

    @JsonPropertyDescription("The size of the result set being returned")
    @JsonProperty
    private final Long size;

    @JsonCreator
    public FlatResult(@JsonProperty("componentId") final String componentId,
                      @JsonProperty("structure") final List<Column> structure,
                      @JsonProperty("values") final List<List<Object>> values,
                      @JsonProperty("size") final Long size,
                      @JsonProperty("errors") final List<String> errors,
                      @JsonProperty("errorMessages") final List<ErrorMessage> errorMessages) {
        super(componentId, errors, errorMessages);
        this.structure = structure;
        this.values = values;
        this.size = size;
    }

    public List<Column> getStructure() {
        return structure;
    }

    public List<List<Object>> getValues() {
        return values;
    }

    public Long getSize() {
        return size;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FlatResult that = (FlatResult) o;
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

    public static FlatResultBuilderImpl builder() {
        return new FlatResultBuilderImpl();
    }

    public FlatResultBuilderImpl copy() {
        return new FlatResultBuilderImpl(this);
    }

    /**
     * Builder for constructing a {@link FlatResult}
     */
    public static final class FlatResultBuilderImpl
            implements FlatResultBuilder {

        private String componentId;
        private List<Column> structure = Collections.emptyList();
        private final List<List<Object>> values;
        private Long totalResults;
        private List<ErrorMessage> errorMessages;

        private FlatResultBuilderImpl() {
            values = new ArrayList<>();
        }

        private FlatResultBuilderImpl(final FlatResult flatResult) {
            componentId = flatResult.getComponentId();
            structure = flatResult.structure;
            values = new ArrayList<>(flatResult.values);
            errorMessages = flatResult.getErrorMessages();
        }

        public FlatResultBuilder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }


        /**
         * Add headings to our data
         *
         * @param structure the columns which act as headings for our data
         * @return The {@link FlatResultBuilderImpl}, enabling method chaining
         */
        @Override
        public FlatResultBuilderImpl structure(final List<Column> structure) {
            this.structure = structure;
            return this;
        }

        /**
         * @param values A 'row' of data points to add to our values
         * @return The {@link FlatResultBuilderImpl}, enabling method chaining
         */
        @Override
        public FlatResultBuilder addValues(final List<Object> values) {
            this.values.add(values);
            return this;
        }

        @Override
        public FlatResultBuilder errorMessages(final List<ErrorMessage> errorMessages) {
            this.errorMessages = errorMessages;
            return this;
        }

        /**
         * Fix the reported size of the result set.
         *
         * @param value The size to use
         * @return The {@link FlatResultBuilderImpl}, enabling method chaining
         */
        @Override
        public FlatResultBuilder totalResults(final Long value) {
            this.totalResults = value;
            return this;
        }

        @Override
        public FlatResult build() {
            if (null != totalResults) {
                return new FlatResult(componentId, structure, values, totalResults,
                        Collections.emptyList(), errorMessages);
            } else {
                return new FlatResult(componentId, structure, values, (long) values.size(),
                        Collections.emptyList(), errorMessages);
            }
        }
    }
}
