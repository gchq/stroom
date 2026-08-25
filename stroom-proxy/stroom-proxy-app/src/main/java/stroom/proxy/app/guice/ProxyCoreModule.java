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
import stroom.proxy.app.event.EventStoreModule;
import stroom.proxy.app.handler.Aggregator;
import stroom.proxy.app.handler.Forwarder;
import stroom.proxy.app.handler.PreAggregator;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.handler.ProxyReceiptIdGenerator;
import stroom.proxy.app.handler.ProxyRequestHandler;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.handler.SimpleReceiver;
import stroom.proxy.app.handler.ZipReceiver;
import stroom.proxy.app.jersey.ProxyJerseyModule;
import stroom.proxy.app.pipeline.monitor.PipelineMetricsRegistrar;
import stroom.proxy.app.pipeline.runtime.ProxyPipelineAssembler;
import stroom.proxy.app.security.ProxySecurityModule;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProgressLogImpl;
import stroom.proxy.repo.queue.QueueModule;
import stroom.proxy.repo.store.StoreModule;
import stroom.receive.common.CertificateExtractorImpl;
import stroom.receive.common.ContentAutoCreationAttrMapFilterFactory;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactoryImpl;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.ReceiveAllAttributeMapFilter;
import stroom.proxy.app.handler.ForwarderConfig;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.InstantForwardFile;
import stroom.proxy.app.handler.InstantForwardHttpPost;
import stroom.receive.common.ReceiveDataRuleSetService;
import stroom.receive.common.RemoteFeedModule;
import stroom.receive.common.RequestHandler;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.impl.TaskContextModule;
import stroom.util.BuildInfoProvider;
import stroom.util.cert.CertificateExtractor;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.http.HttpClientFactory;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.BuildInfo;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Optional;

public class ProxyCoreModule extends AbstractModule {


    @Override
    protected void configure() {
//        install(new DictionaryModule());
        // Allow discovery of feed status from other proxies.
        install(new RemoteFeedModule());

        install(new EventStoreModule());
        install(new TaskContextModule());
        install(new ProxyJerseyModule());
        install(new ProxySecurityModule());
        install(new MockCollectionModule());
        install(new ProxyCacheServiceModule());
        install(new QueueModule());
        install(new StoreModule());

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
    }

    /**
     * Provides the {@link ReceiverFactory} for HTTP data ingest, backed by
     * the pluggable queue pipeline via {@link ProxyPipelineAssembler}.
     */
    @SuppressWarnings("unused")
    @Provides
    @Singleton
    ReceiverFactory provideReceiverFactory(
            final ProxyConfig proxyConfig,
            final Provider<InstantForwardHttpPost> instantForwardHttpPostProvider,
            final Provider<InstantForwardFile> instantForwardFileProvider,
            final Provider<ProxyPipelineAssembler> pipelineAssemblerProvider) {

        final List<ForwarderConfig> instantForwarders = proxyConfig.streamAllEnabledForwarders()
                .filter(ForwarderConfig::isInstant)
                .toList();

        if (!instantForwarders.isEmpty()) {
            // Instant forwarding deliberately bypasses the pipeline: data is relayed straight to the
            // destination with no store write and no queue publish, and the sender is not told the
            // receipt succeeded until the downstream has accepted it. The sender therefore owns the
            // retry, which is the point of the mode - it is outside the pipeline's at-least-once
            // guarantee by design. ProxyConfig.isInstantForwardingValid guarantees there is exactly
            // one enabled forwarder when any of them is instant.
            if (instantForwarders.size() != 1) {
                // ProxyConfig.isInstantForwardingValid says the same thing, but validation can be
                // bypassed with haltBootOnConfigValidationFailure: false, and silently forwarding to
                // one of several configured destinations would lose the rest.
                throw new RuntimeException(LogUtil.message(
                        "Expecting exactly one enabled instant forwarder but found {}. Instant "
                        + "forwarding cannot be combined with other forward destinations.",
                        instantForwarders.size()));
            }
            final ForwarderConfig forwarderConfig = instantForwarders.getFirst();
            return switch (forwarderConfig) {
                case final ForwardHttpPostConfig config -> instantForwardHttpPostProvider.get().get(config);
                case final ForwardFileConfig config -> instantForwardFileProvider.get().get(config);
            };
        }

        return pipelineAssemblerProvider.get().getReceiverFactory();
    }

    /**
     * @return True if any enabled forwarder is configured for instant forwarding, in which case the
     * pipeline is not assembled or started at all - see {@link #provideReceiverFactory}.
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
                                                         final PreAggregator preAggregator,
                                                         final Aggregator aggregator,
                                                         final Forwarder forwarder,
                                                         final SimpleReceiver simpleReceiver,
                                                         final ZipReceiver zipReceiver,
                                                         final PathCreator pathCreator,
                                                         final TempDirProvider tempDirProvider,
                                                         final MetricRegistry metricRegistry) {
        final ProxyPipelineAssembler assembler = new ProxyPipelineAssembler(
                proxyConfig.getPipelineConfig(),
                proxyId,
                preAggregator,
                aggregator,
                forwarder,
                simpleReceiver,
                zipReceiver,
                pathCreator,
                tempDirProvider);

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
