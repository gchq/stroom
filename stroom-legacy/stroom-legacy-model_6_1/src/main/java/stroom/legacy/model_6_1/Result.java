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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TableResult.class, name = "table"),
        @JsonSubTypes.Type(value = FlatResult.class, name = "vis")
})
@XmlType(name = "Result", propOrder = "componentId")
@XmlSeeAlso({TableResult.class, FlatResult.class})
@Schema(
        description = "Base object for describing a set of result data",
        subTypes = {TableResult.class, FlatResult.class})
@Deprecated
public abstract class Result implements Serializable {

    private static final long serialVersionUID = -7455554742243923562L;

    @XmlElement
    //TODO add an example value
    @Schema(description = "The ID of the component that this result set was requested for. See ResultRequest in SearchRequest",
            required = true)
    private String componentId;

    @XmlElement
    @JsonPropertyDescription("If an error has occurred producing this result set then this will have details " +
                             "of the error")
    private String error;

    Result() {
    }

    public Result(final String componentId, final String error) {
        this.componentId = componentId;
        this.error = error;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getError() {
        return error;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final Result that)) {
            return false;
        }

        if (!Objects.equals(error, that.error)) {
            return false;
        }
        return Objects.equals(componentId, that.componentId);
    }

    @Override
    public int hashCode() {
        int result = (error != null
                ? error.hashCode()
                : 0);
        result = 31 * result + (componentId != null
                ? componentId.hashCode()
                : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ComponentResult{" +
               "componentId='" + componentId + "', " +
               "error='" + error + '\'' +
               '}';
    }

    /**
     * Builder for constructing a {@link Result}. This class is abstract and must be overridden for
     * each known Result implementation class.
     *
     * @param <T>           The result class type, either Flat or Table
     * @param <CHILD_CLASS> The subclass, allowing us to template OwnedBuilder correctly
     */
    public abstract static class Builder<T extends Result, CHILD_CLASS extends Builder<T, ?>> {

        private String componentId;
        private String error;

        /**
         * @param value The ID of the component that this result set was requested for. See ResultRequest in SearchRequest
         * @return The {@link Builder}, enabling method chaining
         */
        public CHILD_CLASS componentId(final String value) {
            this.componentId = value;
            return self();
        }

        /**
         * @param value If an error has occurred producing this result set then this will have details
         * @return The {@link Builder}, enabling method chaining
         */
        public CHILD_CLASS error(final String value) {
            this.error = value;
            return self();
        }

        protected String getComponentId() {
            return componentId;
        }

        protected String getError() {
            return error;
        }

        protected abstract CHILD_CLASS self();

        public abstract T build();
    }

}
