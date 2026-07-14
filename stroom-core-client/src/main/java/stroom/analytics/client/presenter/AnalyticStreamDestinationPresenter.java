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

import stroom.analytics.client.presenter.AnalyticStreamDestinationPresenter.AnalyticStreamDestinationView;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.document.client.event.ChangeEvent;
import stroom.document.client.event.ChangeEvent.ChangeHandler;
import stroom.document.client.event.ChangeUiHandlers;
import stroom.document.client.event.HasChangeHandlers;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class AnalyticStreamDestinationPresenter
        extends MyPresenterWidget<AnalyticStreamDestinationView>
        implements ChangeUiHandlers, HasChangeHandlers {

    private final DocSelectionBoxPresenter feedPresenter;

    @Inject
    public AnalyticStreamDestinationPresenter(final EventBus eventBus,
                                              final AnalyticStreamDestinationView view,
                                              final DocSelectionBoxPresenter feedPresenter) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.feedPresenter = feedPresenter;

        feedPresenter.setIncludedTypes(FeedDoc.TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        view.setDestinationFeedView(feedPresenter.getView());
    }

    @Override
    protected void onBind() {
        registerHandler(feedPresenter.addDataSelectionHandler(e -> onChange()));
    }

    public void read(final NotificationStreamDestination streamDestination) {
        if (streamDestination != null) {
            getView().setUseSourceFeedIfPossible(streamDestination.isUseSourceFeedIfPossible());
            feedPresenter.setSelectedEntityReference(streamDestination.getDestinationFeed(), true);
        }
    }

    public NotificationStreamDestination write() {
        return new NotificationStreamDestination(
                feedPresenter.getSelectedEntityReference(),
                getView().isUseSourceFeedIfPossible());
    }

    @Override
    public void onChange() {
        ChangeEvent.fire(this);
    }

    @Override
    public HandlerRegistration addChangeHandler(final ChangeHandler handler) {
        return addHandlerToSource(ChangeEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface AnalyticStreamDestinationView extends View, HasUiHandlers<ChangeUiHandlers> {

        void setDestinationFeedView(View view);

        boolean isUseSourceFeedIfPossible();

        void setUseSourceFeedIfPossible(boolean useSourceFeedIfPossible);
    }
}
