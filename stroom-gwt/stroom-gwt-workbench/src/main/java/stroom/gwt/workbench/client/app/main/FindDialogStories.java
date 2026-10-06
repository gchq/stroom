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

import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.presenter.FindPresenter;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Stories matching `App/Main/FindDialog` in the React Storybook, showing Stroom's real
/// [FindPresenter] (the 'Find' dialog) with fake REST replies.
///
/// The React story's `find` seam becomes `POST /explorer/v2/find`, answered by a handler that, as
/// the React fixture does, filters a small fixed set by the request's `nameFilter`. The dialog is
/// shown by firing `ShowFindEvent`, with the presenter (from GIN) registered as its handler in place
/// of its GWTP proxy.
public final class FindDialogStories {

    // {type, uuid, name, path}
    private static final String[][] DOCUMENTS = {
            {"Dictionary", "d1", "Countries", "System / Dictionaries"},
            {"Feed", "f1", "EVENTS", "System / Feeds"},
            {"Pipeline", "p1", "Events Pipeline", "System / Pipelines"}};

    private static final String FIND_PATH = "/explorer/v2/find";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post(FIND_PATH, FindDialogStories::find)
            .build();

    private FindDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/FindDialog", FindDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A quick filter above a result list; typing filters by name
                .story("Default", FindDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.expect(screen.findByText("Find")).toBeInTheDocument();
                    // A blank filter shows nothing (GWT's empty guard: no request is made).
                    // Differs from React: GWT shows an empty list, with no 'Type to find documents.'
                    play.expect(screen.queryByText("Countries")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post(FIND_PATH).toSpyMatcher());
                    // Type a filter: the matching documents appear (debounced).
                    // Differs from React: GWT's quick filter has no id, only a placeholder
                    play.type(screen.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER), "Events");
                    play.waitFor(3000, () -> play.expect(screen.getByText("EVENTS")).toBeInTheDocument());
                    play.expect(screen.getByText("Events Pipeline")).toBeInTheDocument();
                    play.expect(screen.queryByText("Countries")).toBeNull();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(RequestMatcher.post(FIND_PATH)
                            .withJsonBodyContaining("{\"filter\": {\"nameFilter\": \"Events\"}}")
                            .toSpyMatcher());
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static RestReply find(final RecordedRequest request) {
        final Map<?, ?> body = (Map<?, ?>) JsonValues.parse(request.getBody());
        final Map<?, ?> filter = (Map<?, ?>) body.get("filter");
        final Object nameFilter = filter == null
                ? null
                : filter.get("nameFilter");
        final String query = nameFilter == null
                ? ""
                : nameFilter.toString().toLowerCase();
        final List<String> values = new ArrayList<>();
        for (final String[] doc : DOCUMENTS) {
            if (query.isEmpty() || doc[2].toLowerCase().contains(query)) {
                values.add("{\"docRef\": {\"type\": \"" + doc[0] + "\", \"uuid\": \"" + doc[1] + "\", \"name\": \""
                        + doc[2] + "\"}, \"path\": \"" + doc[3] + "\"}");
            }
        }
        return RestReply.json("{\"values\": [" + String.join(", ", values) + "], \"pageResponse\": {\"offset\": 0, "
                + "\"length\": " + values.size() + ", \"total\": " + values.size() + ", \"exact\": true}}");
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        // As the presenter's GWTP proxy would
        final FindPresenter presenter = injector.getFindPresenter();
        harness.addRegistration(harness.getEventBus().addHandler(ShowFindEvent.getType(), presenter));
        harness.afterStartUp(() -> ShowFindEvent.fire(harness.getHasHandlers()));
        return harness.asWidget();
    }
}
