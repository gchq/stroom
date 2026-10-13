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

import stroom.gwt.workbench.client.app.gin.content.ContentScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.UploadReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.client.event.ImportConfigEvent;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter;
import stroom.importexport.client.presenter.ImportConfigPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories of `App/Main/ImportDialog`, showing Stroom's real [ImportConfigPresenter] (the 'Import'
/// dialog, which uploads the chosen file) and [ImportConfigConfirmPresenter] (the 'Confirm Import'
/// dialog) with fake REST replies.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | the file's upload (`importfile.rpc`): an upload reply, key `res-1` | uploading the file |
/// | `POST /content/v1/import`, by import mode (the request spy) | the import |
/// | `POST /content/v1/abortImport` (the request spy) | aborting the import |
///
/// The story fires `ImportConfigEvent` (as the main menu's 'Import' item does) with both dialogs
/// registered as their events' handlers, as their GWTP proxies would be. The confirm dialog's root
/// folder picker gets its folder from the explorer ([TreeFixtures]).
public final class ImportDialogStories {

    private static final String IMPORT_PATH = "/content/v1/import";
    private static final String UPLOAD_URL = "importfile.rpc";
    private static final String RESOURCE_KEY = "{\"key\": \"res-1\", \"name\": \"import.zip\"}";

    // The file chosen in each story but NothingToImport
    private static final String FILE_NAME = "import.zip";
    private static final String FILE_CONTENT = "<config/>";
    private static final String FILE_TYPE = "application/zip";

    // A clean confirm list (all NEW, no messages): OK imports without a warning
    private static final String CLEAN_ITEMS = """
            [
              {"docRef": {"type": "Dictionary", "uuid": "d1", "name": "Countries"}, "state": "NEW", "action": true,
                "sourcePath": "System/Dictionaries/Countries", "destPath": "System/Dictionaries/Countries",
                "messageList": [], "updatedFieldList": []},
              {"docRef": {"type": "Feed", "uuid": "f1", "name": "EVENTS"}, "state": "NEW", "action": true,
                "sourcePath": "System/Feeds/EVENTS", "destPath": "System/Feeds/EVENTS",
                "messageList": [], "updatedFieldList": []}
            ]""";

    // A row with a warning (its messages and updated fields are in the info pop up, and OK asks to
    // confirm)
    private static final String WARN_ITEMS = """
            [
              {"docRef": {"type": "Feed", "uuid": "f1", "name": "EVENTS"}, "state": "UPDATE", "action": true,
                "sourcePath": "System/Feeds/EVENTS", "destPath": "System/Feeds/EVENTS",
                "messageList": [{"severity": "WARNING", "message": "Will overwrite the existing document"}],
                "updatedFieldList": ["description", "classification"]}
            ]""";

    private static final String NOTHING_TO_IMPORT = "The import package contains nothing that can be "
            + "imported into this version of Stroom.";

    private ImportDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ImportDialog", ImportDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Choose a file, confirm the parsed list; OK imports the ticked items
                // (ACTION_CONFIRMATION)
                .story("ImportWizard", context -> render(context, CLEAN_ITEMS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    uploadFile(play);
                    play.expect(screen.getByText("Dictionary")).toBeInTheDocument();
                    play.expect(screen.getByText("Feed")).toBeInTheDocument();
                    play.expect(screen.getAllByText("System/Feeds/EVENTS")).toHaveLength(2);
                    play.click(confirmDialog(screen).getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IMPORT_PATH)
                                    .withJsonBodyContaining("{\"importSettings\": {\"importMode\": "
                                            + "\"ACTION_CONFIRMATION\"}, \"confirmList\": [{}, {}]}")
                                    .toSpyMatcher()));
                    // Stroom tells the user the import is complete
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Import Complete"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // An empty confirm list warns that there is nothing to import, and stays on the
                // file step
                .story("NothingToImport", context -> render(context, "[]"))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = importDialog(screen);
                    play.upload(dialog.querySelector(StroomDom.FILE_INPUT), "empty.zip", "x", "");
                    dialog.findByText("empty.zip");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.getByText(
                            TextMatch.containingIgnoreCase("contains nothing that can be imported")))
                            .toBeInTheDocument());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith("WARN: " + NOTHING_TO_IMPORT);
                    // Still on the file step (no confirm grid)
                    play.expect(screen.queryByText("Confirm Import")).toBeNull();
                    play.expect(screen.getByText("Import", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).toHaveBeenCalledWith(UPLOAD_URL, "empty.zip", "x");
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The info icon shows the row's messages and updated fields
                .story("InfoPopup", context -> render(context, WARN_ITEMS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    uploadFile(play);
                    play.click(confirmDialog(screen).querySelector(".svgCell-icon, .infoColumn, [title='Info']"));
                    play.waitFor(() -> play.expect(screen.getByText("Will overwrite the existing document"))
                            .toBeInTheDocument());
                    play.expect(screen.getByText("Fields Updated")).toBeInTheDocument();
                    play.expect(screen.getByText("classification")).toBeInTheDocument();
                    ContentStorySupport.expectNoProblems(play);
                })
                // Importing an item with warnings asks for confirmation first
                .story("WarningsConfirm", context -> render(context, WARN_ITEMS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    uploadFile(play);
                    play.click(confirmDialog(screen).getByRole("button", StroomDom.button("OK")));
                    final Play confirm = screen.within(screen.findByText(
                                    TextMatch.containingIgnoreCase("There are warnings in the items selected"))
                            .closest(StroomDom.DIALOG));
                    play.click(confirm.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IMPORT_PATH)
                                    .withJsonBodyContaining("{\"importSettings\": {\"importMode\": "
                                            + "\"ACTION_CONFIRMATION\"}}")
                                    .toSpyMatcher()));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // Cancel aborts the uploaded package
                .story("CancelAborts", context -> render(context, CLEAN_ITEMS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    uploadFile(play);
                    play.click(confirmDialog(screen).getByRole("button", StroomDom.button("Cancel")));
                    // The key of the uploaded file
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/content/v1/abortImport")
                                    .withJsonBodyContaining("{\"key\": \"res-1\"}")
                                    .toSpyMatcher()));
                    // Stroom tells the user the import was aborted
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("WARN: Import Aborted"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    /// Chooses `import.zip` in the 'Import' dialog, waits for its name, then
    /// OK uploads it, and the confirm grid opens with the parsed items. Also checks the upload and
    /// that the confirmation was asked for the uploaded file's resource key.
    private static void uploadFile(final Play play) {
        final Play screen = play.screen();
        final Play dialog = importDialog(screen);
        // Chooses the file as a user does, which fires the input's change event
        play.upload(dialog.querySelector(StroomDom.FILE_INPUT), FILE_NAME, FILE_CONTENT, FILE_TYPE);
        // CustomFileUpload shows the chosen name
        dialog.findByText(FILE_NAME);
        play.click(dialog.getByRole("button", StroomDom.button("OK")));
        play.waitFor(() -> play.expect(screen.getByText("Confirm Import")).toBeInTheDocument());
        play.expect(play.spy(ScreenHarness.UPLOAD_SPY)).toHaveBeenCalledWith(UPLOAD_URL, FILE_NAME, FILE_CONTENT);
        play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                RequestMatcher.post(IMPORT_PATH)
                        .withJsonBodyContaining("{\"resourceKey\": {\"key\": \"res-1\"}, "
                                + "\"importSettings\": {\"importMode\": \"CREATE_CONFIRMATION\"}}")
                        .toSpyMatcher());
    }

    // The 'Import' dialog, once it has opened
    private static Play importDialog(final Play screen) {
        return screen.within(screen.findByText("Import", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // The 'Confirm Import' dialog
    private static Play confirmDialog(final Play screen) {
        return screen.within(screen.getByText("Confirm Import").closest(StroomDom.DIALOG));
    }

    private static Widget render(final StoryContext context, final String confirmList) {
        // The dialog's root folder picker gets its folder's node from the explorer
        final RestFixtures fixtures = TreeFixtures.explorerRoutes(TreeFixtures.fixtureTree())
                // The file's upload
                .upload(UploadReply.success("res-1", FILE_NAME))
                .route(RequestMatcher.post(IMPORT_PATH).withJsonBodyContaining(
                                "{\"importSettings\": {\"importMode\": \"ACTION_CONFIRMATION\"}}"),
                        RestReply.json("{\"resourceKey\": " + RESOURCE_KEY + ", \"confirmList\": []}"))
                .post(IMPORT_PATH, RestReply.json("{\"resourceKey\": " + RESOURCE_KEY + ", \"confirmList\": "
                        + confirmList + "}"))
                .post("/content/v1/abortImport", RestReply.noContent())
                .post(TreeFixtures.DECORATE, ContentStorySupport::decorate)
                .build();
        final ContentScreenGinjector injector = GWT.create(ContentScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                // The confirmations and alerts are shown in Stroom's real dialogs
                .realAlerts()
                .build();
        // As the presenters' GWTP proxies would
        final ImportConfigPresenter importPresenter = injector.getImportConfigPresenter();
        harness.addRegistration(harness.getEventBus().addHandler(ImportConfigEvent.getType(), importPresenter));
        final ImportConfigConfirmPresenter confirmPresenter = injector.getImportConfigConfirmPresenter();
        harness.addRegistration(harness.getEventBus().addHandler(ImportConfigConfirmEvent.getType(),
                confirmPresenter));
        // As the main menu's 'Import' item does
        harness.afterStartUp(() -> ImportConfigEvent.fire(harness.getHasHandlers()));
        return harness.asWidget();
    }
}
