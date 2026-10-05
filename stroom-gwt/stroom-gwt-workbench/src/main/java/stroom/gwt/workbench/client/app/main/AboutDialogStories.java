/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.main;

import stroom.about.client.presenter.AboutPresenter;
import stroom.about.client.view.AboutViewImpl;
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StorySecurityContext;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.preferences.client.DateTimeFormatter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/AboutDialog` in the React Storybook, showing Stroom's real
/// [AboutPresenter] with fake REST replies. It needs two endpoints and, through its
/// [UiConfigCache] and [DateTimeFormatter], a small graph of Stroom services.
public final class AboutDialogStories {

    // The reply of SessionInfoResource.get()
    private static final String SESSION_INFO = """
            {
              "userRef": {"uuid": "admin-uuid", "subjectId": "admin", "displayName": "admin",
                          "group": false, "enabled": true},
              "nodeName": "node1a",
              "buildInfo": {"upTime": 1710000000000, "buildVersion": "v7.5-test", "buildTime": 1700000000000}
            }
            """;

    // The reply of GlobalConfigResource.fetchExtendedUiConfig(), with only what the dialog uses
    private static final String EXTENDED_UI_CONFIG = """
            {
              "uiConfig": {
                "aboutHtml": "<p class=\\"about-marker\\">Stroom is a data processing platform.</p>"
              },
              "externalIdentityProvider": false,
              "dependencyWarningsEnabled": false,
              "lastAnnotationChangeTime": 0
            }
            """;

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get("/sessionInfo/v1", RestReply.json(SESSION_INFO))
            .get("/config/v1/noauth/fetchExtendedUiConfig", RestReply.json(EXTENDED_UI_CONFIG))
            .build();

    private AboutDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/AboutDialog", AboutDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The About dialog shows the build/session info and the uiConfig aboutHtml block
                .story("Default", context -> render(context, FIXTURES))
                .withPlay(play -> {
                    // The dialog is shown on the page's body, as in Stroom
                    final Play screen = play.screen();
                    screen.findByText("About");
                    screen.findByText("Build Version: v7.5-test");
                    play.expect(screen.getByText("Node Name: node1a")).toBeInTheDocument();
                    play.expect(screen.getByText("Stroom is a data processing platform.")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("GET /sessionInfo/v1"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("GET /config/v1/noauth/fetchExtendedUiConfig"));
                });
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ScreenHarness harness = ScreenHarness.create(context, fixtures);

        // The services that GIN would inject
        final UiConfigCache uiConfigCache = new UiConfigCache(
                harness.getRestFactory(), StorySecurityContext.admin());
        final DateTimeFormatter dateTimeFormatter = new DateTimeFormatter(
                new UserPreferencesManager(harness.getRestFactory(), new CurrentPreferences()));

        final AboutPresenter presenter = new AboutPresenter(
                harness.getEventBus(),
                new AboutViewImpl(GWT.create(AboutViewImpl.Binder.class)),
                null,
                harness.getRestFactory(),
                uiConfigCache,
                dateTimeFormatter);
        presenter.show();
        return harness.asWidget();
    }
}
