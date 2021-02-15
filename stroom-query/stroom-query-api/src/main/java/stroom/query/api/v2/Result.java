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
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

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
@ApiModel(
        description = "Base object for describing a set of result data",
        subTypes = {TableResult.class, FlatResult.class, VisResult.class})
public abstract class Result {

    //TODO add an example value
    @ApiModelProperty(
            value = "The ID of the component that this result set was requested for. See ResultRequest in " +
                    "SearchRequest",
            required = true)
    @JsonProperty
    private final String componentId;

    @ApiModelProperty(value = "If an error has occurred producing this result set then this will have details " +
            "of the error")
    @JsonProperty
    private final String error;

    @JsonCreator
    public Result(@JsonProperty("componentId") final String componentId,
                  @JsonProperty("error") final String error) {
        this.componentId = componentId;
        this.error = error;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getError() {
        return error;
    }

    @SuppressWarnings("checkstyle:needbraces")
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
                Objects.equals(error, result.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentId, error);
    }

    @Override
    public String toString() {
        return "Result{" +
                "componentId='" + componentId + '\'' +
                ", error='" + error + '\'' +
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
        String error;

        Builder() {
        }

        Builder(final Result result) {
            this.componentId = result.componentId;
            this.error = result.error;
        }

        /**
         * @param value The ID of the component that this result set was requested for. See ResultRequest in
         *              SearchRequest
         * @return The {@link Builder}, enabling method chaining
         */
        public T_CHILD_CLASS componentId(final String componentId) {
            this.componentId = componentId;
            return self();
        }

        /**
         * @param value If an error has occurred producing this result set then this will have details
         * @return The {@link Builder}, enabling method chaining
         */
        public T_CHILD_CLASS error(final String error) {
            this.error = error;
            return self();
        }

        abstract T_CHILD_CLASS self();

        public abstract T build();
    }

}
