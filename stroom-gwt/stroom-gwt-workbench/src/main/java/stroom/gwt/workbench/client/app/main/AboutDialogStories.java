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

import stroom.about.client.component.About;
import stroom.about.client.component.AboutController;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.preferences.client.DateTimeFormatter;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/AboutDialog`, showing Stroom's real [AboutController] with fake REST
/// replies. It needs two endpoints, the session info and the UI config, which are both start-up
/// fixtures, and through its [UiConfigCache] and [DateTimeFormatter] a small graph of Stroom
/// services, which come from the harness's injector.
public final class AboutDialogStories {

    // The uiConfig part of GlobalConfigResource.fetchExtendedUiConfig(), with only what the dialog
    // uses
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
                    // Each value beside its label, which the template holds
                    play.expect(screen.findByText("v7.5-test").closest("div"))
                            .toHaveTextContent("Build Version: v7.5-test");
                    play.expect(screen.getByText("node1a").closest("div")).toHaveTextContent("Node Name: node1a");
                    play.expect(screen.getByText("Stroom is a data processing platform.")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("GET /sessionInfo/v1"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("GET /config/v1/noauth/fetchExtendedUiConfig"));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // A failure to fetch the UI config is shown; before, it was dropped, so the user
                // was told nothing and the dialog's About text never came
                .story("ConfigFetchFails", context -> render(context, RestFixtures.builder()
                        .get(StartupFixtures.EXTENDED_UI_CONFIG_PATH, RestReply.error(500, "Config unavailable"))
                        .build()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("v7.5-test");
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("Config unavailable")));
                })
                // Close closes it, and it is disposed (the leak check counts it if not)
                .story("Close", AboutDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query dialog = screen.findByRole("dialog", "About");
                    screen.findByText("v7.5-test");
                    play.click(play.within(dialog).getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByRole("dialog", "About")).toBeNull());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        return render(context, RestFixtures.none());
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        // Only the start-up fixtures are needed, with this session info and uiConfig
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .startup(startup -> startup.buildVersion("v7.5-test").nodeName("node1a"))
                .uiConfig(UI_CONFIG)
                .build();

        new AboutController(
                harness.getEventBus(),
                new About(),
                harness.getRestFactory(),
                harness.getUiConfigCache(),
                harness.getDateTimeFormatter()).show(harness.getAppRoot());
        return harness.asWidget();
    }
}
