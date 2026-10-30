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

package stroom.gwt.workbench.client.app.processing;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.client.event.OpenFeedEvent;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.processor.task.client.component.ProcessorTaskList;
import stroom.processor.task.client.component.ProcessorTaskListController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/ProcessorTaskList`, showing Stroom's real [ProcessorTaskListController]
/// (the task list in the lower pane of a folder's 'Active Tasks' tab) with fake REST replies.
///
/// The rows come from `POST /processorTask/v1/find`. A row's Info icon looks up the task's stream
/// with `POST /meta/v1/find` before showing the task's details in a tooltip; the stories answer it
/// with no streams, so the tooltip says the stream was '[Physically deleted]'.
///
/// gh-2598: clicking the Info icons of a few rows eventually showed nothing.
///
/// The `openFeed` spy records the feed name of each [OpenFeedEvent] fired, e.g. by the tooltip's
/// feed link.
public final class ProcessorTaskListStories {

    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid-1", "My Folder");

    private static final String FIND_TASKS_PATH = "/processorTask/v1/find";
    private static final String FIND_META_PATH = "/meta/v1/find";
    private static final String OPEN_FEED_SPY = "openFeed";

    // Three tasks, ids 101 to 103
    private static final String TASK_ROWS = """
            {
              "values": [
                {"id": 101, "metaId": 1001, "nodeName": "node1a", "feedName": "TEST_FEED",
                  "createTimeMs": 1700000000000, "statusTimeMs": 1700000000000, "status": "PROCESSING"},
                {"id": 102, "metaId": 1002, "nodeName": "node1a", "feedName": "TEST_FEED",
                  "createTimeMs": 1700000000000, "statusTimeMs": 1700000000000, "status": "PROCESSING"},
                {"id": 103, "metaId": 1003, "nodeName": "node1a", "feedName": "TEST_FEED",
                  "createTimeMs": 1700000000000, "statusTimeMs": 1700000000000, "status": "QUEUED"}
              ],
              "pageResponse": {"offset": 0, "length": 3, "total": 3, "exact": true}
            }""";

    private static final String NO_META = """
            {"values": [], "pageResponse": {"offset": 0, "length": 0, "total": 0, "exact": true}}""";

    // Long enough for a second Info click to be made before the first lookup replies
    private static final int SLOW_META_MILLIS = 300;

    private ProcessorTaskListStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ProcessorTaskList", ProcessorTaskListStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A row's Info icon shows the task's details
                .story("InfoShowsTheTask", context -> render(context, 0))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findAllByTitle("Info").nth(0));
                    play.waitFor(() -> play.expect(screen.getByText("Stream Task Id")).toBeInTheDocument());
                    play.expect(screen.getByText("101")).toBeInTheDocument();
                    play.expect(screen.getByText("[Physically deleted]")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // gh-2598: the Info icons of several rows in turn, each once the last has shown, and
                // then the first again. Each click shows that row's task
                .story("InfoOfEachRowInTurn", context -> render(context, 0))
                .withPlay(play -> {
                    expectInfoShows(play, 0, "101");
                    expectInfoShows(play, 1, "102");
                    expectInfoShows(play, 2, "103");
                    expectInfoShows(play, 0, "101");
                    expectNoProblems(play);
                })
                // gh-2598: a second row's Info icon clicked while the first row's stream lookup is
                // still in flight. The second row's task is shown, and stays shown
                .story("InfoOfTwoRowsClickedQuickly", context -> render(context, SLOW_META_MILLIS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findAllByTitle("Info").nth(0));
                    play.click(play.findAllByTitle("Info").nth(1));
                    play.waitFor(() -> play.expect(screen.getByText("102")).toBeInTheDocument());
                    // Both lookups have replied
                    play.sleep(SLOW_META_MILLIS * 2);
                    play.expect(screen.getByText("Stream Task Id")).toBeInTheDocument();
                    play.expect(screen.getByText("102")).toBeInTheDocument();
                    play.expect(screen.queryByText("101")).toBeNull();
                    expectNoProblems(play);
                })
                // The tooltip's feed link opens the feed once, however many times the tooltip has
                // been shown (each showing once added another click handler)
                .story("InfoFeedLinkOpensTheFeedOnce", context -> render(context, 0))
                .withPlay(play -> {
                    expectInfoShows(play, 0, "101");
                    expectInfoShows(play, 1, "102");
                    expectInfoShows(play, 2, "103");
                    final Play screen = play.screen();
                    final Query feedLink = screen.querySelector(".info-table .doc-ref-link__open");
                    play.hover(feedLink);
                    play.click(feedLink);
                    play.expect(play.spy(OPEN_FEED_SPY)).toHaveBeenCalledTimes(1);
                    play.expect(play.spy(OPEN_FEED_SPY)).toHaveBeenCalledWith("TEST_FEED");
                    expectNoProblems(play);
                });
    }

    // Clicks the Info icon of the row at index, and checks the tooltip shows that row's task id
    // (and no other's)
    private static void expectInfoShows(final Play play, final int index, final String taskId) {
        final Play screen = play.screen();
        play.click(play.findAllByTitle("Info").nth(index));
        play.waitFor(() -> play.expect(screen.getByText(taskId)).toBeInTheDocument());
        play.expect(screen.getAllByText("Stream Task Id")).toHaveLength(1);
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final int metaDelayMillis) {
        final RestReply metaReply = metaDelayMillis > 0
                ? RestReply.json(NO_META).delayed(metaDelayMillis)
                : RestReply.json(NO_META);
        final RestFixtures fixtures = RestFixtures.builder()
                .post(FIND_TASKS_PATH, RestReply.json(TASK_ROWS))
                .post(FIND_META_PATH, metaReply)
                .build();
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        final Spy openFeed = context.fn(OPEN_FEED_SPY);
        harness.getEventBus().addHandler(OpenFeedEvent.getType(), event -> openFeed.call(event.getName()));
        harness.afterStartUp(() -> {
            final ProcessorTaskListController tasksList = injector.getProcessorTaskListController()
                    .attach(new ProcessorTaskList());
            // As a folder's 'Active Tasks' tab reads it
            tasksList.read(FOLDER, null);
            harness.show(tasksList);
        });
        return harness.asWidget();
    }
}
