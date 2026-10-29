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

import stroom.dictionary.client.component.DictionaryEditor;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DictionaryResource;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.SaveDocumentEvent;
import stroom.entity.client.component.DocumentEditorHost;
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
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.ComponentStories;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.ControllerChecks;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.BiFunction;

/// The `App/Dictionary/DictionaryEditor` stories, showing Stroom's real [DictionaryEditor] (a
/// Dictionary's editor tab: Words, Imports, Effective Words, Documentation and Permissions, with
/// Save, Save As and Download) in a [DocumentEditorHost], as `DictionaryPlugin` opens it, with fake
/// REST replies.
///
/// `DictionaryPlugin` fetches the document (`GET /dictionary/v1/{uuid}`), checks the user may edit
/// it and reads it into the host, which the story does as the plugin does. The effective words
/// are `GET /wordList/v1/{uuid}`, the Permissions tab asks
/// `POST /permission/doc/v1/fetchDocumentUserPermissions` and `getDocUserPermissionsReport`, the
/// explorer tree `POST /explorer/v2/fetchExplorerNodes` ([ExplorerFixture]), and a spy records
/// `OpenDocumentEvent`. The editor comes from GIN.
///
/// Saving goes through Stroom's `DocumentPluginEventManager`, which the stories don't have: a spy
/// records the `SaveDocumentEvent` the host fires.
///
/// `App/Dictionary/DictionaryEditorComponent` runs the same stories against the editor alone,
/// owned by the story's root ([ScreenHarness#show]) as its owner, and adds stories only it
/// has: the document locked and unlocked while open, Save asking the owner to save, and closing the
/// editor while a request is answered late (see `stroom-gwt/COMPONENT_MODEL_DESIGN.md`, Phase 0).
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

    private static final RestFixtures FIXTURES = fixtures(RestReply.json(EFFECTIVE_WORDS));

    // The effective words fail, but only after the story has closed the editor
    private static final int LATE_REPLY_MILLIS = 500;
    private static final RestFixtures LATE_FAILURE_FIXTURES = fixtures(
            RestReply.error(500, "Effective words failed").delayed(LATE_REPLY_MILLIS));

    /// The name of the spy recording what the component copy's owner is asked to save.
    static final String SAVE = "save";
    /// The name of the spy recording the component copy becoming dirty or clean.
    static final String DIRTY = "dirty";

    private DictionaryEditorStories() {
        // Static utility
    }

    private static RestFixtures fixtures(final RestReply effectiveWords) {
        return TreeFixtures.explorerRoutes(
                        new ExplorerFixture(ExplorerFixture.folder("System",
                                ExplorerFixture.doc("Countries", DictionaryDoc.TYPE))))
                .get("/dictionary/v1/dict-countries", RestReply.json(DOC))
                .get("/wordList/v1/dict-countries", effectiveWords)
                .get("/wordList/v1/dict-cities", RestReply.json(CITIES_WORDS))
                .post("/permission/doc/v1/fetchDocumentUserPermissions", RestReply.json(DOC_PERMISSIONS))
                .route(RequestMatcher.post(PERMISSION_REPORT_PATH)
                                .withJsonBodyContaining("{\"userRef\": {\"uuid\": \"u-admin\"}}"),
                        RestReply.json("{\"explicitPermission\": \"OWNER\"}"))
                .post(PERMISSION_REPORT_PATH, RestReply.json(
                        "{\"explicitPermission\": \"VIEW\", \"inheritedPermissionPaths\": "
                                + "{\"USE\": [\"System / Dictionaries\"]}}"))
                .build();
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        addHostStories(addSharedStories(
                registry.component("App/Dictionary/DictionaryEditor", DictionaryEditorStories.class),
                DictionaryEditorStories::render));
        addComponentStories(addSharedStories(
                registry.component("App/Dictionary/DictionaryEditorComponent", DictionaryEditorStories.class),
                DictionaryEditorStories::renderComponent));
    }

    // The stories both editors have, so that the component copy is checked as the original is
    private static ComponentStories addSharedStories(final ComponentStories stories,
                                                     final BiFunction<StoryContext, Boolean, Widget> render) {
        return stories
                .layout(StoryLayout.FULLSCREEN)
                // The full editor: five sub-tabs and the Save / Save As / Download toolbar
                .story("Default", context -> render.apply(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // All five GWT sub-tabs are present, in order
                    for (final String label : new String[]{
                            "Words", "Imports", "Effective Words", "Documentation", "Permissions"}) {
                        play.waitFor(() -> play.expect(tab(play, label)).toBeInTheDocument());
                    }
                    // The toolbar's Save button starts disabled (clean on load); ToolbarButton
                    // marks it with the 'disabled' class
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");

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
                    play.expect(editButton).toHaveAttribute("aria-disabled", "true");
                    play.click(play.getByText("admin"));
                    play.waitFor(() -> play.expect(editButton).not().toHaveAttribute("aria-disabled"));
                    play.click(editButton);
                    // The dialog is a popup on the page's body, not in the canvas
                    play.expect(screen.findByText("Set Permissions")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // Read only (no EDIT permission): the editor retitles the Save button
                .story("ReadOnly", context -> render.apply(context, true))
                .withPlay(play -> {
                    play.findByRole("button", "Save (read only)");
                    // Save As stays available even read only, so the document can still be copied
                    play.expect(play.getByRole("button", "Save As")).not().toHaveAttribute("aria-disabled");
                    // The Words editor is read only
                    play.expect(tab(play, "Words")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // The Words tab (the default), to compare with 'ReadOnly'
                .story("WordsEditable", context -> render.apply(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(tab(play, "Words")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.querySelector(ACE_INPUT)).not().toBeNull());
                    expectNoProblems(play);
                })
                // The Imports tab, to compare with its read only partner
                .story("ImportsEditable", context -> render.apply(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    final Play grid = play.within(play.findByText("Document Name").closest(".data-grid"));
                    play.waitFor(() -> play.expect(grid.getByTitle("Open Dictionary 'Cities'.")).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // Read only: the Imports tab, as 'ImportsEditable' shows it
                .story("ImportsReadOnly", context -> render.apply(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    final Play grid = play.within(play.findByText("Document Name").closest(".data-grid"));
                    play.waitFor(() -> play.expect(grid.getByTitle("Open Dictionary 'Cities'.")).toBeInTheDocument());
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    expectNoProblems(play);
                })
                // The Imports tab is a grid (DocRefList, a "Document Name" link column): the name
                // opens the imported document, and Remove takes the selection
                // after a confirmation
                .story("ImportsGrid", context -> render.apply(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    final Play grid = play.within(play.findByText("Document Name").closest(".data-grid"));
                    // The imported dictionary is a command link (hasOpenLink) that opens the doc.
                    // Its 'open' button, named for what it opens, runs it
                    play.click(grid.getByRole("button", "Open Dictionary 'Cities'."));
                    play.waitFor(() -> play.expect(play.spy(OPEN_DOC)).toHaveBeenCalledWith("dict-cities"));
                    // Remove is enabled (a row is selected by default) and confirms first
                    play.click(play.getByRole("button", "Remove Imported Dictionary"));
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(grid.queryByText("Cities")).toBeNull());
                    expectNoProblems(play);
                })
                // Add Imported Dictionary is a toolbar button of the imports list, which opens the chooser
                // captioned 'Import a dictionary'
                .story("AddImportFromToolbar", context -> render.apply(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(tab(play, "Imports")).toBeInTheDocument());
                    play.click(tab(play, "Imports"));
                    // Imports are added with the 'Add Imported Dictionary' button
                    play.click(play.getByRole("button", "Add Imported Dictionary"));
                    // Stroom's dialogs have no role="dialog", so the caption is found as the
                    // dialog's title text
                    play.expect(screen.findByText("Import a dictionary", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    expectNoProblems(play);
                });
    }

    // The stories only the component copy has: what its owner does, and closing it
    private static void addHostStories(final ComponentStories stories) {
        stories
                // The document is read again locked and unlocked, as DocumentPlugin does when its
                // lock changes: the host tells the editor, and every tab follows
                .story("LockChangeWhileOpen", context -> render(context, false))
                .withPlay(play -> {
                    openTab(play, "Imports");
                    final Query addButton = play.findByRole("button", "Add Imported Dictionary");
                    play.expect(addButton).not().toHaveAttribute("aria-disabled");

                    play.run("lock the document", () -> readAgain(true));
                    play.expect(play.findByRole("button", "Save (read only)")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Add Imported Dictionary (dictionary is read only)"))
                            .toHaveAttribute("aria-disabled", "true");

                    play.run("unlock the document", () -> readAgain(false));
                    play.expect(play.findByRole("button", "Save")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Add Imported Dictionary"))
                            .not().toHaveAttribute("aria-disabled");
                    expectNoProblems(play);
                })
                // A change marks the tab's label, and Save asks Stroom to save the document
                // (SaveDocumentEvent, which DocumentPluginEventManager handles)
                .story("SaveAsksStroomToSave", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Imports");
                    final Play grid = play.within(play.findByText("Document Name").closest(".data-grid"));
                    play.waitFor(() -> play.expect(grid.getByText("Cities")).toBeInTheDocument());
                    play.expect("the tab's label", () -> host().getLabel()).toBe("Countries");
                    play.click(play.getByRole("button", "Remove Imported Dictionary"));
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect("the tab's label", () -> host().getLabel())
                            .toBe("* Countries"));
                    play.click(play.getByRole("button", "Save"));
                    play.expect(play.spy(SAVE)).toHaveBeenCalledWith("Countries, imports: 0");
                    expectNoProblems(play);
                });
    }

    // The host the story shows, for plays that act as DocumentPlugin does
    private static DocumentEditorHost<DictionaryDoc> host() {
        return ScreenHarness.shownComponent();
    }

    // Reads the shown document again, as DocumentPlugin does, read only or not
    private static void readAgain(final boolean readOnly) {
        final DocumentEditorHost<DictionaryDoc> host = host();
        host.setReadOnly(readOnly);
        host.read(host.getDocRef(), host.getEntity());
    }

    private static void addComponentStories(final ComponentStories stories) {
        stories
                // The document is locked and unlocked while it is open: every tab follows, as the
                // editor provides read-only to all of them
                .story("LockChangeWhileOpen", context -> renderComponent(context, false))
                .withPlay(play -> {
                    openTab(play, "Imports");
                    final Query addButton = play.findByRole("button", "Add Imported Dictionary");
                    play.expect(addButton).not().toHaveAttribute("aria-disabled");

                    play.run("lock the document", () -> editor().setReadOnly(true));
                    play.expect(play.findByRole("button", "Save (read only)")).toBeInTheDocument();
                    play.expect(play.getByText("Read only", ".link-tab-panel__read-only-note")).toBeVisible();
                    play.expect(play.getByRole("button", "Add Imported Dictionary (dictionary is read only)"))
                            .toHaveAttribute("aria-disabled", "true");

                    play.run("unlock the document", () -> editor().setReadOnly(false));
                    play.expect(play.findByRole("button", "Save")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Add Imported Dictionary"))
                            .not().toHaveAttribute("aria-disabled");
                    expectNoProblems(play);
                })
                // Removing an import makes the document dirty, and Save asks the owner to save it,
                // with the imports as the tabs show them
                .story("SaveGoesToTheOwner", context -> renderComponent(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    openTab(play, "Imports");
                    final Play grid = play.within(play.findByText("Document Name").closest(".data-grid"));
                    play.waitFor(() -> play.expect(grid.getByText("Cities")).toBeInTheDocument());
                    play.click(play.getByRole("button", "Remove Imported Dictionary"));
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(DIRTY)).toHaveBeenCalledWith("dirty"));
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).not().toHaveAttribute("aria-disabled");
                    play.click(save);
                    play.expect(play.spy(SAVE)).toHaveBeenCalledWith("imports: 0");
                    expectNoProblems(play);
                })
                // Closed while the effective words are loading: their (failed) reply arrives after
                // the editor is disposed, and nothing runs, so no error is shown, and the editor
                // leaves nothing unowned behind
                .story("CloseWhileLoading", context -> renderComponent(context, false, LATE_FAILURE_FIXTURES))
                .withPlay(play -> {
                    // Controllers the story's harness makes for itself are counted too
                    final Value<Integer> unownedBefore = play.capture("unowned", ControllerChecks::unownedCount);
                    openTab(play, "Effective Words");
                    play.run("close the editor, as closing its tab does", ScreenHarness::closeShownComponents);
                    play.expect("editor disposed", () -> editor().isDisposed()).toBe(true);
                    play.sleep(LATE_REPLY_MILLIS * 2);
                    play.expect("unowned components added",
                            () -> ControllerChecks.unownedCount() - unownedBefore.get()).toBeLessThanOrEqual(0);
                    expectNoProblems(play);
                });
    }

    // The component copy the story shows, for plays that act as its owner does
    private static DictionaryEditor editor() {
        return ScreenHarness.shownComponent();
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
        final ScreenHarness harness = createHarness(context, readOnly, FIXTURES, injector);
        harness.fn(SAVE);
        // Opened once Stroom has started, as DictionaryPlugin opens a document: load it, check the
        // user may edit it, read it into the editor's host and show the tab
        harness.afterStartUp(() -> open(harness, injector));
        return harness.asWidget();
    }

    private static Widget renderComponent(final StoryContext context, final boolean readOnly) {
        return renderComponent(context, readOnly, FIXTURES);
    }

    private static Widget renderComponent(final StoryContext context,
                                          final boolean readOnly,
                                          final RestFixtures fixtures) {
        final AppScreenGinjector injector = GWT.create(AppScreenGinjector.class);
        final ScreenHarness harness = createHarness(context, readOnly, fixtures, injector);
        harness.fn(SAVE);
        harness.fn(DIRTY);
        harness.afterStartUp(() -> openComponent(harness, injector));
        return harness.asWidget();
    }

    private static ScreenHarness createHarness(final StoryContext context,
                                               final boolean readOnly,
                                               final RestFixtures fixtures,
                                               final AppScreenGinjector injector) {
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .realAlerts()
                .build();
        harness.getSecurityContext().setDocumentPermission(readOnly
                ? DocumentPermission.VIEW
                : DocumentPermission.EDIT);
        harness.fn(OPEN_DOC);
        harness.getEventBus().addHandler(OpenDocumentEvent.getType(), event ->
                harness.spy(OPEN_DOC, event.getDocRef().getUuid()));
        return harness;
    }

    // As open() does for the original, with the story as the editor's owner: it says whether the
    // user may edit the document, and is told when it is dirty and when to save
    private static void openComponent(final ScreenHarness harness, final AppScreenGinjector injector) {
        final DictionaryEditor editor = injector.getDictionaryEditor();
        editor.setOnDirtyChange(dirty -> harness.spy(DIRTY, dirty
                ? "dirty"
                : "clean"));
        editor.setOnSave(() -> harness.spy(SAVE, "imports: " + NullSafe.size(editor.write().getImports())));
        final TaskMonitorFactory taskMonitorFactory = harness.getComponentRoot().lookup(Contexts.TASK_MONITOR);
        final DictionaryResource resource = GWT.create(DictionaryResource.class);
        harness.getRestFactory()
                .create(resource)
                .method(res -> res.fetch(DOC_REF.getUuid()))
                .onSuccess(doc -> harness.getSecurityContext().hasDocumentPermission(
                        DOC_REF,
                        DocumentPermission.EDIT,
                        allowUpdate -> {
                            editor.setReadOnly(!allowUpdate);
                            editor.read(DOC_REF, doc);
                            harness.show(editor);
                        },
                        error -> harness.spy(ScreenHarness.ALERT_SPY, "ERROR: " + error.getMessage()),
                        taskMonitorFactory))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private static void open(final ScreenHarness harness, final AppScreenGinjector injector) {
        final DocumentEditorHost<DictionaryDoc> controller = new DocumentEditorHost<>(
                harness.getEventBus(), injector.getDictionaryEditor(), DictionaryDoc.TYPE);
        // As DocumentPluginEventManager is asked to save it. Removed with the story's opening, as
        // the handler holds the editor
        harness.addCleanUp(harness.getEventBus().addHandler(SaveDocumentEvent.getType(), event -> harness.spy(SAVE,
                event.getTabData().getLabel().replace("* ", "")
                + ", imports: "
                + NullSafe.size(controller.write(controller.getEntity()).getImports())))::removeHandler);
        final DictionaryResource resource = GWT.create(DictionaryResource.class);
        harness.getRestFactory()
                .create(resource)
                .method(res -> res.fetch(DOC_REF.getUuid()))
                .onSuccess(doc -> harness.getSecurityContext().hasDocumentPermission(
                        DOC_REF,
                        DocumentPermission.EDIT,
                        allowUpdate -> {
                            controller.setReadOnly(!allowUpdate);
                            controller.read(DOC_REF, doc);
                            harness.show(controller);
                        },
                        error -> harness.spy(ScreenHarness.ALERT_SPY, "ERROR: " + error.getMessage()),
                        controller))
                .taskMonitorFactory(controller)
                .exec();
    }
}
