package stroom.data.meta.impl.db;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class DataMetaDbJobs implements ScheduledJobs {

    private MetaValueService metaValueService;

    @Inject
    public DataMetaDbJobs(MetaValueService metaValueService) {
        this.metaValueService = metaValueService;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Flush DataMetaDb")
                        .managed(false)
                        .method((task) -> this.metaValueService.flush())
                        .schedule(PERIODIC, "10s")
                        .build(),
                jobBuilder()
                        .name("Data Attributes Retention")
                        .description("Delete attributes older than system property stroom.meta.deleteAge")
                        .method((task) -> this.metaValueService.deleteOldValues())
                        .schedule(PERIODIC, "1d")
                        .build()
        );
    }
}
