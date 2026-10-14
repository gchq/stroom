/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * An argument of an MCP prompt.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiMcpPromptArgument {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final boolean required;

    /**
     * @param name        The argument's name.
     * @param description What it is for, or null.
     * @param required    True if it must be given.
     */
    @JsonCreator
    public AiMcpPromptArgument(@JsonProperty("name") final String name,
                               @JsonProperty("description") final String description,
                               @JsonProperty("required") final Boolean required) {
        this.name = name;
        this.description = description;
        this.required = Objects.requireNonNullElse(required, false);
    }

    /**
     * @return The argument's name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return What it is for, or null.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return True if it must be given.
     */
    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiMcpPromptArgument that = (AiMcpPromptArgument) o;
        return Objects.equals(name, that.name)
               && Objects.equals(description, that.description)
               && required == that.required;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, required);
    }
}
