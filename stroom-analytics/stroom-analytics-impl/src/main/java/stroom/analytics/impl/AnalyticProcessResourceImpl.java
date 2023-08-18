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

import stroom.analytics.shared.AnalyticProcess;
import stroom.analytics.shared.AnalyticProcessResource;
import stroom.analytics.shared.AnalyticProcessTracker;
import stroom.analytics.shared.FindAnalyticProcessCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.shared.ResultPage;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged(OperationType.UNLOGGED)
class AnalyticProcessResourceImpl implements AnalyticProcessResource {

    private final Provider<AnalyticProcessDao> analyticProcessorDaoProvider;
    private final Provider<AnalyticProcessTrackerDao> analyticProcessorTrackerDaoProvider;

    @Inject
    AnalyticProcessResourceImpl(final Provider<AnalyticProcessDao> analyticProcessorDaoProvider,
                                final Provider<AnalyticProcessTrackerDao>
                                          analyticProcessorTrackerDaoProvider) {
        this.analyticProcessorDaoProvider = analyticProcessorDaoProvider;
        this.analyticProcessorTrackerDaoProvider = analyticProcessorTrackerDaoProvider;
    }

    @Override
    public ResultPage<AnalyticProcess> find(final FindAnalyticProcessCriteria criteria) {
        final AnalyticProcessDao analyticProcessDao = analyticProcessorDaoProvider.get();
        final Optional<AnalyticProcess> optionalFilter =
                analyticProcessDao.getByAnalyticUuid(criteria.getAnalyticDocUuid());
        final List<AnalyticProcess> list = optionalFilter
                .map(List::of)
                .orElse(Collections.emptyList());
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public AnalyticProcess create(final AnalyticProcess analyticProcess) {
        final AnalyticProcessDao analyticProcessDao = analyticProcessorDaoProvider.get();
        return analyticProcessDao.create(analyticProcess);
    }

    @Override
    public AnalyticProcess update(final String uuid, final AnalyticProcess analyticProcess) {
        final AnalyticProcessDao analyticProcessDao = analyticProcessorDaoProvider.get();
        return analyticProcessDao.update(analyticProcess);
    }

    @Override
    public Boolean delete(final String uuid, final AnalyticProcess analyticProcess) {
        final AnalyticProcessDao analyticProcessDao = analyticProcessorDaoProvider.get();
        return analyticProcessDao.delete(analyticProcess);
    }

    @Override
    public AnalyticProcessTracker getTracker(final String filterUuid) {
        final AnalyticProcessTrackerDao analyticProcessTrackerDao =
                analyticProcessorTrackerDaoProvider.get();
        return analyticProcessTrackerDao.get(filterUuid).orElse(null);
    }
}
