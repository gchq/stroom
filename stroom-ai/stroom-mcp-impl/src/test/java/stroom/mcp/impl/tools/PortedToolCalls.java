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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.util.json.JsonUtil;

import java.util.Map;

/// Calls of the ported tools for their tests, as an MCP client makes them.
final class PortedToolCalls {

    private PortedToolCalls() {
        // Static methods only.
    }

    /// @return A call of the tool with the arguments, as JSON.
    static ToolCall call(final String name, final String arguments) {
        return new ToolCall("1", name, arguments);
    }

    /// @return The context of a call from an MCP client, with no workspace.
    static ToolCallContext context() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(UserQuestions.NONE)
                .build();
    }

    /// @return A result's JSON text, read.
    @SuppressWarnings("unchecked")
    static Map<String, Object> json(final String text) {
        return JsonUtil.readValue(text, Map.class);
    }
}
