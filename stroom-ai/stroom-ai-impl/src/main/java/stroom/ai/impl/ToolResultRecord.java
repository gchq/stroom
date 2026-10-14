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

/// The result of a tool call, as stored (as JSON) in a TOOL_RESULT message.
///
/// @param callId     The model's id for the call.
/// @param tool       The tool's name, as the model saw it.
/// @param error      True if the call failed or was not run.
/// @param text       What the model was given: the whole result, or a stub if it was too large.
/// @param path       The workspace file holding the whole result, if it was too large to give in full.
/// @param chars      The length of the whole result.
/// @param durationMs How long the call took.
public record ToolResultRecord(String callId,
                               String tool,
                               boolean error,
                               String text,
                               String path,
                               long chars,
                               long durationMs) {

}
