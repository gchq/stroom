/*
 * Copyright 2023 Crown Copyright
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

import stroom.aws.s3.client.S3ClientModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.DocumentResourceHelperImpl;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.dao.MockDocDependencyService;
import stroom.docstore.impl.fs.FSPersistence;
import stroom.dropwizard.common.DropwizardHttpClientFactory;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.DataDirProviderImpl;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.RemoteReceiveDataRuleSetServiceImpl;
import stroom.proxy.app.cache.ProxyCacheServiceModule;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.handler.DirNames;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardFileDestinationFactory;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.ForwardS3Config;
import stroom.proxy.app.handler.ForwarderConfig;
import stroom.proxy.app.handler.HttpSenderFactory;
import stroom.proxy.app.handler.InstantForwardReceiver;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.handler.ProxyReceiptIdGenerator;
import stroom.proxy.app.handler.ProxyRequestHandler;
import stroom.proxy.app.handler.ProxyS3EventConsumer;
import stroom.proxy.app.handler.Receiver;
import stroom.proxy.app.handler.RefusingReceiver;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.handler.StoringReceiver;
import stroom.proxy.app.jersey.ProxyJerseyModule;
import stroom.proxy.app.pipeline.monitor.PipelineMetricsRegistrar;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;
import stroom.proxy.app.pipeline.stage.forward.ForwardDestinations;
import stroom.proxy.app.security.ProxySecurityModule;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProgressLogImpl;
import stroom.proxy.repo.queue.QueueModule;
import stroom.proxy.repo.store.StoreModule;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.CertificateExtractorImpl;
import stroom.receive.common.ContentAutoCreationAttrMapFilterFactory;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactoryImpl;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.ReceiveAllAttributeMapFilter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.ReceiveDataRuleSetService;
import stroom.receive.common.RemoteFeedModule;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.S3EventConsumer;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.impl.TaskContextModule;
import stroom.util.BuildInfoProvider;
import stroom.util.cert.CertificateExtractor;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.http.HttpClientFactory;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.BuildInfo;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jakarta.inject.Provider;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class ProxyCoreModule extends AbstractModule {


    @Override
    protected void configure() {
//        install(new DictionaryModule());
        // Allow discovery of feed status from other proxies.
        install(new RemoteFeedModule());

        install(new TaskContextModule());
        install(new ProxyJerseyModule());
        install(new ProxySecurityModule());
        install(new MockCollectionModule());
        install(new ProxyCacheServiceModule());
        install(new QueueModule());
        install(new StoreModule());
        install(new S3ClientModule());

        bind(ProxyId.class).asEagerSingleton();
        bind(ReceiptIdGenerator.class).to(ProxyReceiptIdGenerator.class).asEagerSingleton();
        bind(BuildInfo.class).toProvider(BuildInfoProvider.class);
        bind(HttpClientFactory.class).to(DropwizardHttpClientFactory.class);
        // Proxy doesn't do content auto-creation
        bind(ContentAutoCreationAttrMapFilterFactory.class)
                .toInstance(ReceiveAllAttributeMapFilter::getInstance);
        bind(DataReceiptPolicyAttributeMapFilterFactory.class).to(DataReceiptPolicyAttributeMapFilterFactoryImpl.class);
        bind(DocumentResourceHelper.class).to(DocumentResourceHelperImpl.class);
        bind(FeedStatusService.class).to(RemoteFeedStatusService.class);
        bind(CertificateExtractor.class).to(CertificateExtractorImpl.class);
        // Proxy binds to the remote impl
        bind(ReceiveDataRuleSetService.class).to(RemoteReceiveDataRuleSetServiceImpl.class);
        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(SecurityContext.class).to(MockSecurityContext.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(DocDependencyService.class).to(MockDocDependencyService.class);
        bind(DataDirProvider.class).to(DataDirProviderImpl.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);
        bind(S3EventConsumer.class).to(ProxyS3EventConsumer.class);
    }

    /**
     * The one {@link Receiver} every entry point calls. With an instant forwarder configured it relays
     * straight to that destination and the pipeline is never assembled; otherwise it is the pipeline's
     * storing receiver, or a refusing one on a node whose receive stage is disabled
     * ({@code designs/stages/receive.md}).
     */
    @SuppressWarnings("unused")
    @Provides
    @Singleton
    Receiver provideReceiver(final ProxyConfig proxyConfig,
                             final ProxyId proxyId,
                             final DataDirProvider dataDirProvider,
                             final AttributeMapFilterFactory attributeMapFilterFactory,
                             final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                             final LogStream logStream,
                             final Provider<HttpSenderFactory> httpSenderFactoryProvider,
                             final Provider<ForwardFileDestinationFactory> forwardFileDestinationFactoryProvider,
                             final Provider<ProxyPipelineAssembler> pipelineAssemblerProvider) {
        final Path receivingDir = dataDirProvider.get().resolve(DirNames.RECEIVING);

        final List<ForwarderConfig> instantForwarders = proxyConfig.streamAllEnabledForwarders()
                .filter(ForwarderConfig::isInstant)
                .toList();
        if (!instantForwarders.isEmpty()) {
            // Instant forwarding bypasses the pipeline: no store write, no queue, and the sender is
            // not told the receipt succeeded until the downstream has accepted it. The sender owns
            // the retry, which is the point of the mode. ProxyConfig.isInstantForwardingValid has
            // already required exactly one enabled forwarder; this is the last line of defence,
            // because relaying to one of several would lose the rest.
            if (instantForwarders.size() != 1) {
                throw new RuntimeException(LogUtil.message(
                        "Expecting exactly one enabled instant forwarder but found {}. Instant "
                        + "forwarding cannot be combined with other forward destinations.",
                        instantForwarders.size()));
            }
            return switch (instantForwarders.getFirst()) {
                case final ForwardHttpPostConfig config -> InstantForwardReceiver.toHttp(
                        httpSenderFactoryProvider.get().create(config),
                        attributeMapFilterFactory,
                        logStream);
                case final ForwardFileConfig config -> InstantForwardReceiver.toFile(
                        forwardFileDestinationFactoryProvider.get().create(config),
                        receivingDir,
                        attributeMapFilterFactory,
                        logStream);
                case final ForwardS3Config config -> throw new IllegalStateException(LogUtil.message(
                        "Instant forwarding is not supported for S3 destination '{}'. This should be "
                        + "unreachable: ForwardS3Config's constructor rejects instant, so an instant "
                        + "S3 forwarder cannot be built. The sealed switch needs the case regardless.",
                        config.getName()));
            };
        }

        final ProxyPipelineAssembler.ReceiveWiring wiring = pipelineAssemblerProvider.get().getReceiveWiring();
        if (wiring == null) {
            return new RefusingReceiver();
        }
        return new StoringReceiver(
                wiring.store(),
                wiring.outputQueue(),
                wiring.splitZipQueue(),
                proxyId.getId(),
                attributeMapFilterFactory,
                receiveDataConfigProvider,
                logStream,
                receivingDir);
    }

    /**
     * @return True if any enabled forwarder is configured for instant forwarding, in which case the
     * pipeline is not assembled or started at all - see {@link #provideReceiver}.
     */
    public static boolean isInstantForwarding(final ProxyConfig proxyConfig) {
        return proxyConfig.streamAllEnabledForwarders()
                .anyMatch(ForwarderConfig::isInstant);
    }

    @SuppressWarnings("unused")
    @Provides
    @Singleton
    ProxyPipelineAssembler provideProxyPipelineAssembler(final ProxyConfig proxyConfig,
                                                         final ProxyId proxyId,
                                                         final ForwardDestinations forwardDestinations,
                                                         final PathCreator pathCreator,
                                                         final MetricRegistry metricRegistry,
                                                         final Metrics metrics,
                                                         final WorkRegistry workRegistry) {
        final ProxyPipelineAssembler assembler = new ProxyPipelineAssembler(
                proxyConfig.getPipelineConfig(),
                proxyId,
                forwardDestinations.getWirings(),
                pathCreator,
                proxyConfig.getDurability(),
                metrics,
                workRegistry);

        // Register pipeline metrics after assembly.
        PipelineMetricsRegistrar.register(assembler.getRuntime(), metricRegistry);

        return assembler;
    }

    @SuppressWarnings("unused")
    @Provides
    @Singleton
    Persistence providePersistence(final PathCreator pathCreator, final ProxyConfig proxyConfig) {
        final String path = proxyConfig.getContentDir();
        return new FSPersistence(pathCreator.toAppPath(path));
    }

    @SuppressWarnings("unused")
    @Provides
    EntityEventBus entityEventBus() {
        return EntityEventBus.NO_OP_EVENT_BUS;
    }

    @Provides
    DocFinder docFinder() {
        return new DocFinder() {
            @Override
            public List<DocRef> findByName(final String type, final String nameFilter, final boolean allowWildCards) {
                return List.of();
            }

            @Override
            public List<DocRef> findByNames(final String type,
                                            final List<String> nameFilters,
                                            final boolean allowWildCards) {
                return List.of();
            }

            @Override
            public Optional<String> getName(final DocRef docRef) {
                if (docRef == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(docRef.getName());
            }
        };
    }
}
