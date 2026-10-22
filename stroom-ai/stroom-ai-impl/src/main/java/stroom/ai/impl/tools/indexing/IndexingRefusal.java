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

package stroom.ai.impl.tools.indexing;

/// Why an indexing helper can't go on, worded for the agent to act on (the Stroom MCP server raised these as
/// `ToolError`s). A tool gives the message as its error.
public class IndexingRefusal extends RuntimeException {

    /// @param message Why, and what to do instead.
    public IndexingRefusal(final String message) {
        super(message);
    }
}
