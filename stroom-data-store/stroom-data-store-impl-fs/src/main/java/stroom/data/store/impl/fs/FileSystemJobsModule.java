package stroom.data.store.impl.fs;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.task.api.job.ScheduledJobs;
import stroom.task.api.job.ScheduledJobsBinder;

public class FileSystemJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        ScheduledJobsBinder.create(binder()).bind(FileSystemDataStoreJobs.class);
    }
}
