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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// A step that is the user's to take, offered by a tool call as part of its [ToolResult], e.g. to create a query
/// the agent proposed. The chat shows it as a button on the result's card, once it has been checked (see
/// `UserActions`), as an MCP tool's are; the result's text describes it too, so a caller that can't show buttons
/// loses nothing.
///
/// @param id        The action's id, unique within the result, e.g. `create-query-1`.
/// @param action    What kind of action it is, e.g. `UserActions.CREATE_QUERY`.
/// @param arguments What the action needs, by name, in the order given (e.g. `name` and `query`).
public record ToolUserAction(String id, String action, Map<String, Object> arguments) {

    public ToolUserAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    /// @return The action as an MCP tool would give it: its `id`, `action` and then its arguments, as
    /// `UserActions` checks it.
    public Map<String, Object> toMap() {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("action", action);
        map.putAll(arguments);
        return map;
    }
}
