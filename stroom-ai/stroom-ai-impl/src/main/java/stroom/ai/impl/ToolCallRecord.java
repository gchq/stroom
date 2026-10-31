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

/// A tool call made by the model, as stored (as JSON) in a TOOL_CALL message.
///
/// @param callId    The model's id for the call.
/// @param tool      The tool's name, as the model saw it.
/// @param arguments The arguments, as JSON.
/// @param rationale What the model said alongside the call, if anything.
public record ToolCallRecord(String callId,
                             String tool,
                             String arguments,
                             String rationale) {

}
