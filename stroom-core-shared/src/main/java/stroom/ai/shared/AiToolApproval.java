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
 * A decision the user has made about a tool in a chat, e.g. to allow it for the rest of the chat.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiToolApproval {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final String serverKey;
    @JsonProperty
    private final String toolName;
    @JsonProperty
    private final AiToolApprovalDecision decision;

    /**
     * @param id        The decision's id.
     * @param serverKey The MCP server the tool is on.
     * @param toolName  The tool's name on its server, or "*" for all of the server's read-only tools.
     * @param decision  Whether the tool is allowed or denied.
     */
    @JsonCreator
    public AiToolApproval(@JsonProperty("id") final Integer id,
                          @JsonProperty("serverKey") final String serverKey,
                          @JsonProperty("toolName") final String toolName,
                          @JsonProperty("decision") final AiToolApprovalDecision decision) {
        this.id = Objects.requireNonNullElse(id, 0);
        this.serverKey = serverKey;
        this.toolName = toolName;
        this.decision = decision;
    }

    /**
     * @return The decision's id.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The MCP server the tool is on.
     */
    public String getServerKey() {
        return serverKey;
    }

    /**
     * @return The tool's name on its server, or "*" for all of the server's read-only tools.
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * @return Whether the tool is allowed or denied.
     */
    public AiToolApprovalDecision getDecision() {
        return decision;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiToolApproval that = (AiToolApproval) o;
        return id == that.id
               && Objects.equals(serverKey, that.serverKey)
               && Objects.equals(toolName, that.toolName)
               && Objects.equals(decision, that.decision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, serverKey, toolName, decision);
    }
}
