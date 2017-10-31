package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import stroom.proxy.handler.ForwardRequestConfig;
import stroom.proxy.handler.ForwardRequestHandlerFactory;
import stroom.proxy.handler.HandlerFactory;
import stroom.proxy.handler.LogRequestConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.util.shared.Monitor;
import stroom.util.task.MonitorImpl;

public class ProxyModule extends AbstractModule {
    private final ProxyConfig proxyConfig;

    public ProxyModule(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    protected void configure() {
        bind(LogRequestConfig.class).toProvider(proxyConfig::getLogRequestConfig);
        bind(ForwardRequestConfig.class).toProvider(proxyConfig::getForwardRequestConfig);
        bind(ProxyRepositoryConfig.class).toProvider(proxyConfig::getProxyRepositoryConfig);
        bind(ProxyRepositoryReaderConfig.class).toProvider(proxyConfig::getProxyRepositoryReaderConfig);

        bind(Monitor.class).to(MonitorImpl.class);
        bind(HandlerFactory.class).to(ForwardRequestHandlerFactory.class);
        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();
    }
}
