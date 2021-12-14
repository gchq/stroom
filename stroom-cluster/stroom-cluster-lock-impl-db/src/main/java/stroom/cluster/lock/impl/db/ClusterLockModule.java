package stroom.cluster.lock.impl.db;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class ClusterLockModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(ClusterLockService.class).to(ClusterLockServiceImpl.class);
        bind(ClusterLockResource.class).to(ClusterLockResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(DbClusterLock.class);

        RestResourcesBinder.create(binder())
                .bind(ClusterLockResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(UnlockOldLocks.class, builder -> builder
                        .name("Unlock old locks")
                        .description("Every 10 minutes try and unlock/remove any locks that " +
                                "we hold that have not been refreshed by their owner for 10 minutes.")
                        .managed(false)
                        .schedule(PERIODIC, "10m"))
                .bindJobTo(KeepAlive.class, builder -> builder
                        .name("Keep alive")
                        .description("Keeps a locks alive")
                        .managed(false)
                        .schedule(PERIODIC, "1m"));
    }

    private static class UnlockOldLocks extends RunnableWrapper {

        @Inject
        UnlockOldLocks(final ClusterLockClusterHandler clusterLockClusterHandler) {
            super(clusterLockClusterHandler::unlockOldLocks);
        }
    }

    private static class KeepAlive extends RunnableWrapper {

        @Inject
        KeepAlive(final ClusterLockServiceImpl clusterLockService) {
            super(clusterLockService::keepAlive);
        }
    }
}
