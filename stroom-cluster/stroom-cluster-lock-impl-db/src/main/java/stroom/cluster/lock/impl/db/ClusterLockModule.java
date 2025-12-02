package stroom.cluster.lock.impl.db;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class ClusterLockModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ClusterLockService.class).to(ClusterLockServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(DbClusterLock.class);
    }
}
