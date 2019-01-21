package stroom.config.global.impl.db;

import stroom.task.api.job.Schedule;
import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

public class GlobalConfigJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Property Cache Reload")
                .description("Reload properties in the cluster")
                .schedule(Schedule.ScheduleType.PERIODIC, "1m")
                .to(PropertyCacheReload.class);
    }

    private static class PropertyCacheReload extends TaskConsumer {
        @Inject
        PropertyCacheReload(final GlobalConfigService globalConfigService) {
            super(task -> globalConfigService.updateConfigObjects());
        }
    }
}
