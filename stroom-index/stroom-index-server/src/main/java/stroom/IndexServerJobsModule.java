package stroom;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.index.IndexJobs;
import stroom.search.SearchJobs;
import stroom.search.shard.ShardJobs;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

public class IndexServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<ScheduledJobs> jobs = Multibinder.newSetBinder(binder(), ScheduledJobs.class);
        jobs.addBinding().to(ShardJobs.class);
        jobs.addBinding().to(SearchJobs.class);
        jobs.addBinding().to(IndexJobs.class);
    }
}
