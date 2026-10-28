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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitUrlRequest;
import tools.jackson.databind.json.JsonMapper;

/// The JSON mapper for MCP messages, Stroom's MCP endpoint and its MCP client both: the MCP SDK's own, able to write
/// a form request (MCP elicitation).
///
/// The SDK's [ElicitRequest] names its type in its `mode` property, which its records also have as a property.
/// Jackson 3.2 refuses to write a type that has both ("Conflict between type id property 'mode' and bean property
/// with same name"), so with the SDK's mapper every form Stroom's MCP endpoint asked a client to show failed to be
/// sent, and the call waited until it timed out (seen when Jackson was uplifted from 3.1.2 to 3.2.4, with MCP SDK
/// 2.0.1; 2.1.0 declares it the same way). Here `mode` is the type's existing property, as it is: written once, and
/// read as the type.
public final class McpJson {

    // From the mapper the SDK uses by default (JacksonMcpJsonMapperSupplier's).
    private static final McpJsonMapper MAPPER = new JacksonMcpJsonMapper(
            JsonMapper.shared()
                    .rebuild()
                    .addMixIn(ElicitRequest.class, ElicitRequestMixIn.class)
                    .build());

    private McpJson() {
        // Static methods only.
    }

    /// @return The mapper for MCP messages.
    public static McpJsonMapper mapper() {
        return MAPPER;
    }

    // ElicitRequest's type information, as the SDK declares it but with the type id written by the records'
    // own mode property.
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "mode",
            defaultImpl = ElicitFormRequest.class)
    @JsonSubTypes({@JsonSubTypes.Type(value = ElicitFormRequest.class, name = ElicitFormRequest.MODE),
            @JsonSubTypes.Type(value = ElicitUrlRequest.class, name = ElicitUrlRequest.MODE)})
    private interface ElicitRequestMixIn {

    }
}
