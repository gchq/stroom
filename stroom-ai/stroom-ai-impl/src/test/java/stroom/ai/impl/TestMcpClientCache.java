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

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMcpClientCache {

    private final McpToolServerConfig server = McpToolServerConfig.builder()
            .name("s")
            .endpoint("http://localhost/mcp")
            .build();

    @Test
    void testConnectionIsReusedForTheSameChat() {
        final McpClientCache cache = new McpClientCache();
        final AtomicInteger connects = new AtomicInteger();

        final McpServerConnection first = cache.get(1, "user", server, () -> connection(connects));
        final McpServerConnection second = cache.get(1, "user", server, () -> connection(connects));
        final McpServerConnection otherChat = cache.get(2, "user", server, () -> connection(connects));

        assertThat(second).isSameAs(first);
        assertThat(otherChat).isNotSameAs(first);
        assertThat(connects).hasValue(2);
    }

    @Test
    void testChangedConfigGetsANewConnection() {
        final McpClientCache cache = new McpClientCache();
        final AtomicInteger connects = new AtomicInteger();

        final McpServerConnection first = cache.get(1, "user", server, () -> connection(connects));
        final McpServerConnection changed = cache.get(1, "user",
                server.copy().endpoint("http://localhost/other").build(), () -> connection(connects));

        assertThat(changed).isNotSameAs(first);
    }

    @Test
    void testEvictChatClosesItsConnections() {
        final McpClientCache cache = new McpClientCache();
        final McpServerConnection chat1 = cache.get(1, "user", server, () -> connection(new AtomicInteger()));
        final McpServerConnection chat2 = cache.get(2, "user", server, () -> connection(new AtomicInteger()));

        cache.evictChat(1);

        Mockito.verify(chat1.client()).close();
        Mockito.verify(chat2.client(), Mockito.never()).close();
        assertThat(cache.size()).isOne();
    }

    @Test
    void testIdleConnectionsAreClosed() {
        final McpClientCache cache = new McpClientCache(Duration.ZERO);
        final McpServerConnection idle = cache.get(1, "user", server, () -> connection(new AtomicInteger()));

        cache.closeIdle();

        Mockito.verify(idle.client()).close();
        assertThat(cache.size()).isZero();
    }

    @Test
    void testFailedConnectIsNotCached() {
        final McpClientCache cache = new McpClientCache();

        assertThatThrownBy(() -> cache.get(1, "user", server, () -> {
            throw new RuntimeException("unreachable");
        })).hasMessage("unreachable");
        assertThat(cache.size()).isZero();
    }

    private McpServerConnection connection(final AtomicInteger connects) {
        connects.incrementAndGet();
        return new McpServerConnection("s", server, Mockito.mock(McpSyncClient.class), null);
    }
}
