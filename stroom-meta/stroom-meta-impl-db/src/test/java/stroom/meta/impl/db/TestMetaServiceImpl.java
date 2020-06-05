package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.RetentionRuleOutcome;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.TimeUnit;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.impl.MetaModule;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.collections.BatchingCollector;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.time.TimePeriod;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaServiceImpl {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaServiceImpl.class);

    private static final String FEED_1 = "FEED1";
    private static final String FEED_2 = "FEED2";
    private static final String FEED_3 = "FEED3";
    private static final String FEED_4 = "FEED4";
    private static final String FEED_5 = "FEED5";

    private static final List<String> ALL_FEEDS = List.of(
            "FEED1",
            "FEED2",
            "FEED3",
            "FEED4",
            "FEED5");

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaService metaService;
    @Inject
    private MetaDaoImpl metaDao;
    @Inject
    private DataRetentionConfig dataRetentionConfig;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                new MetaModule(),
                new MetaDbModule(),
                new MockClusterLockModule(),
                new MockSecurityContextModule(),
                new MockCollectionModule(),
                new MockDocRefInfoModule(),
                new MockWordListProviderModule(),
                new CacheModule(),
                new DbTestModule(),
                new MetaTestModule())
                .injectMembers(this);
        // Delete everything
        cleanup.clear();
    }
    @Test
    void testSummary() {
        final Meta meta1 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta2 = metaService.create(createRawProperties(FEED_2));

        final ExpressionOperator expression = new Builder(Op.AND)
                .addTerm(MetaFields.ID, Condition.EQUALS, meta2.getId())
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        SelectionSummary selectionSummary = metaService.getSelectionSummary(new FindMetaCriteria());

        assertThat(selectionSummary.getItemCount()).isEqualTo(2);
        assertThat(selectionSummary.getStatusCount()).isEqualTo(1);

        int deleted = metaService.updateStatus(
                new FindMetaCriteria(expression), null, Status.DELETED);
        assertThat(deleted).isEqualTo(1);

        selectionSummary = metaService.getSelectionSummary(new FindMetaCriteria());

        assertThat(selectionSummary.getItemCount()).isEqualTo(2);
        assertThat(selectionSummary.getStatusCount()).isEqualTo(2);
    }

    @Test
    void testRetentionDelete_noRules() {
        List<DataRetentionRuleAction> ruleActions = Collections.emptyList();

        TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        metaService.delete(ruleActions, period);

        // No rules, no data deleted
        assertTotalRowCount(3, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_allData() {

        // Testing a true condition
        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(
                        1,
                        new ExpressionOperator.Builder(Op.AND).build(),
                        RetentionRuleOutcome.DELETE)
        );
        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // Rules all say delete
        assertTotalRowCount(3, Status.DELETED);
    }

    @Test
    void testRetentionDelete_noData() {

        // Testing a true condition
        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(
                        1,
                        new ExpressionOperator.Builder(Op.NOT).build(),
                        RetentionRuleOutcome.DELETE)
        );
        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // Rules all say delete, but nothing will match
        assertTotalRowCount(0, Status.DELETED);
    }

    @Test
    void testRetentionDelete_retainAll() {

        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.RETAIN),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.RETAIN)
        );

        TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        metaService.delete(ruleActions, period);

        // Rules all say retain
        assertTotalRowCount(3, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_deleteAll_locked() {

        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData(Status.LOCKED);

        assertTotalRowCount(3, Status.LOCKED);

        TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // All data is locked so nothing deleted
        assertTotalRowCount(3, Status.LOCKED);
    }

    @Test
    void testRetentionDelete_deleteAll() {

        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // All data is locked so nothing deleted
        assertTotalRowCount(3, Status.DELETED);
    }

    @Test
    void testRetentionDelete_deleteSome() {

        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.RETAIN)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // Only one feed is deleted
        assertTotalRowCount(1, Status.DELETED);
        assertTotalRowCount(2, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_caseFallThrough() {

        // Two rules for same expression, rule 2 will never match
        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(2, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(4, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        TimePeriod period = TimePeriod.fromEpochTo(Instant.now());

        metaService.delete(ruleActions, period);

        // Rule 4 trumps rule 3
        assertTotalRowCount(2, Status.DELETED);
        assertTotalRowCount(1, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_caseFallThrough2() {

        // Two rules for same expression, rule 3 will never match
        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(3, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(4, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        TimePeriod period = TimePeriod.fromEpochTo(Instant.now());

        metaService.delete(ruleActions, period);

        // Rule 1 trumps rule 2
        assertTotalRowCount(3, Status.DELETED);
        assertTotalRowCount(0, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_period() {

        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        Instant now = Instant.now();

        setupRetentionData(Status.UNLOCKED, now.minus(3, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(2, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(1, ChronoUnit.DAYS));

        assertTotalRowCount(9, Status.UNLOCKED);

        // Period should cover set of data
        TimePeriod period = TimePeriod.between(
                now.minus(2, ChronoUnit.DAYS)
                        .minus(1, ChronoUnit.HOURS),
                now.minus(2, ChronoUnit.DAYS)
                        .plus(1, ChronoUnit.HOURS));

        metaService.delete(ruleActions, period);

        // Rule 4 trumps rule 3
        assertTotalRowCount(3, Status.DELETED);
        assertTotalRowCount(6, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_period2() {

        List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        Instant now = Instant.now();

        setupRetentionData(Status.UNLOCKED, now.minus(3, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(2, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(1, ChronoUnit.DAYS));

        assertTotalRowCount(9, Status.UNLOCKED);

        // Period should cover set of data
        TimePeriod period = TimePeriod.between(
                Instant.EPOCH,
                now.minus(1, ChronoUnit.DAYS));

        metaService.delete(ruleActions, period);

        // Rule 4 trumps rule 3
        assertTotalRowCount(6, Status.DELETED);
        assertTotalRowCount(3, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_volumeTest() {

        LOGGER.info("Loading data");

        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.RETAIN),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE),
                buildRuleAction(4, FEED_4, RetentionRuleOutcome.RETAIN),
                buildRuleAction(5, FEED_5, RetentionRuleOutcome.DELETE)
        );

        final Instant now = Instant.now();
        // The following will load 1k rows then delete 60
        final int totalDays = 10;
        final int rowsPerFeedPerDay = 10;
        final int feedCount = 10;
        final int daysToDelete = 2;

        // The following will load 1mil rows then delete 1500
//        final int totalDays = 100;
//        final int rowsPerFeedPerDay = 100;
//        final int feedCount = 100;
//        final int daysToDelete = 5;

        final int insertBatchSize = 10_000;
        final int totalRows = totalDays * feedCount * rowsPerFeedPerDay;

        final AtomicLong counter = new AtomicLong(0);

        IntStream.range(0, totalDays)
                .boxed()
                .flatMap(i -> {
                    Instant createTime = now.minus(i, ChronoUnit.DAYS);
                    return IntStream.rangeClosed(1, feedCount)
                            .boxed()
                            .flatMap(j -> IntStream.rangeClosed(1, rowsPerFeedPerDay)
                                    .boxed()
                                    .map(k ->
                                            // Spread the records over time to avoid
                                            // the batching picking up multiples
                                            createProperties(
                                                    "FEED" + j,
                                                    createTime
                                                            .minus(j, ChronoUnit.SECONDS)
                                                            .minus(k, ChronoUnit.MILLIS))));
                })
                .collect(BatchingCollector.of(insertBatchSize, batch -> {
                    metaDao.create(batch, Status.UNLOCKED);
                    counter.addAndGet(batch.size());
                    LOGGER.info("Processed {} of {}", counter.get(), totalRows);
                }));

        assertTotalRowCount(totalRows, Status.UNLOCKED);

        final Instant deletionDay = now
                .minus(totalDays / 2, ChronoUnit.DAYS)
                .plus(1, ChronoUnit.HOURS);


        // Period should cover set of data
        final TimePeriod period = TimePeriod.between(
                deletionDay.minus(daysToDelete, ChronoUnit.DAYS),
                deletionDay);

        final int expectedRowsDeleted = 3 * daysToDelete * rowsPerFeedPerDay;

        // Use a batch size smaller than the expected number of deletes to ensure we exercise
        // batching
        dataRetentionConfig.setDeleteBatchSize(Math.max(1, (expectedRowsDeleted / 2) - 10));
//        dataRetentionConfig.setDeleteBatchSize(7);

        LOGGER.info("Doing data retention delete for period {}, batch size {}",
                period, dataRetentionConfig.getDeleteBatchSize());

        metaService.delete(ruleActions, period);

        LOGGER.info("deleteBatchSize {}", dataRetentionConfig.getDeleteBatchSize());
        LOGGER.info("Period {}", period);
        LOGGER.info("deletionDay {}", deletionDay);
        LOGGER.info("daysToDelete {}", daysToDelete);
        LOGGER.info("totalRows {}", totalRows);
        LOGGER.info("expectedRowsDeleted {}", expectedRowsDeleted);

        assertTotalRowCount(totalRows);
        assertTotalRowCount(expectedRowsDeleted, Status.DELETED);
        LOGGER.info("Done");
    }

    /**
     * Seeing which execution timer was faster
     */
    @Disabled
    @Test
    void testLogExecutionTime() {
        int iterations = 100_000_000;
        int runs = 5;
        AtomicLong counter = new AtomicLong();

        for (int j = 0; j < runs; j++) {
            counter.set(0);
            LogExecutionTime outer = new LogExecutionTime();

            for (int i = 0; i < iterations; i++) {
                LogExecutionTime inner = new LogExecutionTime();
                counter.incrementAndGet();
                LOGGER.trace("Some message ", inner);
            }
            LOGGER.info("Outer LogExecutionTime {} {}", counter, outer);
        }

        for (int j = 0; j < runs; j++) {
            counter.set(0);
            LogExecutionTime outer = new LogExecutionTime();
            for (int i = 0; i < iterations; i++) {
                LOGGER.logDurationIfTraceEnabled(() -> {
                    counter.incrementAndGet();
                }, "some message");
            }
            LOGGER.info("Outer Lambda {} {}", counter, outer);
        }
    }

    private void assertTotalRowCount(final int expectedRowCount, final Status status) {
        FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(new ExpressionOperator.Builder(true, Op.AND)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build());
        final int rowCount = metaDao.count(criteria);

        assertThat(rowCount)
                .isEqualTo(expectedRowCount);
    }

    private void assertTotalRowCount(final int expectedRowCount) {
        FindMetaCriteria criteria = new FindMetaCriteria();
        final int rowCount = metaDao.count(criteria);

        assertThat(rowCount)
                .isEqualTo(expectedRowCount);
    }

    private void setupRetentionData() {
        setupRetentionData(Status.UNLOCKED, Instant.now());
    }

    private void setupRetentionData(final Status status) {
        setupRetentionData(status, Instant.now());
    }

    private void setupRetentionData(final Status status, final Instant createTime) {
        final Meta meta1 = metaService.create(createProperties(FEED_1, createTime));
        final Meta meta2 = metaService.create(createProperties(FEED_2, createTime));
        final Meta meta3 = metaService.create(createProperties(FEED_3, createTime));

        // Ensure all records have the desired status
        if (!Status.LOCKED.equals(status)) {
            metaService.updateStatus(new FindMetaCriteria(), Status.LOCKED, status);
        }
    }

    private MetaProperties createProperties(final String feedName, final Instant createTime) {
        return new MetaProperties.Builder()
                .createMs(createTime.toEpochMilli())
                .feedName(feedName)
                .processorUuid("12345")
                .pipelineUuid("PIPELINE_UUID")
                .typeName("TEST_STREAM_TYPE")
                .build();
    }

    private MetaProperties createRawProperties(final String feedName) {
        return new MetaProperties.Builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .typeName("RAW_TEST_STREAM_TYPE")
                .build();
    }

    private DataRetentionRuleAction buildRuleAction(final int ruleNo,
                                                    final String feedName,
                                                    final RetentionRuleOutcome outcome) {

        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(true, Op.AND)
                .addTerm(MetaFields.FIELD_FEED, Condition.EQUALS, feedName)
                .addTerm(MetaFields.FIELD_TYPE, Condition.EQUALS, "TEST_STREAM_TYPE")
                .build();

        // The age on the rule doesn't matter for the dao tests
        return buildRuleAction(ruleNo, expressionOperator, outcome);
    }

    private DataRetentionRuleAction buildRuleAction(final int ruleNo,
                                                    final ExpressionOperator expressionOperator,
                                                    final RetentionRuleOutcome outcome) {

        // The age on the rule doesn't matter for the dao tests
        return new DataRetentionRuleAction(new DataRetentionRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo,
                true,
                expressionOperator,
                1,
                TimeUnit.YEARS,
                false), outcome);
    }
}
