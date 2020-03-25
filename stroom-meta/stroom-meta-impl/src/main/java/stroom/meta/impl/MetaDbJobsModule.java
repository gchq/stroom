package stroom.meta.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class MetaDbJobsModule extends ScheduledJobsModule {
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

    private static class FlushDataMetaDb extends TaskRunnable {
        @Inject
        FlushDataMetaDb(final MetaValueDao metaValueService) {
            super(metaValueService::flush);
        }
    }

    private static class DataAttributesRetention extends TaskRunnable {
        @Inject
        DataAttributesRetention(final MetaValueDao metaValueService) {
            super(metaValueService::deleteOldValues);
        }
    }
}
