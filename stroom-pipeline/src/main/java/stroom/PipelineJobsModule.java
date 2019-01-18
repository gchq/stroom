package stroom;

import com.google.inject.AbstractModule;
import stroom.pipeline.PipelineJobs;
import stroom.pipeline.destination.RollingDestinations;
import stroom.refdata.store.RefDataStoreJobs;
import stroom.task.api.job.ScheduledJobsBinder;
import stroom.util.lifecycle.LifecycleAwareBinder;

public class PipelineJobsModule extends AbstractModule {
    @Override
    protected void configure(){
        ScheduledJobsBinder.create(binder())
                .bind(RefDataStoreJobs.class)
                .bind(PipelineJobs.class);

        LifecycleAwareBinder.create(binder()).bind(RollingDestinations.class);
    }
}
