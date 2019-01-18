package stroom;

import com.google.inject.AbstractModule;
import stroom.pipeline.PipelineJobs;
import stroom.refdata.store.RefDataStoreJobs;
import stroom.task.api.job.ScheduledJobsBinder;

public class PipelineJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        ScheduledJobsBinder.create(binder())
                .bind(RefDataStoreJobs.class)
                .bind(PipelineJobs.class);
    }
}
