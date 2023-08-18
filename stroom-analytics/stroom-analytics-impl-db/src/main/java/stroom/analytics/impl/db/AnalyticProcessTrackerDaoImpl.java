package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticProcessTrackerDao;
import stroom.analytics.shared.AnalyticProcessTracker;
import stroom.analytics.shared.AnalyticProcessTrackerData;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jooq.Record;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.analytics.impl.db.jooq.tables.AnalyticProcessTracker.ANALYTIC_PROCESS_TRACKER;

@Singleton
public class AnalyticProcessTrackerDaoImpl implements AnalyticProcessTrackerDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticProcessTrackerDaoImpl.class);

    private final AnalyticsDbConnProvider analyticsDbConnProvider;
    private final ObjectMapper mapper;

    @Inject
    public AnalyticProcessTrackerDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    @Override
    public Optional<AnalyticProcessTracker> get(final String processUuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_PROCESS_TRACKER.FK_ANALYTIC_PROCESS_UUID,
                        ANALYTIC_PROCESS_TRACKER.DATA)
                .from(ANALYTIC_PROCESS_TRACKER)
                .where(ANALYTIC_PROCESS_TRACKER.FK_ANALYTIC_PROCESS_UUID.eq(processUuid))
                .fetchOptional());
        return result.map(this::recordToAnalyticProcessorFilterTracker);
    }

    @Override
    public void create(final AnalyticProcessTracker analyticProcessTracker) {
        final String data = serialise(analyticProcessTracker);
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(ANALYTIC_PROCESS_TRACKER,
                        ANALYTIC_PROCESS_TRACKER.FK_ANALYTIC_PROCESS_UUID,
                        ANALYTIC_PROCESS_TRACKER.DATA)
                .values(analyticProcessTracker.getFilterUuid(),
                        data)
                .execute());
    }

    @Override
    public void update(final AnalyticProcessTracker tracker) {
        final String data = serialise(tracker);
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(ANALYTIC_PROCESS_TRACKER)
                .set(ANALYTIC_PROCESS_TRACKER.DATA, data)
                .where(ANALYTIC_PROCESS_TRACKER.FK_ANALYTIC_PROCESS_UUID.eq(tracker.getFilterUuid()))
                .execute());
    }

    @Override
    public void delete(final AnalyticProcessTracker tracker) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(ANALYTIC_PROCESS_TRACKER)
                .where(ANALYTIC_PROCESS_TRACKER.FK_ANALYTIC_PROCESS_UUID.eq(tracker.getFilterUuid()))
                .execute());
    }

    private AnalyticProcessTracker recordToAnalyticProcessorFilterTracker(final Record record) {
        final String data = record.get(ANALYTIC_PROCESS_TRACKER.DATA);
        final AnalyticProcessTrackerData analyticProcessTrackerData = deserialise(data);
        return new AnalyticProcessTracker(
                record.get(ANALYTIC_PROCESS_TRACKER.FK_ANALYTIC_PROCESS_UUID),
                analyticProcessTrackerData);
    }

    private String serialise(final AnalyticProcessTracker tracker) {
        if (tracker.getAnalyticProcessTrackerData() != null) {
            try {
                return mapper.writeValueAsString(tracker.getAnalyticProcessTrackerData());
            } catch (final JsonProcessingException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return null;
    }

    private AnalyticProcessTrackerData deserialise(final String data) {
        AnalyticProcessTrackerData analyticProcessTrackerData = null;
        if (data != null && !data.isBlank()) {
            try {
                analyticProcessTrackerData = mapper.readValue(data, AnalyticProcessTrackerData.class);
            } catch (final JsonProcessingException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return analyticProcessTrackerData;
    }
}
