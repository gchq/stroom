package stroom.statistics.sql;

import com.google.inject.AbstractModule;
import stroom.statistics.sql.search.SQLStatisticSearchJobs;
import stroom.task.api.job.ScheduledJobsBinder;

public class SQLStatisticsJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        ScheduledJobsBinder.create(binder())
                .bind(SQLStatisticSearchJobs.class)
                .bind(SQLStatisticsJobs.class);
    }
}
