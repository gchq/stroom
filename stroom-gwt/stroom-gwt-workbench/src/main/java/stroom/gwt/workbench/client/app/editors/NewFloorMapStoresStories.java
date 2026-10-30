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

package stroom.gwt.workbench.client.app.editors;

import stroom.docref.DocRef;
import stroom.floormap.client.component.NewFloorMapStoresController;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.client.widgets.tree.TreeFixtures;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.widget.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.user.client.ui.Widget;

/// The `App/Editors/FloorMapInit` stories, showing Stroom's real [NewFloorMapStoresController]: the
/// 'Initialise New Floor Map' dialog that asks for a new Floor Map's facts store (an SQL Temporal
/// Store) and events store (a Floor Map Event Store), shown as `DocumentPluginEventManager` shows a
/// plugin's `DocInitialisationHandler` once the document has been created.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /explorer/v2/fetchExplorerNodes`, `getFromDocRef` | the store pickers' explorer trees |
/// | `GET /floormap/v1/{uuid}` | OK: the new document |
/// | `PUT /floormap/v1/{uuid}` (the request spy) | OK: the document with its stores |
/// | `DELETE /explorer/v2/delete` (the request spy) | Cancel: deleting the new document |
///
/// The dialog's outcome (`onComplete`) is recorded by the `onComplete` spy.
public final class NewFloorMapStoresStories {

    private static final DocRef DOC_REF = new DocRef(FloorMapDoc.TYPE, "floormap-new", "New Map");

    private static final String CAPTION = "Initialise New Floor Map";
    private static final String DOC_PATH = "/floormap/v1/floormap-new";
    private static final String ON_COMPLETE = "onComplete";

    // The new document, as the server created it
    private static final String DOC = """
            {"type": "FloorMap", "uuid": "floormap-new", "name": "New Map"}""";

    // The explorer tree the pickers show (each picker shows only its own type)
    private static final ExplorerFixture TREE = new ExplorerFixture(ExplorerFixture.folder("System",
            ExplorerFixture.doc("office-facts", SqlTemporalStoreDoc.TYPE).withUuid("facts-1"),
            ExplorerFixture.doc("office-events", FloorMapEventStoreDoc.TYPE).withUuid("events-1")));

    private NewFloorMapStoresStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/FloorMapInit", NewFloorMapStoresStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // OK is disabled until both stores are chosen; OK then saves the document with both
                .story("ChooseStores", context -> render(context, RestReply.json(DOC)))
                .withPlay(play -> {
                    final Play dialog = dialog(play);
                    final Query ok = dialog.getByRole("button", StroomDom.button("OK"));
                    play.waitFor(() -> play.expect(ok).toBeDisabled());
                    choose(play, dialog, 0, "Choose Facts Store", "office-facts");
                    play.waitFor(() -> play.expect(dialog.getByText("office-facts")).toBeInTheDocument());
                    // Only the facts store: OK is still disabled, and Ctrl+Enter doesn't save
                    play.expect(ok).toBeDisabled();
                    play.keyboard("{Control>}{Enter}{/Control}");
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.get(DOC_PATH).toSpyMatcher());
                    choose(play, dialog, 1, "Choose Events Store", "office-events");
                    play.waitFor(() -> play.expect(ok).toBeEnabled());
                    play.click(ok);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(DOC_PATH)
                                    .withJsonBodyContaining("""
                                            {"factsStoreRef": {"type": "SqlTemporalStore", "uuid": "facts-1",
                                                               "name": "office-facts"},
                                             "eventsStoreRef": {"type": "FloorMapEventStore", "uuid": "events-1",
                                                                "name": "office-events"}}""")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.screen().queryByText(CAPTION, StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ON_COMPLETE)).toHaveBeenCalledWith("true");
                    DocEditors.expectNoProblems(play);
                })
                // A save that fails resets the dialog: it stays open with OK enabled, so OK can be
                // tried again
                .story("SaveFails", context -> render(context, RestReply.error(500, "Disk full")))
                .withPlay(play -> {
                    final Play dialog = dialog(play);
                    final Query ok = dialog.getByRole("button", StroomDom.button("OK"));
                    choose(play, dialog, 0, "Choose Facts Store", "office-facts");
                    choose(play, dialog, 1, "Choose Events Store", "office-events");
                    play.waitFor(() -> play.expect(ok).toBeEnabled());
                    play.click(ok);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put(DOC_PATH).toSpyMatcher()));
                    play.waitFor(() -> play.expect(ok).toBeEnabled());
                    play.expect(dialog.getByRole("button", StroomDom.button("Cancel"))).toBeEnabled();
                    play.expect(play.screen().getByText(CAPTION, StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(play.spy(ON_COMPLETE)).not().toHaveBeenCalled();
                    DocEditors.expectNoUnhandledRequests(play);
                })
                // Cancel closes the dialog at once and deletes the new document; OK never saved it
                .story("CancelDeletes", context -> render(context, RestReply.json(DOC)))
                .withPlay(play -> {
                    final Play dialog = dialog(play);
                    final Query ok = dialog.getByRole("button", StroomDom.button("OK"));
                    choose(play, dialog, 0, "Choose Facts Store", "office-facts");
                    play.waitFor(() -> play.expect(dialog.getByText("office-facts")).toBeInTheDocument());
                    play.expect(ok).toBeDisabled();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(play.screen().queryByText(CAPTION, StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.delete("/explorer/v2/delete")
                                    .withJsonBodyContaining("""
                                            {"docRefs": [{"type": "FloorMap", "uuid": "floormap-new"}]}""")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ON_COMPLETE)).toHaveBeenCalledWith("false"));
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.put(DOC_PATH).toSpyMatcher());
                    DocEditors.expectNoProblems(play);
                });
    }

    // The dialog, once shown
    private static Play dialog(final Play play) {
        final Play screen = play.screen();
        return screen.within(screen.findByText(CAPTION, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // Opens the dialog's index'th store picker (a DropDownBox, which opens on a mousedown)
    // and chooses the named store
    private static void choose(final Play play,
                               final Play dialog,
                               final int index,
                               final String caption,
                               final String store) {
        final Play screen = play.screen();
        play.fireEvent().mouseDown(dialog.querySelectorAll(".drop-down").nth(index), EventInit.create());
        final Play picker = screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE)
                .closest(StroomDom.DIALOG));
        play.click(picker.findByText(store));
        play.click(picker.getByRole("button", StroomDom.button("OK")));
        play.waitFor(() -> play.expect(screen.queryByText(caption, StroomDom.DIALOG_TITLE)).toBeNull());
    }

    private static Widget render(final StoryContext context, final RestReply saveReply) {
        final RestFixtures fixtures = TreeFixtures.explorerRoutes(TREE)
                .get(DOC_PATH, RestReply.json(DOC))
                .put(DOC_PATH, saveReply)
                .delete("/explorer/v2/delete", RestReply.json("{\"explorerNodes\": [], \"message\": \"\"}"))
                .build();
        // Registered as the story renders, so a play can check it wasn't called
        context.fn(ON_COMPLETE);
        return DocEditors.render(context, fixtures, false, (harness, injector) -> {
            // As DocumentPluginEventManager does, through FloorMapPlugin's handler, once a new
            // document has been created
            injector.getNewFloorMapStoresController().show(harness.getAppRoot(),
                    DOC_REF,
                    ok -> harness.spy(ON_COMPLETE, String.valueOf(ok)),
                    new DefaultTaskMonitorFactory(harness.getHasHandlers()));
        });
    }
}
