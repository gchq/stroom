package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticRuleState;
import stroom.analytics.impl.AnalyticRuleStateDao;
import stroom.db.util.JooqUtil;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

//import static stroom.analytics.impl.db.jooq.tables.AnalyticRule.ANALYTIC_RULE;
import static stroom.analytics.impl.db.jooq.tables.AnalyticRuleState.ANALYTIC_RULE_STATE;

@Singleton
class AnalyticRuleStateDaoImpl implements AnalyticRuleStateDao {

    private final AnalyticsDbConnProvider dbConnProvider;

    @Inject
    AnalyticRuleStateDaoImpl(final AnalyticsDbConnProvider dbConnProvider) {
        this.dbConnProvider = dbConnProvider;
    }

    @Override
    public Optional<AnalyticRuleState> getState(final String analyticUuid) {
        var optional = JooqUtil.contextResult(dbConnProvider, context -> context
                .select(ANALYTIC_RULE_STATE.ID,
                        ANALYTIC_RULE_STATE.VERSION,
                        ANALYTIC_RULE_STATE.CREATE_TIME_MS,
                        ANALYTIC_RULE_STATE.CREATE_USER,
                        ANALYTIC_RULE_STATE.UPDATE_TIME_MS,
                        ANALYTIC_RULE_STATE.UPDATE_USER,
                        ANALYTIC_RULE_STATE.ANALYTIC_UUID,
                        ANALYTIC_RULE_STATE.LAST_META_ID,
                        ANALYTIC_RULE_STATE.LAST_EVENT_ID,
                        ANALYTIC_RULE_STATE.LAST_EVENT_TIME,
                        ANALYTIC_RULE_STATE.LAST_EXECUTION_TIME)
                .from(ANALYTIC_RULE_STATE)
                .where(ANALYTIC_RULE_STATE.ANALYTIC_UUID.eq(analyticUuid))
                .fetchOptional());
        return optional.map(record -> new AnalyticRuleState(
                record.get(ANALYTIC_RULE_STATE.ID),
                record.get(ANALYTIC_RULE_STATE.VERSION),
                record.get(ANALYTIC_RULE_STATE.CREATE_TIME_MS),
                record.get(ANALYTIC_RULE_STATE.CREATE_USER),
                record.get(ANALYTIC_RULE_STATE.UPDATE_TIME_MS),
                record.get(ANALYTIC_RULE_STATE.UPDATE_USER),
                record.get(ANALYTIC_RULE_STATE.ANALYTIC_UUID),
                record.get(ANALYTIC_RULE_STATE.LAST_META_ID),
                record.get(ANALYTIC_RULE_STATE.LAST_EVENT_ID),
                record.get(ANALYTIC_RULE_STATE.LAST_EVENT_TIME),
                record.get(ANALYTIC_RULE_STATE.LAST_EXECUTION_TIME)));
    }

    @Override
    public void createState(final AnalyticRuleState analyticRuleState) {
        JooqUtil.context(dbConnProvider, context -> context
                .insertInto(ANALYTIC_RULE_STATE,
                        ANALYTIC_RULE_STATE.VERSION,
                        ANALYTIC_RULE_STATE.CREATE_TIME_MS,
                        ANALYTIC_RULE_STATE.CREATE_USER,
                        ANALYTIC_RULE_STATE.UPDATE_TIME_MS,
                        ANALYTIC_RULE_STATE.UPDATE_USER,
                        ANALYTIC_RULE_STATE.ANALYTIC_UUID,
                        ANALYTIC_RULE_STATE.LAST_META_ID,
                        ANALYTIC_RULE_STATE.LAST_EVENT_ID,
                        ANALYTIC_RULE_STATE.LAST_EVENT_TIME,
                        ANALYTIC_RULE_STATE.LAST_EXECUTION_TIME)
                .values(analyticRuleState.version(),
                        analyticRuleState.createTime(),
                        analyticRuleState.createUser(),
                        analyticRuleState.updateTime(),
                        analyticRuleState.updateUser(),
                        analyticRuleState.analyticUuid(),
                        analyticRuleState.lastMetaId(),
                        analyticRuleState.lastEventId(),
                        analyticRuleState.lastEventTime(),
                        analyticRuleState.lastExecutionTime())
                .execute());
    }

    @Override
    public void updateState(final AnalyticRuleState analyticRuleState) {
        JooqUtil.context(dbConnProvider, context -> context
                .update(ANALYTIC_RULE_STATE)
                .set(ANALYTIC_RULE_STATE.VERSION, ANALYTIC_RULE_STATE.VERSION.plus(1))
                .set(ANALYTIC_RULE_STATE.UPDATE_TIME_MS, analyticRuleState.updateTime())
                .set(ANALYTIC_RULE_STATE.UPDATE_USER, analyticRuleState.updateUser())
                .set(ANALYTIC_RULE_STATE.LAST_META_ID, analyticRuleState.lastMetaId())
                .set(ANALYTIC_RULE_STATE.LAST_EVENT_ID, analyticRuleState.lastEventId())
                .set(ANALYTIC_RULE_STATE.LAST_EVENT_TIME, analyticRuleState.lastEventTime())
                .set(ANALYTIC_RULE_STATE.LAST_EXECUTION_TIME, analyticRuleState.lastExecutionTime())
                .where(ANALYTIC_RULE_STATE.ANALYTIC_UUID.eq(analyticRuleState.analyticUuid()))
                .execute());
    }
}
