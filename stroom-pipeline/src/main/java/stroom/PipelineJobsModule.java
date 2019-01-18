package stroom;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.pipeline.PipelineJobs;
import stroom.refdata.store.RefDataStoreJobs;
import stroom.task.api.job.ScheduledJobs;

public class PipelineJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        final Multibinder<ScheduledJobs> jobsBinder = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobsBinder.addBinding().to(RefDataStoreJobs.class);
        jobsBinder.addBinding().to(PipelineJobs.class);
    }
}
