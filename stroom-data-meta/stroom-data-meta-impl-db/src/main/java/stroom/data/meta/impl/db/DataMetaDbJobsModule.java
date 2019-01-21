package stroom.data.meta.impl.db;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

public class DataMetaDbJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Flush DataMetaDb")
                .managed(false)
                .schedule(PERIODIC, "10s")
                .to(FlushDataMetaDb.class);
        bindJob()
                .name("Data Attributes Retention")
                .description("Delete attributes older than system property stroom.meta.deleteAge")
                .schedule(PERIODIC, "1d")
                .to(DataAttributesRetention.class);
    }

    private static class FlushDataMetaDb extends TaskConsumer {
        @Inject
        FlushDataMetaDb(final MetaValueService metaValueService) {
            super(task -> metaValueService.flush());
        }
    }

    private static class DataAttributesRetention extends TaskConsumer {
        @Inject
        DataAttributesRetention(final MetaValueService metaValueService) {
            super(task -> metaValueService.deleteOldValues());
        }
    }
}
