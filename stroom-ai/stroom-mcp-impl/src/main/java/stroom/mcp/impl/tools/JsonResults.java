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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolResult;
import stroom.util.json.JsonUtil;

import java.util.Map;

/// How the ported tools give their results: as JSON, with the same field names and nesting as the Stroom MCP
/// server's tools, both as the text the model reads and as the result's structured content.
final class JsonResults {

    private JsonResults() {
    }

    /// @param result The tool's result, a JSON-able map.
    /// @return The result as JSON text, with the map as its structured content.
    static ToolResult of(final Map<String, Object> result) {
        return ToolResult.text(JsonUtil.writeValueAsString(result, false))
                .withStructuredContent(result);
    }

    /// @param refusal Why the tool can't go on.
    /// @return A failed result saying so.
    static ToolResult refused(final ToolRefusal refusal) {
        return ToolResult.error(refusal.getMessage());
    }

    /// @param message Why the tool can't go on.
    /// @return A failed result saying so.
    static ToolResult refused(final String message) {
        return ToolResult.error(message);
    }
}
