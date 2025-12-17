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

import stroom.analytics.client.presenter.AnalyticNotificationEditPresenter.AnalyticNotificationEditView;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestination;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.ReportDoc;
import stroom.dashboard.client.main.UniqueUtil;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class AnalyticNotificationEditPresenter
        extends MyPresenterWidget<AnalyticNotificationEditView>
        implements DirtyUiHandlers {

    private final AnalyticStreamDestinationPresenter analyticStreamDestinationPresenter;
    private final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter;
    private final UiConfigCache uiConfigCache;
    private String notificationUUID;

    @SuppressWarnings("checkstyle:LineLength")
    @Inject
    public AnalyticNotificationEditPresenter(final EventBus eventBus,
                                             final AnalyticNotificationEditView view,
                                             final AnalyticStreamDestinationPresenter analyticStreamDestinationPresenter,
                                             final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter,
                                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.uiConfigCache = uiConfigCache;
        view.setUiHandlers(this);
        this.analyticStreamDestinationPresenter = analyticStreamDestinationPresenter;
        this.analyticEmailDestinationPresenter = analyticEmailDestinationPresenter;
    }

    @Override
    protected void onBind() {
        registerHandler(analyticStreamDestinationPresenter.addDirtyHandler(e -> onDirty()));
        registerHandler(analyticEmailDestinationPresenter.addDirtyHandler(e -> onDirty()));
    }

    public void read(final DocRef docRef,
                     final NotificationConfig config) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                if (config != null) {
                    notificationUUID = config.getUuid();
                    getView().setEnabled(config.isEnabled());
                    getView().setLimitNotifications(config.isLimitNotifications());
                    getView().setMaxNotifications(config.getMaxNotifications());
                    getView().setResumeAfter(config.getResumeAfter());
                    getView().setDestinationType(config.getDestinationType());

                    setDestinationPresenter(config.getDestinationType());
                }

                // Initialise the sub presenters whether we have config or not, so they get the right defaults
                final AbstractAnalyticUiDefaultConfig defaultConfig;
                if (ReportDoc.TYPE.equals(docRef.getType())) {
                    defaultConfig = extendedUiConfig.getReportUiDefaultConfig();
                } else {
                    defaultConfig = extendedUiConfig.getAnalyticUiDefaultConfig();
                }

                final NotificationDestination destination = NullSafe.get(
                        config,
                        NotificationConfig::getDestination);

                final NotificationEmailDestination emailDestination = getOrDefaultEmailDestination(
                        destination, defaultConfig);
                NullSafe.consume(emailDestination, analyticEmailDestinationPresenter::read);

                final NotificationStreamDestination streamDestination = getOrDefaultStreamDestination(
                        destination, defaultConfig);
                NullSafe.consume(streamDestination, analyticStreamDestinationPresenter::read);
            }
        }, this);
    }

    private NotificationEmailDestination getOrDefaultEmailDestination(
            final NotificationDestination destination,
            final AbstractAnalyticUiDefaultConfig analyticUiDefaultConfig) {

        final NotificationEmailDestination emailDestination;
        if (destination instanceof NotificationEmailDestination) {
            emailDestination = (NotificationEmailDestination) destination;
        } else {
            emailDestination = NotificationEmailDestination.builder()
                    .subjectTemplate(analyticUiDefaultConfig.getDefaultSubjectTemplate())
                    .bodyTemplate(analyticUiDefaultConfig.getDefaultBodyTemplate())
                    .build();
        }
        return emailDestination;
    }

    private NotificationStreamDestination getOrDefaultStreamDestination(
            final NotificationDestination destination,
            final AbstractAnalyticUiDefaultConfig analyticUiDefaultConfig) {

        final NotificationStreamDestination streamDestination;
        if (destination instanceof NotificationStreamDestination) {
            streamDestination = (NotificationStreamDestination) destination;
        } else {
            streamDestination = NotificationStreamDestination.builder()
                    .destinationFeed(analyticUiDefaultConfig.getDefaultDestinationFeed())
                    .build();
        }
        return streamDestination;
    }

    public NotificationConfig write() {
        NotificationDestination destination = null;
        if (getView().getDestinationType() != null) {
            switch (getView().getDestinationType()) {
                case EMAIL: {
                    destination = analyticEmailDestinationPresenter.write();
                    break;
                }
                case STREAM: {
                    destination = analyticStreamDestinationPresenter.write();
                    break;
                }
            }
        }

        if (notificationUUID == null) {
            notificationUUID = UniqueUtil.generateUUID();
        }

        return NotificationConfig
                .builder()
                .uuid(notificationUUID)
                .enabled(getView().isEnabled())
                .limitNotifications(getView().isLimitNotifications())
                .maxNotifications(getView().getMaxNotifications())
                .resumeAfter(getView().getResumeAfter())
                .destinationType(getView().getDestinationType())
                .destination(destination)
                .build();
    }

    private void setDestinationPresenter(final NotificationDestinationType destinationType) {
        if (destinationType != null) {
            switch (destinationType) {
                case EMAIL: {
                    getView().setDestinationView(analyticEmailDestinationPresenter.getView());
                    break;
                }
                case STREAM: {
                    getView().setDestinationView(analyticStreamDestinationPresenter.getView());
                    break;
                }
            }
        }
    }

    @Override
    public void onDirty() {
        setDestinationPresenter(getView().getDestinationType());
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        analyticEmailDestinationPresenter.setTaskMonitorFactory(taskMonitorFactory);
//        analyticStreamDestinationPresenter.setTaskListener(taskListener);
    }

    public interface AnalyticNotificationEditView extends View, HasUiHandlers<DirtyUiHandlers> {


        boolean isEnabled();

        void setEnabled(boolean enabled);

        boolean isLimitNotifications();

        void setLimitNotifications(boolean limitNotifications);

        int getMaxNotifications();

        void setMaxNotifications(int maxNotifications);

        SimpleDuration getResumeAfter();

        void setResumeAfter(SimpleDuration resumeAfter);

        NotificationDestinationType getDestinationType();

        void setDestinationType(NotificationDestinationType destinationType);

        void setDestinationView(View view);
    }
}
