/*
 * Copyright 2017 Crown Copyright
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
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticTracker;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.view.shared.ViewDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged(OperationType.UNLOGGED)
class AnalyticProcessResourceImpl implements AnalyticProcessResource {

    private final Provider<AnalyticTrackerDao> analyticProcessorTrackerDaoProvider;
    private final Provider<AnalyticRuleProcessors> analyticRuleProcessorsProvider;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;

    @Inject
    AnalyticProcessResourceImpl(final Provider<AnalyticTrackerDao> analyticProcessorTrackerDaoProvider,
                                final Provider<AnalyticRuleProcessors> analyticRuleProcessorsProvider,
                                final Provider<ProcessorFilterService> processorFilterServiceProvider) {
        this.analyticProcessorTrackerDaoProvider = analyticProcessorTrackerDaoProvider;
        this.analyticRuleProcessorsProvider = analyticRuleProcessorsProvider;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
    }

    @Override
    public AnalyticTracker getTracker(final String analyticUuid) {
        final AnalyticTrackerDao analyticTrackerDao =
                analyticProcessorTrackerDaoProvider.get();
        return analyticTrackerDao.get(analyticUuid).orElse(null);
    }

    @Override
    public ProcessorFilterRow getFilter(final AnalyticRuleDoc analyticRuleDoc) {
        if (analyticRuleDoc != null) {
            final List<ProcessorFilter> list = new ArrayList<>();
            final AnalyticRuleProcessors analyticRuleProcessors = analyticRuleProcessorsProvider.get();
            final Optional<ViewDoc> optionalViewDoc = analyticRuleProcessors.getViewDoc(analyticRuleDoc);
            optionalViewDoc.ifPresent(viewDoc -> {
                final List<Processor> processors = analyticRuleProcessors.getProcessor(viewDoc);
                if (processors.size() == 1) {
                    final Processor processor = processors.get(0);
                    final List<ProcessorFilter> existing = analyticRuleProcessors
                            .getProcessorFilters(analyticRuleDoc, processor);
                    list.addAll(existing);
                }
            });

            if (list.size() == 1) {
                return processorFilterServiceProvider.get().getRow(list.get(0));
            }
        }

        return null;
    }
}
