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

import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.AnalyticNotificationResource;
import stroom.analytics.shared.AnalyticNotificationRow;
import stroom.analytics.shared.AnalyticNotificationState;
import stroom.analytics.shared.FindAnalyticNotificationCriteria;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;

class AnalyticNotificationResourceImpl implements AnalyticNotificationResource {

    private final Provider<AnalyticNotificationDao> analyticNotificationDaoProvider;
    private final Provider<AnalyticNotificationStateDao> analyticNotificationStateDaoProvider;

    @Inject
    AnalyticNotificationResourceImpl(final Provider<AnalyticNotificationDao> analyticNotificationDaoProvider,
                                     final Provider<AnalyticNotificationStateDao> analyticNotificationStateDaoProvider) {
        this.analyticNotificationDaoProvider = analyticNotificationDaoProvider;
        this.analyticNotificationStateDaoProvider = analyticNotificationStateDaoProvider;
    }

    @Override
    public ResultPage<AnalyticNotificationRow> find(final FindAnalyticNotificationCriteria criteria) {
        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
        final AnalyticNotificationStateDao analyticNotificationStateDao = analyticNotificationStateDaoProvider.get();
        final List<AnalyticNotification> notifications =
                analyticNotificationDao.getByAnalyticUuid(criteria.getAnalyticDocUuid());
        final List<AnalyticNotificationRow> list = new ArrayList<>();
        for (final AnalyticNotification notification : notifications) {
            final Optional<AnalyticNotificationState> state = analyticNotificationStateDao.get(notification.getUuid());
            final AnalyticNotificationRow row = new AnalyticNotificationRow(notification, state.orElse(null));
            list.add(row);
        }
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public AnalyticNotification create(final AnalyticNotification notification) {
        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
        return analyticNotificationDao.create(notification);
    }

    @Override
    public AnalyticNotification update(final String uuid, final AnalyticNotification notification) {
        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
        return analyticNotificationDao.update(notification);
    }

    @Override
    public Boolean delete(final String uuid, final AnalyticNotification notification) {
        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
        return analyticNotificationDao.delete(notification);
    }
}
