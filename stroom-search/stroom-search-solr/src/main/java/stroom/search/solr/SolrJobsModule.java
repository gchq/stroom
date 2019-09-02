package stroom.search.solr;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;
import stroom.search.solr.search.SolrSearchResponseCreatorManager;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;
import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class SolrJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Solr Index Retention")
                .description("Logically delete indexed documents in Solr indexes based on the specified deletion query")
                .schedule(CRON, "0 2 *")
                .to(DataRetention.class);

        bindJob()
                .name("Evict expired elements")
                .schedule(PERIODIC, "10s")
                .managed(false)
                .to(EvictExpiredElements.class);

        bindJob()
                .name("Solr Index Optimise")
                .description("Optimise Solr indexes")
                .schedule(CRON, "0 3 *")
                .to(SolrIndexOptimiseExecutorJob.class);
    }

    private static class DataRetention extends TaskConsumer {
        @Inject
        DataRetention(final SolrIndexRetentionExecutor dataRetentionExecutor) {
            super(task -> dataRetentionExecutor.exec());
        }
    }

    private static class EvictExpiredElements extends TaskConsumer {
        @Inject
        EvictExpiredElements(final SolrSearchResponseCreatorManager manager) {
            super(task -> manager.evictExpiredElements());
        }
    }

    private static class SolrIndexOptimiseExecutorJob extends TaskConsumer {
        @Inject
        SolrIndexOptimiseExecutorJob(final SolrIndexOptimiseExecutor executor) {
            super(task -> executor.exec());
        }
    }
}

