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
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.client.presenter.ImportConfigConfirmPresenter;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportSettings;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.util.shared.ResourceKey;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;

/// Stories matching `App/Main/ImportDialog` in the React Storybook, showing Stroom's real
/// [ImportConfigConfirmPresenter] (the 'Confirm Import' dialog) with fake REST replies.
///
/// The React stories start by choosing a file, which `ImportConfigPresenter` uploads with
/// `CustomFileUpload` (its own `XMLHttpRequest`, which stories can't answer, see PORTING.md). So
/// these stories start where the upload finishes: as `ImportConfigPresenter` does with the uploaded
/// file's resource key, the story asks `ContentResource.importContent` for the confirmation list
/// (`POST /content/v1/import`, `CREATE_CONFIRMATION`) and fires `ImportConfigConfirmEvent` with the
/// reply, with the confirm dialog (from GIN) as its handler. The React `ContentApi` becomes routes for
/// `POST /content/v1/import` (by import mode) and `POST /content/v1/abortImport` (and the root folder
/// picker's explorer requests, [TreeFixtures]); its recorder becomes
/// checks on the request spy. `NothingToImport` is blocked: its warning is shown by
/// `ImportConfigPresenter` when the upload's confirmation list is empty.
public final class ImportDialogStories {

    private static final String IMPORT_PATH = "/content/v1/import";
    private static final String RESOURCE_KEY = "{\"key\": \"res-1\", \"name\": \"import.zip\"}";

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

    private ImportDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ImportDialog", ImportDialogStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Confirm the parsed list; OK imports the ticked items (ACTION_CONFIRMATION)
                .story("ImportWizard", context -> render(context, CLEAN_ITEMS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Differs from React: the story starts at the confirmation, after the upload
                    screen.findByText("Confirm Import");
                    play.expect(screen.getByText("Dictionary")).toBeInTheDocument();
                    play.expect(screen.getByText("Feed")).toBeInTheDocument();
                    play.expect(screen.getAllByText("System/Feeds/EVENTS")).toHaveLength(2);
                    play.click(dialog(screen).getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post(IMPORT_PATH)
                                    .withJsonBodyContaining("{\"importSettings\": {\"importMode\": "
                                            + "\"ACTION_CONFIRMATION\"}, \"confirmList\": [{}, {}]}")
                                    .toSpyMatcher()));
                    // Differs from React: GWT tells the user the import is complete
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("INFO: Import Complete"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                // The info icon shows the row's messages and updated fields
                .story("InfoPopup", context -> render(context, WARN_ITEMS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Confirm Import");
                    play.click(dialog(screen).querySelector(".svgCell-icon, .infoColumn, [title='Info']"));
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
                    screen.findByText("Confirm Import");
                    play.click(dialog(screen).getByRole("button", StroomDom.button("OK")));
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
                    screen.findByText("Confirm Import");
                    play.click(dialog(screen).getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/content/v1/abortImport")
                                    .withJsonBodyContaining("{\"key\": \"res-1\"}")
                                    .toSpyMatcher()));
                    // Differs from React: GWT tells the user the import was aborted
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith("WARN: Import Aborted"));
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    // The 'Confirm Import' dialog
    private static Play dialog(final Play screen) {
        return screen.within(screen.getByText("Confirm Import").closest(StroomDom.DIALOG));
    }

    private static Widget render(final StoryContext context, final String confirmList) {
        // The dialog's root folder picker gets its folder's node from the explorer
        final RestFixtures fixtures = TreeFixtures.explorerRoutes(TreeFixtures.fixtureTree())
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
        // As the presenter's GWTP proxy would
        final ImportConfigConfirmPresenter presenter = injector.getImportConfigConfirmPresenter();
        harness.addRegistration(harness.getEventBus().addHandler(ImportConfigConfirmEvent.getType(), presenter));
        // As ImportConfigPresenter does when the file has been uploaded
        final ContentResource contentResource = GWT.create(ContentResource.class);
        harness.afterStartUp(() -> harness.getRestFactory()
                .create(contentResource)
                .method(res -> res.importContent(new ImportConfigRequest(new ResourceKey("res-1", "import.zip"),
                        ImportSettings.createConfirmation(),
                        new ArrayList<>())))
                .onSuccess(response -> ImportConfigConfirmEvent.fire(harness.getHasHandlers(), response))
                .taskMonitorFactory(new DefaultTaskMonitorFactory(harness.getHasHandlers()))
                .exec());
        return harness.asWidget();
    }
}
