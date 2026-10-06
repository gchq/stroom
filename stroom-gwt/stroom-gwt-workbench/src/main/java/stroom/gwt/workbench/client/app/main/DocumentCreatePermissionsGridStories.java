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

import stroom.explorer.shared.ExplorerConstants;
import stroom.gwt.workbench.client.app.gin.security.SecurityScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.presenter.DocumentCreatePermissionsListPresenter;
import stroom.security.shared.DocumentUserPermissionsReport;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// Stories matching `App/Main/DocumentCreatePermissionsGrid` in the React Storybook, showing
/// Stroom's real [DocumentCreatePermissionsListPresenter] (the grid of document types a user may
/// create in a folder, in the 'Set Document Create Permissions' dialog) with fake REST replies.
///
/// React's `report` is given to the grid with `setup(report)`, as
/// `DocumentUserCreatePermissionsEditPresenter` gives it, and its `fetchTypes` is Stroom's document
/// types (`GET /explorer/v2/fetchDocumentTypes`, here the three React types). GWT's grid has no
/// change callback (Stroom reads `getExplicitCreatePermissions()` when the dialog's OK is pressed),
/// so the story reports the working set to React's `onChange` spy after each click.
public final class DocumentCreatePermissionsGridStories {

    /// The name of the spy recording the working set (sorted) after each change.
    static final String ON_CHANGE = "onChange";

    private static final String TYPES = """
            [{"group": "STRUCTURE", "type": "Dictionary", "displayType": "Dictionary", "icon": "DOCUMENT_DICTIONARY"},
             {"group": "DATA_PROCESSING", "type": "Pipeline", "displayType": "Pipeline", "icon": "DOCUMENT_PIPELINE"},
             {"group": "DATA_PROCESSING", "type": "Feed", "displayType": "Feed", "icon": "DOCUMENT_FEED"}]""";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get(StartupFixtures.DOCUMENT_TYPES_PATH,
                    RestReply.json("{\"types\": " + TYPES + ", \"visibleTypes\": " + TYPES + "}"))
            .build();

    private DocumentCreatePermissionsGridStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DocumentCreatePermissionsGrid", DocumentCreatePermissionsGridStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Ticking a type adds it to the working set
                .story("ToggleType", context -> render(context, "Dictionary"))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Pipeline")).toBeInTheDocument());
                    // Differs from React: GWT's grid tick is a TickBoxCell div, not a checkbox
                    play.click(play.within(play.getByText("Pipeline").closest("tr")).querySelector(".tickBox"));
                    final Spy onChange = play.spy(ON_CHANGE);
                    play.waitFor(() -> play.expect("the working set", () -> lastSet(onChange))
                            .toContain("Pipeline"));
                    SecurityPlays.expectNoProblems(play);
                })
                // The header tick toggles the '[ all ]' sentinel into the set
                .story("ToggleAll", context -> render(context))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByText("Dictionary")).toBeInTheDocument());
                    // The header's tick box is the grid's first
                    play.click(play.querySelector(".tickBox"));
                    final Spy onChange = play.spy(ON_CHANGE);
                    play.waitFor(() -> play.expect("the working set", () -> lastSet(onChange))
                            .toContain(ExplorerConstants.ALL_CREATE_PERMISSIONS));
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// @return The last working set reported to the spy, as a list of its members.
    private static List<String> lastSet(final Spy onChange) {
        final List<Object> call = onChange.getLastCall();
        if (call == null || call.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> members = new ArrayList<>();
        for (final String member : String.valueOf(call.get(0)).split(",")) {
            members.add(member);
        }
        return members;
    }

    private static Widget render(final StoryContext context, final String... explicit) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        harness.fn(ON_CHANGE);
        harness.afterStartUp(() -> {
            final DocumentCreatePermissionsListPresenter presenter =
                    harness.addContent(injector.getDocumentCreatePermissionsListPresenter());
            // React's report: the explicit types, and Feed inherited from /System
            final Set<String> explicitTypes = new HashSet<>(List.of(explicit));
            presenter.setup(new DocumentUserPermissionsReport(null, explicitTypes, Collections.emptyMap(),
                    Map.of("Feed", List.of("/System"))));
            // Differs from React: no change callback; the working set is reported once the grid
            // has handled the click
            harness.addRegistration(presenter.getWidget().addDomHandler(event ->
                            Scheduler.get().scheduleDeferred(() -> harness.spy(ON_CHANGE,
                                    String.join(",", new TreeSet<>(presenter.getExplicitCreatePermissions())))),
                    ClickEvent.getType()));
        });
        return harness.asWidget();
    }
}
