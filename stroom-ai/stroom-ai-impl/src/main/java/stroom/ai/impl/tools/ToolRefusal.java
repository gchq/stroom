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

/// A ported tool's refusal: what the tool, or a helper it calls, tells the caller when it can't go on, worded for
/// the agent to act on (as the Stroom MCP server's `ToolError`s were): e.g. a sample that is a file path, a check
/// that can't be made because this Stroom holds no event-logging XML schema, or a mapping change that can't apply.
/// A tool gives its message as an error result.
public class ToolRefusal extends RuntimeException {

    /// @param message Why the tool can't go on, and what to do instead.
    public ToolRefusal(final String message) {
        super(message);
    }
}
