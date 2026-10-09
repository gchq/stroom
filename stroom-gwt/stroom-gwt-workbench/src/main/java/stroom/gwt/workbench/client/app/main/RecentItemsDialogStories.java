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

import stroom.data.grid.client.PagerViewImpl;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.event.ShowRecentItemsEvent;
import stroom.explorer.client.presenter.FindDocResultListPresenter;
import stroom.explorer.client.presenter.RecentItems;
import stroom.explorer.client.presenter.RecentItemsPresenter;
import stroom.explorer.client.view.FindViewImpl;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// Stories of `App/Main/RecentItemsDialog`, showing Stroom's real [RecentItemsPresenter] (the
/// 'Recent Items' dialog) with fake REST replies.
///
/// A [RestFixtures] route answers `POST /explorer/v2/find`, and the `onOpenDoc` spy records
/// [OpenDocumentEvent].
public final class RecentItemsDialogStories {

    /// The name of the spy recording the documents opened.
    static final String OPEN_DOC_SPY = "onOpenDoc";

    // The rows of the dialog's result list (GWT marks each data row of a CellTable with __gwt_row)
    private static final String RESULT_ROWS = ".FindCellTable tr[__gwt_row]";

    private static final DocRef RECENT_DICTIONARY = new DocRef("Dictionary", "d-1", "Recent Dictionary");
    private static final DocRef OLDER_FEED = new DocRef("Feed", "f-1", "Older Feed");

    // The reply of ExplorerResource.find(), in the order of recency.
    // The dialog shows the results in the order the server returns them (the server orders them by
    // recency), so this fixture is already in that order
    private static final String FIND_RESULTS = """
            {
              "values": [
                {
                  "docRef": {"type": "Dictionary", "uuid": "d-1", "name": "Recent Dictionary"},
                  "path": "System / Config"
                },
                {
                  "docRef": {"type": "Feed", "uuid": "f-1", "name": "Older Feed"},
                  "path": "System / Feeds"
                }
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }
            """;

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/explorer/v2/find", RestReply.json(FIND_RESULTS))
            .build();

    private RecentItemsDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/RecentItemsDialog", RecentItemsDialogStories.class)
                // Centred; the dialog itself is shown on the page's body
                .layout(StoryLayout.CENTERED)
                // Recent items load; double-click opens the document
                .story("Recent", context -> render(context, Arrays.asList(RECENT_DICTIONARY, OLDER_FEED)))
                .withPlay(play -> {
                    // The dialog is shown on the page's body, as in Stroom
                    final Play screen = play.screen();
                    screen.findByText("Recent Items");
                    screen.findByText("Recent Dictionary");
                    play.expect(screen.getByText("Older Feed")).toBeInTheDocument();
                    play.expect(screen.querySelectorAll(RESULT_ROWS)).toHaveLength(2);
                    // The recent items are sent to the server to find
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("POST /explorer/v2/find"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("\"uuid\":\"d-1\""));

                    play.dblClick(screen.getByText("Recent Dictionary"));
                    play.waitFor(() -> play.expect(play.spy(OPEN_DOC_SPY))
                            .toHaveBeenCalledWith("Dictionary Recent Dictionary"));
                    // Opening a document closes the dialog
                    play.waitFor(() -> play.expect(screen.queryByText("Recent Items")).toBeNull());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Empty history shows no results, without asking the server
                .story("Empty", context -> render(context, Collections.emptyList()))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Recent Items");
                    // The dialog shows an empty result list with no message
                    play.expect(screen.querySelectorAll(RESULT_ROWS)).toHaveLength(0);
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Widget render(final StoryContext context, final List<DocRef> recent) {
        final ScreenHarness harness = ScreenHarness.create(context, FIXTURES);
        final EventBus eventBus = harness.getEventBus();

        // RecentItems is normally filled as documents are opened
        final RecentItems recentItems = new RecentItems();
        recentItems.getRecentItems().addAll(recent);

        // The presenter, with the views and child presenter that GIN would inject
        final FindDocResultListPresenter resultList = new FindDocResultListPresenter(
                eventBus,
                new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class)),
                harness.getRestFactory());
        final RecentItemsPresenter presenter = new RecentItemsPresenter(
                eventBus,
                new FindViewImpl(GWT.create(FindViewImpl.Binder.class)),
                null,
                resultList,
                recentItems);

        harness.fn(OPEN_DOC_SPY);
        eventBus.addHandler(OpenDocumentEvent.getType(), event ->
                harness.spy(OPEN_DOC_SPY, event.getDocRef().getType() + " " + event.getDocRef().getName()));

        // Show the dialog as the app does, i.e. as GWTP's proxy would on the event
        eventBus.addHandler(ShowRecentItemsEvent.getType(), presenter);
        ShowRecentItemsEvent.fire(harness.getHasHandlers());
        return harness.asWidget();
    }
}
