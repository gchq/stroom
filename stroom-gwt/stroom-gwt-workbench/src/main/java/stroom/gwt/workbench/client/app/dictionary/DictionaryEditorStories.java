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


package stroom.gwt.workbench.client.app.dictionary;

import stroom.dictionary.client.presenter.DictionaryPresenter;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DictionaryResource;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.gwt.workbench.client.app.gin.AppScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.DocumentPermission;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Dictionary/DictionaryEditor` stories, showing Stroom's real [DictionaryPresenter] (a
/// Dictionary's editor tab: Words, Imports, Effective Words, Documentation and Permissions, with
/// Save, Save As and Download) with fake REST replies.
///
/// `DictionaryPlugin` fetches the document (`GET /dictionary/v1/{uuid}`), checks the user may edit
/// it and reads it into the editor, which the story does as the plugin does. The effective words
/// are `GET /wordList/v1/{uuid}`, the Permissions tab asks
/// `POST /permission/doc/v1/fetchDocumentUserPermissions` and `getDocUserPermissionsReport`, the
/// explorer tree `POST /explorer/v2/fetchExplorerNodes` ([ExplorerFixture]), and a spy records
/// `OpenDocumentEvent`. The presenter comes from GIN.
///
/// Save is enabled by Stroom's own `DocTabPresenter` as the words change; saving goes through
/// Stroom's `DocumentPluginEventManager`, which the stories don't have, and no story saves.
public final class DictionaryEditorStories {

    /// The name of the spy recording the documents opened.
    static final String OPEN_DOC = "openDoc";

    private static final DocRef DOC_REF = new DocRef(DictionaryDoc.TYPE, "dict-countries", "Countries");

    // DictionaryResource.fetch()
    private static final String DOC = """
            {
              "type": "Dictionary", "uuid": "dict-countries", "name": "Countries",
              "description": "# Countries\\n\\nISO country names used across pipelines.",
              "data": "England\\nScotland\\nWales",
              "imports": [{"type": "Dictionary", "uuid": "dict-cities", "name": "Cities"}]
            }""";

    // WordListResource.getWords(): the dictionary's own words plus two imported from 'Cities'
    private static final String EFFECTIVE_WORDS = """
            {
              "wordList": [
                {"word": "England", "sourceUuid": "dict-countries"},
                {"word": "Scotland", "sourceUuid": "dict-countries"},
                {"word": "Wales", "sourceUuid": "dict-countries"},
                {"word": "London", "sourceUuid": "dict-cities"},
                {"word": "Edinburgh", "sourceUuid": "dict-cities"}
              ],
              "sourceUuidToDocRefMap": {
                "dict-countries": {"type": "Dictionary", "uuid": "dict-countries", "name": "Countries"},
                "dict-cities": {"type": "Dictionary", "uuid": "dict-cities", "name": "Cities"}
              }
            }""";

    private static final String CITIES_WORDS = """
            {"wordList": [{"word": "London", "sourceUuid": "dict-cities"},
              {"word": "Edinburgh", "sourceUuid": "dict-cities"}],
              "sourceUuidToDocRefMap": {
                "dict-cities": {"type": "Dictionary", "uuid": "dict-cities", "name": "Cities"}}}""";

    // DocPermissionResource.fetchDocumentUserPermissions(): a user and a group with explicit
    // permissions
    private static final String DOC_PERMISSIONS = """
            {
              "values": [
                {"userRef": {"uuid": "u-admin", "subjectId": "admin", "displayName": "admin", "group": false,
                  "enabled": true}, "permission": "OWNER"},
                {"userRef": {"uuid": "g-analysts", "subjectId": "Analysts", "displayName": "Analysts",
                  "group": true, "enabled": true}, "permission": "VIEW", "inheritedPermission": "USE"}
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    // Stroom's Ace editor's text area
    private static final String ACE_INPUT = ".ace_text-input";

    private static final String PERMISSION_REPORT_PATH = "/permission/doc/v1/getDocUserPermissionsReport";

    private static final RestFixtures FIXTURES = TreeFixtures.explorerRoutes(
                    new ExplorerFixture(ExplorerFixture.folder("System",
                            ExplorerFixture.doc("Countries", DictionaryDoc.TYPE))))
            .get("/dictionary/v1/dict-countries", RestReply.json(DOC))
            .get("/wordList/v1/dict-countries", RestReply.json(EFFECTIVE_WORDS))
            .get("/wordList/v1/dict-cities", RestReply.json(CITIES_WORDS))
            .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json(DOC_PERMISSIONS))
            .route(RequestMatcher.post(PERMISSION_REPORT_PATH)
                            .withJsonBodyContaining("{\"userRef\": {\"uuid\": \"u-admin\"}}"),
                    RestReply.json("{\"explicitPermission\": \"OWNER\"}"))
            .post(PERMISSION_REPORT_PATH, RestReply.json(
                    "{\"explicitPermission\": \"VIEW\", \"inheritedPermissionPaths\": "
                            + "{\"USE\": [\"System / Dictionaries\"]}}"))
            .build();

    private DictionaryEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Dictionary/DictionaryEditor", DictionaryEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The full editor: five sub-tabs and the Save / Save As / Download toolbar
                .story("Default", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // All five GWT sub-tabs are present, in order
                    for (final String label : new String[]{
                            "Words", "Imports", "Effective Words", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(tab(play, label)).toBeInTheDocument());
                    }
                    // The toolbar's Save button starts disabled (clean on load); InlineSvgButton
                    // marks it with the 'disabled' class
                    play.expect(play.getByRole("button", "Save")).toHaveClass("disabled");

                    // Effective Words shows the combined list (local and imported)
                    play.click(tab(play, "Effective Words"));
                    play.waitFor(() -> play.expect(play.getAllByText("Imported").count())
                            .toSatisfy("more than none", count -> ((Integer) count) > 0));
                    play.expect(play.getAllByText("Local").count())
                            .toSatisfy("more than none", count -> ((Integer) count) > 0);

                    // Permissions: the users/groups grid loads; selecting a row shows its details
                    // and enables Edit, which opens the Set Permissions dialog
                    play.click(tab(play, "Permissions"));
                    play.waitFor(() -> play.expect(play.getByText("admin")).toBeInTheDocument());
                    play.expect(play.getByText("Analysts")).toBeInTheDocument();
                    final Query editButton = play.getByRole("button", TextMatch.regex("edit permissions", "i"));
                    play.expect(editButton).toHaveClass("disabled");
                    play.click(play.getByText("admin"));
                    play.waitFor(() -> play.expect(editButton).not().toHaveClass("disabled"));
                    play.click(editButton);
                    // The dialog is a popup on the page's body, not in the canvas
                    play.expect(screen.findByText("Set Permissions")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Read only (no EDIT permission): DocTabPresenter.onRead retitles the Save button
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.findByRole("button", "Save is not available as this document is read only");
                    // Save As stays available even read only, so the document can still be copied
                    play.expect(play.getByRole("button", "Save As")).not().toHaveClass("disabled");
                    // The Words editor is read only
                    play.expect(tab(play, "Words")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The Words tab (the default), to compare with 'ReadOnly'
                .story("WordsEditable", context -> render(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(tab(play, "Words")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    expectNoProblems(play);
                })
                // The Imports tab, to compare with its read only partner
                .story("ImportsEditable", context -> render(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    final Play grid = play.within(play.findByText("Document Name").closest(".dataGridWidget"));
                    play.waitFor(() -> play.expect(grid.getByTitle("Open Dictionary 'Cities'.")).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // Read only: the Imports tab, as 'ImportsEditable' shows it
                .story("ImportsReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    final Play grid = play.within(play.findByText("Document Name").closest(".dataGridWidget"));
                    play.waitFor(() -> play.expect(grid.getByTitle("Open Dictionary 'Cities'.")).toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".docTab-readOnlyNote")).toBeVisible();
                    expectNoProblems(play);
                })
                // The Imports tab is a grid (DocRefListPresenter.initTableColumns("Document Name",
                // true)): the name opens the imported document, and Remove takes the selection
                // after a confirmation
                .story("ImportsGrid", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    final Play grid = play.within(play.findByText("Document Name").closest(".dataGridWidget"));
                    // The imported dictionary is a command link (hasOpenLink) that opens the doc.
                    // Its 'open' icon, titled with what it opens, runs it
                    play.click(grid.within(grid.getByTitle("Open Dictionary 'Cities'."))
                            .querySelector(StroomDom.COMMAND_LINK_OPEN));
                    play.waitFor(() -> play.expect(play.spy(OPEN_DOC)).toHaveBeenCalledWith("dict-cities"));
                    // Remove is enabled (a row is selected by default) and confirms first
                    play.click(play.getByRole("button", "Remove Import"));
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(grid.queryByText("Cities")).toBeNull());
                    expectNoProblems(play);
                })
                // Add Import is a toolbar button of the imports list, which opens the chooser
                // captioned 'Import a dictionary'
                .story("AddImportFromToolbar", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    // Imports are added with the 'Add Import' button
                    play.click(play.getByRole("button", "Add Import"));
                    // Stroom's dialogs have no role="dialog", so the caption is found as the
                    // dialog's title text
                    play.expect(screen.findByText("Import a dictionary", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    expectNoProblems(play);
                });
    }

    // A sub-tab of the editor.
    // Stroom's link tabs have no role="tab", so they're found by their label
    private static Query tab(final Play play, final String label) {
        return play.getByText(label, StroomDom.LINK_TAB_LABEL);
    }

    // Opens one of the editor's sub-tabs
    private static void openTab(final Play play, final String label) {
        play.waitFor(() -> play.expect(tab(play, label)).toBeInTheDocument());
        play.click(tab(play, label));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .realAlerts()
                .build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        harness.fn(OPEN_DOC);
        harness.getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                harness.spy(OPEN_DOC, event.getDocRef().getUuid()));

        // Opened once Stroom has started, as DictionaryPlugin opens a document: load it, check the
        // user may edit it, read it into the editor and show the tab
        harness.afterStartUp(() -> open(harness, injector));
        return harness.asWidget();
    }

    private static void open(final ScreenHarness harness, final AppScreenGinjector injector) {
        final DictionaryPresenter presenter = injector.getDictionaryPresenter();
        final DictionaryResource resource = GWT.create(DictionaryResource.class);
        harness.getRestFactory()
                .create(resource)
                .method(res -> res.fetch(DOC_REF.getUuid()))
                .onSuccess(doc -> harness.getSecurityContext().hasDocumentPermission(
                        DOC_REF,
                        DocumentPermission.EDIT,
                        allowUpdate -> {
                            presenter.read(DOC_REF, doc, !allowUpdate);
                            harness.addContent(presenter);
                        },
                        error -> harness.spy(ScreenHarness.ALERT_SPY, "ERROR: " + error.getMessage()),
                        presenter))
                .taskMonitorFactory(presenter)
                .exec();
    }
}
