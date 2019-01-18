package stroom.dashboard;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.api.job.ScheduledJobs;

public class DashboardJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<ScheduledJobs> jobsBinder = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobsBinder.addBinding().to(DashboardJobs.class);
    }
}
