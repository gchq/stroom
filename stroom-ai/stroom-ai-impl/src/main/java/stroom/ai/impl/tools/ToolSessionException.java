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

/// Thrown when a tool needs something its session doesn't have, e.g. a chat's history in a session that isn't a
/// chat. The tool's caller is told the message, so it can stop asking.
public class ToolSessionException extends RuntimeException {

    /// @param message What the tool needs, for the caller.
    public ToolSessionException(final String message) {
        super(message);
    }
}
