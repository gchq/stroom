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
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.node.shared.NodeResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.button.client.Button;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/ApiErrorAlertHost`: a failing request's error shown in an error dialog with
/// no per-caller wiring.
///
/// Stroom has no separate host component: its `RestFactory` gives every request with no failure
/// handler a `DefaultErrorHandler`, which fires an `AlertEvent`, which `AlertPlugin` shows in
/// `CommonAlertPresenter`'s 'Alert' dialog. The story's button makes such a request
/// (`NodeResource.listAllNodes`, `GET /node/v1/all`), answered with an error, and the harness shows
/// Stroom's real alert dialog.
public final class ApiErrorAlertHostStories {

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get("/node/v1/all", RestReply.error(500, "Boom from the server"))
            .lenient()
            .build();

    private ApiErrorAlertHostStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ApiErrorAlertHost", ApiErrorAlertHostStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A failing request's error is shown in an error dialog
                .story("RoutesApiErrorToDialog", ApiErrorAlertHostStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.getByRole("button", TextMatch.containingIgnoreCase("trigger api error")));
                    // The error is shown in a dialog with the server's message and a Close button.
                    // The dialog is on the page's body
                    play.expect(screen.findByText(TextMatch.containing("Boom from the server"))).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("Boom from the server"));
                    play.click(screen.getByRole("button", StroomDom.button("Close")));
                    play.waitFor(() -> play.expect(screen.queryByText(TextMatch.containing("Boom from the server")))
                            .toBeNull());
                });
    }

    private static Widget render(final StoryContext context) {
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .realAlerts()
                .build();
        final NodeResource nodeResource = GWT.create(NodeResource.class);
        final Button button = new Button();
        button.setText("Trigger API error");
        button.addStyleName("Button--contained-primary");
        // A request with no failure handler: RestFactory reports its error with DefaultErrorHandler
        button.addClickHandler(event -> harness.getRestFactory()
                .create(nodeResource)
                .method(NodeResource::listAllNodes)
                .taskMonitorFactory(new DefaultTaskMonitorFactory(harness.getHasHandlers()))
                .exec());
        final SimplePanel panel = new SimplePanel(button);
        panel.getElement().getStyle().setPadding(24, Unit.PX);
        harness.add(panel);
        return harness.asWidget();
    }
}
