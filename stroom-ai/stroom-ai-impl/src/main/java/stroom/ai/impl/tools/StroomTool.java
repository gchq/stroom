/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.ai.shared.AiToolCategory;

/// One of Stroom's own tools, as any agent may call it: the "Ask Stroom AI" chat, and clients over MCP.
/// It is Stroom's own contract, with nothing of langchain4j or of the chat in it: [LangChain4jTools] adapts it for
/// the chat's model, and each call is given a [ToolCallContext] with the session it is made in.
///
/// Add a new tool by implementing this interface and binding it in [BuiltInToolsModule]; [BuiltInTools] picks it
/// up automatically.
public interface StroomTool {

    /// @return The tool's name, description and input schema, as advertised to the model.
    ToolDefinition definition();

    /// @return What the tool is for, as tool policies grant it (see [AiToolCategory]).
    AiToolCategory category();

    /// Runs the tool.
    ///
    /// @param call    The call, including its JSON [ToolCall#arguments()].
    /// @param context The session the call is made in, and what the tool may use of it.
    /// @return The result to give the caller: its text, and any files and user actions that go with it. Return a
    /// plain explanatory text result on bad input rather than throwing - it is given to the model, which can
    /// correct itself.
    ToolResult execute(ToolCall call, ToolCallContext context);

    /// Whether the tool only reads or works things out. It maps to MCP's `readOnlyHint` tool annotation.
    ///
    /// @return True if the tool only reads or works things out, and cannot change anything. Only read-only tools
    /// may be used while the user is away (an unattended run). Defaults to true; override for any tool that
    /// changes something.
    default boolean isReadOnly() {
        return true;
    }

    /// Whether one call of the tool only reads, for a tool with some commands that change something and some that
    /// don't (e.g. memory's `read` and `write`). While the user is away, only calls that only read run.
    ///
    /// @param call The call, with its arguments.
    /// @return True if this call cannot change anything. Defaults to [#isReadOnly()].
    default boolean isReadOnly(final ToolCall call) {
        return isReadOnly();
    }

    /// Whether some calls of the tool only read, so a read-only tool policy still offers it, and refuses only the
    /// calls that change something.
    ///
    /// @return True if the tool, or some of its calls, cannot change anything. Defaults to [#isReadOnly()];
    /// override with true for a tool that overrides [#isReadOnly(ToolCall)].
    default boolean hasReadOnlyCalls() {
        return isReadOnly();
    }

    /// Whether the tool's output could hold instructions planted by someone else. It maps to MCP's
    /// `openWorldHint` tool annotation.
    ///
    /// @return True if what the tool returns could hold instructions planted by someone other than the user (e.g.
    /// tool output, workspace files or Stroom data). Once a run has used such a tool, changes it makes that outlive
    /// the chat need the user's approval. Defaults to true; override only for tools whose output is known to be
    /// safe.
    default boolean readsUntrustedContent() {
        return true;
    }

    /// @return True if the tool limits how much it returns itself (e.g. a page of a file), so its result is never
    /// saved to the workspace in place of being returned, which would only make another file to read.
    default boolean boundsItsOutput() {
        return false;
    }

    /// The permissions the tool needs, as metadata about it (e.g. for an MCP endpoint to say which tools a user
    /// can make use of). It is not shown to the model and enforces nothing: the tool makes its own checks when it
    /// is called, and is offered whatever the user's permissions.
    ///
    /// @return The app permissions the tool requires or that unlock parts of its output, and the document
    /// permissions it checks on what it reads. Defaults to [ToolPermissions#NONE]; override for any tool that
    /// checks a permission.
    default ToolPermissions permissions() {
        return ToolPermissions.NONE;
    }
}
