package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticTracker;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;

public class AnalyticHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticHelper.class);

    private final AnalyticRuleStore analyticRuleStore;
    private final AnalyticTrackerDao analyticTrackerDao;
    private final TaskContextFactory taskContextFactory;
    private final ViewStore viewStore;
    private final MetaService metaService;

    @Inject
    public AnalyticHelper(final AnalyticRuleStore analyticRuleStore,
                          final AnalyticTrackerDao analyticTrackerDao,
                          final TaskContextFactory taskContextFactory,
                          final ViewStore viewStore,
                          final MetaService metaService) {
        this.analyticRuleStore = analyticRuleStore;
        this.analyticTrackerDao = analyticTrackerDao;
        this.taskContextFactory = taskContextFactory;
        this.viewStore = viewStore;
        this.metaService = metaService;
    }

    public ViewDoc loadViewDoc(final String ruleIdentity,
                               final DocRef viewDocRef) {
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to process analytic: " +
                    ruleIdentity +
                    " because selected view cannot be found");
        }
        if (viewDoc.getPipeline() == null) {
            throw new RuntimeException("Unable to process analytic: " +
                    ruleIdentity +
                    " because view does not specify a pipeline");
        }
        return viewDoc;
    }

    public static String getAnalyticRuleIdentity(final AnalyticRuleDoc analyticRuleDoc) {
        return analyticRuleDoc.getName() +
                " (" +
                analyticRuleDoc.getUuid() +
                ")";
    }

    public void disableProcess(final AnalyticRuleDoc analyticRuleDoc) {
        final AnalyticProcessConfig<?> analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
        if (analyticProcessConfig != null) {
            final AnalyticRuleDoc modified = analyticRuleDoc
                    .copy()
                    .analyticProcessConfig(
                            analyticProcessConfig
                                    .copy()
                                    .enabled(false)
                                    .build())
                    .build();
            analyticRuleStore.writeDocument(modified);
        }
    }

    public List<AnalyticRuleDoc> getRules() {
        final List<DocRef> docRefList = analyticRuleStore.list();
        final List<AnalyticRuleDoc> rules = new ArrayList<>();
        for (final DocRef docRef : docRefList) {
            try {
                final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                if (analyticRuleDoc != null) {
                    rules.add(analyticRuleDoc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return rules;
    }

    public AnalyticTracker getTracker(final AnalyticRuleDoc analyticRuleDoc) {
        Optional<AnalyticTracker> optionalTracker =
                analyticTrackerDao.get(analyticRuleDoc.getUuid());
        while (optionalTracker.isEmpty()) {
            final AnalyticTracker tracker = new AnalyticTracker(analyticRuleDoc.getUuid(), null);
            analyticTrackerDao.create(tracker);
            optionalTracker = analyticTrackerDao.get(analyticRuleDoc.getUuid());
        }
        return optionalTracker.get();
    }

    public void updateTracker(final AnalyticTracker tracker) {
        analyticTrackerDao.update(tracker);
    }

    public void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    public List<Meta> findMeta(final ExpressionOperator expression,
                               final Long minMetaId,
                               final Long minMetaCreateTimeMs,
                               final Long maxMetaCreateTimeMs,
                               final int length) {
        // Don't select deleted streams.
        final ExpressionOperator statusExpression = ExpressionOperator.builder().op(Op.OR)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                .build();

        ExpressionOperator.Builder builder = ExpressionOperator.builder()
                .addOperator(expression);
        if (minMetaId != null) {
            builder = builder.addTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId);
        }

        if (minMetaCreateTimeMs != null) {
            builder = builder.addTerm(MetaFields.CREATE_TIME,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    DateUtil.createNormalDateTimeString(minMetaCreateTimeMs));
        }
        if (maxMetaCreateTimeMs != null) {
            builder = builder.addTerm(MetaFields.CREATE_TIME,
                    Condition.LESS_THAN_OR_EQUAL_TO,
                    DateUtil.createNormalDateTimeString(maxMetaCreateTimeMs));
        }
        builder = builder.addOperator(statusExpression);

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(builder.build());
        findMetaCriteria.setSort(MetaFields.ID.getName(), false, false);
        findMetaCriteria.obtainPageRequest().setLength(length);

        return metaService.find(findMetaCriteria).getValues();
    }

    public static long getMin(Long currentValue, Long newValue) {
        if (newValue == null) {
            return 0L;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.min(currentValue, newValue);
    }

    public static long getMax(Long currentValue, Long newValue) {
        if (newValue == null) {
            return Long.MAX_VALUE;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.max(currentValue, newValue);
    }
}
