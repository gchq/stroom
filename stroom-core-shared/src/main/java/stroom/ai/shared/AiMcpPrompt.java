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
 * A prompt an MCP server offers, which the user can use as a slash command in the chat.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiMcpPrompt {

    @JsonProperty
    private final String server;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String command;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final List<AiMcpPromptArgument> arguments;

    /**
     * @param server      The MCP server that offers it.
     * @param name        Its name on the server.
     * @param command     The slash command for it, e.g. "/onboard_data_source".
     * @param description What it does, or null.
     * @param arguments   Its arguments, in order.
     */
    @JsonCreator
    public AiMcpPrompt(@JsonProperty("server") final String server,
                       @JsonProperty("name") final String name,
                       @JsonProperty("command") final String command,
                       @JsonProperty("description") final String description,
                       @JsonProperty("arguments") final List<AiMcpPromptArgument> arguments) {
        this.server = server;
        this.name = name;
        this.command = command;
        this.description = description;
        this.arguments = arguments;
    }

    /**
     * @return The MCP server that offers it.
     */
    public String getServer() {
        return server;
    }

    /**
     * @return Its name on the server.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The slash command for it, e.g. "/onboard_data_source".
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
     * @return Its arguments, in order.
     */
    public List<AiMcpPromptArgument> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiMcpPrompt that = (AiMcpPrompt) o;
        return Objects.equals(server, that.server)
               && Objects.equals(name, that.name)
               && Objects.equals(command, that.command)
               && Objects.equals(description, that.description)
               && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, name, command, description, arguments);
    }
}
