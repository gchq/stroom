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
import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.analytics.shared.AnalyticNotificationResource;
import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class AnalyticNotificationEditPresenter
        extends MyPresenterWidget<AnalyticNotificationEditView> {

    private static final AnalyticNotificationResource ANALYTIC_NOTIFICATION_RESOURCE =
            GWT.create(AnalyticNotificationResource.class);

    private final RestFactory restFactory;

    private final EntityDropDownPresenter feedPresenter;

    @Inject
    public AnalyticNotificationEditPresenter(final EventBus eventBus,
                                             final AnalyticNotificationEditView view,
                                             final RestFactory restFactory,
                                             final EntityDropDownPresenter feedPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.feedPresenter = feedPresenter;

        feedPresenter.setIncludedTypes(FeedDoc.DOCUMENT_TYPE);
        feedPresenter.setRequiredPermissions(DocumentPermissionNames.READ);
        view.setDestinationFeedView(feedPresenter.getView());
    }

    public void show(final AnalyticNotification notification,
                     final Consumer<AnalyticNotification> consumer,
                     final boolean create) {
        final String caption = create
                ? "Create Notification"
                : "Edit Notification";
        read(notification);

        final PopupSize popupSize = PopupSize.resizable(640, 480);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AnalyticNotificationStreamConfig config = AnalyticNotificationStreamConfig.builder()
                                .timeToWaitForData(getView().getTimeToWaitForData())
                                .destinationFeed(feedPresenter.getSelectedEntityReference())
                                .useSourceFeedIfPossible(getView().isUseSourceFeedIfPossible())
                                .build();

                        final AnalyticNotification updated = notification.copy()
                                .enabled(getView().isEnabled())
                                .config(config)
                                .build();

                        final Rest<AnalyticNotification> rest = restFactory.create();
                        if (create) {
                            rest
                                    .onSuccess(result -> {
                                        consumer.accept(result);
                                        e.hide();
                                    })
                                    .call(ANALYTIC_NOTIFICATION_RESOURCE)
                                    .create(updated);
                        } else {
                            rest
                                    .onSuccess(result -> {
                                        consumer.accept(result);
                                        e.hide();
                                    })
                                    .call(ANALYTIC_NOTIFICATION_RESOURCE)
                                    .update(updated.getUuid(), updated);
                        }

                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void read(final AnalyticNotification notification) {
        getView().setEnabled(notification.isEnabled());

        final AnalyticNotificationConfig config = notification.getConfig();
        if (config instanceof AnalyticNotificationStreamConfig) {
            final AnalyticNotificationStreamConfig streamConfig = (AnalyticNotificationStreamConfig) config;
            getView().setTimeToWaitForData(streamConfig.getTimeToWaitForData());
            getView().setUseSourceFeedIfPossible(streamConfig.isUseSourceFeedIfPossible());
            feedPresenter.setSelectedEntityReference(streamConfig.getDestinationFeed());
        }
    }

    public interface AnalyticNotificationEditView extends View, Focus {

        boolean isEnabled();

        void setEnabled(final boolean enabled);

        SimpleDuration getTimeToWaitForData();

        void setTimeToWaitForData(SimpleDuration timeToWaitForData);

        void setDestinationFeedView(View view);

        boolean isUseSourceFeedIfPossible();

        void setUseSourceFeedIfPossible(final boolean useSourceFeedIfPossible);
    }
}
