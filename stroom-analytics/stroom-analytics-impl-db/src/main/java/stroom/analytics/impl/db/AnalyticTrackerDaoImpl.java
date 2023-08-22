package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticTrackerDao;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.AnalyticTrackerData;
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

import static stroom.analytics.impl.db.jooq.tables.AnalyticTracker.ANALYTIC_TRACKER;

@Singleton
public class AnalyticTrackerDaoImpl implements AnalyticTrackerDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticTrackerDaoImpl.class);

    private final AnalyticsDbConnProvider analyticsDbConnProvider;
    private final ObjectMapper mapper;

    @Inject
    public AnalyticTrackerDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    @Override
    public Optional<AnalyticTracker> get(final String analyticUuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_TRACKER.FK_ANALYTIC_UUID,
                        ANALYTIC_TRACKER.DATA)
                .from(ANALYTIC_TRACKER)
                .where(ANALYTIC_TRACKER.FK_ANALYTIC_UUID.eq(analyticUuid))
                .fetchOptional());
        return result.map(this::recordToAnalyticTracker);
    }

    @Override
    public void create(final AnalyticTracker analyticTracker) {
        final String data = serialise(analyticTracker);
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(ANALYTIC_TRACKER,
                        ANALYTIC_TRACKER.FK_ANALYTIC_UUID,
                        ANALYTIC_TRACKER.DATA)
                .values(analyticTracker.getAnalyticUuid(),
                        data)
                .execute());
    }

    @Override
    public void update(final AnalyticTracker tracker) {
        final String data = serialise(tracker);
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(ANALYTIC_TRACKER)
                .set(ANALYTIC_TRACKER.DATA, data)
                .where(ANALYTIC_TRACKER.FK_ANALYTIC_UUID.eq(tracker.getAnalyticUuid()))
                .execute());
    }

    @Override
    public void delete(final AnalyticTracker tracker) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(ANALYTIC_TRACKER)
                .where(ANALYTIC_TRACKER.FK_ANALYTIC_UUID.eq(tracker.getAnalyticUuid()))
                .execute());
    }

    private AnalyticTracker recordToAnalyticTracker(final Record record) {
        final String data = record.get(ANALYTIC_TRACKER.DATA);
        final AnalyticTrackerData analyticTrackerData = deserialise(data);
        return new AnalyticTracker(
                record.get(ANALYTIC_TRACKER.FK_ANALYTIC_UUID),
                analyticTrackerData);
    }

    private String serialise(final AnalyticTracker tracker) {
        if (tracker.getAnalyticTrackerData() != null) {
            try {
                return mapper.writeValueAsString(tracker.getAnalyticTrackerData());
            } catch (final JsonProcessingException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return null;
    }

    private AnalyticTrackerData deserialise(final String data) {
        AnalyticTrackerData analyticTrackerData = null;
        if (data != null && !data.isBlank()) {
            try {
                analyticTrackerData = mapper.readValue(data, AnalyticTrackerData.class);
            } catch (final JsonProcessingException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return analyticTrackerData;
    }
}
