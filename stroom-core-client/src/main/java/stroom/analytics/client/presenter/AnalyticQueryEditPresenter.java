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

package stroom.analytics.client.presenter;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.query.client.presenter.QueryEditPresenter;

import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Inject;

public class AnalyticQueryEditPresenter
        extends AbstractQueryEditPresenter<AnalyticRuleDoc> {

    @Inject
    public AnalyticQueryEditPresenter(final EventBus eventBus,
                                      final QueryEditPresenter queryEditPresenter) {
        super(eventBus, queryEditPresenter);
    }

    void start() {
        queryEditPresenter.start();
    }

    void stop() {
        queryEditPresenter.stop();
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc entity) {
        return entity
                .copy()
                .timeRange(queryEditPresenter.getTimeRange())
                .query(queryEditPresenter.getQuery())
                .build();
    }
}
