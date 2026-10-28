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

package stroom.analytics.client.component;

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
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Adds or edits an analytic's notification ([AnalyticNotificationEdit]) in a dialog made for one
/// use, its destination's settings shown for the destination type chosen.
public class AnalyticNotificationEditController extends Controller {

    private final AnalyticNotificationEdit view;
    private final AnalyticStreamDestinationController analyticStreamDestination;
    private final AnalyticEmailDestinationController analyticEmailDestination;
    private final UiConfigCache uiConfigCache;
    private String notificationUUID;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                  The application's event bus.
    /// @param view                      The content.
    /// @param analyticStreamDestination A stream destination's settings.
    /// @param analyticEmailDestination  An email destination's settings.
    /// @param uiConfigCache             Gives the destinations' defaults.
    @Inject
    public AnalyticNotificationEditController(final EventBus eventBus,
                                              final AnalyticNotificationEdit view,
                                              final AnalyticStreamDestinationController analyticStreamDestination,
                                              final AnalyticEmailDestinationController analyticEmailDestination,
                                              final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.analyticStreamDestination = own(analyticStreamDestination);
        this.analyticEmailDestination = own(analyticEmailDestination);
        this.uiConfigCache = uiConfigCache;
        initWidget(view);

        view.setOnChange(this::onChange);
        analyticStreamDestination.setOnChange(this::onChange);
        analyticEmailDestination.setOnChange(this::onChange);
    }

    /// Shows a notification in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener              What shows it.
    /// @param docRef              The analytic or report the notification is for.
    /// @param analyticProcessType How the analytic is processed.
    /// @param config              The notification.
    /// @param caption             The dialog's caption.
    /// @param onOk                Given the edited notification on OK; not called on Cancel.
    public void edit(final HasControllerNode opener,
                     final DocRef docRef,
                     final AnalyticProcessType analyticProcessType,
                     final NotificationConfig config,
                     final String caption,
                     final Consumer<NotificationConfig> onOk) {
        read(docRef, analyticProcessType, config);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(564, 564))
                .caption(caption)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(write());
                    }
                    e.hide();
                })
                .fire();
    }

    private void read(final DocRef docRef,
                      final AnalyticProcessType analyticProcessType,
                      final NotificationConfig config) {
        analyticStreamDestination.setAnalyticProcessType(analyticProcessType);
        uiConfigCache.get(guard(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                if (config != null) {
                    notificationUUID = config.getUuid();
                    view.setEnabled(config.isEnabled());
                    view.setLimitNotifications(config.isLimitNotifications());
                    view.setMaxNotifications(config.getMaxNotifications());
                    view.setResumeAfter(config.getResumeAfter());
                    view.setDestinationType(config.getDestinationType());
                    showDestination(config.getDestinationType());
                }

                // Initialise the destinations whether we have config or not, so they get the right
                // defaults
                final AbstractAnalyticUiDefaultConfig defaultConfig;
                if (ReportDoc.TYPE.equals(docRef.getType())) {
                    defaultConfig = extendedUiConfig.getReportUiDefaultConfig();
                    analyticStreamDestination.setConfigTarget(ConfigTarget.REPORT_UI_DEFAULT);
                } else {
                    defaultConfig = extendedUiConfig.getAnalyticUiDefaultConfig();
                    analyticStreamDestination.setConfigTarget(ConfigTarget.ANALYTIC_UI_DEFAULT);
                }

                final NotificationDestination destination = NullSafe.get(config, NotificationConfig::getDestination);
                NullSafe.consume(getOrDefaultEmailDestination(destination, defaultConfig),
                        analyticEmailDestination::read);
                NullSafe.consume(getOrDefaultStreamDestination(destination, defaultConfig),
                        analyticStreamDestination::read);
            }
        }), taskMonitor());
    }

    private NotificationEmailDestination getOrDefaultEmailDestination(
            final NotificationDestination destination,
            final AbstractAnalyticUiDefaultConfig analyticUiDefaultConfig) {
        if (destination instanceof final NotificationEmailDestination emailDestination) {
            return emailDestination;
        }
        return NotificationEmailDestination.builder()
                .subjectTemplate(analyticUiDefaultConfig.getDefaultSubjectTemplate())
                .bodyTemplate(analyticUiDefaultConfig.getDefaultBodyTemplate())
                .build();
    }

    private NotificationStreamDestination getOrDefaultStreamDestination(
            final NotificationDestination destination,
            final AbstractAnalyticUiDefaultConfig analyticUiDefaultConfig) {
        if (destination instanceof final NotificationStreamDestination streamDestination) {
            return streamDestination;
        }
        return NotificationStreamDestination.builder()
                .destinationFeed(analyticUiDefaultConfig.getDefaultDestinationFeed())
                .build();
    }

    private NotificationConfig write() {
        NotificationDestination destination = null;
        if (view.getDestinationType() != null) {
            switch (view.getDestinationType()) {
                case EMAIL: {
                    destination = analyticEmailDestination.write();
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
                .enabled(view.isEnabled())
                .limitNotifications(view.isLimitNotifications())
                .maxNotifications(view.getMaxNotifications())
                .resumeAfter(view.getResumeAfter())
                .destinationType(view.getDestinationType())
                .destination(destination)
                .build();
    }

    private void showDestination(final NotificationDestinationType destinationType) {
        if (destinationType != null) {
            switch (destinationType) {
                case EMAIL: {
                    view.setDestination(analyticEmailDestination);
                    break;
                }
                case STREAM: {
                    view.setDestination(analyticStreamDestination);
                    break;
                }
            }
        }
    }

    private void onChange() {
        showDestination(view.getDestinationType());
    }

}
