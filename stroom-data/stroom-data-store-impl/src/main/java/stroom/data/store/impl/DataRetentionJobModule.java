package stroom.data.store.impl;

import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class DataRetentionJobModule extends AbstractModule {
    @Override
    protected void configure() {

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DataRetention.class, builder -> builder
                        .name("Feed Based Data Retention")
                        .description("Delete data that exceeds the retention period specified by feed")
                        .schedule(CRON, "0 0 *"));
    }

    private static class DataRetention extends RunnableWrapper {
        @Inject
        DataRetention(final FeedDataRetentionExecutor feedDataRetentionExecutor) {
            super(feedDataRetentionExecutor::exec);
        }
    }
}
