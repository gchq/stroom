package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import stroom.proxy.handler.LogRequestConfig;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;

public class ProxyStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(LogRequestConfig.class);

        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();
    }
}
