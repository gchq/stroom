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

import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.presenter.FindInContentPresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/FindInContentDialog` in the React Storybook, showing Stroom's real
/// [FindInContentPresenter] (the 'Find In Content' dialog: a pattern box with match case and regex
/// toggles over a result list and a highlighted preview) with fake REST replies.
///
/// The React story's `findInContent` seam becomes `POST /explorer/v2/findInContent` and
/// `fetchHighlights` becomes `POST /explorer/v2/fetchHighlights` (one reply per document, routed by
/// the request's document). The dialog is shown by firing `ShowFindInContentEvent`, with the
/// presenter (from GIN) as its handler in place of its GWTP proxy.
public final class FindInContentDialogStories {

    private static final String FIND_IN_CONTENT_PATH = "/explorer/v2/findInContent";
    private static final String HIGHLIGHTS_PATH = "/explorer/v2/fetchHighlights";

    private static final String MATCHES = """
            {
              "values": [
                {
                  "docContentMatch": {
                    "docRef": {"type": "Pipeline", "uuid": "p1", "name": "Events Pipeline"},
                    "extension": "xml", "location": {"offset": 4, "length": 6},
                    "sample": "The events pipeline reads XML", "sampleAtStartOfLine": true, "tags": ["xml"]
                  },
                  "path": "System / Pipelines"
                },
                {
                  "docContentMatch": {
                    "docRef": {"type": "Dictionary", "uuid": "d1", "name": "Events Dictionary"},
                    "extension": "txt", "location": {"offset": 0, "length": 6},
                    "sample": "events, alerts, incidents", "sampleAtStartOfLine": true, "tags": []
                  },
                  "path": "System / Dictionaries"
                }
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    // Distinctive preview markers (absent from the result samples) so the play can tell which
    // document is previewed
    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post(FIND_IN_CONTENT_PATH, RestReply.json(MATCHES))
            .route(RequestMatcher.post(HIGHLIGHTS_PATH).withJsonBodyContaining("{\"docRef\": {\"uuid\": \"d1\"}}"),
                    RestReply.json("{\"docRef\": {\"type\": \"Dictionary\", \"uuid\": \"d1\"}, "
                            + "\"text\": \"DICTIONARYPREVIEW alerts incidents\", "
                            + "\"highlights\": [{\"offset\": 0, \"length\": 17}]}"))
            .post(HIGHLIGHTS_PATH, RestReply.json("{\"docRef\": {\"type\": \"Pipeline\", \"uuid\": \"p1\"}, "
                    + "\"text\": \"PIPELINEPREVIEW reads XML\", \"highlights\": [{\"offset\": 0, \"length\": 15}]}"))
            .build();

    private FindInContentDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/FindInContentDialog", FindInContentDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Default", FindInContentDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(screen.findByText("Find In Content")).toBeInTheDocument();
                    // Type a pattern: the matching documents appear (debounced 750ms).
                    // Differs from React: GWT's pattern box is a text area with no label
                    play.type(screen.querySelector(".FindViewImpl-top textarea"), "events");
                    play.waitFor(4000, () -> play.expect(screen.getByText("Events Pipeline")).toBeInTheDocument());
                    play.expect(screen.getByText("Events Dictionary")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_IN_CONTENT_PATH)
                                    .withJsonBodyContaining("{\"filter\": {\"pattern\": \"events\"}}")
                                    .toSpyMatcher());
                    // The first result auto-selects and its highlighted preview loads (in the
                    // read-only Ace editor)
                    play.waitFor(4000, () -> play.expect(screen.getByText(TextMatch.containing("PIPELINEPREVIEW")))
                            .toBeInTheDocument());
                    // Match case and Regex toggles are present.
                    // Differs from React: GWT's toggles are InlineSvgToggleButtons, divs titled with
                    // their names rather than buttons
                    play.expect(screen.getByTitle("Match case")).toBeInTheDocument();
                    play.expect(screen.getByTitle("Regex")).toBeInTheDocument();
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.afterStartUp(() -> {
            // As the presenter's GWTP proxy would
            final FindInContentPresenter presenter = injector.getFindInContentPresenter();
            harness.addRegistration(harness.getEventBus().addHandler(ShowFindInContentEvent.getType(),
                    presenter::onShow));
            ShowFindInContentEvent.fire(harness.getHasHandlers());
        });
        return harness.asWidget();
    }
}
