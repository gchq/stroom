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

import stroom.analytics.client.component.AnalyticStreamDestinationController;
import stroom.analytics.client.presenter.AnalyticNotificationEditPresenter.AnalyticNotificationEditView;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestination;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.ReportDoc;
import stroom.config.global.shared.ConfigTarget;
import stroom.dashboard.client.main.UniqueUtil;
import stroom.docref.DocRef;
import stroom.document.client.event.ChangeUiHandlers;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class AnalyticNotificationEditPresenter
        extends MyPresenterWidget<AnalyticNotificationEditView>
        implements ChangeUiHandlers {

    private final AnalyticStreamDestinationController analyticStreamDestination;
    private final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter;
    private final UiConfigCache uiConfigCache;
    private String notificationUUID;

    @SuppressWarnings("checkstyle:LineLength")
    @Inject
    public AnalyticNotificationEditPresenter(final EventBus eventBus,
                                             final AnalyticNotificationEditView view,
                                             final AnalyticStreamDestinationController analyticStreamDestination,
                                             final AnalyticEmailDestinationPresenter analyticEmailDestinationPresenter,
                                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        own(analyticStreamDestination);
        own(analyticEmailDestinationPresenter);
        this.uiConfigCache = uiConfigCache;
        view.setUiHandlers(this);
        this.analyticStreamDestination = analyticStreamDestination;
        this.analyticEmailDestinationPresenter = analyticEmailDestinationPresenter;

        analyticStreamDestination.setOnChange(this::onChange);
        analyticEmailDestinationPresenter.setOnChange(this::onChange);
    }

    public void read(final DocRef docRef,
                     final AnalyticProcessType analyticProcessType,
                     final NotificationConfig config) {
        analyticStreamDestination.setAnalyticProcessType(analyticProcessType);
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
                    analyticStreamDestination.setConfigTarget(ConfigTarget.REPORT_UI_DEFAULT);
                } else {
                    defaultConfig = extendedUiConfig.getAnalyticUiDefaultConfig();
                    analyticStreamDestination.setConfigTarget(ConfigTarget.ANALYTIC_UI_DEFAULT);
                }

                final NotificationDestination destination = NullSafe.get(
                        config,
                        NotificationConfig::getDestination);

                final NotificationEmailDestination emailDestination = getOrDefaultEmailDestination(
                        destination, defaultConfig);
                NullSafe.consume(emailDestination, analyticEmailDestinationPresenter::read);

                final NotificationStreamDestination streamDestination = getOrDefaultStreamDestination(
                        destination, defaultConfig);
                NullSafe.consume(streamDestination, analyticStreamDestination::read);
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
                    destination = analyticStreamDestination.write();
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
                    getView().setDestinationView(analyticStreamDestination);
                    break;
                }
            }
        }
    }

    @Override
    public void onChange() {
        setDestinationPresenter(getView().getDestinationType());
    }

    public interface AnalyticNotificationEditView extends View, HasUiHandlers<ChangeUiHandlers> {


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

        void setDestinationView(IsWidget view);
    }
}
