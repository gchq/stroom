package stroom.config.global.impl.db;

import stroom.task.api.job.Schedule;
import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class GlobalConfigJobs implements ScheduledJobs {
    private GlobalConfigService globalConfigService;

    @Inject
    public GlobalConfigJobs(GlobalConfigService globalConfigService){
        this.globalConfigService = globalConfigService;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
            jobBuilder()
                    .name("Property Cache Reload")
                .description("Reload properties in the cluster")
                .schedule(Schedule.ScheduleType.PERIODIC, "1m")
                .method((task) -> this.globalConfigService.updateConfigObjects())
                .build()
        );
    }
}
