package stroom.data.meta.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.api.job.ScheduledJobs;

public class DataMetaJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        final Multibinder<ScheduledJobs> jobs = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobs.addBinding().to(DataMetaDbJobs.class);
    }
}
