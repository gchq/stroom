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

/// Calls the MCP tools a run has, for built-in tools that work with them, e.g. to upload a file.
public interface McpToolCaller {

    /// @param toolName A tool's name, as the model sees it.
    /// @return True if it is one of the run's MCP tools.
    boolean hasTool(String toolName);

    /// @param toolName One of the run's MCP tools.
    /// @return The name of the server it is on.
    String serverOf(String toolName);

    /// Calls an MCP tool.
    ///
    /// @param toolName      One of the run's MCP tools.
    /// @param argumentsJson Its arguments, as a JSON object.
    /// @return What it returned.
    String call(String toolName, String argumentsJson);
}
