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

package stroom.openai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Asks Stroom to connect to an MCP tool server, as set up in the dialog, and say which tools it would offer.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TestMcpToolServerRequest {

    @JsonProperty
    private final String modelUuid;
    @JsonProperty
    private final McpToolServerConfig mcpToolServer;

    /// @param modelUuid     The AI model the server belongs to, which the user must be able to use.
    /// @param mcpToolServer The server's settings, as they are in the dialog.
    @JsonCreator
    public TestMcpToolServerRequest(@JsonProperty("modelUuid") final String modelUuid,
                                    @JsonProperty("mcpToolServer") final McpToolServerConfig mcpToolServer) {
        this.modelUuid = modelUuid;
        this.mcpToolServer = mcpToolServer;
    }

    /// @return The AI model the server belongs to.
    public String getModelUuid() {
        return modelUuid;
    }

    /// @return The server's settings.
    public McpToolServerConfig getMcpToolServer() {
        return mcpToolServer;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TestMcpToolServerRequest that = (TestMcpToolServerRequest) o;
        return Objects.equals(modelUuid, that.modelUuid)
               && Objects.equals(mcpToolServer, that.mcpToolServer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelUuid, mcpToolServer);
    }
}
