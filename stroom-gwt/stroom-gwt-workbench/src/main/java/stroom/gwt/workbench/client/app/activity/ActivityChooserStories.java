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


package stroom.gwt.workbench.client.app.activity;

import stroom.activity.client.ActivityChangedEvent;
import stroom.activity.client.ActivitySummary;
import stroom.activity.client.CurrentActivity;
import stroom.activity.shared.Activity;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ContentStorySupport;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.button.client.Button;
import stroom.widget.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/Activity Chooser`: the current activity's summary button and Stroom's real
/// activity chooser (`ManageActivityController`, opened by [CurrentActivity]) with fake REST
/// replies.
///
/// Stroom has no separate badge widget: the summary button is declared in `Explorer.ui.xml`
/// (`activityButton`) and filled by `ExplorerController.updateActivitySummary`, which the story
/// copies; clicking it opens the chooser (`CurrentActivity.showActivityChooser`), and choosing an
/// activity fires `ActivityChangedEvent`, which updates the summary. The activities are the
/// `GET /activity/v1` reply (see [ActivityFixtures]). The plays check what each story renders.
public final class ActivityChooserStories {

    private static final String[] ACTIVITIES = {
            ActivityFixtures.activity(1,
                    ActivityFixtures.prop("name", "Name", "Incident 2024-08 triage", true),
                    ActivityFixtures.prop("ref", "Reference", "INC-4821", false)),
            ActivityFixtures.activity(2,
                    ActivityFixtures.prop("name", "Name", "Routine monitoring", true),
                    ActivityFixtures.prop("team", "Team", "SOC Blue", false)),
            ActivityFixtures.activity(3,
                    ActivityFixtures.prop("name", "Name", "Threat hunt - lateral movement", true),
                    ActivityFixtures.prop("ref", "Reference", "HUNT-0092", false)),
            ActivityFixtures.activity(4,
                    ActivityFixtures.prop("name", "Name", "Training / sandbox", true))};

    private ActivityChooserStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/Activity Chooser", ActivityChooserStories.class)
                .layout(StoryLayout.CENTERED)
                // The summary shows the current activity (none at first); clicking it opens the
                // chooser
                .story("Default", context -> render(context, null, "Choose Activity", false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Current Activity");
                    play.click(play.getByRole("button"));
                    screen.findByText("Choose Activity");
                    screen.findByText(TextMatch.containing("Routine monitoring"));
                    ContentStorySupport.expectNoProblems(play);
                })
                // Open with an activity already current: its row is the selection
                .story("OpenWithSelection", context ->
                        render(context, ACTIVITIES[1], "Select Current Activity", true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Select Current Activity");
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.containing("Routine monitoring")))
                            .toBeInTheDocument());
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context,
                                 final String current,
                                 final String title,
                                 final boolean open) {
        final RestFixtures fixtures = ActivityFixtures.common(RestFixtures.builder()
                        .get(ActivityFixtures.PATH, RestReply.json(ActivityFixtures.page(ACTIVITIES))),
                current)
                .build();
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .uiConfig(ActivityFixtures.uiConfig(title))
                .build();

        // As ExplorerController builds its activity button
        final CurrentActivity currentActivity = injector.getCurrentActivity();
        final Button activityButton = new Button();
        activityButton.setContent(SafeHtmlUtils.EMPTY_SAFE_HTML);
        activityButton.setStyleName("explorer__activity-button");
        final SimplePanel activityOuter = new SimplePanel(activityButton);
        activityOuter.setStyleName("explorer__activity");
        activityButton.setOnClick(currentActivity::showActivityChooser);
        harness.addRegistration(harness.getEventBus().addHandler(ActivityChangedEvent.getType(), event ->
                activityButton.setContent(ActivitySummary.toHtml(event.getActivity()))));
        // The summary is the screen each opening shows (as the leak check opens it again); the play
        // opens the chooser from it
        harness.afterStartUp(() -> {
            harness.add(activityOuter);
            currentActivity.getActivity(activity -> {
                activityButton.setContent(ActivitySummary.toHtml(activity));
                if (open) {
                    currentActivity.showActivityChooser();
                }
            }, new DefaultTaskMonitorFactory(harness.getHasHandlers()));
        });
        return harness.asWidget();
    }
}
