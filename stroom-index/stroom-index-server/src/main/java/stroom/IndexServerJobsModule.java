package stroom;

import com.google.inject.AbstractModule;
import stroom.index.IndexJobs;
import stroom.search.SearchJobs;
import stroom.search.shard.ShardJobs;
import stroom.task.api.job.ScheduledJobsBinder;

public class IndexServerJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        ScheduledJobsBinder.create(binder())
                .bind(ShardJobs.class)
                .bind(SearchJobs.class)
                .bind(IndexJobs.class);
    }
}
