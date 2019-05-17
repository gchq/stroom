package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import stroom.content.ContentSyncConfig;
import stroom.datafeed.server.DataReceiptPolicyMetaMapFilterFactory;
import stroom.datafeed.server.RequestHandler;
import stroom.dictionary.server.DictionaryStore;
import stroom.dictionary.server.DictionaryStoreImpl;
import stroom.docstore.server.Persistence;
import stroom.docstore.server.fs.FSPersistence;
import stroom.feed.server.FeedStatusService;
import stroom.proxy.handler.FeedStatusConfig;
import stroom.proxy.handler.ForwardStreamConfig;
import stroom.proxy.handler.ForwardStreamHandlerFactory;
import stroom.proxy.handler.LogStreamConfig;
import stroom.proxy.handler.ProxyRequestConfig;
import stroom.proxy.handler.ProxyRequestHandler;
import stroom.proxy.handler.RemoteFeedStatusService;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.ruleset.server.DataReceiptPolicyMetaMapFilterFactoryImpl;
import stroom.ruleset.server.RuleSetService;
import stroom.ruleset.server.RuleSetServiceImpl;
import stroom.security.SecurityContext;
import stroom.util.shared.Monitor;
import stroom.util.task.MonitorImpl;

import javax.ws.rs.client.Client;
import java.nio.file.Paths;

public class ProxyModule extends AbstractModule {
    private final ProxyConfig proxyConfig;
    private final Client jerseyClient;

    public ProxyModule(final ProxyConfig proxyConfig,
                       final Client jerseyClient) {
        this.proxyConfig = proxyConfig;
        this.jerseyClient = jerseyClient;
    }

    @Override
    protected void configure() {
        // Bind the application config.
        bind(ProxyConfig.class).toInstance(this.proxyConfig);
        // Bind the dropwizard managed jersey client
        bind(Client.class).toInstance(this.jerseyClient);

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own
        bind(ProxyRequestConfig.class).toInstance(proxyConfig.getProxyRequestConfig());
        bind(LogStreamConfig.class).toInstance(proxyConfig.getLogStreamConfig());
        bind(ForwardStreamConfig.class).toInstance(proxyConfig.getForwardStreamConfig());
        bind(ProxyRepositoryConfig.class).toInstance(proxyConfig.getProxyRepositoryConfig());
        bind(ProxyRepositoryReaderConfig.class).toInstance(proxyConfig.getProxyRepositoryReaderConfig());
        bind(ContentSyncConfig.class).toInstance(proxyConfig.getContentSyncConfig());
        bind(FeedStatusConfig.class).toInstance(proxyConfig.getFeedStatusConfig());

        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(Monitor.class).to(MonitorImpl.class);
        bind(StreamHandlerFactory.class).to(ForwardStreamHandlerFactory.class);
        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();

        bind(DataReceiptPolicyMetaMapFilterFactory.class).to(DataReceiptPolicyMetaMapFilterFactoryImpl.class);
        bind(RuleSetService.class).to(RuleSetServiceImpl.class).in(Singleton.class);
        bind(DictionaryStore.class).to(DictionaryStoreImpl.class).in(Singleton.class);
        bind(FeedStatusService.class).to(RemoteFeedStatusService.class);
        bind(SecurityContext.class).to(NoSecurityContext.class);
    }

    @Provides @Singleton
    Persistence providePersistence() {
        return new FSPersistence(Paths.get(proxyConfig.getProxyContentDir()));
    }
}
