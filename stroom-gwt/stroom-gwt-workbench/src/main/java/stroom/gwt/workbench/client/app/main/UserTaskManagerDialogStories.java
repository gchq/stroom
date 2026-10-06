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

import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.task.client.event.OpenUserTaskManagerEvent;
import stroom.task.client.presenter.UserTaskManagerPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories matching `App/Main/UserTaskManagerDialog` in the React Storybook, showing Stroom's real
/// [UserTaskManagerPresenter] (the current user's 'Task Manager' dialog) with fake REST replies.
///
/// The dialog polls: once a second, while it is open, it lists the nodes (`GET /node/v1/all`) and
/// fetches the user's tasks from each (`GET /task/v1/user/{node}`, React's `userTasks`), so the
/// fixtures answer every poll with the same reply. Its timer only stops when the dialog is asked
/// to close, so the story registers it with `ScreenHarness.closeOnCleanUp`. React's `terminate`
/// is `POST /task/v1/terminate/{node}`, checked with the request spy. The presenter comes from GIN
/// and is opened by its event, as its GWTP proxy would.
public final class UserTaskManagerDialogStories {

    // TaskResource.userTasks()
    private static final String TASKS = """
            {
              "values": [
                {"id": {"id": "t1"}, "nodeName": "node1", "taskName": "Search", "taskInfo": "Running query",
                  "submitTimeMs": 1000, "timeNowMs": 6000},
                {"id": {"id": "t2"}, "nodeName": "node1", "taskName": "Export", "taskInfo": "Writing zip",
                  "submitTimeMs": 3000, "timeNowMs": 6000}
              ],
              "errors": [],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get("/node/v1/all", RestReply.json("[\"node1\"]"))
            .get("/task/v1/user/node1", RestReply.json(TASKS))
            .post("/task/v1/terminate/node1", RestReply.json("true"))
            .build();

    private UserTaskManagerDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/UserTaskManagerDialog", UserTaskManagerDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Lists the current user's tasks (name/age/info) and terminates one on confirm
                .story("Tasks", UserTaskManagerDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(3000, () -> play.expect(screen.getByText("Search")).toBeInTheDocument());
                    play.expect(screen.getByText("Export")).toBeInTheDocument();
                    // GWT sorts by age descending (oldest first), so the first row is the older task
                    // (t1, submitted earlier). Terminate it, then confirm
                    play.click(screen.getAllByTitle("Terminate").nth(0));
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/task/v1/terminate/node1")
                                    .withJsonBodyContaining("{\"criteria\": {\"idSet\": [{\"id\": \"t1\"}]}}")
                                    .toSpyMatcher()));
                    // It polls: the tasks were fetched again a second later
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.waitFor(3000, () -> play.expect("user task fetches", () -> countTaskFetches(requests))
                            .toSatisfy("more than one", count -> ((Integer) count) > 1));
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static int countTaskFetches(final Spy requests) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            if ("GET /task/v1/user/node1".equals(call.get(0))) {
                count++;
            }
        }
        return count;
    }

    private static Widget render(final StoryContext context) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                // The confirmation is answered in Stroom's real dialog
                .realAlerts()
                .build();

        // As the presenter's GWTP proxy would; asked to close on re-render, to stop its polling
        final UserTaskManagerPresenter presenter = harness.closeOnCleanUp(injector.getUserTaskManagerPresenter());
        harness.addRegistration(harness.getEventBus().addHandler(OpenUserTaskManagerEvent.getType(), presenter));
        // As the 'Task Manager' button does
        OpenUserTaskManagerEvent.fire(harness.getHasHandlers());
        return harness.asWidget();
    }
}
