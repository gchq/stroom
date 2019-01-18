package stroom;

import com.google.inject.AbstractModule;
import stroom.cache.CacheJobs;
import stroom.data.store.DataStoreJobModule;
import stroom.jobsystem.JobSystemJobsModule;
import stroom.node.NodeJobs;
import stroom.policy.PolicyJobsModule;
import stroom.resource.ResourceJobs;
import stroom.streamtask.StreamTaskJobs;
import stroom.task.api.job.ScheduledJobsBinder;
import stroom.volume.VolumeJobs;

public class StroomCoreServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        ScheduledJobsBinder.create(binder())
                .bind(DataStoreJobModule.class)
                .bind(StreamTaskJobs.class)
                .bind(JobSystemJobsModule.class)
                .bind(PolicyJobsModule.class)
                .bind(CacheJobs.class)
                .bind(ResourceJobs.class)
                .bind(VolumeJobs.class)
                .bind(NodeJobs.class);
    }
}
