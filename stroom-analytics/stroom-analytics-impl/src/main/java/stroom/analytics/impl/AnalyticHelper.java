/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
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
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Optional;

public class AnalyticHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticHelper.class);

    private final AnalyticLoader analyticLoader;
    private final AnalyticTrackerDao analyticTrackerDao;
    private final ViewStore viewStore;
    private final MetaService metaService;
    private final Provider<AnalyticUiDefaultConfig> analyticUiDefaultConfigProvider;

    @Inject
    public AnalyticHelper(final AnalyticLoader analyticLoader,
                          final AnalyticTrackerDao analyticTrackerDao,
                          final ViewStore viewStore,
                          final MetaService metaService,
                          final Provider<AnalyticUiDefaultConfig> analyticUiDefaultConfigProvider) {
        this.analyticLoader = analyticLoader;
        this.analyticTrackerDao = analyticTrackerDao;
        this.viewStore = viewStore;
        this.metaService = metaService;
        this.analyticUiDefaultConfigProvider = analyticUiDefaultConfigProvider;
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

    public void disableProcess(final AbstractAnalyticRuleDoc doc) {
        analyticLoader.disableProcess(doc);
    }

    public List<AbstractAnalyticRuleDoc> getRules() {
        return analyticLoader.loadAll();
    }

    public AnalyticTracker getTracker(final AbstractAnalyticRuleDoc analyticRuleDoc) {
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

    public List<Meta> findMeta(final ExpressionOperator expression,
                               final Long minMetaId,
                               final Long minMetaCreateTimeMs,
                               final Long maxMetaCreateTimeMs,
                               final int length) {
        // Don't select deleted streams.
        final ExpressionOperator statusExpression = ExpressionOperator.builder().op(Op.OR)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                .build();

        ExpressionOperator.Builder builder = ExpressionOperator.builder()
                .addOperator(expression);
        if (minMetaId != null) {
            builder = builder.addIdTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId);
        }

        if (minMetaCreateTimeMs != null) {
            builder = builder.addDateTerm(MetaFields.CREATE_TIME,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    DateUtil.createNormalDateTimeString(minMetaCreateTimeMs));
        }
        if (maxMetaCreateTimeMs != null) {
            builder = builder.addDateTerm(MetaFields.CREATE_TIME,
                    Condition.LESS_THAN_OR_EQUAL_TO,
                    DateUtil.createNormalDateTimeString(maxMetaCreateTimeMs));
        }
        builder = builder.addOperator(statusExpression);

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(builder.build());
        findMetaCriteria.setSort(MetaFields.ID.getFldName(), false, false);
        findMetaCriteria.obtainPageRequest().setLength(length);

        return metaService.find(findMetaCriteria).getValues();
    }

    public String getErrorFeedName(final AbstractAnalyticRuleDoc analyticRuleDoc) {
        String errorFeedName = null;
        if (analyticRuleDoc.getErrorFeed() != null) {
            errorFeedName = analyticRuleDoc.getErrorFeed().getName();
        }
        if (errorFeedName == null) {
            LOGGER.debug(() -> "Error feed not defined: " +
                               AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc));

            final DocRef defaultErrorFeed = analyticUiDefaultConfigProvider.get().getDefaultErrorFeed();
            if (defaultErrorFeed == null) {
                throw new RuntimeException("Default error feed not defined");
            }
            errorFeedName = defaultErrorFeed.getName();
        }
        return errorFeedName;
    }
}
