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

import stroom.activity.client.CurrentActivity;
import stroom.dashboard.client.query.QueryInfoPresenter;
import stroom.dashboard.client.query.QueryInfoViewImpl;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/currentActivity` in the React Storybook: whether a query needs the
/// query info popup, given the current activity (GWT's `QueryInfoPresenter.isRequired`: required
/// unless the activity has a `requireQueryInfo` property equal, ignoring case, to "false").
///
/// The React story calls the port's `requiresQueryInfo` function directly. Stroom's is a private
/// method of [QueryInfoPresenter], used by its `show` method, so the story asks six real
/// `QueryInfoPresenter`s (each with its own [CurrentActivity]) to show the popup, with the query
/// info popup enabled in the UI config, and records, in the `requiresQueryInfo` spy, whether each
/// shows the popup (required) or goes straight on (not required). The current activities are a
/// sequence of replies to `GET /activity/v1/current`, in the order the presenters ask for them.
public final class CurrentActivityStories {

    /// The name of the spy recording each case's result.
    static final String REQUIRES_QUERY_INFO = "requiresQueryInfo";

    // The cases, in the order of the replies
    private static final String[] CASES = {
            "no activity",
            "Reference=INV-1",
            "requireQueryInfo=false",
            "requireQueryInfo=FALSE",
            "requireQueryInfo=true",
            "requireQueryInfo=yes"};

    private CurrentActivityStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/currentActivity", CurrentActivityStories.class)
                .layout(StoryLayout.CENTERED)
                .story("RequiresQueryInfoGate", CurrentActivityStories::render)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.spy(REQUIRES_QUERY_INFO).callCount()).toBe(CASES.length));
                    // No current activity: required (the default)
                    play.expect(play.spy(REQUIRES_QUERY_INFO)).toHaveBeenCalledWith("no activity: true");
                    // An activity with no such property: still required
                    play.expect(play.spy(REQUIRES_QUERY_INFO)).toHaveBeenCalledWith("Reference=INV-1: true");
                    // requireQueryInfo=false (any case): not required, so the popup is skipped
                    play.expect(play.spy(REQUIRES_QUERY_INFO)).toHaveBeenCalledWith("requireQueryInfo=false: false");
                    play.expect(play.spy(REQUIRES_QUERY_INFO)).toHaveBeenCalledWith("requireQueryInfo=FALSE: false");
                    // requireQueryInfo=true, or any other value: required
                    play.expect(play.spy(REQUIRES_QUERY_INFO)).toHaveBeenCalledWith("requireQueryInfo=true: true");
                    play.expect(play.spy(REQUIRES_QUERY_INFO)).toHaveBeenCalledWith("requireQueryInfo=yes: true");
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static RestReply activity(final String id, final String value) {
        return RestReply.json(ActivityFixtures.activity(1, "{\"id\": \"" + id + "\", \"name\": \"" + id
                + "\", \"value\": \"" + value + "\"}"));
    }

    private static Widget render(final StoryContext context) {
        final RestFixtures fixtures = RestFixtures.builder()
                .get(ActivityFixtures.CURRENT_PATH,
                        RestReply.noContent(),
                        activity("Reference", "INV-1"),
                        activity("requireQueryInfo", "false"),
                        activity("requireQueryInfo", "FALSE"),
                        activity("requireQueryInfo", "true"),
                        activity("requireQueryInfo", "yes"))
                .build();
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                // The query info popup is enabled
                .uiConfig(StartupFixtures.DEFAULT_UI_CONFIG.replace("\"infoPopup\": {\"enabled\": false",
                        "\"infoPopup\": {\"enabled\": true"))
                .build();
        harness.fn(REQUIRES_QUERY_INFO);

        // As the React story renders
        final HTML text = new HTML("requiresQueryInfo &mdash; required unless the activity sets "
                + "<code>requireQueryInfo=false</code>.");
        text.getElement().getStyle().setProperty("fontFamily", "monospace");
        text.getElement().getStyle().setFontSize(13, Unit.PX);
        harness.add(text);

        harness.afterStartUp(() -> {
            for (final String name : CASES) {
                final CurrentActivity currentActivity = new CurrentActivity(harness.getEventBus(), () -> null,
                        harness.getRestFactory());
                final QueryInfoPresenter presenter = new QueryInfoPresenter(harness.getEventBus(),
                        new QueryInfoViewImpl(GWT.create(QueryInfoViewImpl.Binder.class)),
                        harness.getUiConfigCache(),
                        currentActivity);
                // Required: the presenter shows its popup
                harness.addRegistration(harness.getEventBus().addHandler(ShowPopupEvent.getType(), event -> {
                    if (event.getPresenterWidget() == presenter) {
                        harness.spy(REQUIRES_QUERY_INFO, name + ": true");
                    }
                }));
                // Not required: the presenter goes straight on with no query info
                presenter.show("", state -> {
                    if (state.getQueryInfo() == null) {
                        harness.spy(REQUIRES_QUERY_INFO, name + ": false");
                    }
                }, new DefaultTaskMonitorFactory(harness.getHasHandlers()));
            }
        });
        return harness.asWidget();
    }
}
