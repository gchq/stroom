package stroom.data.retention.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.TimeUnit;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContextFactory;
import stroom.util.time.TimePeriod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestDataRetentionPolicyExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionPolicyExecutor.class);

    private static final ExpressionOperator EMPTY_AND_OP = new ExpressionOperator.Builder(
            true, ExpressionOperator.Op.AND).build();

    private ClusterLockService clusterLockService = new MockClusterLockService();
    private DataRetentionConfig dataRetentionConfig = new DataRetentionConfig();
    private TaskContextFactory taskContextFactory = new SimpleTaskContextFactory();

    @Mock
    private MetaService metaService;

    @Captor
    private ArgumentCaptor<List<ExpressionOperator>> ruleExpressionsCaptor;

    @Captor
    private ArgumentCaptor<TimePeriod> periodCaptor;

    @Test
    void testDataRetention() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(4, true, 10, TimeUnit.DAYS),
                buildRule(3, true, 1, TimeUnit.MONTHS),
                buildRule(2, true, 1, TimeUnit.YEARS),
                buildForeverRule(1, true)
        );

        final DataRetentionPolicyExecutor dataRetentionPolicyExecutor = new DataRetentionPolicyExecutor(
                clusterLockService,
                () -> buildRules(rules),
                dataRetentionConfig,
                metaService,
                taskContextFactory);

        when(metaService.delete(
                ruleExpressionsCaptor.capture(),
                periodCaptor.capture(),
                anyInt()))
                .thenReturn(0);

        dataRetentionPolicyExecutor.exec();

        final List<List<ExpressionOperator>> ruleExpressionsList = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> periods = periodCaptor.getAllValues();

        int expectedPeriodCount = 3;
        assertThat(periods).hasSize(expectedPeriodCount);
        assertThat(ruleExpressionsList).hasSize(expectedPeriodCount);
    }

    @Test
    void testTime() {

        Instant now = Instant.now();
        Instant now2 = now.plusSeconds(1).minusSeconds(1);

        assertThat(now.isBefore(now2)).isFalse();
    }

    private DataRetentionRules buildRules(List<DataRetentionRule> rules) {
        final DataRetentionRules dataRetentionRules = new DataRetentionRules(rules);
        dataRetentionRules.setVersion(UUID.randomUUID().toString());
        return dataRetentionRules;
    }
    private DataRetentionRule buildRule(final int ruleNo,
                                        final boolean isEnabled,
                                        final int age,
                                        final TimeUnit timeUnit,
                                        final ExpressionOperator expressionOperator) {


        return new DataRetentionRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo + "_" + age + timeUnit.getDisplayValue(),
                isEnabled,
                expressionOperator,
                age,
                timeUnit,
                false);
    }

    private DataRetentionRule buildRule(final int ruleNo,
                                        final boolean isEnabled,
                                        final int age,
                                        final TimeUnit timeUnit) {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(true, ExpressionOperator.Op.AND)
                .addTerm(MetaFields.FIELD_FEED, ExpressionTerm.Condition.EQUALS, "RULE_" + ruleNo + "FEED")
                .build();
        return buildRule(ruleNo, isEnabled, age, timeUnit, expressionOperator);
    }

    private DataRetentionRule buildForeverRule(final int ruleNo,
                                               final boolean isEnabled) {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(true, ExpressionOperator.Op.AND)
                .addTerm(MetaFields.FIELD_FEED, ExpressionTerm.Condition.EQUALS, "RULE_" + ruleNo + "FEED")
                .build();
        return buildForeverRule(ruleNo, isEnabled, expressionOperator);

    }

    private DataRetentionRule buildForeverRule(final int ruleNo,
                                               final boolean isEnabled,
                                               final ExpressionOperator expressionOperator) {
        return new DataRetentionRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule " + ruleNo,
                isEnabled,
                expressionOperator,
                1,
                TimeUnit.YEARS,
                true);
    }

}