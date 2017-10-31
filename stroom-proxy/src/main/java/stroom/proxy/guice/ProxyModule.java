package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import stroom.datafeed.server.RequestHandler;
import stroom.proxy.handler.ForwardStreamConfig;
import stroom.proxy.handler.ForwardStreamHandlerFactory;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.proxy.handler.LogStreamConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.proxy.handler.ProxyRequestHandler;
import stroom.util.shared.Monitor;
import stroom.util.task.MonitorImpl;

public class ProxyModule extends AbstractModule {
    private final ProxyConfig proxyConfig;

    public ProxyModule(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    protected void configure() {
        bind(LogStreamConfig.class).toProvider(proxyConfig::getLogStreamConfig);
        bind(ForwardStreamConfig.class).toProvider(proxyConfig::getForwardStreamConfig);
        bind(ProxyRepositoryConfig.class).toProvider(proxyConfig::getProxyRepositoryConfig);
        bind(ProxyRepositoryReaderConfig.class).toProvider(proxyConfig::getProxyRepositoryReaderConfig);

        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(Monitor.class).to(MonitorImpl.class);
        bind(StreamHandlerFactory.class).to(ForwardStreamHandlerFactory.class);
        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();
    }
}
