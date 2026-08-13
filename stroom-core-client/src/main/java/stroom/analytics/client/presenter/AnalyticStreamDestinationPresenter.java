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
import stroom.config.global.client.presenter.ConfigDefaultSetter;
import stroom.config.global.shared.ConfigTarget;
import stroom.document.client.event.ChangeEvent;
import stroom.document.client.event.ChangeEvent.ChangeHandler;
import stroom.document.client.event.HasChangeHandlers;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class AnalyticStreamDestinationPresenter
        extends MyPresenterWidget<AnalyticStreamDestinationView>
        implements AnalyticStreamDestinationUiHandlers, HasChangeHandlers {

    private final DocSelectionBoxPresenter feedPresenter;
    private final ConfigDefaultSetter configDefaultSetter;
    private ConfigTarget configTarget = ConfigTarget.ANALYTIC_UI_DEFAULT;

    @Inject
    public AnalyticStreamDestinationPresenter(final EventBus eventBus,
                                              final AnalyticStreamDestinationView view,
                                              final DocSelectionBoxPresenter feedPresenter,
                                              final ConfigDefaultSetter configDefaultSetter) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.feedPresenter = feedPresenter;
        this.configDefaultSetter = configDefaultSetter;

        feedPresenter.setIncludedTypes(FeedDoc.TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        view.setDestinationFeedView(feedPresenter.getView());

        // Only an administrator can change a global property, so don't offer it to anyone else.
        view.setSetDefaultVisible(configDefaultSetter.isAllowed());
    }

    /**
     * Reports and analytic rules keep their defaults separately, so the owning document decides which one this
     * feed would be promoted to.
     */
    public void setConfigTarget(final ConfigTarget configTarget) {
        this.configTarget = configTarget;
    }

    @Override
    protected void onBind() {
        registerHandler(feedPresenter.addDataSelectionHandler(e -> onChange()));
    }

    @Override
    public void onSetDefaultDestinationFeed() {
        configDefaultSetter.setDefault(
                this,
                configTarget,
                AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_DESTINATION_FEED,
                feedPresenter.getSelectedEntityReference(),
                "destination feed",
                this);
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


    public interface AnalyticStreamDestinationView extends View,
            HasUiHandlers<AnalyticStreamDestinationUiHandlers> {

        void setDestinationFeedView(View view);

        void setSetDefaultVisible(boolean visible);

        boolean isUseSourceFeedIfPossible();

        void setUseSourceFeedIfPossible(boolean useSourceFeedIfPossible);
    }
}
