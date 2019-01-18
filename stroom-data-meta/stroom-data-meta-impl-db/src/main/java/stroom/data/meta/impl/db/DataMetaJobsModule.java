package stroom.data.meta.impl.db;

import com.google.inject.AbstractModule;
import stroom.task.api.job.ScheduledJobsBinder;

public class DataMetaJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        ScheduledJobsBinder.create(binder()).bind(DataMetaDbJobs.class);
    }
}
