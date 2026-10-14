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

package stroom.ai.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;

import java.util.List;

/**
 * The MCP clients and tool provider built for a single "Ask Stroom AI" question. Closing the toolset
 * closes every underlying MCP client (and its HTTP connection); a per-client failure to close is logged
 * and does not stop the others closing.
 */
public final class McpToolset implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpToolset.class);

    public static final McpToolset EMPTY = new McpToolset(List.of(), null);

    private final List<McpClient> clients;
    private final McpToolProvider toolProvider;

    McpToolset(final List<McpClient> clients, final McpToolProvider toolProvider) {
        this.clients = List.copyOf(clients);
        this.toolProvider = toolProvider;
    }

    /**
     * @return true if there are no MCP tool servers to use, in which case the chat behaves exactly as it
     * did before MCP support was added.
     */
    public boolean isEmpty() {
        return toolProvider == null || clients.isEmpty();
    }

    public McpToolProvider getToolProvider() {
        return toolProvider;
    }

    @Override
    public void close() {
        for (final McpClient client : clients) {
            try {
                client.close();
            } catch (final Exception e) {
                LOGGER.warn(() -> "Failed to close MCP client '" + client.key() + "'", e);
            }
        }
    }
}
