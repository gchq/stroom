/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.RetentionRuleOutcome;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.db.util.JooqUtil;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.event.logging.mock.MockStroomEventLoggingModule;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.impl.MetaModule;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.collections.BatchingCollector;
import stroom.util.date.DateUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.TimePeriod;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.meta.impl.db.MetaDaoImpl.meta;

class TestMetaServiceImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaServiceImpl.class);

    private static final String FEED_1 = "FEED1";
    private static final String FEED_2 = "FEED2";
    private static final String FEED_3 = "FEED3";
    private static final String FEED_4 = "FEED4";
    private static final String FEED_5 = "FEED5";
    protected static final String TEST_STREAM_TYPE = "TEST_STREAM_TYPE";

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaService metaService;
    @Inject
    private MetaDaoImpl metaDao;
    @Inject
    private MetaDbConnProvider metaDbConnProvider;

    private DataRetentionConfig dataRetentionConfig;

    @BeforeEach
    void setup() {
        dataRetentionConfig = new DataRetentionConfig();

        final Module dataRetentionConfigModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(DataRetentionConfig.class)
                        .toProvider(() ->
                                getDataRetentionConfig());
            }
        };

        Guice.createInjector(
                        new MetaModule(),
                        new MetaDbModule(),
                        new MetaDaoModule(),
                        new MockClusterLockModule(),
                        new MockSecurityContextModule(),
                        new MockCollectionModule(),
                        new MockDocRefInfoModule(),
                        new MockWordListProviderModule(),
                        new MockMetricsModule(),
                        new CacheModule(),
                        new DbTestModule(),
                        new MetaTestModule(),
                        new MockTaskModule(),
                        new MockStroomEventLoggingModule(),
                        dataRetentionConfigModule)
                .injectMembers(this);
        // Delete everything
        cleanup.cleanup();
    }

    public DataRetentionConfig getDataRetentionConfig() {
        return dataRetentionConfig;
    }

    @Test
    void testUpdateStatus_byOneID() {
        final Meta meta1 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta2 = metaService.create(createRawProperties(FEED_2));

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta2.getId())
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        final int deleted = metaService.updateStatus(
                criteria, null, Status.DELETED);
        assertThat(deleted)
                .isEqualTo(1);

        assertThat(metaService.getMeta(meta1.getId(), true))
                .extracting(Meta::getStatus)
                .isNotEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta2.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
    }

    @Test
    void testUpdateStatus_byManyIDs() {
        final Meta meta1 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta2 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta3 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta4 = metaService.create(createProperties(FEED_1, Instant.now()));

//        TestMetaDaoImpl.dumpMetaTable(metaDbConnProvider);

        final ExpressionOperator expression = MetaExpressionUtil.createDataIdSetExpression(Set.of(
                meta1.getId(),
                meta3.getId(),
                meta4.getId()));
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        final int deleted = metaService.updateStatus(
                criteria, null, Status.DELETED);

//        TestMetaDaoImpl.dumpMetaTable(metaDbConnProvider);

        assertThat(deleted)
                .isEqualTo(3);

        assertThat(metaService.getMeta(meta1.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta2.getId(), true))
                .extracting(Meta::getStatus)
                .isNotEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta3.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta4.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
    }

    @Test
    void testUpdateStatus_byFilter() {
        final Meta meta1 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta2 = metaService.create(createProperties(FEED_2, Instant.now()));
        final Meta meta3 = metaService.create(createProperties(FEED_3, Instant.now()));
        final Meta meta4 = metaService.create(createProperties(FEED_4, Instant.now()));
        final Meta meta5 = metaService.create(createProperties(FEED_5, Instant.now()));

//        TestMetaDaoImpl.dumpMetaTable(metaDbConnProvider);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(MetaFields.ID.getFldName(), Condition.EQUALS, Long.toString(meta1.getId()))
                .addTerm(
                        MetaFields.FIELD_FEED,
                        Condition.IN,
                        String.join(",", FEED_3, FEED_4, FEED_5))
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        final int deleted = metaService.updateStatus(
                criteria, null, Status.DELETED);

        assertThat(deleted)
                .isEqualTo(4);

        assertThat(metaService.getMeta(meta1.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta2.getId(), true))
                .extracting(Meta::getStatus)
                .isNotEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta3.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta4.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(meta5.getId(), true))
                .extracting(Meta::getStatus)
                .isEqualTo(Status.DELETED);
    }

    @Test
    void testSummary() {
        final Meta meta1 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta2 = metaService.create(createRawProperties(FEED_2));

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, meta2.getId())
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);

        SelectionSummary selectionSummary = metaService.getSelectionSummary(new FindMetaCriteria());

        assertThat(selectionSummary.getItemCount()).isEqualTo(2);
        assertThat(selectionSummary.getStatusCount()).isEqualTo(1);

        final int deleted = metaService.updateStatus(
                new FindMetaCriteria(expression), null, Status.DELETED);
        assertThat(deleted).isEqualTo(1);

        selectionSummary = metaService.getSelectionSummary(new FindMetaCriteria());

        assertThat(selectionSummary.getItemCount()).isEqualTo(2);
        assertThat(selectionSummary.getStatusCount()).isEqualTo(2);
    }

    @Test
    void testRetentionDelete_noRules() {
        final List<DataRetentionRuleAction> ruleActions = Collections.emptyList();

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        metaService.delete(ruleActions, period);

        // No rules, no data deleted
        assertTotalRowCount(3, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_allData() {

        // Testing a true condition
        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(
                        1,
                        ExpressionOperator.builder().build(),
                        RetentionRuleOutcome.DELETE)
        );
        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // Rules all say delete
        assertTotalRowCount(3, Status.DELETED);
    }

    @Test
    void testRetentionDelete_emptyNot() {

        // Testing a true condition
        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(
                        1,
                        ExpressionOperator.builder().op(Op.NOT).build(),
                        RetentionRuleOutcome.DELETE)
        );
        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        // A NOT with no children does nothing.
        metaService.delete(ruleActions, period);

        // Rules all say delete
        assertTotalRowCount(3, Status.DELETED);
    }

    @Test
    void testRetentionDelete_retainAll() {

        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.RETAIN),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.RETAIN)
        );

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        metaService.delete(ruleActions, period);

        // Rules all say retain
        assertTotalRowCount(3, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_deleteAll_locked() {

        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData(Status.LOCKED);

        assertTotalRowCount(3, Status.LOCKED);

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // All data is locked so nothing deleted
        assertTotalRowCount(3, Status.LOCKED);
    }

    @Test
    void testRetentionDelete_deleteAll() {

        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // All data is locked so nothing deleted
        assertTotalRowCount(3, Status.DELETED);
    }

    @Test
    void testRetentionDelete_deleteSome() {

        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.RETAIN)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // Only one feed is deleted
        assertTotalRowCount(1, Status.DELETED);
        assertTotalRowCount(2, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_noMatch() {

        // Testing a true condition
        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(
                        1,
                        "NOT_FOUND_FEED",
                        RetentionRuleOutcome.DELETE)
        );
        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        final TimePeriod period = TimePeriod.between(Instant.EPOCH, Instant.now());

        metaService.delete(ruleActions, period);

        // Nothing will match so no deletes.
        assertTotalRowCount(0, Status.DELETED);
    }

    @Test
    void testRetentionDelete_caseFallThrough() {

        // Two rules for same expression, rule 2 will never match
        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(2, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(4, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        final TimePeriod period = TimePeriod.fromEpochTo(Instant.now());

        metaService.delete(ruleActions, period);

        // Rule 4 trumps rule 3
        assertTotalRowCount(2, Status.DELETED);
        assertTotalRowCount(1, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_caseFallThrough2() {

        // Two rules for same expression, rule 3 will never match
        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_1, RetentionRuleOutcome.RETAIN),
                buildRuleAction(3, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(4, FEED_3, RetentionRuleOutcome.DELETE)
        );

        setupRetentionData();

        assertTotalRowCount(3, Status.UNLOCKED);

        final TimePeriod period = TimePeriod.fromEpochTo(Instant.now());

        metaService.delete(ruleActions, period);

        // Rule 1 trumps rule 2
        assertTotalRowCount(3, Status.DELETED);
        assertTotalRowCount(0, Status.UNLOCKED);
    }

    @Test
    void testRetentionDelete_period() {

        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        final Instant now = Instant.now();

        setupRetentionData(Status.UNLOCKED, now.minus(3, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(2, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(1, ChronoUnit.DAYS));

        assertTotalRowCount(9, Status.UNLOCKED);

        // Period should cover set of data
        final TimePeriod period = TimePeriod.between(
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

        final List<DataRetentionRuleAction> ruleActions = List.of(
                buildRuleAction(1, FEED_1, RetentionRuleOutcome.DELETE),
                buildRuleAction(2, FEED_2, RetentionRuleOutcome.DELETE),
                buildRuleAction(3, FEED_3, RetentionRuleOutcome.DELETE)
        );

        final Instant now = Instant.now();

        setupRetentionData(Status.UNLOCKED, now.minus(3, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(2, ChronoUnit.DAYS));
        setupRetentionData(Status.UNLOCKED, now.minus(1, ChronoUnit.DAYS));

        assertTotalRowCount(9, Status.UNLOCKED);

        // Period should cover set of data
        final TimePeriod period = TimePeriod.between(
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
                    final Instant createTime = now.minus(i, ChronoUnit.DAYS);
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
                    metaDao.bulkCreate(batch, Status.UNLOCKED);
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
        dataRetentionConfig = dataRetentionConfig.withDeleteBatchSize(Math.max(1, (expectedRowsDeleted / 2) - 10));
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

    @Test
    void testRetentionSummary() {

        LOGGER.info("Loading data");

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, FEED_1, 1, TimeUnit.DAYS),
                buildRule(2, FEED_2, 2, TimeUnit.DAYS),
                buildRule(3, FEED_3, 1, TimeUnit.WEEKS),
                buildForeverRule(4, FEED_4)
        );
        final Map<Integer, DataRetentionRule> ruleNoToRuleMap = rules.stream()
                .collect(Collectors.toMap(
                        DataRetentionRule::getRuleNumber,
                        Function.identity()));

        final Instant now = Instant.now();
        final int totalDays = 10;
        final int rowsPerFeedPerDay = 2;
        final int feedCount = 5;

        final int insertBatchSize = 10_000;
        final int totalRows = totalDays * feedCount * rowsPerFeedPerDay;

        final AtomicLong counter = new AtomicLong(0);

        IntStream.range(0, totalDays)
                .boxed()
                .flatMap(i -> {
                    final Instant createTime = now.minus(i, ChronoUnit.DAYS);
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
                    metaDao.bulkCreate(batch, Status.UNLOCKED);
                    counter.addAndGet(batch.size());
                    LOGGER.info("Processed {} of {}", counter.get(), totalRows);
                }));


        assertTotalRowCount(totalRows, Status.UNLOCKED);

        final List<DataRetentionDeleteSummary> summaries = metaDao.getRetentionDeletionSummary(
                DataRetentionRules.builder().uuid(UUID.randomUUID().toString()).rules(rules).build(),
                new FindDataRetentionImpactCriteria());

        LOGGER.info("totalRows {}", totalRows);

        assertTotalRowCount(totalRows);

        final List<Meta> metaList = metaDao.find(new FindMetaCriteria())
                .getValues()
                .stream()
                .sorted(Comparator.comparing(Meta::getCreateMs))
                .toList();

        LOGGER.info("meta:\n\n{}\n", AsciiTable.builder(metaList)
                .withColumn(Column.of("ID", Meta::getId))
                .withColumn(Column.of("Feed", Meta::getFeedName))
                .withColumn(Column.of("Create Time", meta -> DateUtil.createNormalDateTimeString(meta.getCreateMs())))
                .build());

        LOGGER.info("result:\n\n{}\n", AsciiTable.builder(summaries)
                .withColumn(Column.of("Feed", DataRetentionDeleteSummary::getFeed))
                .withColumn(Column.of("Type", DataRetentionDeleteSummary::getType))
                .withColumn(Column.builder("Rule No.", DataRetentionDeleteSummary::getRuleNumber)
                        .rightAligned()
                        .build())
                .withColumn(Column.of("Rule Name", DataRetentionDeleteSummary::getRuleName))
                .withColumn(Column.of("Rule Age", summary2 ->
                        ruleNoToRuleMap.get(summary2.getRuleNumber()).getAgeString()))
                .withColumn(Column.builder("Stream Delete Count", DataRetentionDeleteSummary::getCount)
                        .rightAligned()
                        .build())
                .build());

        // Rule 1 has age 1 day so all other days' records should be in line for deletion and thus
        // in the summary count.
        assertThat(getCount(FEED_1, TEST_STREAM_TYPE, 1, summaries))
                .isEqualTo((totalDays - 1) * rowsPerFeedPerDay);
        // Rule 2 has age 2 days
        assertThat(getCount(FEED_2, TEST_STREAM_TYPE, 2, summaries))
                .isEqualTo((totalDays - 2) * rowsPerFeedPerDay);
        // Rule 2 has age 1 week
        assertThat(getCount(FEED_3, TEST_STREAM_TYPE, 3, summaries))
                .isEqualTo((totalDays - 7) * rowsPerFeedPerDay);

        LOGGER.info("Done");
    }

    private int getCount(final String feedName,
                         final String type,
                         final int ruleNo,
                         final List<DataRetentionDeleteSummary> summaries) {
        return summaries.stream()
                .filter(summary ->
                        summary.getRuleNumber() == ruleNo
                        && summary.getFeed().equals(feedName)
                        && summary.getType().equals(type))
                .findAny()
                .orElseThrow()
                .getCount();
    }

    /**
     * Seeing which execution timer was faster
     */
    @Disabled
    @Test
    void testLogExecutionTime() {
        final int iterations = 100_000_000;
        final int runs = 5;
        final AtomicLong counter = new AtomicLong();

        for (int j = 0; j < runs; j++) {
            counter.set(0);
            final LogExecutionTime outer = new LogExecutionTime();

            for (int i = 0; i < iterations; i++) {
                final LogExecutionTime inner = new LogExecutionTime();
                counter.incrementAndGet();
                LOGGER.trace("Some message ", inner);
            }
            LOGGER.info("Outer LogExecutionTime {} {}", counter, outer);
        }

        for (int j = 0; j < runs; j++) {
            counter.set(0);
            final LogExecutionTime outer = new LogExecutionTime();
            for (int i = 0; i < iterations; i++) {
                LOGGER.logDurationIfTraceEnabled(() -> {
                    counter.incrementAndGet();
                }, "some message");
            }
            LOGGER.info("Outer Lambda {} {}", counter, outer);
        }
    }

    @Test
    void testFind_all() {
        final Meta meta1 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta2 = metaService.create(createProperties(FEED_2, Instant.now()));
        final Meta meta3 = metaService.create(createProperties(FEED_3, Instant.now()));

        final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder().op(Op.AND).build());
        final ResultPage<Meta> resultPage = metaService.find(criteria);
        assertThat(resultPage)
                .isNotNull();
        assertThat(resultPage.getValues())
                .containsExactlyInAnyOrder(meta1, meta2, meta3);
    }

    @Test
    void testFind_withExpression() {
        final Meta meta1 = metaService.create(createProperties(FEED_1, Instant.now()));
        final Meta meta2 = metaService.create(createProperties(FEED_2, Instant.now()));
        final Meta meta3 = metaService.create(createProperties(FEED_3, Instant.now()));

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(ExpressionTerm.builder()
                        .field(MetaFields.FIELD_FEED)
                        .condition(Condition.EQUALS)
                        .value(FEED_1)
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .field(MetaFields.FIELD_FEED)
                        .condition(Condition.EQUALS)
                        .value(FEED_2)
                        .build())
                .build();
        final FindMetaCriteria criteria = new FindMetaCriteria(expressionOperator);
        final ResultPage<Meta> resultPage = metaService.find(criteria);
        assertThat(resultPage)
                .isNotNull();
        assertThat(resultPage.getValues())
                .containsExactlyInAnyOrder(meta1, meta2);
    }

    private void assertTotalRowCount(final int expectedRowCount, final Status status) {
        final FindMetaCriteria criteria = new FindMetaCriteria();
        criteria.setExpression(ExpressionOperator.builder()
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build());
        final int rowCount = metaDao.count(criteria);

        assertThat(rowCount)
                .isEqualTo(expectedRowCount);
    }

    private void assertTotalRowCount(final int expectedRowCount) {
        final FindMetaCriteria criteria = new FindMetaCriteria();
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
//            metaService.updateStatus(new FindMetaCriteria(), Status.LOCKED, status);
            unlockAllLockedStreams();
        }
    }

    private void unlockAllLockedStreams() {

        JooqUtil.context(metaDbConnProvider, context -> {
            final byte unlockedId = MetaStatusId.getPrimitiveValue(Status.UNLOCKED);
            final byte lockedId = MetaStatusId.getPrimitiveValue(Status.LOCKED);
            final int count = context.update(meta)
                    .set(meta.STATUS, unlockedId)
                    .set(meta.STATUS_TIME, Instant.now().toEpochMilli())
                    .where(meta.STATUS.eq(lockedId))
                    .execute();
            LOGGER.debug("Unlocked {} meta records", count);
        });
    }

    private MetaProperties createProperties(final String feedName, final Instant createTime) {
        return MetaProperties.builder()
                .createMs(createTime.toEpochMilli())
                .feedName(feedName)
                .processorUuid("12345")
                .pipelineUuid("PIPELINE_UUID")
                .typeName(TEST_STREAM_TYPE)
                .build();
    }

    private MetaProperties createRawProperties(final String feedName) {
        return MetaProperties.builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .typeName("RAW_TEST_STREAM_TYPE")
                .build();
    }

    private DataRetentionRuleAction buildRuleAction(final int ruleNo,
                                                    final String feedName,
                                                    final RetentionRuleOutcome outcome) {

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(MetaFields.FIELD_FEED, Condition.EQUALS, feedName)
                .addTerm(MetaFields.FIELD_TYPE, Condition.EQUALS, TEST_STREAM_TYPE)
                .build();

        // The age on the rule doesn't matter for the dao tests
        return buildRuleAction(ruleNo, expressionOperator, outcome);
    }

    private DataRetentionRule buildRule(final int ruleNo,
                                        final String feedName,
                                        final int age,
                                        final TimeUnit timeUnit) {

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(MetaFields.FIELD_FEED, Condition.EQUALS, feedName)
                .addTerm(MetaFields.FIELD_TYPE, Condition.EQUALS, TEST_STREAM_TYPE)
                .build();

        // The age on the rule doesn't matter for the dao tests
        return buildRule(ruleNo, expressionOperator, age, timeUnit);
    }

    private DataRetentionRule buildForeverRule(final int ruleNo,
                                               final String feedName) {

        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(MetaFields.FIELD_FEED, Condition.EQUALS, feedName)
                .addTerm(MetaFields.FIELD_TYPE, Condition.EQUALS, TEST_STREAM_TYPE)
                .build();

        // The age on the rule doesn't matter for the dao tests
        return DataRetentionRule.foreverRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo,
                true,
                expressionOperator);
    }

    private DataRetentionRuleAction buildRuleAction(final int ruleNo,
                                                    final ExpressionOperator expressionOperator,
                                                    final RetentionRuleOutcome outcome) {

        // The age on the rule doesn't matter for the dao tests
        return new DataRetentionRuleAction(buildRule(ruleNo, expressionOperator), outcome);
    }

    private DataRetentionRule buildRule(final int ruleNo,
                                        final ExpressionOperator expressionOperator) {

        // The age on the rule doesn't matter for the dao tests
        return new DataRetentionRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo,
                true,
                expressionOperator,
                1,
                TimeUnit.YEARS,
                false);
    }

    private DataRetentionRule buildRule(final int ruleNo,
                                        final ExpressionOperator expressionOperator,
                                        final int age,
                                        final TimeUnit timeUnit) {

        // The age on the rule doesn't matter for the dao tests
        return new DataRetentionRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo,
                true,
                expressionOperator,
                age,
                timeUnit,
                false);
    }
}
