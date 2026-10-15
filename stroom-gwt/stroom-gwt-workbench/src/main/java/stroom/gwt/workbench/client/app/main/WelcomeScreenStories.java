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

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.welcome.client.presenter.WelcomePresenter;
import stroom.welcome.client.view.WelcomeViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/WelcomeScreen`, showing Stroom's real [WelcomePresenter] (the start-up
/// Welcome tab) with fake REST replies.
///
/// The screen only makes Stroom's start-up requests (the session info and the UI config's
/// `welcomeHtml`), so the story has no fixtures of its own: the harness's start-up fixtures answer
/// them with their defaults (the `admin` user, build `SNAPSHOT`, node `node1a`, the usual "About
/// Stroom" HTML). The presenter's UI config cache and date formatter come from the harness's
/// injector.
public final class WelcomeScreenStories {

    private WelcomeScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/WelcomeScreen", WelcomeScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The startup Welcome tab: server "About Stroom" HTML + the build/user info panel
                .story("Default", WelcomeScreenStories::render)
                .withPlay(play -> {
                    play.findByText("About Stroom");
                    play.expect(play.getByText("Stroom is designed to receive data from multiple systems."))
                            .toBeInTheDocument();
                    // Build/user info panel from the session fixture
                    play.findByText("SNAPSHOT");
                    play.expect(play.getByText("Build Version:")).toBeInTheDocument();
                    play.expect(play.getByText("Node Name:")).toBeInTheDocument();
                    play.expect(play.getByText("node1a")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("GET /sessionInfo/v1"));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context) {
        final ScreenHarness harness = ScreenHarness.create(context, RestFixtures.none());

        final WelcomePresenter presenter = new WelcomePresenter(
                harness.getEventBus(),
                new WelcomeViewImpl(GWT.create(WelcomeViewImpl.Binder.class)),
                harness.getRestFactory(),
                harness.getUiConfigCache(),
                harness.getDateTimeFormatter());
        // A tab's content, so shown in the story's canvas rather than as a popup
        harness.add(harness.unbindOnCleanUp(presenter).getWidget());
        return harness.asWidget();
    }
}
