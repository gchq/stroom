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

package stroom.proxy.app.guice;

import stroom.collection.mock.MockCollectionModule;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.DocumentResourceHelperImpl;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
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
import stroom.proxy.app.handler.ReceiverFactoryProvider;
import stroom.proxy.app.handler.SimpleReceiver;
import stroom.proxy.app.handler.ZipReceiver;
import stroom.proxy.app.pipeline.ProxyPipelineAssembler;
import stroom.proxy.app.pipeline.ProxyPipelineConfig;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.jersey.ProxyJerseyModule;
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
import stroom.util.shared.BuildInfo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jakarta.inject.Provider;

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
        bind(DocRefDecorator.class).to(NoDecorationDocRefDecorator.class);
        bind(DataDirProvider.class).to(DataDirProviderImpl.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);
    }

    /**
     * Conditionally provides the {@link ReceiverFactory} for HTTP data ingest.
     * <ul>
     *     <li>When {@code pipeline.enabled=true}: delegates to the new
     *         {@link ProxyPipelineAssembler}, which wires production handlers
     *         to the pluggable queue pipeline.</li>
     *     <li>When {@code pipeline.enabled=false} (default): uses the legacy
     *         {@link ReceiverFactoryProvider} backed by DirQueue.</li>
     * </ul>
     * <p>
     * The legacy path should be removed once the pluggable pipeline
     * architecture has been validated in production.
     * </p>
     */
    @SuppressWarnings("unused")
    @Provides
    @Singleton
    ReceiverFactory provideReceiverFactory(final ProxyConfig proxyConfig,
                                           final ReceiverFactoryProvider legacyProvider,
                                           final Provider<ProxyPipelineAssembler> pipelineAssemblerProvider) {
        if (proxyConfig.getPipelineConfig().isEnabled()) {
            return pipelineAssemblerProvider.get().getReceiverFactory();
        }
        return legacyProvider.get();
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
                                                         final PathCreator pathCreator) {
        return new ProxyPipelineAssembler(
                proxyConfig.getPipelineConfig(),
                proxyId,
                preAggregator,
                aggregator,
                forwarder,
                simpleReceiver,
                zipReceiver,
                pathCreator);
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
}
