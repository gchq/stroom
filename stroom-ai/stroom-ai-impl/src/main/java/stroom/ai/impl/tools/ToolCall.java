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

package stroom.ai.impl.tools;

/// A call of a [StroomTool], as a model or client made it.
///
/// @param id        The caller's id for the call, or null. Results and user actions are matched to it.
/// @param name      The tool's name.
/// @param arguments The call's arguments, as a JSON object, or null or blank if there are none.
public record ToolCall(String id, String name, String arguments) {

}
