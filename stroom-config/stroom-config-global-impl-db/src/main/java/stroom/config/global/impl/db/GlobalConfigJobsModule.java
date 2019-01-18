package stroom.config.global.impl.db;

import com.google.inject.AbstractModule;
import stroom.task.api.job.ScheduledJobsBinder;

public class GlobalConfigJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        ScheduledJobsBinder.create(binder()).bind(GlobalConfigJobs.class);
    }
}
