/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AnalyticNotificationEditPresenter.AnalyticNotificationEditView;
import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermissionNames;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class AnalyticNotificationEditPresenter
        extends DocumentEditPresenter<AnalyticNotificationEditView, AnalyticRuleDoc> {

    private final EntityDropDownPresenter feedPresenter;

    @Inject
    public AnalyticNotificationEditPresenter(final EventBus eventBus,
                                             final AnalyticNotificationEditView view,
                                             final EntityDropDownPresenter feedPresenter) {
        super(eventBus, view);
        this.feedPresenter = feedPresenter;

        feedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);
        view.setDestinationFeedView(feedPresenter.getView());
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document, final boolean readOnly) {
        final AnalyticNotificationConfig config = document.getAnalyticNotificationConfig();
        if (config instanceof AnalyticNotificationStreamConfig) {
            final AnalyticNotificationStreamConfig streamConfig = (AnalyticNotificationStreamConfig) config;
            getView().setUseSourceFeedIfPossible(streamConfig.isUseSourceFeedIfPossible());
            feedPresenter.setSelectedEntityReference(streamConfig.getDestinationFeed());
        }
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc document) {
        final AnalyticNotificationStreamConfig streamConfig = new AnalyticNotificationStreamConfig(
                feedPresenter.getSelectedEntityReference(),
                getView().isUseSourceFeedIfPossible());
        return document.copy().analyticNotificationConfig(streamConfig).build();
    }

    public interface AnalyticNotificationEditView extends View {

        void setDestinationFeedView(View view);

        boolean isUseSourceFeedIfPossible();

        void setUseSourceFeedIfPossible(final boolean useSourceFeedIfPossible);
    }
}
