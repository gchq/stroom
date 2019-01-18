package stroom.config.global.impl.db;

import stroom.task.api.job.Schedule;
import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

class GlobalConfigJobsModule extends ScheduledJobsModule {
    private final Provider<GlobalConfigService> globalConfigServiceProvider;

    @Inject
    GlobalConfigJobsModule(final Provider<GlobalConfigService> globalConfigServiceProvider) {
        this.globalConfigServiceProvider = globalConfigServiceProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Property Cache Reload")
                .description("Reload properties in the cluster")
                .schedule(Schedule.ScheduleType.PERIODIC, "1m")
                .to(() -> (task) -> globalConfigServiceProvider.get().updateConfigObjects());
    }
}
