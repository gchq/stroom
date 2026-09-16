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
import stroom.proxy.app.handler.Destination;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardFileDestinationFactory;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.HttpSender;
import stroom.proxy.app.handler.HttpSenderFactory;
import stroom.proxy.app.handler.InstantForwardReceiver;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.handler.Receiver;
import stroom.proxy.app.handler.RefusingReceiver;
import stroom.proxy.app.handler.StoringReceiver;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler.ReceiveWiring;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveDataConfig;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which receiver a configuration gets. Instant forwarding relays during receipt with no store and no
 * queue, so it must bypass the pipeline entirely; everything else goes through it, and a node whose
 * receive stage is disabled refuses.
 */
class TestInstantForwardingWiring {

    @TempDir
    Path dataDir;

    @Test
    void testAnInstantHttpForwarderBypassesThePipeline() {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addForwardHttpDestination(ForwardHttpPostConfig.builder()
                        .enabled(true)
                        .instant(true)
                        .name("instant-http")
                        .forwardUrl("http://downstream:8080/datafeed")
                        .build())
                .build();
        assertThat(ProxyCoreModule.isInstantForwarding(proxyConfig)).isTrue();

        final HttpSenderFactory httpSenderFactory = Mockito.mock(HttpSenderFactory.class);
        Mockito.when(httpSenderFactory.create(Mockito.any())).thenReturn(Mockito.mock(HttpSender.class));

        final Receiver receiver = provide(proxyConfig, () -> httpSenderFactory, failingProvider(), failingProvider());

        assertThat(receiver).isInstanceOf(InstantForwardReceiver.class);
    }

    @Test
    void testAnInstantFileForwarderBypassesThePipeline() {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addForwardFileDestination(ForwardFileConfig.builder()
                        .enabled()
                        .withInstant(true)
                        .withName("instant-file")
                        .withPath("forward_dest")
                        .build())
                .build();
        assertThat(ProxyCoreModule.isInstantForwarding(proxyConfig)).isTrue();

        final ForwardFileDestinationFactory destinationFactory = Mockito.mock(ForwardFileDestinationFactory.class);
        Mockito.when(destinationFactory.create(Mockito.any())).thenReturn(Mockito.mock(Destination.class));

        final Receiver receiver = provide(proxyConfig, failingProvider(), () -> destinationFactory, failingProvider());

        assertThat(receiver).isInstanceOf(InstantForwardReceiver.class);
    }

    @Test
    void testAStoreAndForwardConfigurationGetsThePipelinesReceiver() {
        final ProxyPipelineAssembler assembler = Mockito.mock(ProxyPipelineAssembler.class);
        Mockito.when(assembler.getReceiveWiring()).thenReturn(new ReceiveWiring(
                Mockito.mock(FileStore.class), Mockito.mock(FileGroupQueue.class), null));

        final Receiver receiver = provide(storeAndForward(), failingProvider(), failingProvider(), () -> assembler);

        assertThat(receiver).isInstanceOf(StoringReceiver.class);
    }

    @Test
    void testADisabledReceiveStageGetsAReceiverThatRefuses() {
        final ProxyPipelineAssembler assembler = Mockito.mock(ProxyPipelineAssembler.class);
        Mockito.when(assembler.getReceiveWiring()).thenReturn(null);

        final Receiver receiver = provide(storeAndForward(), failingProvider(), failingProvider(), () -> assembler);

        assertThat(receiver).isInstanceOf(RefusingReceiver.class);
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

    private static ProxyConfig storeAndForward() {
        return ProxyConfig.builder()
                .addForwardHttpDestination(ForwardHttpPostConfig.builder()
                        .enabled(true)
                        .instant(false)
                        .name("store-and-forward-http")
                        .forwardUrl("http://downstream:8080/datafeed")
                        .build())
                .build();
    }

    private Receiver provide(final ProxyConfig proxyConfig,
                             final Provider<HttpSenderFactory> httpSenderFactoryProvider,
                             final Provider<ForwardFileDestinationFactory> forwardFileDestinationFactoryProvider,
                             final Provider<ProxyPipelineAssembler> assemblerProvider) {
        return new ProxyCoreModule().provideReceiver(
                proxyConfig,
                new ProxyId(proxyConfig, () -> dataDir),
                () -> dataDir,
                Mockito.mock(AttributeMapFilterFactory.class),
                () -> ReceiveDataConfig.builder().build(),
                Mockito.mock(LogStream.class),
                httpSenderFactoryProvider,
                forwardFileDestinationFactoryProvider,
                assemblerProvider);
    }

    /**
     * @return a provider that fails the test if the branch under test asks for it.
     */
    private static <T> Provider<T> failingProvider() {
        return () -> {
            throw new AssertionError("This provider should not have been used on this branch");
        };
    }
}
