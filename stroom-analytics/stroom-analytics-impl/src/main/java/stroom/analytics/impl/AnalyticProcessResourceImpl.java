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

package stroom.analytics.impl;

import stroom.analytics.rule.impl.AnalyticRuleProcessors;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticTracker;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.ExpressionOperator;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged(OperationType.UNLOGGED)
class AnalyticProcessResourceImpl implements AnalyticProcessResource {

    private final Provider<AnalyticTrackerDao> analyticProcessorTrackerDaoProvider;
    private final Provider<AnalyticRuleProcessors> analyticRuleProcessorsProvider;

    @Inject
    AnalyticProcessResourceImpl(final Provider<AnalyticTrackerDao> analyticProcessorTrackerDaoProvider,
                                final Provider<AnalyticRuleProcessors> analyticRuleProcessorsProvider) {
        this.analyticProcessorTrackerDaoProvider = analyticProcessorTrackerDaoProvider;
        this.analyticRuleProcessorsProvider = analyticRuleProcessorsProvider;
    }

    @Override
    public AnalyticTracker getTracker(final String analyticUuid) {
        final AnalyticTrackerDao analyticTrackerDao =
                analyticProcessorTrackerDaoProvider.get();
        return analyticTrackerDao.get(analyticUuid).orElse(null);
    }

    @Override
    public ExpressionOperator getDefaultProcessingFilterExpression(final String query) {
        return analyticRuleProcessorsProvider.get().getDefaultProcessingFilterExpression(query);
    }
}
