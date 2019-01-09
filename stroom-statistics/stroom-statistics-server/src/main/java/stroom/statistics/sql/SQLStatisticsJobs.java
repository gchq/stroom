package stroom.statistics.sql;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class SQLStatisticsJobs implements ScheduledJobs {

    private SQLStatisticEventStore sqlStatisticEventStore;

    @Inject
    public SQLStatisticsJobs(SQLStatisticEventStore sqlStatisticEventStore) {
        this.sqlStatisticEventStore = sqlStatisticEventStore;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Evict from object pool")
                        .description("Evict from SQL Statistics event store object pool")
                        .managed(false)
                        .schedule(PERIODIC, "1m")
                        .method((task) -> this.sqlStatisticEventStore.evict())
                        .build()
        );
    }
}
