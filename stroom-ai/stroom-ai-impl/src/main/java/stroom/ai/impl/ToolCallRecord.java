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

/// A tool call made by the model, as stored (as JSON) in a TOOL_CALL message.
///
/// @param callId    The model's id for the call.
/// @param tool      The tool's name, as the model saw it.
/// @param arguments The arguments, as JSON.
/// @param rationale What the model said alongside the call, if anything.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public record ToolCallRecord(@JsonProperty("callId") String callId,
                             @JsonProperty("tool") String tool,
                             @JsonProperty("arguments") String arguments,
                             @JsonProperty("rationale") String rationale) {

    @JsonCreator
    public ToolCallRecord {
        // Explicit constructor for Jackson.
    }
}
