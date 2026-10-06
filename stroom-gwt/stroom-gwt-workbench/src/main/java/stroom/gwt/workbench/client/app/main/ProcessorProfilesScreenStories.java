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

import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.processor.client.presenter.ProcessorProfilePresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;

/// Stories matching `App/Main/ProcessorProfilesScreen` in the React Storybook, showing Stroom's
/// real [ProcessorProfilePresenter] (the 'Processor Profiles' tab, as `ProcessorProfilePlugin`
/// opens it) with fake REST replies.
///
/// | React | Stroom |
/// |---|---|
/// | `findProfiles` | `POST /processorProfile/v1/find` (a sequence: the list as it changes) |
/// | `fetchById` | `GET /processorProfile/v1/fetchById/{id}` |
/// | `create` | `GET /processorProfile/v1/fetchByName/{name}` (the name check), then `POST /processorProfile/v1` |
/// | `update` | `GET /processorProfile/v1/fetchByName/{name}`, then `PUT /processorProfile/v1/{id}` |
/// | `nodeGroups.findGroups` | `POST /node/nodeGroup/v2/find`, `GET /node/nodeGroup/v2/fetchByName/{name}` |
///
/// The recorder's checks become checks on the request spy.
public final class ProcessorProfilesScreenStories {

    private static final String FIND_PATH = "/processorProfile/v1/find";
    private static final String UPDATE_PATH = "/processorProfile/v1/1";

    private static final String NIGHTLY = """
            {"id": 1, "version": 1, "name": "Nightly", "nodeGroupName": "All Nodes",
              "profilePeriods": [{"uuid": "p1"}], "timeZone": {"use": "UTC"}}""";

    private static final String HOURLY = """
            {"id": 99, "version": 1, "name": "Hourly", "profilePeriods": [], "timeZone": {"use": "UTC"}}""";

    private static final String PROFILES = """
            {"values": [NIGHTLY], "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}}"""
            .replace("NIGHTLY", NIGHTLY);

    private static final String PROFILES_CREATED = """
            {"values": [NIGHTLY, HOURLY], "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}"""
            .replace("NIGHTLY", NIGHTLY)
            .replace("HOURLY", HOURLY);

    private static final String ALL_NODES = """
            {"id": 1, "version": 1, "name": "All Nodes", "enabled": true, "invertSelection": false}""";

    private static final String NODE_GROUPS = """
            {
              "values": [
                ALL_NODES,
                {"id": 2, "version": 1, "name": "Processing", "enabled": true, "invertSelection": false}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""".replace("ALL_NODES", ALL_NODES);

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            // The list as loaded, then after 'Hourly' is created
            .post(FIND_PATH, RestReply.json(PROFILES), RestReply.json(PROFILES_CREATED))
            .get("/processorProfile/v1/fetchById/1", RestReply.json(NIGHTLY))
            .get("/processorProfile/v1/fetchByName/Nightly", RestReply.json(NIGHTLY))
            // No profile has the name yet
            .get("/processorProfile/v1/fetchByName/Hourly", RestReply.noContent())
            .post("/processorProfile/v1", RestReply.json(HOURLY))
            .put(UPDATE_PATH, request -> RestReply.json(request.getBody()))
            .post("/node/nodeGroup/v2/find", RestReply.json(NODE_GROUPS))
            .get("/node/nodeGroup/v2/fetchByName/All%20Nodes", RestReply.json(ALL_NODES))
            .build();

    // The period editor's caption.
    // Differs from React: GWT's ProfilePeriodEditPresenter captions it 'Edit Period' (even for a new
    // period), not 'Processing Schedule'
    private static final String PERIOD_CAPTION = "Edit Period";

    private ProcessorProfilesScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ProcessorProfilesScreen", ProcessorProfilesScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Adding a Processing Schedule period (days + node-thread limit) persists on save
                .story("EditPeriods", ProcessorProfilesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Nightly");
                    // Open the profile editor: its schedules sub-grid shows the seeded (empty) period
                    play.dblClick(play.getByText("Nightly"));
                    final Play profile = screen.within(screen.findByText(
                            TextMatch.containing("Edit Processor Profile")).closest(StroomDom.DIALOG));
                    final Query periodRow = periodRow(play, profile);
                    // Differs from React: p1 has no days, and GWT shows 'None' (ProfilePeriod's
                    // constructor defaults null days to an empty Days, whose toString is 'None'), not
                    // an empty cell
                    play.expect(periodRow).toHaveTextContent(TextMatch.containing("None"));
                    play.expect(periodRow).not().toHaveTextContent(TextMatch.containing("00:00:00"));
                    // Add a period
                    play.click(profile.getByTitle("New Period"));
                    final Play period = screen.within(screen.findByText(PERIOD_CAPTION, StroomDom.DIALOG_TITLE)
                            .closest(StroomDom.DIALOG));
                    // The check boxes: Monday..Sunday, then Limit Single Node Threads and Limit Total
                    // Cluster Threads
                    play.click(period.getAllByRole("checkbox").nth(0));
                    play.click(period.getAllByRole("checkbox").nth(7));
                    // Differs from React: GWT always shows the 'Max Node Threads' spinner
                    period.findByText("Max Node Threads");
                    play.click(period.getByRole("button", StroomDom.button("OK")));
                    // The new period row shows the day range (a single contiguous day is 'Mon-Mon')
                    profile.findByText("Mon-Mon");
                    play.click(profile.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(UPDATE_PATH)
                                    .withBody("two periods, the new one limiting node threads on Mondays",
                                            ProcessorProfilesScreenStories::hasAddedMondayPeriod)
                                    .toSpyMatcher()));
                    expectNoProblems(play);
                })
                // The Processing Schedule sub-grid opens a period on double click, not on Enter
                .story("PeriodOpensOnDoubleClickOnly", ProcessorProfilesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Nightly");
                    play.dblClick(play.getByText("Nightly"));
                    final Play profile = screen.within(screen.findByText(
                            TextMatch.containing("Edit Processor Profile")).closest(StroomDom.DIALOG));
                    // p1 has no days, so its cell is empty: the row is found by position
                    // The row's first cell: GWT's grid handles the events of its cells
                    final Query row = play.within(periodRow(play, profile)).querySelector("td");
                    // Enter on the selected row must not open the editor
                    play.click(row);
                    play.keyboard("{Enter}");
                    play.expect(screen.queryByText(PERIOD_CAPTION, StroomDom.DIALOG_TITLE)).toBeNull();
                    // A double click does
                    play.dblClick(row);
                    screen.findByText(PERIOD_CAPTION, StroomDom.DIALOG_TITLE);
                    expectNoProblems(play);
                })
                // Editing a profile preserves its periods/time zone; New creates one via the dialog
                .story("CrudFlow", ProcessorProfilesScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Nightly");
                    // Create a new profile.
                    // Differs from React: the 'New' button is an icon button titled 'New'
                    play.click(play.getByTitle("New"));
                    screen.findByText("Create Processor Profile");
                    // Differs from React: the field's id is the FormGroup's identity, 'profileName'
                    final Query name = screen.querySelector("#profileName");
                    // It's seeded with "New Profile"
                    play.expect(name).toHaveValue("New Profile");
                    play.clear(name);
                    play.type(name, "Hourly");
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/processorProfile/v1")
                                    .withJsonBodyContaining("{\"name\": \"Hourly\"}")
                                    .toSpyMatcher()));
                    play.findByText("Hourly");
                    // Edit the existing profile: its periods/time zone must round-trip untouched
                    play.dblClick(play.getByText("Nightly"));
                    screen.findByText(TextMatch.containing("Edit Processor Profile"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    // Differs from React: GWT's ProcessorProfileEditPresenter closes an unchanged
                    // profile without saving it (Objects.equals(updated, processorProfile)), so its
                    // periods and time zone are kept by not being sent at all
                    play.waitFor(() -> play.expect(screen.queryByText(TextMatch.containing("Edit Processor Profile")))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.put(UPDATE_PATH).toSpyMatcher());
                    expectNoProblems(play);
                });
    }

    // The first period row of the profile editor's 'Processing Schedules' grid, once it shows.
    // Differs from React: GWT's grid rows are <tr __gwt_row> elements, not '[data-row-index]'
    private static Query periodRow(final Play play, final Play profile) {
        play.waitFor(() -> play.expect(profile.querySelectorAll(StroomDom.GRID_ROW).count()).toBe(1));
        return profile.querySelectorAll(StroomDom.GRID_ROW).nth(0);
    }

    private static List<?> periods(final String body) {
        if (body == null) {
            return List.of();
        }
        final Object periods = ((Map<?, ?>) JsonValues.parse(body)).get("profilePeriods");
        return periods instanceof List
                ? (List<?>) periods
                : List.of();
    }

    private static boolean hasAddedMondayPeriod(final String body) {
        final List<?> periods = periods(body);
        if (periods.size() != 2) {
            return false;
        }
        for (final Object period : periods) {
            final Map<?, ?> map = (Map<?, ?>) period;
            if (Boolean.TRUE.equals(map.get("limitNodeThreads"))) {
                final Object days = map.get("days");
                return days instanceof Map
                       && ((Map<?, ?>) days).get("days") instanceof List
                       && ((List<?>) ((Map<?, ?>) days).get("days")).contains("MONDAY");
            }
        }
        return false;
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> {
            final ProcessorProfilePresenter presenter = injector.getProcessorProfilePresenter();
            harness.addContent(presenter);
            // As the content pane does when it shows a Refreshable tab
            presenter.refresh();
        });
        return harness.asWidget();
    }
}
