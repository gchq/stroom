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

package stroom.ai.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/// A form an MCP tool asked the user to fill in while it ran (MCP elicitation), as stored (as JSON) in an
/// ELICITATION message.
///
/// @param kind      Always [#KIND].
/// @param status    Where it has got to.
/// @param server    The MCP server that asked.
/// @param tool      The tool that asked, if known.
/// @param message   What the server said, shown to the user as plain text.
/// @param schema    The form, as a flat JSON schema.
/// @param expiresMs When it stops waiting for an answer.
/// @param content   The user's answer, once accepted. It goes to the server, never to the model.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public record ElicitationRecord(@JsonProperty("kind") String kind,
                                @JsonProperty("status") Status status,
                                @JsonProperty("server") String server,
                                @JsonProperty("tool") String tool,
                                @JsonProperty("message") String message,
                                @JsonProperty("schema") Map<String, Object> schema,
                                @JsonProperty("expiresMs") long expiresMs,
                                @JsonProperty("content") Map<String, Object> content) {

    public static final String KIND = "ELICITATION";

    @JsonCreator
    public ElicitationRecord {
        // Explicit constructor for Jackson.
    }

    /// @return A copy with a new status and answer.
    public ElicitationRecord answered(final Status newStatus, final Map<String, Object> answer) {
        return new ElicitationRecord(kind, newStatus, server, tool, message, schema, expiresMs, answer);
    }


    // --------------------------------------------------------------------------------


    /// Where a form has got to.
    public enum Status {
        PENDING,
        ACCEPTED,
        DECLINED,
        CANCELLED,
        EXPIRED
    }
}
