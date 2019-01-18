package stroom;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.cache.CacheJobs;
import stroom.data.store.DataStoreJobs;
import stroom.jobsystem.JobSystemJobs;
import stroom.node.NodeJobs;
import stroom.policy.PolicyJobs;
import stroom.resource.ResourceJobs;
import stroom.streamtask.StreamTaskJobs;
import stroom.task.api.job.ScheduledJobs;
import stroom.volume.VolumeJobs;

public class StroomCoreServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<ScheduledJobs> jobs = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobs.addBinding().to(DataStoreJobs.class);
        jobs.addBinding().to(StreamTaskJobs.class);
        jobs.addBinding().to(JobSystemJobs.class);
        jobs.addBinding().to(PolicyJobs.class);
        jobs.addBinding().to(CacheJobs.class);
        jobs.addBinding().to(ResourceJobs.class);
        jobs.addBinding().to(VolumeJobs.class);
        jobs.addBinding().to(NodeJobs.class);
    }
}
