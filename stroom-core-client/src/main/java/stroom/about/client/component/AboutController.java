/*
 * Copyright 2016 Crown Copyright
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

package stroom.about.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.SessionInfoResource;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.BuildInfo;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The About dialog ([About]), made for one use: fetches the build and node Stroom is running on,
/// and the About text configured for it.
public class AboutController extends Controller {

    private static final SessionInfoResource SESSION_INFO_RESOURCE = GWT.create(SessionInfoResource.class);

    private final About view;
    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;
    private final DateTimeFormatter dateTimeFormatter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Fetches the build and node.
    /// @param uiConfigCache     Gives the About text.
    /// @param dateTimeFormatter Formats the dates.
    @Inject
    public AboutController(final EventBus eventBus,
                           final About view,
                           final RestFactory restFactory,
                           final UiConfigCache uiConfigCache,
                           final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);
    }

    /// Shows the About dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    public void show(final HasControllerNode opener) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .caption("About")
                .fire();
        fetch();
    }

    private void fetch() {
        restFactory
                .create(SESSION_INFO_RESOURCE)
                .method(SessionInfoResource::get)
                .onSuccess(sessionInfo -> {
                    final BuildInfo buildInfo = sessionInfo.getBuildInfo();
                    view.setBuildVersion(buildInfo.getBuildVersion());
                    view.setBuildDate(dateTimeFormatter.format(buildInfo.getBuildTime()));
                    view.setUpDate(dateTimeFormatter.format(buildInfo.getUpTime()));
                    view.setNodeName(sessionInfo.getNodeName());
                })
                .onFailure(caught -> AlertEvent.fireError(this, caught.getMessage(), null))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();

        uiConfigCache.get(uiConfig -> {
            // Closed before the config came
            if (uiConfig != null && !isDisposed()) {
                view.setAboutHtml(uiConfig.getAboutHtml());
            }
        }, taskMonitor());
    }

}
