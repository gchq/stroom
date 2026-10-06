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
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.presenter.BatchDocumentPermissionsPresenter;
import stroom.security.shared.DocumentPermissionFields;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/DocumentPermissionsScreen` in the React Storybook, showing Stroom's
/// real [BatchDocumentPermissionsPresenter] (the 'Document Permissions' tab) with fake REST
/// replies.
///
/// The React story's `BatchDocPermissionApi` becomes routes for Stroom's `ExplorerResource`:
/// `find` → `POST /explorer/v2/advancedFind` and `changeDocumentPermissions` →
/// `POST /explorer/v2/changeDocumentPermissions` (its recorder becomes a check on the request spy).
/// Confirmations are Stroom's real dialogs. The presenter comes from GIN and is opened as
/// `DocumentPermissionsPlugin.open` opens it (filtered to the descendants of System).
public final class DocumentPermissionsScreenStories {

    private static final String CHANGE_PATH = "/explorer/v2/changeDocumentPermissions";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .post("/explorer/v2/advancedFind", RestReply.json("""
                    {"values": [
                      {"docRef": {"type": "Dictionary", "uuid": "d-1", "name": "Alpha Dictionary"},
                       "path": "System / Config"},
                      {"docRef": {"type": "Feed", "uuid": "f-1", "name": "Beta Feed"}, "path": "System / Feeds"}
                    ], "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}}"""))
            .post(CHANGE_PATH, RestReply.json("true"))
            .build();

    private DocumentPermissionsScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/DocumentPermissionsScreen", DocumentPermissionsScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Batch Edit: 'Remove all permissions' across the filtered documents, confirmed
                .story("DocumentPermissions", DocumentPermissionsScreenStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Alpha Dictionary");
                    play.expect(play.getByText("Beta Feed")).toBeInTheDocument();
                    play.click(play.getByTitle("Batch Edit Permissions For Filtered Documents"));
                    // Differs from React: Stroom's dialogs have no role="dialog"
                    final Play dialog = screen.within(screen.findByText(
                            "Batch Change Permissions For All Filtered Documents").closest(StroomDom.DIALOG));
                    // The change kind: 'Remove all permissions ...' needs nothing else.
                    // Differs from React: GWT's SelectionBox opens when its text box is clicked
                    play.click(dialog.querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Remove all permissions for all users [DANGEROUS]"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // Confirm
                    final Play confirm = screen.within(screen.findByText(
                                    "Are you sure you want to change permissions for 2 documents?")
                            .closest(StroomDom.DIALOG));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("{\"change\": {\"type\": \"RemoveAllPermissions\"}}")
                                    .toSpyMatcher()));
                    // Stroom reports the success with an info alert
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Successfully changed permissions."));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The Filter button opens the expression builder over the document permission fields
                .story("FilterDialog", DocumentPermissionsScreenStories::render)
                .withPlay(play -> {
                    play.findByText("Alpha Dictionary");
                    play.click(play.getByTitle("Filter Documents To Apply Permissions Changes On"));
                    play.screen().findByText("Filter Documents");
                    SecurityPlays.expectNoProblems(play);
                });
    }

    private static Widget render(final StoryContext context) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .realAlerts()
                .build();
        // Opened as DocumentPermissionsPlugin.open opens it: the descendants of System, once Stroom
        // has started (the list reads the UI config)
        harness.afterStartUp(() -> {
            final BatchDocumentPermissionsPresenter presenter =
                    harness.addContent(injector.getBatchDocumentPermissionsPresenter());
            presenter.setExpression(ExpressionOperator.builder()
                    .addTerm(ExpressionTerm.builder()
                            .field(DocumentPermissionFields.DESCENDANTS.getFldName())
                            .condition(Condition.OF_DOC_REF)
                            .docRef(ExplorerConstants.SYSTEM_DOC_REF)
                            .build())
                    .build());
        });
        return harness.asWidget();
    }
}
