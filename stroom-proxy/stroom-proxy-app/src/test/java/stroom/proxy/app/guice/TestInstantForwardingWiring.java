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

package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.InstantForwardFile;
import stroom.proxy.app.handler.InstantForwardHttpPost;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Instant forwarding relays data straight to the destination during receipt, with no store write,
 * no queue publish, and no success response to the sender until the downstream has accepted it. It
 * therefore has to bypass the pipeline entirely.
 * <p>
 * This is pinned because the wiring is a branch in {@link ProxyCoreModule#provideReceiverFactory} and
 * in {@link stroom.proxy.app.ProxyLifecycle}, and because the only end-to-end test that exercised
 * instant mode has been {@code @Disabled} since 2024-01-24. Without these assertions the mode can be
 * silently disconnected again - which is exactly what happened when the pipeline became the default.
 */
class TestInstantForwardingWiring {

    @Test
    void testInstantHttpForwarderBypassesThePipeline() {
        final ReceiverFactory instantReceiverFactory = Mockito.mock(ReceiverFactory.class);
        final InstantForwardHttpPost instantForwardHttpPost = Mockito.mock(InstantForwardHttpPost.class);
        Mockito.when(instantForwardHttpPost.get(Mockito.any(ForwardHttpPostConfig.class)))
                .thenReturn(instantReceiverFactory);

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addForwardHttpDestination(ForwardHttpPostConfig.builder()
                        .enabled(true)
                        .instant(true)
                        .name("instant-http")
                        .forwardUrl("http://downstream:8080/datafeed")
                        .build())
                .build();

        assertThat(ProxyCoreModule.isInstantForwarding(proxyConfig)).isTrue();

        final ReceiverFactory receiverFactory = new ProxyCoreModule().provideReceiverFactory(
                proxyConfig,
                () -> instantForwardHttpPost,
                failingProvider(),
                failingProvider());

        assertThat(receiverFactory).isSameAs(instantReceiverFactory);
    }

    @Test
    void testInstantFileForwarderBypassesThePipeline() {
        final ReceiverFactory instantReceiverFactory = Mockito.mock(ReceiverFactory.class);
        final InstantForwardFile instantForwardFile = Mockito.mock(InstantForwardFile.class);
        Mockito.when(instantForwardFile.get(Mockito.any(ForwardFileConfig.class)))
                .thenReturn(instantReceiverFactory);

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addForwardFileDestination(ForwardFileConfig.builder()
                        .enabled()
                        .withInstant(true)
                        .withName("instant-file")
                        .withPath("forward_dest")
                        .build())
                .build();

        assertThat(ProxyCoreModule.isInstantForwarding(proxyConfig)).isTrue();

        final ReceiverFactory receiverFactory = new ProxyCoreModule().provideReceiverFactory(
                proxyConfig,
                failingProvider(),
                () -> instantForwardFile,
                failingProvider());

        assertThat(receiverFactory).isSameAs(instantReceiverFactory);
    }

    @Test
    void testNonInstantForwarderUsesThePipeline() {
        final ReceiverFactory pipelineReceiverFactory = Mockito.mock(ReceiverFactory.class);
        final ProxyPipelineAssembler assembler = Mockito.mock(ProxyPipelineAssembler.class);
        Mockito.when(assembler.getReceiverFactory()).thenReturn(pipelineReceiverFactory);

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addForwardHttpDestination(ForwardHttpPostConfig.builder()
                        .enabled(true)
                        .instant(false)
                        .name("store-and-forward-http")
                        .forwardUrl("http://downstream:8080/datafeed")
                        .build())
                .build();

        assertThat(ProxyCoreModule.isInstantForwarding(proxyConfig)).isFalse();

        final ReceiverFactory receiverFactory = new ProxyCoreModule().provideReceiverFactory(
                proxyConfig,
                failingProvider(),
                failingProvider(),
                () -> assembler);

        assertThat(receiverFactory).isSameAs(pipelineReceiverFactory);
    }

    @Test
    void testADisabledInstantForwarderDoesNotBypassThePipeline() {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addForwardHttpDestination(ForwardHttpPostConfig.builder()
                        .enabled(false)
                        .instant(true)
                        .name("disabled-instant")
                        .forwardUrl("http://downstream:8080/datafeed")
                        .build())
                .build();

        assertThat(ProxyCoreModule.isInstantForwarding(proxyConfig)).isFalse();
    }

    /**
     * @return a provider that fails the test if the branch under test asks for it.
     */
    private static <T> jakarta.inject.Provider<T> failingProvider() {
        return () -> {
            throw new AssertionError("This provider should not have been used on this branch");
        };
    }
}
