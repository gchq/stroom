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

package stroom.ai.impl;

import stroom.openai.shared.McpToolServerConfig;
import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/// A connected MCP tool server. Connections are kept for a while between questions in the same chat (see
/// [McpClientCache]), so the server's tool list is cached here too, until it is too old or the server says it
/// has changed.
final class McpServerConnection implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpServerConnection.class);

    static final Duration TOOL_LIST_MAX_AGE = Duration.ofMinutes(5);

    private final String label;
    private final String toolNamePrefix;
    private final McpToolServerConfig config;
    private final McpSyncClient client;
    private final AtomicReference<UserIdentity> userIdentity;
    private volatile List<Tool> tools;
    private volatile long toolsFetchedMs;
    private volatile long lastUsedMs;

    /// @param label        The name used for the server in messages and in the resource tools.
    /// @param config       The server's configuration.
    /// @param client       The connected client.
    /// @param userIdentity The user that Stroom session tokens are passed through for. It is updated each time
    ///                     the connection is used, as a cached connection can outlive the session that made it.
    McpServerConnection(final String label,
                        final McpToolServerConfig config,
                        final McpSyncClient client,
                        final AtomicReference<UserIdentity> userIdentity) {
        this.label = Objects.requireNonNull(label);
        this.toolNamePrefix = McpToolNames.serverPrefix(label);
        this.config = config;
        this.client = Objects.requireNonNull(client);
        this.userIdentity = Objects.requireNonNullElseGet(userIdentity, AtomicReference::new);
        this.lastUsedMs = System.currentTimeMillis();
    }

    String label() {
        return label;
    }

    /// @return The prefix given to the names of this server's tools, see [McpToolNames].
    String toolNamePrefix() {
        return toolNamePrefix;
    }

    McpToolServerConfig config() {
        return config;
    }

    McpSyncClient client() {
        return client;
    }

    /// Sets the user whose session token is passed through to the server, and marks the connection as used.
    void use(final UserIdentity identity) {
        if (identity != null) {
            userIdentity.set(identity);
        }
        lastUsedMs = System.currentTimeMillis();
    }

    /// @return When the connection was last used.
    long lastUsedMs() {
        return lastUsedMs;
    }

    /// @return What the server says, when connected, about how to use it, or null if it says nothing.
    String instructions() {
        return client.getServerInstructions();
    }

    /// @return The server's tools, from the cache if the list is recent enough.
    List<Tool> tools() {
        final List<Tool> current = tools;
        if (current != null && System.currentTimeMillis() - toolsFetchedMs < TOOL_LIST_MAX_AGE.toMillis()) {
            return current;
        }
        final List<Tool> fetched = listTools();
        tools = fetched;
        toolsFetchedMs = System.currentTimeMillis();
        return fetched;
    }

    /// Forgets the cached tool list, e.g. when the server says its tools have changed.
    void invalidateTools() {
        LOGGER.debug(() -> "Tool list changed for MCP server '" + label + "'");
        tools = null;
    }

    private List<Tool> listTools() {
        final List<Tool> list = new ArrayList<>();
        String cursor = null;
        do {
            final ListToolsResult result = cursor == null
                    ? client.listTools()
                    : client.listTools(cursor);
            list.addAll(NullSafe.list(result.tools()));
            cursor = result.nextCursor();
        } while (NullSafe.isNonBlankString(cursor));
        return List.copyOf(list);
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> "Failed to close MCP client '" + label + "'", e);
        }
    }
}
