package stroom.data.store.impl;

import stroom.util.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class DataRetentionJobModule extends AbstractModule {
    @Override
    protected void configure() {

        ScheduledJobsBinder.create(binder())
                .bindJobTo(DataRetention.class, builder -> builder
                        .withName("Feed Based Data Retention")
                        .withDescription("Delete data that exceeds the retention period specified by feed")
                        .withSchedule(CRON, "0 0 *"));
    }

    private static class DataRetention extends RunnableWrapper {
        @Inject
        DataRetention(final FeedDataRetentionExecutor feedDataRetentionExecutor) {
            super(feedDataRetentionExecutor::exec);
        }
    }
}
