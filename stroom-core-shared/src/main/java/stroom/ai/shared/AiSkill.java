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

import java.util.List;
import java.util.Objects;

/**
 * A skill the user can read (a saved procedure in a skills doc), as the chat offers it: a slash command that runs
 * it by name, with its parameters.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiSkill {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String folder;
    @JsonProperty
    private final String command;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final List<AiMcpPromptArgument> parameters;

    /**
     * @param name        The skill's name.
     * @param folder      The path of its skills doc's folder.
     * @param command     The slash command that runs it, e.g. "/skill:user-activity-timeline".
     * @param description What it does, or null.
     * @param parameters  Its parameters, in order (a default is said in the description).
     */
    @JsonCreator
    public AiSkill(@JsonProperty("name") final String name,
                   @JsonProperty("folder") final String folder,
                   @JsonProperty("command") final String command,
                   @JsonProperty("description") final String description,
                   @JsonProperty("parameters") final List<AiMcpPromptArgument> parameters) {
        this.name = name;
        this.folder = folder;
        this.command = command;
        this.description = description;
        this.parameters = parameters;
    }

    /**
     * @return The skill's name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The path of its skills doc's folder.
     */
    public String getFolder() {
        return folder;
    }

    /**
     * @return The slash command that runs it.
     */
    public String getCommand() {
        return command;
    }

    /**
     * @return What it does, or null.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return Its parameters, in order.
     */
    public List<AiMcpPromptArgument> getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiSkill that = (AiSkill) o;
        return Objects.equals(name, that.name)
               && Objects.equals(folder, that.folder)
               && Objects.equals(command, that.command)
               && Objects.equals(description, that.description)
               && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, folder, command, description, parameters);
    }
}
