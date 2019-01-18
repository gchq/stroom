package stroom.statistics.sql;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.statistics.sql.search.SQLStatisticSearchJobs;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

public class SQLStatisticsJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        final Multibinder<ScheduledJobs> jobs = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobs.addBinding().to(SQLStatisticSearchJobs.class);
        jobs.addBinding().to(SQLStatisticsJobs.class);
    }
}
