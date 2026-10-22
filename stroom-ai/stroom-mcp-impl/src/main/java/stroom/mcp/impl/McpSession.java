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

package stroom.mcp.impl;

import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/// A session of an MCP client on Stroom's `/mcp` endpoint, as this node holds it: who it belongs to, what the
/// client says it can do, the hidden chat holding its workspace (made when a tool first needs it), the calls
/// running in it.
final class McpSession {

    private final String id;
    private final UserRef owner;
    private final Implementation clientInfo;
    private final ClientCapabilities capabilities;
    private final McpWorkspaces.State workspace = new McpWorkspaces.State();
    private final RunningCalls runningCalls = new RunningCalls();
    private final long startedMs = System.currentTimeMillis();
    private final AtomicLong lastUsedMs = new AtomicLong(startedMs);

    /// @param id           The session's id, as the client sends it in its `Mcp-Session-Id` header.
    /// @param owner        The user who started it, the only one who may use it.
    /// @param clientInfo   The client's name and version, as it gave them, or null.
    /// @param capabilities What the client said it can do (e.g. show forms), or null.
    McpSession(final String id,
               final UserRef owner,
               final Implementation clientInfo,
               final ClientCapabilities capabilities) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.clientInfo = clientInfo;
        this.capabilities = capabilities;
    }

    /// @return The session's id.
    String id() {
        return id;
    }

    /// @return The user who started it.
    UserRef owner() {
        return owner;
    }

    /// @return The client's name, as it gave it, or "an MCP client".
    String clientName() {
        return NullSafe.nonBlankStringElse(NullSafe.get(clientInfo, Implementation::name), "an MCP client");
    }

    /// @return True if the client said it can show the user a form (MCP elicitation).
    boolean canShowForms() {
        return capabilities != null && capabilities.elicitation() != null;
    }

    /// @return The session, as the tools and the audit log see it.
    ToolSession toolSession() {
        return new ToolSession(ToolSession.Kind.MCP, id,
                new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, clientName()));
    }

    /// @return The session's workspace: the hidden chat holding it (once one has been made), and how many calls
    /// are using it (see [McpWorkspaces]).
    McpWorkspaces.State workspace() {
        return workspace;
    }

    /// @return The calls running in the session, by the id of the client's request, so they can be cancelled.
    RunningCalls runningCalls() {
        return runningCalls;
    }

    /// @return When the session was started.
    long startedMs() {
        return startedMs;
    }

    /// Marks the session as used now.
    void used() {
        lastUsedMs.set(System.currentTimeMillis());
    }

    /// @return When the session was last used.
    long lastUsedMs() {
        return lastUsedMs.get();
    }
}
