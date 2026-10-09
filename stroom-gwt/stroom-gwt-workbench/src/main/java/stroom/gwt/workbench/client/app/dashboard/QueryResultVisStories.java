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

package stroom.gwt.workbench.client.app.dashboard;

import stroom.gwt.workbench.client.app.editors.QueryEditorStories;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// The `App/Dashboard/QueryResultVis` stories, showing Stroom's real `QueryResultVisPresenter` (a
/// query's visualisation pane) in its Query editor, as `App/Editors/QueryEditor` does, with fake
/// REST replies.
///
/// The presenter keeps its pause state and shows it on its refresh button (titled 'Pause Update'
/// while updating, 'Resume Update' and marked `paused` when paused), which the play checks. The
/// button can only be pressed while the search is updating, so the search's last reply is delayed.
public final class QueryResultVisStories {

    // A first reply with the visualisation's data, then (later) the complete search
    private static final RestFixtures FIXTURES = QueryEditorStories.visFixtures(
            RestReply.json(QueryEditorStories.visResponse(false)),
            RestReply.json(QueryEditorStories.visResponse(true)).delayed(20_000));

    private QueryResultVisStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Dashboard/QueryResultVis", QueryResultVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("PauseButtonReportsUp", context -> QueryEditorStories.renderEditor(context, FIXTURES))
                .withPlay(play -> {
                    play.click(play.findByRole("button", "Execute Query"));
                    play.waitFor(8000, () -> play.expect(DocumentEditors.tab(play, "Visualisation")).toBeVisible());
                    // The pause state is the button's. Not paused at first
                    final Query button = pauseButton(play);
                    play.waitFor(() -> play.expect(button).toHaveAttribute("title", "Pause Update"));
                    play.expect(button).not().toHaveClass("paused");
                    // Pause, and the button offers to resume
                    play.click(button);
                    play.waitFor(() -> play.expect(button).toHaveClass("paused"));
                    play.expect(button).toHaveAttribute("title", "Resume Update");
                    // Resume
                    play.click(button);
                    play.waitFor(() -> play.expect(button).not().toHaveClass("paused"));
                    DocumentEditors.expectNoProblems(play);
                });
    }

    // The visualisation pane's refresh (pause) button
    private static Query pauseButton(final Play play) {
        return play.querySelector(".dashboardVis-refresh");
    }
}
