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

import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.event.GetCurrentTabsEvent;
import stroom.explorer.client.event.OpenTabSessionEvent;
import stroom.explorer.client.presenter.TabSessionManager;
import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
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

import java.util.Collections;

/// Stories matching `App/Main/TabSessionChooserDialog` in the React Storybook, showing Stroom's real
/// `TabSessionChooserPresenter`, as Stroom's [TabSessionManager] shows it for 'Open Tab Session',
/// with fake REST replies.
///
/// The React story's `sessions` prop becomes `GET /tabSession/v1` (`TabSessionResource
/// .getForCurrentUser`), and its `onPick` is a spy on what picking a session does in Stroom: it
/// opens the session's documents (`OpenDocumentEvent`). The manager (from GIN) is the real one; the
/// story answers its `GetCurrentTabsEvent` (no tabs open) as the content pane would.
public final class TabSessionChooserDialogStories {

    /// The name of the spy recording the documents of the session picked.
    static final String ON_PICK = "onPick";

    private static final String SESSIONS = """
            [
              {"sessionId": "s1", "name": "Morning review",
                "docRefs": [{"type": "Feed", "uuid": "f1", "name": "Feed A"}]},
              {"sessionId": "s2", "name": "Pipeline work",
                "docRefs": [{"type": "Pipeline", "uuid": "p1", "name": "Pipe A"}]}
            ]""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get("/tabSession/v1", RestReply.json(SESSIONS))
            .build();

    private TabSessionChooserDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/TabSessionChooserDialog", TabSessionChooserDialogStories.class)
                .layout(StoryLayout.CENTERED)
                // Choosing a session and clicking OK picks it
                .story("Choose", TabSessionChooserDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Select Tab Session To Open:");
                    play.click(screen.getByText("Pipeline work"));
                    play.click(screen.getByRole("button", StroomDom.button("OK")));
                    // Differs from React: picking a session in GWT opens its documents
                    play.waitFor(() -> play.expect(play.spy(ON_PICK)).toHaveBeenCalledWith("Pipeline Pipe A"));
                    play.expect(play.spy(ON_PICK)).not().toHaveBeenCalledWith("Feed Feed A");
                    ContentStorySupport.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.fn(ON_PICK);
        harness.addRegistration(harness.getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                harness.spy(ON_PICK, event.getDocRef().getType() + " " + event.getDocRef().getName())));
        // As the content pane does: no tabs are open
        harness.addRegistration(harness.getEventBus().addHandler(GetCurrentTabsEvent.getType(), event ->
                event.getCallback().accept(Collections.emptyList())));
        final TabSessionManager manager = injector.getTabSessionManager();
        harness.addCleanUp(manager::unbind);
        harness.afterStartUp(() -> OpenTabSessionEvent.fire(harness.getHasHandlers()));
        return harness.asWidget();
    }
}
