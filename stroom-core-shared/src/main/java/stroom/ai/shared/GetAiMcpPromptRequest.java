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

import java.util.Map;
import java.util.Objects;

/**
 * Asks an MCP server for one of its prompts, filled in with arguments.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class GetAiMcpPromptRequest {

    @JsonProperty
    private final AskStroomAiConfig config;
    @JsonProperty
    private final String server;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final Map<String, String> arguments;

    /**
     * @param config    The user's Ask Stroom AI settings, for the model whose MCP servers to ask.
     * @param server    The MCP server.
     * @param name      The prompt's name on the server.
     * @param arguments The prompt's arguments.
     */
    @JsonCreator
    public GetAiMcpPromptRequest(@JsonProperty("config") final AskStroomAiConfig config,
                                 @JsonProperty("server") final String server,
                                 @JsonProperty("name") final String name,
                                 @JsonProperty("arguments") final Map<String, String> arguments) {
        this.config = config;
        this.server = server;
        this.name = name;
        this.arguments = arguments;
    }

    /**
     * @return The user's Ask Stroom AI settings, for the model whose MCP servers to ask.
     */
    public AskStroomAiConfig getConfig() {
        return config;
    }

    /**
     * @return The MCP server.
     */
    public String getServer() {
        return server;
    }

    /**
     * @return The prompt's name on the server.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The prompt's arguments.
     */
    public Map<String, String> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GetAiMcpPromptRequest that = (GetAiMcpPromptRequest) o;
        return Objects.equals(config, that.config)
               && Objects.equals(server, that.server)
               && Objects.equals(name, that.name)
               && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, server, name, arguments);
    }
}
