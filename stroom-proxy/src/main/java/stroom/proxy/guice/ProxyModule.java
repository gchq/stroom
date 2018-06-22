package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import stroom.content.ContentSyncConfig;
import stroom.datafeed.server.MetaMapFilterFactory;
import stroom.datafeed.server.RequestHandler;
import stroom.dictionary.server.DictionaryStore;
import stroom.dictionary.server.DictionaryStoreImpl;
import stroom.docstore.server.Persistence;
import stroom.docstore.server.Store;
import stroom.docstore.server.fs.FSPersistence;
import stroom.proxy.handler.ForwardStreamConfig;
import stroom.proxy.handler.ForwardStreamHandlerFactory;
import stroom.proxy.handler.LogStreamConfig;
import stroom.proxy.handler.ProxyRequestConfig;
import stroom.proxy.handler.ProxyRequestHandler;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.ruleset.server.MetaMapFilterFactoryImpl;
import stroom.ruleset.server.RuleSetService;
import stroom.ruleset.server.RuleSetServiceImpl;
import stroom.security.SecurityContext;
import stroom.util.shared.Monitor;
import stroom.util.task.MonitorImpl;

import java.nio.file.Paths;

public class ProxyModule extends AbstractModule {
    private final ProxyConfig proxyConfig;

    public ProxyModule(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    protected void configure() {
        bind(ProxyRequestConfig.class).toProvider(proxyConfig::getProxyRequestConfig);
        bind(LogStreamConfig.class).toProvider(proxyConfig::getLogStreamConfig);
        bind(ForwardStreamConfig.class).toProvider(proxyConfig::getForwardStreamConfig);
        bind(ProxyRepositoryConfig.class).toProvider(proxyConfig::getProxyRepositoryConfig);
        bind(ProxyRepositoryReaderConfig.class).toProvider(proxyConfig::getProxyRepositoryReaderConfig);
        bind(ContentSyncConfig.class).toProvider(proxyConfig::getContentSyncConfig);

        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(Monitor.class).to(MonitorImpl.class);
        bind(StreamHandlerFactory.class).to(ForwardStreamHandlerFactory.class);
        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();

        bind(MetaMapFilterFactory.class).to(MetaMapFilterFactoryImpl.class);
        bind(RuleSetService.class).to(RuleSetServiceImpl.class).in(Singleton.class);
        bind(DictionaryStore.class).to(DictionaryStoreImpl.class).in(Singleton.class);
        bind(SecurityContext.class).to(NoSecurityContext.class);
    }

    @Provides @Singleton
    Persistence providePersistence() {
        return new FSPersistence(Paths.get(proxyConfig.getProxyConfigDir()));
    }
}
