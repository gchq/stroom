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

import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticProcessorFilterResource;
import stroom.analytics.shared.AnalyticProcessorFilterRow;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.FindAnalyticProcessorFilterCriteria;
import stroom.util.shared.ResultPage;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;

class AnalyticProcessorFilterResourceImpl implements AnalyticProcessorFilterResource {

    private final Provider<AnalyticProcessorFilterDao> analyticProcessorFilterDaoProvider;
    private final Provider<AnalyticProcessorFilterTrackerDao> analyticProcessorFilterTrackerDaoProvider;

    @Inject
    AnalyticProcessorFilterResourceImpl(final Provider<AnalyticProcessorFilterDao> analyticProcessorFilterDaoProvider,
                                        final Provider<AnalyticProcessorFilterTrackerDao> analyticProcessorFilterTrackerDaoProvider) {
        this.analyticProcessorFilterDaoProvider = analyticProcessorFilterDaoProvider;
        this.analyticProcessorFilterTrackerDaoProvider = analyticProcessorFilterTrackerDaoProvider;
    }

    @Override
    public ResultPage<AnalyticProcessorFilterRow> find(final FindAnalyticProcessorFilterCriteria criteria) {
        final AnalyticProcessorFilterDao analyticProcessorFilterDao = analyticProcessorFilterDaoProvider.get();
        final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao =
                analyticProcessorFilterTrackerDaoProvider.get();
        final Optional<AnalyticProcessorFilter> optionalFilter =
                analyticProcessorFilterDao.getByAnalyticUuid(criteria.getAnalyticDocUuid());
        final List<AnalyticProcessorFilterRow> list = optionalFilter.map(filter -> {
                    final Optional<AnalyticProcessorFilterTracker> optionalTracker =
                            analyticProcessorFilterTrackerDao.get(filter.getUuid());
                    return new AnalyticProcessorFilterRow(filter, optionalTracker.orElse(null));
                })
                .map(List::of)
                .orElse(Collections.emptyList());
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public AnalyticProcessorFilter create(final AnalyticProcessorFilter processorFilter) {
        final AnalyticProcessorFilterDao analyticProcessorFilterDao = analyticProcessorFilterDaoProvider.get();
        return analyticProcessorFilterDao.create(processorFilter);
    }

    @Override
    public AnalyticProcessorFilter update(final String uuid, final AnalyticProcessorFilter processorFilter) {
        final AnalyticProcessorFilterDao analyticProcessorFilterDao = analyticProcessorFilterDaoProvider.get();
        return analyticProcessorFilterDao.update(processorFilter);
    }

    @Override
    public Boolean delete(final String uuid, final AnalyticProcessorFilter processorFilter) {
        final AnalyticProcessorFilterDao analyticProcessorFilterDao = analyticProcessorFilterDaoProvider.get();
        return analyticProcessorFilterDao.delete(processorFilter);
    }
}
