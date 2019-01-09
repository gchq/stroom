package stroom.config.global.impl.db;

import stroom.util.lifecycle.jobmanagement.Schedule;
import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

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
