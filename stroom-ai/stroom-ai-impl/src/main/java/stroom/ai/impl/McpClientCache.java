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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// Keeps MCP connections open between the questions of a chat, so a server is not connected to (and its
/// tools listed) again for every question. A connection is closed when it has not been used for
/// [#IDLE_TIMEOUT], when its chat is deleted, or when the server's configuration changes (the configuration is
/// part of the key, so a changed server simply gets a new connection and the old one goes idle).
///
/// Connections are local to the node. A chat that moves to another node gets new connections there.
@Singleton
public class McpClientCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpClientCache.class);

    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);

    private final Map<Key, McpServerConnection> connections = new ConcurrentHashMap<>();
    private final Duration idleTimeout;

    @Inject
    public McpClientCache() {
        this(IDLE_TIMEOUT);
    }

    McpClientCache(final Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    /// @param chatId   The chat the connection is for.
    /// @param userUuid The chat's user.
    /// @param config   The server's configuration.
    /// @param creator  Connects to the server if there is no connection to reuse. May throw.
    /// @return A connection for the chat, reused if there is one.
    McpServerConnection get(final int chatId,
                            final String userUuid,
                            final McpToolServerConfig config,
                            final Supplier<McpServerConnection> creator) {
        closeIdle();
        final Key key = new Key(chatId, userUuid, config);
        final McpServerConnection existing = connections.get(key);
        if (existing != null) {
            return existing;
        }
        final McpServerConnection created = creator.get();
        final McpServerConnection raced = connections.putIfAbsent(key, created);
        if (raced != null) {
            created.close();
            return raced;
        }
        LOGGER.debug(() -> "Cached MCP connection to '" + created.label() + "' for chat " + chatId);
        return created;
    }

    /// Closes and forgets every connection for a chat, e.g. because it has been deleted.
    public void evictChat(final int chatId) {
        closeWhere(entry -> entry.getKey().chatId() == chatId);
    }

    /// Closes and forgets the connections that have not been used for the idle timeout.
    void closeIdle() {
        final long oldestAllowedMs = System.currentTimeMillis() - idleTimeout.toMillis();
        closeWhere(entry -> entry.getValue().lastUsedMs() <= oldestAllowedMs);
    }

    /// @return The number of connections held.
    int size() {
        return connections.size();
    }

    private void closeWhere(final Predicate<Entry<Key, McpServerConnection>> predicate) {
        final List<McpServerConnection> toClose = new ArrayList<>();
        connections.entrySet().removeIf(entry -> {
            if (predicate.test(entry)) {
                toClose.add(entry.getValue());
                return true;
            }
            return false;
        });
        toClose.forEach(connection -> {
            LOGGER.debug(() -> "Closing cached MCP connection to '" + connection.label() + "'");
            connection.close();
        });
    }

    private record Key(int chatId, String userUuid, McpToolServerConfig config) {

        private Key {
            Objects.requireNonNull(config);
        }
    }
}
