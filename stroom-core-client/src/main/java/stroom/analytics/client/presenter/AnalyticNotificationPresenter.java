/*
 * Copyright 2023 Crown Copyright
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
import stroom.analytics.shared.QueryLanguageVersion;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class AnalyticNotificationPresenter
        extends AbstractNotificationPresenter<AnalyticRuleDoc> {

    private final AnalyticNotificationListPresenter notificationListPresenter;


    @Inject
    public AnalyticNotificationPresenter(final EventBus eventBus,
                                         final AnalyticNotificationView view,
                                         final AnalyticNotificationListPresenter notificationListPresenter) {
        super(eventBus, view, notificationListPresenter);
        this.notificationListPresenter = notificationListPresenter;
    }

    @Override
    protected AnalyticRuleDoc onWrite(AnalyticRuleDoc analyticRuleDoc) {
        analyticRuleDoc = notificationListPresenter.onWrite(analyticRuleDoc);
        return analyticRuleDoc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .build();
    }
}
