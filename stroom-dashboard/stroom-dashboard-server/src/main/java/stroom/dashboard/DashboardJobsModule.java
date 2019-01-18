package stroom.dashboard;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

public class DashboardJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<ScheduledJobs> jobsBinder = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobsBinder.addBinding().to(DashboardJobs.class);
    }
}
