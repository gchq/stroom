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

import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

/// Makes [McpTestClient]s of the endpoint's REAL tools, for tests outside this package (the end-to-end suites in
/// `stroom.mcp.impl.e2e`), which can't see [McpTools] itself.
public final class McpTestClients {

    private final McpTools mcpTools;
    private final SecurityContext securityContext;

    @Inject
    McpTestClients(final McpTools mcpTools, final SecurityContext securityContext) {
        this.mcpTools = mcpTools;
        this.securityContext = securityContext;
    }

    /// A client whose calls are made as the user, its forms answered by a stand-in for the user who agrees to
    /// everything unless told otherwise ([McpTestClient#answeringWith]).
    ///
    /// @param owner        The session's user.
    /// @param canShowForms True if the client says it can show forms (MCP elicitation).
    /// @return The client.
    public McpTestClient client(final UserRef owner, final boolean canShowForms) {
        return new McpTestClient(mcpTools, securityContext, owner, canShowForms);
    }
}
