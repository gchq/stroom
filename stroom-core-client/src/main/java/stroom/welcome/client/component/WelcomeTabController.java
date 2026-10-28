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

package stroom.welcome.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.SessionInfoResource;
import stroom.content.client.component.ContentTabController;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the site's welcome text, who the user is and which Stroom this is.
public class WelcomeTabController extends ContentTabController {

    public static final String WELCOME = "Welcome";
    public static final String TAB_TYPE = WELCOME;

    private static final SessionInfoResource SESSION_INFO_RESOURCE = GWT.create(SessionInfoResource.class);

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              Its component.
    /// @param restFactory       Makes REST requests.
    /// @param uiConfigCache     The UI's configuration.
    /// @param dateTimeFormatter Formats dates and times.
    @Inject
    public WelcomeTabController(final EventBus eventBus,
                                final WelcomeTab view,
                                final RestFactory restFactory,
                                final UiConfigCache uiConfigCache,
                                final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        initWidget(view);

        restFactory
                .create(SESSION_INFO_RESOURCE)
                .method(SessionInfoResource::get)
                .onSuccess(sessionInfo -> {
                    final UserRef userRef = sessionInfo.getUserRef();
                    view.setUser(userRef.getSubjectId(), userRef.getDisplayName(), userRef.getFullName());

                    final BuildInfo buildInfo = sessionInfo.getBuildInfo();
                    view.setBuild(buildInfo.getBuildVersion(),
                            dateTimeFormatter.format(buildInfo.getBuildTime()),
                            dateTimeFormatter.format(buildInfo.getUpTime()),
                            sessionInfo.getNodeName());
                })
                .onFailure(caught ->
                        AlertEvent.fireError(WelcomeTabController.this, caught.getMessage(), null))
                .owner(this)
                .taskMonitorFactory(this)
                .exec();

        uiConfigCache.get(result -> {
            if (result != null) {
                view.setWelcomeHtml(result.getWelcomeHtml());
            }
        }, this);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.OO;
    }

    @Override
    public String getLabel() {
        return WELCOME;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
