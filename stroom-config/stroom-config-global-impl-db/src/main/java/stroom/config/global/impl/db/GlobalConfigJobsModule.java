package stroom.config.global.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

public class GlobalConfigJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        final Multibinder<ScheduledJobs> jobs = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobs.addBinding().to(GlobalConfigJobs.class);
    }
}
