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

import stroom.analytics.client.presenter.ExecutionScheduleManager;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.AppPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Stories matching `App/Main/ExecutionSchedulesScreen` in the React Storybook, showing Stroom's
/// real [ExecutionScheduleManager] (the 'Execution Schedule Manager' tab, as
/// `ExecutionScheduleManagerPlugin` opens it) with fake REST replies.
///
/// | React | Stroom |
/// |---|---|
/// | `find` | `POST /executionSchedule/v1/fetchExecutionSchedule` |
/// | `update` | `POST /executionSchedule/v1/updateExecutionSchedule` (echoes the schedule) |
/// | `remove` | `POST /executionSchedule/v1/deleteExecutionSchedules` |
/// | `executeNow` | `POST /executionSchedule/v1/executeSchedulesNow` |
/// | `listNodes` | `GET /node/v1/all` |
/// | `fetchTracker` | `POST /executionSchedule/v1/fetchTracker` |
/// | `getScheduledTimes` | `POST /scheduledTime/v1` (the schedule box's validation) |
/// | (the filter's validation) | `POST /expression/v1/validate` |
/// | `configApi.setConfigValue` | `POST /config/v1/setConfigValue` |
///
/// The recorder's checks become checks on the request spy. The confirmations and alerts are
/// Stroom's real dialogs (`realAlerts()`).
public final class ExecutionSchedulesScreenStories {

    private static final String BASE = "/executionSchedule/v1";
    private static final String FETCH_PATH = BASE + "/fetchExecutionSchedule";
    private static final String UPDATE_PATH = BASE + "/updateExecutionSchedule";
    private static final String DELETE_PATH = BASE + "/deleteExecutionSchedules";
    private static final String RUN_NOW_PATH = BASE + "/executeSchedulesNow";
    private static final String SET_CONFIG_PATH = "/config/v1/setConfigValue";

    // The schedules have no scheduleBounds, as React's (BatchExecutionScheduleEditViewImpl once read
    // them without a null check)
    private static final String HOURLY = """
            {"uuid": "s1", "name": "Hourly rollup", "enabled": true, "nodeName": "node1",
              "owningDoc": {"type": "AnalyticRule", "uuid": "a1", "name": "Suspicious logins"},
              "schedule": {"type": "CRON", "expression": "0 0 * * * ?"}, "contiguous": false,
              "runAsUser": {"uuid": "u-admin", "subjectId": "admin", "displayName": "admin", "group": false,
                "enabled": true}}""";

    private static final String DAILY = """
            {"uuid": "s2", "name": "NAME", "enabled": false, "nodeName": "node2",
              "owningDoc": {"type": "AnalyticRule", "uuid": "a2", "name": "Weekly summary"},
              "schedule": {"type": "FREQUENCY", "expression": "1d"}, "contiguous": false,
              "runAsUser": {"uuid": "u-analyst", "subjectId": "analyst", "displayName": "analyst", "group": false,
                "enabled": true}}""";

    private static final String SCHEDULES = schedules("Daily report");
    // The list once 'Daily report' has been renamed
    private static final String EDITED_SCHEDULES = schedules("Daily digest");

    private static final RestFixtures FIXTURES = fixtures("[\"node1\", \"node2\", \"node3\"]",
            RestReply.json(SCHEDULES));
    private static final RestFixtures ONE_NODE_FIXTURES = fixtures("[\"node1\"]", RestReply.json(SCHEDULES));
    // The list as loaded (the first load and the flush of selections that goes with each), then edited
    private static final RestFixtures EDIT_FIXTURES = fixtures("[\"node1\", \"node2\", \"node3\"]",
            RestReply.json(SCHEDULES), RestReply.json(SCHEDULES), RestReply.json(EDITED_SCHEDULES));

    private static final String RUN_NOW = "Run Schedules Now";
    private static final String EDIT = "Edit Selected Schedule";
    private static final String SET_DEFAULT_QUESTION = "Set 'node2' as the default processing node for all users?";

    private ExecutionSchedulesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ExecutionSchedulesScreen", ExecutionSchedulesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The schedule list loads (type prefixed Schedule cell); Delete removes the selection
                .story("Schedules", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Hourly rollup");
                    play.findByText("Daily report");
                    // Schedule.toString() prefixes the type
                    play.expect(play.getByText("Cron 0 0 * * * ?")).toBeInTheDocument();
                    play.click(play.getByText("Daily report"));
                    final Query delete = play.getByTitle("Delete Schedules");
                    play.waitFor(() -> play.expect(delete).not().toHaveClass("disabled"));
                    play.click(delete);
                    // Differs from React: GWT's message is 'You are about to delete 1 schedule.'
                    final Play confirm = dialogWith(screen, TextMatch.containing("You are about to delete 1 schedule"));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(DELETE_PATH).withJsonBodyContaining("[{\"uuid\": \"s2\"}]")
                                    .toSpyMatcher()));
                    expectNoErrors(play);
                })
                // Run Now offers 'Apply to Selection' or 'Apply to Filtered', then checks before running
                .story("RunNow", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Hourly rollup");
                    // Not gated on a selection: the dialog offers Filtered too
                    play.click(play.getByTitle(RUN_NOW));
                    screen.findByText(TextMatch.containing("will be run now, executing according to frequency"));
                    // No selection: GWT warns rather than running
                    play.click(screen.getByRole("button", StroomDom.button("Apply to Selection")));
                    final Play warning = dialogWith(screen, "No schedules selected.");
                    play.click(warning.getByRole("button", StroomDom.button("Close")));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(RUN_NOW_PATH).toSpyMatcher());
                    // Differs from React: GWT's 'Run Now' dialog stays open (a Close dialog) until
                    // closed, so it is closed before selecting a row
                    play.click(dialog(screen, "Run Now").getByRole("button", StroomDom.button("Close")));
                    // Select the enabled schedule -> confirm -> it runs
                    play.click(play.getByText("Hourly rollup"));
                    play.click(play.getByTitle(RUN_NOW));
                    play.click(screen.findByRole("button", StroomDom.button("Apply to Selection")));
                    final Play confirm = dialogWith(screen,
                            TextMatch.containing("You are about to force 1 execution schedule to run now"));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(RUN_NOW_PATH).withJsonBodyContaining("[{\"uuid\": \"s1\"}]")
                                    .toSpyMatcher()));
                    expectNoErrors(play);
                })
                // 'Apply to Filtered' runs only the enabled schedules, after a warning
                .story("RunNowFilteredSkipsDisabled", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Hourly rollup");
                    play.click(play.getByTitle(RUN_NOW));
                    play.click(screen.findByRole("button", StroomDom.button("Apply to Filtered")));
                    // GWT warns first, and only continues once it is dismissed
                    final Play warning = dialogWith(screen,
                            "Some of the filtered schedules are disabled and will not be run.");
                    play.click(warning.getByRole("button", StroomDom.button("Close")));
                    // The confirmation counts only the enabled one
                    final Play confirm = dialogWith(screen,
                            TextMatch.containing("You are about to force 1 execution schedule to run now"));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    // s2 is disabled, so it is never sent
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(RUN_NOW_PATH).withJsonBodyContaining("[{\"uuid\": \"s1\"}]")
                                    .toSpyMatcher()));
                    expectNoErrors(play);
                })
                // Applying a filter re-runs the find with the expression
                .story("Filter", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Hourly rollup");
                    play.click(play.getByTitle("Filter Schedules"));
                    play.click(dialog(screen, "Filter Schedules").getByRole("button", StroomDom.button("OK")));
                    // The expression is validated
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/expression/v1/validate").toSpyMatcher()));
                    // The filter is applied: the find re-runs with the (empty) expression, which has no
                    // children; the dialog closes and nothing fails (ExecutionScheduleManager's
                    // formatISOExpressions once failed for an operator with no children)
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FETCH_PATH)
                                    .withJsonBodyContaining("{\"expression\": {\"type\": \"operator\"}}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.queryByText("Filter Schedules", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.findByText("Hourly rollup");
                    // Differs from React: GWT's Clear Filter button is always there, but only enabled
                    // for an expression with terms (setButtonState), so an empty filter leaves it
                    // disabled and React's Clear Filter step can't be taken
                    play.expect(play.getByTitle("Clear Filter")).toHaveClass("disabled");
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    expectNoErrors(play);
                })
                // Batch Edit applies only the enabled fields to every filtered schedule
                .story("BatchEdit", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Hourly rollup");
                    play.click(play.getByTitle("Batch Edit Schedules"));
                    final Play dialog = dialog(screen, "Batch Change Selected Schedules");
                    // Enable only the Name field (its check box is the first) and set a value
                    play.click(dialog.getAllByRole("checkbox").nth(0));
                    play.type(dialog.getAllByRole("textbox").nth(0), "Renamed batch");
                    play.click(dialog.getByRole("button", StroomDom.button("Apply to Filtered")));
                    final Play confirm = dialogWith(screen, TextMatch.containing("You are about to edit"));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("the schedules updated", () -> updates(requests))
                            .toEqual(List.of("s1 'Renamed batch' enabled=true", "s2 'Renamed batch' enabled=false")));
                    expectNoErrors(play);
                })
                // Batch Edit with no field enabled warns "No changes selected." and writes nothing
                .story("BatchNoChange", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Hourly rollup");
                    play.click(play.getByTitle("Batch Edit Schedules"));
                    final Play dialog = dialog(screen, "Batch Change Selected Schedules");
                    play.click(dialog.getByRole("button", StroomDom.button("Apply to Filtered")));
                    screen.findByText("No changes selected.");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(UPDATE_PATH).toSpyMatcher());
                    expectNoErrors(play);
                })
                // Editing a schedule renames it, enables it and saves it
                .story("Edit", context -> render(context, EDIT_FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Daily report");
                    openEditor(play);
                    final Play dialog = dialog(screen, "Edit Schedule");
                    // Differs from React: the field's id is its FormGroup's identity, 'name'
                    final Query name = dialog.querySelector("#name");
                    play.clear(name);
                    play.type(name, "Daily digest");
                    // The only check box in the dialog is Enabled
                    play.click(dialog.getByRole("checkbox"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(UPDATE_PATH)
                                    .withJsonBodyContaining("{\"name\": \"Daily digest\", \"enabled\": true, "
                                            + "\"contiguous\": true, \"owningDoc\": {\"uuid\": \"a2\"}}")
                                    .toSpyMatcher()));
                    play.findByText("Daily digest");
                    expectNoErrors(play);
                })
                // The first fetch carries the default sort, Name descending ignoring case
                .story("DefaultSortIsNameDescending", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    play.findByText("Daily report");
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(() -> play.expect("the first fetch's sort list", () -> firstSortList(requests))
                            .toEqual(List.of(Map.of("id", "Name", "desc", true, "ignoreCase", true))));
                    // A request default only: no header shows a sort icon
                    play.expect(play.querySelector(".column-sortIcon")).toBeNull();
                    expectNoErrors(play);
                })
                // A schedule naming a node that no longer exists
                .story("UnknownNodeIsMarked", context -> render(context, ONE_NODE_FIXTURES, false))
                .withPlay(play -> {
                    play.findByText("Daily report");
                    // Differs from React: the Execution Schedule Manager's node column is plain text
                    // (only an analytic rule's own schedule list, ScheduledProcessListPresenter, marks
                    // a node that no longer exists), so no row is marked
                    play.expect(play.getByText("node2")).toBeInTheDocument();
                    play.expect(play.querySelectorAll(".svg-image__alert-simple")).toHaveLength(0);
                    expectNoErrors(play);
                })
                // "Set Default" makes the chosen node the defaultNode for everyone, after a confirmation
                .story("SetDefaultNodeWritesTheProperty", context -> render(context, FIXTURES, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Daily report");
                    openEditor(play);
                    final Play dialog = dialog(screen, "Edit Schedule");
                    // Cancel the confirmation: nothing is written
                    play.click(dialog.findByRole("button", StroomDom.button("Set Default")));
                    play.click(dialogWith(screen, SET_DEFAULT_QUESTION)
                            .getByRole("button", StroomDom.button("Cancel")));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(SET_CONFIG_PATH).toSpyMatcher());
                    // Confirm it: the property is written for the analytic rules' defaults
                    play.click(dialog.getByRole("button", StroomDom.button("Set Default")));
                    play.click(dialogWith(screen, SET_DEFAULT_QUESTION).getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(SET_CONFIG_PATH)
                                    .withJsonBodyContaining("{\"target\": \"ANALYTIC_UI_DEFAULT\", "
                                                            + "\"propertyName\": \"defaultNode\", "
                                                            + "\"stringValue\": \"node2\"}")
                                    .toSpyMatcher()));
                    // ...and the user is told, by name
                    screen.findByText("The default processing node is now 'node2'.");
                    expectNoErrors(play);
                })
                // Without MANAGE_PROPERTIES the control isn't offered
                .story("SetDefaultHiddenWithoutPermission", context -> render(context, FIXTURES, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Daily report");
                    openEditor(play);
                    final Play dialog = dialog(screen, "Edit Schedule");
                    // GWT hides the button (setVisible(false)), so it has no accessible role
                    play.expect(dialog.queryByRole("button", StroomDom.button("Set Default"))).toBeNull();
                    expectNoErrors(play);
                });
    }

    // Selects 'Daily report' and opens it with 'Edit Selected Schedule'
    private static void openEditor(final Play play) {
        play.click(play.getByText("Daily report"));
        final Query edit = play.getByTitle(EDIT);
        play.waitFor(() -> play.expect(edit).not().toHaveClass("disabled"));
        play.click(edit);
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // The dialog showing the given text, e.g. a confirmation
    private static Play dialogWith(final Play screen, final TextMatch text) {
        return screen.within(screen.findByText(text).closest(StroomDom.DIALOG));
    }

    private static Play dialogWith(final Play screen, final String text) {
        return dialogWith(screen, TextMatch.exact(text));
    }

    // Each schedule update sent, as 'uuid 'name' enabled=x'
    private static List<String> updates(final Spy requests) {
        final List<String> updates = new ArrayList<>();
        for (final List<Object> call : requests.getCalls()) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if ("POST".equals(request.getMethod()) && UPDATE_PATH.equals(request.getPath())) {
                final Map<?, ?> schedule = (Map<?, ?>) JsonValues.parse(request.getBody());
                updates.add(schedule.get("uuid") + " '" + schedule.get("name") + "' enabled="
                            + schedule.get("enabled"));
            }
        }
        updates.sort(String::compareTo);
        return updates;
    }

    // The sort list of the first fetch of the schedules
    private static Object firstSortList(final Spy requests) {
        for (final List<Object> call : requests.getCalls()) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if ("POST".equals(request.getMethod()) && FETCH_PATH.equals(request.getPath())) {
                return ((Map<?, ?>) JsonValues.parse(request.getBody())).get("sortList");
            }
        }
        return null;
    }

    // The alerts the stories show are checked on screen; no error may be reported
    private static void expectNoErrors(final Play play) {
        final Spy alerts = play.spy(ScreenHarness.ALERT_SPY);
        play.expect("the errors alerted", () -> errors(alerts)).toEqual(List.of());
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static List<String> errors(final Spy alerts) {
        final List<String> errors = new ArrayList<>();
        for (final List<Object> call : alerts.getCalls()) {
            final String alert = String.valueOf(call.get(0));
            if (alert.startsWith("ERROR")) {
                errors.add(alert);
            }
        }
        return errors;
    }

    private static String schedules(final String dailyName) {
        return """
                {"values": [HOURLY, DAILY], "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}"""
                .replace("HOURLY", HOURLY)
                .replace("DAILY", DAILY.replace("NAME", dailyName));
    }

    private static RestFixtures fixtures(final String nodes, final RestReply schedules, final RestReply... more) {
        return RestFixtures.builder()
                .post(FETCH_PATH, schedules, more)
                .post(UPDATE_PATH, request -> RestReply.json(request.getBody()))
                .post(DELETE_PATH, RestReply.json("true"))
                .post(RUN_NOW_PATH, RestReply.json("true"))
                .post(BASE + "/fetchTracker", RestReply.json("{\"lastEffectiveExecutionTimeMs\": 1700000000000}"))
                .get("/node/v1/all", RestReply.json(nodes))
                .post("/scheduledTime/v1", RestReply.json("{\"nextScheduledTimeMs\": 1700000000000}"))
                .post("/expression/v1/validate", RestReply.json("{\"ok\": true}"))
                .post(SET_CONFIG_PATH, RestReply.json("true"))
                .build();
    }

    private static Widget render(final StoryContext context,
                                 final RestFixtures fixtures,
                                 final boolean canManageProperties) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        // React's fixture grants no app permissions; Stroom needs Manage Processors to show the screen
        final AppPermission[] permissions;
        if (canManageProperties) {
            permissions = new AppPermission[]{
                    AppPermission.MANAGE_PROCESSORS_PERMISSION,
                    AppPermission.MANAGE_PROPERTIES_PERMISSION};
        } else {
            permissions = new AppPermission[]{AppPermission.MANAGE_PROCESSORS_PERMISSION};
        }
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .appPermissions(permissions)
                .realAlerts()
                .build();
        harness.afterStartUp(() -> harness.addContent(injector.getExecutionScheduleManager()));
        return harness.asWidget();
    }
}
