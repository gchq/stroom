package stroom;

import com.google.inject.AbstractModule;
import stroom.cache.CacheJobs;
import stroom.data.store.DataStoreJobs;
import stroom.jobsystem.JobSystemJobs;
import stroom.node.NodeJobs;
import stroom.policy.PolicyJobs;
import stroom.resource.ResourceJobs;
import stroom.streamtask.StreamTaskJobs;
import stroom.util.lifecycle.jobmanagement.ScheduledJobsBinder;
import stroom.volume.VolumeJobs;

public class StroomCoreServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        ScheduledJobsBinder.create(binder())
                .bind(DataStoreJobs.class)
                .bind(StreamTaskJobs.class)
                .bind(JobSystemJobs.class)
                .bind(PolicyJobs.class)
                .bind(CacheJobs.class)
                .bind(ResourceJobs.class)
                .bind(VolumeJobs.class)
                .bind(NodeJobs.class);
    }
}
