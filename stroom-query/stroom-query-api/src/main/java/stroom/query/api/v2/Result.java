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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TableResult.class, name = "table"),
        @JsonSubTypes.Type(value = FlatResult.class, name = "flat"),
        @JsonSubTypes.Type(value = VisResult.class, name = "vis")
})
@JsonInclude(Include.NON_NULL)
@Schema(
        description = "Base object for describing a set of result data",
        subTypes = {TableResult.class, FlatResult.class, VisResult.class})
public abstract class Result {

    //TODO add an example value
    @Schema(description = "The ID of the component that this result set was requested for. See ResultRequest in " +
            "SearchRequest",
            required = true)
    @JsonProperty
    private final String componentId;

    @Schema(description = "If an error has occurred producing this result set then this will have details " +
            "of the error")
    @JsonProperty
    private final List<String> errors;

    @JsonCreator
    public Result(@JsonProperty("componentId") final String componentId,
                  @JsonProperty("errors") final List<String> errors) {
        this.componentId = componentId;
        this.errors = errors;
    }

    public String getComponentId() {
        return componentId;
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Result result = (Result) o;
        return Objects.equals(componentId, result.componentId) &&
                Objects.equals(errors, result.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId, errors);
    }

    @Override
    public String toString() {
        return "Result{" +
                "componentId='" + componentId + '\'' +
                ", errors='" + errors + '\'' +
                '}';
    }

    /**
     * Builder for constructing a {@link Result}. This class is abstract and must be overridden for
     * each known Result implementation class.
     *
     * @param <T>             The result class type, either Flat or Table
     * @param <T_CHILD_CLASS> The subclass, allowing us to template OwnedBuilder correctly
     */
    public abstract static class Builder<T extends Result, T_CHILD_CLASS extends Builder<T, ?>> {

        String componentId;
        List<String> errors;

        Builder() {
        }

        Builder(final Result result) {
            this.componentId = result.componentId;
            this.errors = result.errors;
        }

        /**
         * @param componentId The ID of the component that this result set was requested for. See ResultRequest in
         *                    SearchRequest
         * @return The {@link Builder}, enabling method chaining
         */
        public T_CHILD_CLASS componentId(final String componentId) {
            this.componentId = componentId;
            return self();
        }

        /**
         * @param errors If an error has occurred producing this result set then this will have details
         * @return The {@link Builder}, enabling method chaining
         */
        public T_CHILD_CLASS errors(final List<String> errors) {
            this.errors = errors;
            return self();
        }

        abstract T_CHILD_CLASS self();

        public abstract T build();
    }

}
