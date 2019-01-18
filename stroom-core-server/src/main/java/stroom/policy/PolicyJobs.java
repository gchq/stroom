package stroom.policy;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.CRON;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class PolicyJobs implements ScheduledJobs {
    private DataRetentionExecutor dataRetentionExecutor;

    @Inject
    public PolicyJobs(DataRetentionExecutor dataRetentionExecutor){
        this.dataRetentionExecutor = dataRetentionExecutor;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                .name("Data Retention")
                .description("Delete data that exceeds the retention period specified by data retention policy")
                .schedule(CRON, "0 0 *")
                .method((task) -> this.dataRetentionExecutor.exec())
                .build()
        );
    }
}
