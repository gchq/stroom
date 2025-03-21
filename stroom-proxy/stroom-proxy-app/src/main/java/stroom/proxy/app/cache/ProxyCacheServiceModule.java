package stroom.proxy.app.cache;

import stroom.cache.impl.CacheModule;
import stroom.util.HasAdminTasks;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;

public class ProxyCacheServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new CacheModule());

        bind(ProxyCacheService.class).to(ProxyCacheServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), HasAdminTasks.class)
                .addBinding(ProxyCacheServiceImpl.class);
    }
}
