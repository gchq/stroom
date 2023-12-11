package stroom.proxy.repo.dao.lmdb;

import com.google.inject.AbstractModule;

public class ProxyLmdbModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        bind(LmdbEnv.class).toProvider(LmdbEnvProvider.class);
    }
}
