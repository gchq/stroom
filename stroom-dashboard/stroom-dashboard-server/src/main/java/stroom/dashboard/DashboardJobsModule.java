package stroom.dashboard;

import com.google.inject.AbstractModule;
import stroom.task.api.job.ScheduledJobsBinder;

public class DashboardJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        ScheduledJobsBinder.create(binder()).bind(DashboardJobs.class);
    }
}
