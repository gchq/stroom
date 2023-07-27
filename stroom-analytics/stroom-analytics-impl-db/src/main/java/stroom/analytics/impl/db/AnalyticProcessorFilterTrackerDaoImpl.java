package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticProcessorFilterTrackerDao;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.db.util.JooqUtil;

import org.jooq.Record;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.analytics.impl.db.jooq.tables.AnalyticProcessorFilterTracker.ANALYTIC_PROCESSOR_FILTER_TRACKER;

@Singleton
public class AnalyticProcessorFilterTrackerDaoImpl implements AnalyticProcessorFilterTrackerDao {

    private final AnalyticsDbConnProvider analyticsDbConnProvider;


    @Inject
    public AnalyticProcessorFilterTrackerDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
    }

    @Override
    public Optional<AnalyticProcessorFilterTracker> get(final String filterUuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_PROCESSOR_FILTER_TRACKER.FK_ANALYTIC_PROCESSOR_FILTER_UUID,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_MS,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_META_ID,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_ID,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_TIME,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.META_COUNT,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.EVENT_COUNT,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.MESSAGE)
                .from(ANALYTIC_PROCESSOR_FILTER_TRACKER)
                .where(ANALYTIC_PROCESSOR_FILTER_TRACKER.FK_ANALYTIC_PROCESSOR_FILTER_UUID.eq(filterUuid))
                .fetchOptional());
        return result.map(this::recordToAnalyticProcessorFilterTracker);
    }

    @Override
    public void create(final AnalyticProcessorFilterTracker tracker) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(ANALYTIC_PROCESSOR_FILTER_TRACKER,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.FK_ANALYTIC_PROCESSOR_FILTER_UUID,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_MS,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_META_ID,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_ID,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_TIME,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.META_COUNT,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.EVENT_COUNT,
                        ANALYTIC_PROCESSOR_FILTER_TRACKER.MESSAGE)
                .values(tracker.getFilterUuid(),
                        tracker.getLastPollMs(),
                        tracker.getLastPollTaskCount(),
                        tracker.getLastMetaId(),
                        tracker.getLastEventId(),
                        tracker.getLastEventTime(),
                        tracker.getMetaCount(),
                        tracker.getEventCount(),
                        tracker.getMessage())
                .execute());
    }

    @Override
    public void update(final AnalyticProcessorFilterTracker tracker) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(ANALYTIC_PROCESSOR_FILTER_TRACKER)
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, tracker.getLastPollMs())
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT, tracker.getLastPollTaskCount())
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_META_ID, tracker.getLastMetaId())
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_ID, tracker.getLastEventId())
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_TIME, tracker.getLastEventTime())
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.META_COUNT, tracker.getMetaCount())
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.EVENT_COUNT, tracker.getEventCount())
                .set(ANALYTIC_PROCESSOR_FILTER_TRACKER.MESSAGE, tracker.getMessage())
                .where(ANALYTIC_PROCESSOR_FILTER_TRACKER.FK_ANALYTIC_PROCESSOR_FILTER_UUID.eq(tracker.getFilterUuid()))
                .execute());
    }

    @Override
    public void delete(final AnalyticProcessorFilterTracker tracker) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(ANALYTIC_PROCESSOR_FILTER_TRACKER)
                .where(ANALYTIC_PROCESSOR_FILTER_TRACKER.FK_ANALYTIC_PROCESSOR_FILTER_UUID.eq(tracker.getFilterUuid()))
                .execute());
    }

    private AnalyticProcessorFilterTracker recordToAnalyticProcessorFilterTracker(final Record record) {
        return AnalyticProcessorFilterTracker.builder()
                .filterUuid(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.FK_ANALYTIC_PROCESSOR_FILTER_UUID))
                .lastPollMs(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_MS))
                .lastPollTaskCount(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT))
                .lastMetaId(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_META_ID))
                .lastEventId(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_ID))
                .lastEventTime(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.LAST_EVENT_TIME))
                .metaCount(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.META_COUNT))
                .eventCount(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.EVENT_COUNT))
                .message(record.get(ANALYTIC_PROCESSOR_FILTER_TRACKER.MESSAGE))
                .build();
    }
}
