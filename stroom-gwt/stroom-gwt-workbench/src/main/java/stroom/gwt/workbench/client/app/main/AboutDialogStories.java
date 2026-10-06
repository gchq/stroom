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
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.preferences.client.DateTimeFormatter;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/AboutDialog` in the React Storybook, showing Stroom's real
/// [AboutPresenter] with fake REST replies. It needs two endpoints, the session info and the UI
/// config, which are both start-up fixtures, and through its [UiConfigCache] and
/// [DateTimeFormatter] a small graph of Stroom services, which come from the harness's injector.
public final class AboutDialogStories {

    // The uiConfig part of GlobalConfigResource.fetchExtendedUiConfig(), with only what the dialog
    // uses, as in React's fetchUiConfig fixture
    private static final String UI_CONFIG = """
            {"aboutHtml": "<p class=\\"about-marker\\">Stroom is a data processing platform.</p>"}
            """;

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
                .story("Default", AboutDialogStories::render)
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
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        // Only the start-up fixtures are needed, with React's session info and uiConfig
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.none())
                .startup(startup -> startup.buildVersion("v7.5-test").nodeName("node1a"))
                .uiConfig(UI_CONFIG)
                .build();

        final AboutPresenter presenter = new AboutPresenter(
                harness.getEventBus(),
                new AboutViewImpl(GWT.create(AboutViewImpl.Binder.class)),
                null,
                harness.getRestFactory(),
                harness.getUiConfigCache(),
                harness.getDateTimeFormatter());
        presenter.show();
        return harness.asWidget();
    }
}
