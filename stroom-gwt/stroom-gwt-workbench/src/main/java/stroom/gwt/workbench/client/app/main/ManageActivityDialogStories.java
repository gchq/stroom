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

import stroom.activity.client.ManageActivityPresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/ManageActivityDialog` in the React Storybook, showing Stroom's real
/// [ManageActivityPresenter] (the 'Choose Activity' dialog, with its `ActivityEditPresenter`) with
/// fake REST replies.
///
/// The React story's in-memory `ActivityApi` becomes routes for Stroom's `ActivityResource`
/// (`/activity/v1`, see [ActivityFixtures]): `list` (`GET ?filter=`, a sequence where the list
/// changes), `create` (`POST`), `update` (`PUT /{id}`), `remove` (`DELETE /{id}`), `validate`
/// (`POST /validate`, checking each property's regex as the server does), `getCurrent`/`setCurrent`
/// (`GET`/`PUT /current`). Its recorder becomes checks on the request spy, and `onCommit` is a spy
/// on the consumer Stroom passes to `ManageActivityPresenter.show`. The editor's form is the UI
/// config's `activity.editorBody`.
public final class ManageActivityDialogStories {

    /// The name of the spy recording the activity committed (the id of the activity chosen).
    static final String ON_COMMIT = "onCommit";

    private static final String CASE_1 = ActivityFixtures.activity(1,
            ActivityFixtures.prop("code", "Code", "CASE-1", true),
            ActivityFixtures.prop("description", "Description", "Investigating case one", false));
    private static final String CASE_2 = ActivityFixtures.activity(2,
            ActivityFixtures.prop("code", "Code", "CASE-2", true));
    private static final String CASE_9 = ActivityFixtures.activity(3,
            ActivityFixtures.prop("code", "code", "CASE-9", false),
            ActivityFixtures.prop("description", "description", "A sufficiently long description", false));

    private static final String CHOOSE_ACTIVITY = "Choose Activity";

    private ManageActivityDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ManageActivityDialog", ManageActivityDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Lists the activities; a double-click sets the current activity and closes. GWT
                // renders each `showInList` property on its own line with the name in bold
                .story("ChooseSetsCurrent", context -> render(context, fixtures(
                        RestReply.json(ActivityFixtures.page(CASE_1, CASE_2)))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("CASE-1")))
                            .toBeInTheDocument());
                    // The property name is bold and carries the colon, as GWT's `<b>name:</b>` does
                    play.expect(screen.getAllByText("Code:", "b").count()).toBeGreaterThan(0);
                    play.expect(screen.getByText(TextMatch.containing("Investigating case one"))).toBeInTheDocument();
                    // Double-click the second row: it is made current and the dialog closes
                    play.dblClick(screen.getByText(TextMatch.containing("CASE-2")));
                    play.waitFor(() -> play.expect(play.spy(ON_COMMIT)).toHaveBeenCalledWith("2"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(ActivityFixtures.CURRENT_PATH).withJsonBodyContaining("{\"id\": 2}")
                                    .toSpyMatcher());
                    play.waitFor(() -> play.expect(screen.queryByText(CHOOSE_ACTIVITY)).toBeNull());
                    ContentStorySupport.expectNoProblems(play);
                })
                // New: the config-driven edit form; fill it and save adds a row via the two-step
                // create/update
                .story("CreateActivity", context -> render(context, fixtures(
                        RestReply.json(ActivityFixtures.page(CASE_1, CASE_2)),
                        RestReply.json(ActivityFixtures.page(CASE_1, CASE_2, CASE_9)))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText(TextMatch.containing("CASE-1"));
                    play.click(screen.getByTitle("New"));
                    // The edit form renders the config template's controls
                    play.waitFor(() -> play.expect(screen.querySelector("input[name=\"code\"]")).toBeInTheDocument());
                    play.type(screen.querySelector("input[name=\"code\"]"), "CASE-9");
                    play.type(screen.querySelector("textarea[name=\"description\"]"),
                            "A sufficiently long description");
                    play.click(screen.within(screen.getByText("Edit Activity").closest(StroomDom.DIALOG))
                            .getByRole("button", StroomDom.button("OK")));
                    // The two-step create ran and the new row appears in the list
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(ActivityFixtures.PATH).toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(ActivityFixtures.PATH + "/3")
                                    .withJsonBodyContaining("{\"details\": {\"properties\": [{\"id\": \"code\", "
                                            + "\"value\": \"CASE-9\"}, {\"id\": \"description\"}]}}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("CASE-9")))
                            .toBeInTheDocument());
                    ContentStorySupport.expectNoProblems(play);
                })
                // Validation: a too-short description is rejected with the config's
                // validationMessage
                .story("ValidationBlocksSave", context -> render(context, fixtures(
                        RestReply.json(ActivityFixtures.page(CASE_1, CASE_2)))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText(TextMatch.containing("CASE-1"));
                    play.click(screen.getByTitle("New"));
                    play.waitFor(() -> play.expect(screen.querySelector("input[name=\"code\"]")).toBeInTheDocument());
                    play.type(screen.querySelector("input[name=\"code\"]"), "X");
                    play.type(screen.querySelector("textarea[name=\"description\"]"), "short");
                    play.click(screen.within(screen.getByText("Edit Activity").closest(StroomDom.DIALOG))
                            .getByRole("button", StroomDom.button("OK")));
                    // Differs from React: GWT shows the server's messages in a warning alert
                    // ('Validation Error'), not an inline role="alert" element
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("WARN: Validation Error"));
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("at least 10 characters")))
                            .toBeInTheDocument());
                    // Nothing was created; the editor stays open
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(ActivityFixtures.PATH).toSpyMatcher());
                    play.expect(screen.getByText("Edit Activity")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Delete: select a row, Delete, confirm: the activity is removed
                .story("DeleteActivity", context -> render(context, fixtures(
                        RestReply.json(ActivityFixtures.page(CASE_1, CASE_2)),
                        RestReply.json(ActivityFixtures.page(CASE_2)))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(screen.findByText(TextMatch.containing("CASE-1")));
                    play.click(screen.getByTitle("Delete"));
                    // Confirm in Stroom's confirmation dialog
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing(
                            "Are you sure you want to delete the selected activity?"))).toBeInTheDocument());
                    play.click(screen.within(screen.getByText(TextMatch.containing("Are you sure you want to delete"))
                                    .closest(StroomDom.DIALOG))
                            .getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete(ActivityFixtures.PATH + "/1").toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText(TextMatch.containing("CASE-1"))).toBeNull());
                    ContentStorySupport.expectNoProblems(play);
                })
                // The quick filter narrows the list to the matching activities (the server filters)
                .story("QuickFilter", context -> render(context, fixtures(
                        RestReply.json(ActivityFixtures.page(CASE_1, CASE_2)))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("CASE-2")))
                            .toBeInTheDocument());
                    // Differs from React: GWT's quick filter has no label, only a placeholder, and
                    // filters as it's typed into (a change event alone doesn't filter)
                    play.type(screen.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "CASE-2");
                    play.waitFor(3000, () -> play.expect(screen.queryByText(TextMatch.containing("CASE-1")))
                            .toBeNull());
                    play.waitFor(() -> play.expect(screen.getByText(TextMatch.containing("CASE-2")))
                            .toBeInTheDocument());
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static RestFixtures fixtures(final RestReply list, final RestReply... more) {
        final RestFixtures.Builder builder = RestFixtures.builder()
                .route(RequestMatcher.get(ActivityFixtures.PATH).withQuery("filter=CASE-2"),
                        RestReply.json(ActivityFixtures.page(CASE_2)))
                .get(ActivityFixtures.PATH, list, more)
                .post(ActivityFixtures.PATH, RestReply.json(ActivityFixtures.activity(3)))
                .delete(ActivityFixtures.PATH + "/1", RestReply.json("true"));
        return ActivityFixtures.common(builder, CASE_1).build();
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .uiConfig(ActivityFixtures.uiConfig(CHOOSE_ACTIVITY))
                // The confirmation and validation warning are shown in Stroom's real dialogs
                .realAlerts()
                .build();
        harness.fn(ON_COMMIT);
        harness.afterStartUp(() -> {
            final ManageActivityPresenter presenter = injector.getManageActivityPresenter();
            presenter.show(activity -> harness.spy(ON_COMMIT, activity == null
                    ? "none"
                    : String.valueOf(activity.getId())));
        });
        return harness.asWidget();
    }
}
