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

package stroom.gwt.workbench.client.app.security;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.security.SecurityScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Security/DocumentPermissionsTab` stories, showing Stroom's real
/// [DocumentUserPermissionsPresenter] (a document's 'Permissions' tab) with fake REST replies.
///
/// | Stroom REST endpoint | Used for |
/// |---|---|
/// | `POST /permission/doc/v1/fetchDocumentUserPermissions` | the users' permissions |
/// | `POST /permission/doc/v1/getDocUserPermissionsReport` | a user's report (by the user's UUID) |
/// | `POST /explorer/v2/advancedFind` | counting the descendants |
/// | `POST /explorer/v2/changeDocumentPermissions` | applying to descendants, create permissions |
/// | `GET /explorer/v2/fetchDocumentTypes` | the document types (three) |
///
/// The plays check the requests with the request spy. The tab is opened as
/// `DocumentPermissionsPlugin` opens it for a `ShowDocumentPermissionsEvent` (`setDocRef`).
/// Confirmations are Stroom's real dialogs. The presenter comes from GIN.
public final class DocumentPermissionsTabStories {

    private static final String EDIT_TITLE = "edit permissions";
    private static final String CHANGE_PATH = "/explorer/v2/changeDocumentPermissions";

    private static final DocRef FOLDER = new DocRef("Folder", "folder-1", "Reports");
    private static final DocRef GIT_REPO = new DocRef("GitRepo", "gitrepo-1", "Example Solr Index");
    private static final DocRef DICTIONARY = new DocRef("Dictionary", "dict-1", "Countries");

    private static final String ADMIN = """
            {"uuid": "u-admin", "displayName": "admin", "group": false, "enabled": true}""";
    private static final String ANALYSTS = """
            {"uuid": "g-analysts", "displayName": "Analysts", "group": true, "enabled": true}""";

    private static final String TYPES = """
            [{"group": "DATA_PROCESSING", "type": "Feed", "displayType": "Feed", "icon": "DOCUMENT_FEED"},
             {"group": "STRUCTURE", "type": "Dictionary", "displayType": "Dictionary",
              "icon": "DOCUMENT_DICTIONARY"},
             {"group": "DATA_PROCESSING", "type": "Pipeline", "displayType": "Pipeline",
              "icon": "DOCUMENT_PIPELINE"}]""";

    private DocumentPermissionsTabStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Security/DocumentPermissionsTab", DocumentPermissionsTabStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // On a folder, Set Permissions can apply the permission to the descendants
                .story("ApplyToDescendants", context -> render(context, FOLDER, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Analysts"));
                    final Query edit = play.getByRole("button", TextMatch.containingIgnoreCase(EDIT_TITLE));
                    play.waitFor(() -> play.expect(edit).not().toHaveClass("disabled"));
                    play.click(edit);
                    screen.findByText("Set Permissions");
                    // The descendants are counted, then the change confirmed
                    play.click(screen.getByRole("button", StroomDom.button("Apply To Descendants")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/explorer/v2/advancedFind").toSpyMatcher()));
                    final Play confirm = screen.within(screen.findByText(
                                    "Are you sure you want to change permissions for 3 documents?")
                            .closest(StroomDom.DIALOG));
                    // The OK of the confirmation, not of Set Permissions beneath it
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    // The bulk change sets the selected permission (USE) on the descendants
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withJsonBodyContaining("""
                                            {"change": {"type": "SetPermission", "permission": "USE"}}""")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(screen.getByText("Successfully changed permissions."))
                            .toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Successfully changed permissions.");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // On a folder: the create document types columns and details
                .story("FolderCreateDocumentTypes", context -> render(context, FOLDER, true))
                .withPlay(play -> {
                    expectCreateColumns(play);
                    play.waitFor(() -> play.expect(play.getByText("admin")).toBeInTheDocument());
                    // admin's explicit column shows type icons (Feed and Dictionary).
                    // The type icons are in a 'create-document-types-container', each with the class
                    // 'create-document-types-icon' and titled with its type
                    play.expect(play.querySelectorAll(".create-document-types-container").count())
                            .toBeGreaterThanOrEqual(1);
                    play.expect(play.getByTitle("Feed")).toHaveClass("create-document-types-icon");
                    play.expect(play.getByTitle("Dictionary")).toHaveClass("create-document-types-icon");
                    // admin's effective column covers all three types: 'ALL'
                    play.expect(play.getByText("ALL")).toBeInTheDocument();
                    // The details of the selected user
                    play.click(play.getByText("admin"));
                    play.waitFor(() -> play.expect(play.getByText("Explicit Create Document Permissions:"))
                            .toBeInTheDocument());
                    play.expect(play.getByText("Inherited Create Document Permissions:")).toBeInTheDocument();
                    play.expect(play.getByText(TextMatch.containing("from: System / Reports"))).toBeInTheDocument();
                    SecurityPlays.expectNoProblems(play);
                })
                // A GitRepo is folder-like
                .story("GitRepoIsFolderLike", context -> render(context, GIT_REPO, true))
                .withPlay(play -> {
                    expectCreateColumns(play);
                    SecurityPlays.expectNoProblems(play);
                })
                // Create permissions are set in their own dialog, raised from Set Permissions
                .story("NestedCreatePermissionsDialog", context -> render(context, FOLDER, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("admin"));
                    final Query edit = play.getByRole("button", TextMatch.containingIgnoreCase(EDIT_TITLE));
                    play.waitFor(() -> play.expect(edit).not().toHaveClass("disabled"));
                    play.click(edit);
                    // Stroom's dialogs have no role="dialog"; each is the closest
                    // '.dialog-container' of its caption
                    final Play parent = screen.within(screen.findByText("Set Permissions").closest(StroomDom.DIALOG));
                    // The parent dialog holds no create grid, only the button that raises it
                    play.expect(parent.queryByText("Document Type")).toBeNull();
                    play.click(parent.getByRole("button", StroomDom.button("Set Document Create Permissions")));
                    // The nested dialog: document, user, the grid and its own Apply To Descendants
                    final Play nested = screen.within(screen.findByText("Set Document Create Permissions",
                            StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.waitFor(() -> play.expect(nested.getByText(TextMatch.containing("Reports")))
                            .toBeInTheDocument());
                    play.expect(nested.getByText(TextMatch.containing("admin"))).toBeInTheDocument();
                    play.waitFor(() -> play.expect(nested.getByText("Pipeline")).toBeInTheDocument());
                    play.expect(nested.getByRole("button", StroomDom.button("Apply To Descendants")))
                            .toBeInTheDocument();
                    // Pipeline is half ticked (inherited); ticking it makes it explicit.
                    // The tick box is a TickBoxCell div with no label
                    play.click(nested.within(nested.getByText("Pipeline").closest("tr")).querySelector(".tickBox"));
                    play.click(nested.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(CHANGE_PATH)
                                    .withBody("SetDocumentUserCreatePermissions of Feed, Dictionary and Pipeline",
                                            body -> body != null
                                                    && body.contains("SetDocumentUserCreatePermissions")
                                                    && body.contains("\"Feed\"")
                                                    && body.contains("\"Dictionary\"")
                                                    && body.contains("\"Pipeline\""))
                                    .toSpyMatcher()));
                    // The nested dialog closes, leaving Set Permissions open
                    play.waitFor(() -> play.expect(screen.queryByText("Set Document Create Permissions",
                            StroomDom.DIALOG_TITLE)).toBeNull());
                    play.expect(screen.getByText("Set Permissions")).toBeInTheDocument();
                    SecurityPlays.expectNoProblems(play);
                })
                // A document (not a folder) has neither folder-only button
                .story("NoDescendantsButtonOnDocument", context -> render(context, DICTIONARY, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByText("Analysts"));
                    play.click(play.getByRole("button", TextMatch.containingIgnoreCase(EDIT_TITLE)));
                    screen.findByText("Set Permissions");
                    // Stroom hides the folder-only buttons rather than leaving them out, so they
                    // have no role (they are not accessible) but are still there
                    play.expect(screen.queryByRole("button", StroomDom.button("Apply To Descendants"))).toBeNull();
                    play.expect(screen.queryByRole("button", StroomDom.button("Set Document Create Permissions")))
                            .toBeNull();
                    SecurityPlays.expectNoProblems(play);
                });
    }

    /// The two folder-only columns.
    private static void expectCreateColumns(final Play play) {
        play.waitFor(() -> play.expect(play.getByRole("columnheader", "Explicit Create Document Types"))
                .toBeInTheDocument());
        play.expect(play.getByRole("columnheader", "Effective Create Document Types")).toBeInTheDocument();
    }

    /// @param withCreate Whether admin has create permissions as well as the usual permissions.
    private static RestFixtures fixtures(final boolean withCreate) {
        final String adminRow = withCreate
                ? """
                {"userRef": ADMIN, "permission": "OWNER", "documentCreatePermissions": ["Feed", "Dictionary"],
                 "inheritedDocumentCreatePermissions": ["Pipeline"]}"""
                : "{\"userRef\": ADMIN, \"permission\": \"OWNER\"}";
        final String adminReport = withCreate
                ? """
                {"explicitPermission": "OWNER", "explicitCreatePermissions": ["Feed", "Dictionary"],
                 "inheritedCreatePermissionPaths": {"Pipeline": ["System / Reports"]}}"""
                : "{\"explicitPermission\": \"OWNER\"}";
        final String rows = (adminRow + ", {\"userRef\": ANALYSTS, \"permission\": \"USE\"}")
                .replace("ADMIN", ADMIN)
                .replace("ANALYSTS", ANALYSTS);
        final RestFixtures.Builder builder = RestFixtures.builder()
                .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json(page(rows, 2)))
                .route(RequestMatcher.post("/permission/doc/v1/getDocUserPermissionsReport")
                                .withJsonBodyContaining("{\"userRef\": {\"uuid\": \"u-admin\"}}"),
                        RestReply.json(adminReport))
                .post("/permission/doc/v1/getDocUserPermissionsReport",
                        RestReply.json("{\"explicitPermission\": \"USE\"}"))
                .post("/explorer/v2/advancedFind", RestReply.json(page("""
                        {"path": "System / Reports",
                         "docRef": {"type": "Dictionary", "uuid": "d1", "name": "Countries"}},
                        {"path": "System / Reports", "docRef": {"type": "Feed", "uuid": "f1", "name": "EVENTS"}},
                        {"path": "System / Reports / Sub",
                         "docRef": {"type": "XSLT", "uuid": "x1", "name": "Transform"}}""",
                        3)))
                .post(CHANGE_PATH, RestReply.json("true"));
        if (withCreate) {
            builder.get(StartupFixtures.DOCUMENT_TYPES_PATH,
                    RestReply.json("{\"types\": " + TYPES + ", \"visibleTypes\": " + TYPES + "}"));
        }
        return builder.build();
    }

    private static String page(final String rows, final int count) {
        return "{\"values\": [" + rows + "], \"pageResponse\": {\"offset\": 0, \"length\": " + count
                + ", \"total\": " + count + ", \"exact\": true}}";
    }

    private static Widget render(final StoryContext context, final DocRef docRef, final boolean withCreate) {
        final SecurityScreenGinjector injector = GWT.create(SecurityScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures(withCreate))
                .injector(injector)
                .realAlerts()
                .build();
        // Opened as DocumentPermissionsPlugin opens it for a ShowDocumentPermissionsEvent, once
        // Stroom has started (the list reads the UI config)
        harness.afterStartUp(() -> {
            final DocumentUserPermissionsPresenter presenter =
                    harness.addContent(injector.getDocumentUserPermissionsPresenter());
            presenter.setDocRef(docRef);
        });
        return harness.asWidget();
    }
}
