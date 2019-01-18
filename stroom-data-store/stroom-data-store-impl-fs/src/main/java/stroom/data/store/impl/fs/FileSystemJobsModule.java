package stroom.data.store.impl.fs;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.api.job.ScheduledJobs;

public class FileSystemJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        final Multibinder<ScheduledJobs> jobs = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobs.addBinding().to(FileSystemDataStoreJobs.class);
    }
}
