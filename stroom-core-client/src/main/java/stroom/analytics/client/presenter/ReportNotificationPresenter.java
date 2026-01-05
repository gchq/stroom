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

package stroom.analytics.client.presenter;

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ReportDoc;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.ui.config.client.UiConfigCache;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class ReportNotificationPresenter
        extends AbstractNotificationPresenter<ReportDoc> {

    private final ReportNotificationListPresenter notificationListPresenter;


    @Inject
    public ReportNotificationPresenter(final EventBus eventBus,
                                       final AnalyticNotificationView view,
                                       final DocSelectionBoxPresenter errorFeedPresenter,
                                       final ReportNotificationListPresenter notificationListPresenter,
                                       final UiConfigCache uiConfigCache) {
        super(eventBus,
                view,
                errorFeedPresenter,
                notificationListPresenter,
                uiConfigCache);
        this.notificationListPresenter = notificationListPresenter;
    }

    @Override
    protected ReportDoc onWrite(ReportDoc reportDoc) {
        reportDoc = notificationListPresenter.onWrite(reportDoc);
        return reportDoc
                .copy()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .errorFeed(errorFeedPresenter.getSelectedEntityReference())
                .includeRuleDocumentation(getView().isIncludeRuleDocumentation())
                .build();
    }
}
