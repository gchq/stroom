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
import stroom.floormap.client.component.FloorMapEditorTabController;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapResource;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.gin.editors.EditorsScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Editors/FloorMapEditor` stories, showing Stroom's real [FloorMapEditorTabController] (a
/// Floor Map's 'Editor' tab: the map canvas, its timeline and its Layers panel) with fake REST
/// replies.
///
/// As `FloorMapEditor` does for its Editor tab, the story fetches the document
/// (`GET /floormap/v1/{uuid}`) and reads it into the editor ([DocEditors#open]). The editor then
/// asks the map's facts store (`SqlTemporalStoreResource`, `/sqltemporalstore/v1`) for its
/// `timeRange`, and the facts at the selected time (`fetchAtTime`, `fetchAll` and `find`), all of
/// which are empty here: the map has no objects. The Layers panel's Discover scans the whole store
/// with `find`, a page of 10,000 entries at a time, until a page's response is exact;
/// the store has two pages, the first with two desks and the second with a gate.
public final class FloorMapEditorTabStories {

    private static final DocRef DOC_REF = new DocRef(FloorMapDoc.TYPE, "floormap-1", "Office");

    // FloorMapResource.fetch(): a floor map whose facts are in the store 'office' (the editor shows
    // no menu without one)
    private static final String DOC = """
            {"type": "FloorMap", "uuid": "floormap-1", "name": "Office",
              "factsStoreRef": {"type": "SqlTemporalStore", "uuid": "store-1", "name": "office"}}""";

    // The map with a layer for desks, drawn as blue circles
    private static final String DOC_WITH_LAYER = """
            {"type": "FloorMap", "uuid": "floormap-1", "name": "Office",
              "factsStoreRef": {"type": "SqlTemporalStore", "uuid": "store-1", "name": "office"},
              "typeStyles": [{"type": "desk", "shape": "CIRCLE", "colour": "#336699"}]}""";

    // The map with layers for desks and gates, desks first (painted behind)
    private static final String DOC_WITH_TWO_LAYERS = """
            {"type": "FloorMap", "uuid": "floormap-1", "name": "Office",
              "factsStoreRef": {"type": "SqlTemporalStore", "uuid": "store-1", "name": "office"},
              "typeStyles": [{"type": "desk", "shape": "CIRCLE", "colour": "#336699"},
                             {"type": "gate", "shape": "SQUARE", "colour": "#996633"}]}""";

    private static final String FIND = "/sqltemporalstore/v1/find";

    // FloorMapEditorTabController.DISCOVER_PAGE_SIZE
    private static final int DISCOVER_PAGE_SIZE = 10_000;

    // Discover's first page: two desks, and more to come (the page response isn't exact)
    private static final String DISCOVER_PAGE_1 = """
            {"values": [
                {"map": "office", "key": "desk-1", "effectiveTimeMs": 1700000000000,
                  "value": "{\\"type\\": \\"desk\\", \\"coords\\": [10, 20]}"},
                {"map": "office", "key": "desk-2", "effectiveTimeMs": 1700000000000,
                  "value": "{\\"type\\": \\"desk\\", \\"coords\\": [30, 20]}"}],
              "pageResponse": {"offset": 0, "length": 2, "exact": false}}""";

    // Discover's second and last page: a gate
    private static final String DISCOVER_PAGE_2 = """
            {"values": [
                {"map": "office", "key": "gate-1", "effectiveTimeMs": 1700000000000,
                  "value": "{\\"type\\": \\"gate\\", \\"coords\\": [50, 0]}"}],
              "pageResponse": {"offset": 2, "length": 1, "total": 3, "exact": true}}""";

    private static final RestFixtures FIXTURES = fixtures(DOC);

    // The canvas's focusable element (FloorMapCanvas's focus panel)
    private static final String CANVAS = ".floor-map-canvas";
    // Told when the editor tells its document of a change
    private static final String ON_CHANGE = "onChange";

    // The canvas's context menu with nothing selected, named for what it acts on
    private static final String MAP_ACTIONS = "Map Actions";

    // The desk layer's swatch, which opens its appearance
    // The desk layer's appearance dialog's title
    private static final String APPEARANCE = "Appearance — desk";

    private static final String DESK_SWATCH = "Edit the appearance of the desk layer (shape & colour)";

    // The desk layer's grip, which moves it
    private static final String DESK_GRIP = "Reorder the desk layer — drag, or use the up and down arrow keys";

    // The Layers panel's Discover, which adds the store's types as layers
    private static final String DISCOVER = "Discover types from the facts store";

    private FloorMapEditorTabStories() {
        // Static utility
    }

    // The tab's routes, for a document
    private static RestFixtures fixtures(final String doc) {
        return DocEditors.docSelectionRoutes(
                        DocEditors.permissionRoutes(RestFixtures.builder()))
                .get("/floormap/v1/floormap-1", RestReply.json(doc))
            .put("/floormap/v1/floormap-1", request -> RestReply.json(request.getBody()))
            // An empty store: no time range and no facts
            .post("/sqltemporalstore/v1/timeRange", RestReply.json("{}"))
            .post("/sqltemporalstore/v1/fetchAtTime", RestReply.json("[]"))
            .post("/sqltemporalstore/v1/fetchAll", RestReply.json("[]"))
            // Discover's paged scan of the store: the first page says more follow (not exact), the
            // second is the last
            .route(discoverPage(0), RestReply.json(DISCOVER_PAGE_1))
            .route(discoverPage(2), RestReply.json(DISCOVER_PAGE_2))
            .post("/sqltemporalstore/v1/find", RestReply.json(
                    "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, "
                    + "\"exact\": true}}"))
                .build();
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/FloorMapEditor", FloorMapEditorTabStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The Editor tab's timeline has a help button, which shows the timeline's help
                .story("TimelineHelp", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.querySelector(CANVAS)).toHaveAttribute("role", "group"));
                    play.click(play.getByRole("button", "Timeline help"));
                    play.expect(screen.findByText("Timeline", "h4")).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                })
                // The Fact List and Time List show below the timeline, filling the rest of the bottom
                // strip (they were given no height when the strip was a DockLayoutPanel)
                .story("FactAndTimeLists", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(CANVAS)).toHaveAttribute("role", "group"));
                    play.expect(play.getByRole("grid", "Facts").height()).toBeGreaterThan(50.0);
                    play.expect(play.getByRole("grid", "Fact Times").height()).toBeGreaterThan(50.0);
                    DocEditors.expectNoProblems(play);
                })
                // Adding an object stages a fact for the store, which the written document doesn't
                // hold, so the editor tells its document of the change itself: the document then
                // asks it for its pending changes, and Save is enabled
                .story("AddObjectTellsDocument", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query canvas = play.querySelector(CANVAS);
                    play.waitFor(() -> play.expect(canvas).toHaveAttribute("role", "group"));
                    play.run("focus the canvas", () -> canvas.element().get().focus());
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    expectMenuWithFocus(play, screen);
                    play.click(screen.getByRole("menuitem", "Add Object Here"));
                    final Play dialog = screen.within(screen.findByRole("dialog", "Add Object"));
                    play.type(dialog.getByRole("textbox", "Type"), "desk");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByRole("dialog", "Add Object")).toBeNull());
                    play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled();
                })
                // Shift+F10 and the context menu key, the standard keys for a context menu, open the
                // focused canvas's menu, as Enter and Space do. The menu is named for what it acts
                // on and takes the focus, and the browser's own contextmenu event for the key
                // does not open a second menu
                .story("ContextMenuByKeyboard", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query canvas = play.querySelector(CANVAS);
                    play.waitFor(() -> play.expect(canvas).toHaveAttribute("role", "group"));

                    play.run("focus the canvas", () -> canvas.element().get().focus());
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    expectMenuWithFocus(play, screen);
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByRole("menu")).toBeNull());

                    play.run("focus the canvas", () -> canvas.element().get().focus());
                    play.keyboard("{ContextMenu}");
                    expectMenuWithFocus(play, screen);
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByRole("menu")).toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                // Set Scale, from the canvas's menu, shows the crosshair and takes the focus; Escape
                // leaves the mode, and the normal pointer is back
                .story("SetScaleCrosshair", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query canvas = play.querySelector(CANVAS);
                    play.waitFor(() -> play.expect(canvas).toHaveAttribute("role", "group"));
                    play.run("focus the canvas", () -> canvas.element().get().focus());
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.click(screen.findByRole("menuitem", "Set Scale"));
                    play.waitFor(() -> play.expect(canvas).toHaveClass("floor-map-canvas--crosshair"));
                    play.waitFor(() -> play.expect(canvas).toHaveFocus());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(canvas)
                            .not().toHaveClass("floor-map-canvas--crosshair"));
                    DocEditors.expectNoProblems(play);
                })
                // Set Scale: a line measured on the map is given its real length, and OK scales the
                // map, so the same line measured again reads that length
                .story("SetScaleOk", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = measureLine(play, screen);
                    play.waitFor(() -> play.expect(dialog.getByRole("textbox")).toHaveFocus());
                    play.type(dialog.getByRole("textbox"), "2.4");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Scale", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    final Play again = measureLine(play, screen);
                    play.expect(again.getByText(TextMatch.containing("2.4 m"))).toBeInTheDocument();
                    play.click(again.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Scale", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    DocEditors.expectNoProblems(play);
                })
                // Set Scale with no distance: OK says what is wanted, and the dialog stays open
                .story("SetScaleInvalid", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = measureLine(play, screen);
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY)).toHaveBeenCalledWith(
                            "WARN: Enter the real distance this line spans, as a positive number."));
                    play.expect(screen.queryByText("Set Scale", StroomDom.DIALOG_TITLE)).not().toBeNull();
                })
                // Set Scale's Cancel closes it and leaves the map's scale as it was
                .story("SetScaleCancel", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play dialog = measureLine(play, screen);
                    play.type(dialog.getByRole("textbox"), "2.4");
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Set Scale", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    final Play again = measureLine(play, screen);
                    play.expect(again.queryByText(TextMatch.containing("2.4 m"))).toBeNull();
                    DocEditors.expectNoProblems(play);
                })
                // Discover reads the store a page at a time: a second page is asked for from where
                // the first ended, because the first wasn't exact, and the types of both pages
                // become layers
                .story("DiscoverPages", FloorMapEditorTabStories::render)
                .withPlay(play -> {
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    final Query discover = play.findByRole("button", "Discover types from the facts store");
                    // No layer for either type yet
                    play.expect(play.queryByText("desk")).toBeNull();
                    play.expect(play.queryByText("gate")).toBeNull();
                    play.click(discover);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            discoverPage(0).toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            discoverPage(2).toSpyMatcher()));
                    play.waitFor(() -> play.expect(play.getByText("gate")).toBeInTheDocument());
                    play.expect(play.getByText("desk")).toBeInTheDocument();
                    // The last page was exact, so there was no third request
                    play.expect("Discover's finds", () -> SecurityPlays.requests(requests,
                            RequestMatcher.post(FIND).withJsonBodyContaining(
                                    "{\"pageRequest\": {\"length\": " + DISCOVER_PAGE_SIZE + "}}")).size())
                            .toBe(2);
                    DocEditors.expectNoProblems(play);
                })
                // A layer's swatch opens its appearance, drawing its shape in its colour; Cancel
                // closes it, and it opens again, where OK keeps what it shows
                .story("LayerAppearance", context -> render(context, fixtures(DOC_WITH_LAYER)))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.click(play.findByRole("button", DESK_SWATCH));
                    Play dialog = appearance(screen);
                    play.expect(dialog.getByRole("radio", "Shape")).toBeChecked();
                    play.expect(dialog.getByRole("combobox", "Shape")).toHaveValue("CIRCLE");
                    play.expect(dialog.getByRole("radio", "Icon")).not().toBeChecked();
                    play.expect(dialog.getByRole("button",
                            "Upload a new image into this document's assets")).toBeDisabled();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText(APPEARANCE)).toBeNull());

                    play.click(play.getByRole("button", DESK_SWATCH));
                    dialog = appearance(screen);
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText(APPEARANCE)).toBeNull());
                    play.expect(play.getByRole("button", DESK_SWATCH)).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // A layer's grip moves it with the arrow keys, keeping the focus on the moved layer's
                // grip and saying where it went
                .story("LayerReorderKeyboard", context -> render(context, fixtures(DOC_WITH_TWO_LAYERS)))
                .withPlay(play -> {
                    final Query grip = play.findByRole("button", DESK_GRIP);
                    play.run("focus the desk layer's grip", () -> grip.element().get().focus());
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect(play.getByText("desk layer moved to position 2 of 2"))
                            .toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.getByRole("button", DESK_GRIP)).toHaveFocus());
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(play.getByText("desk layer moved to position 1 of 2"))
                            .toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.getByRole("button", DESK_GRIP)).toHaveFocus());
                    DocEditors.expectNoProblems(play);
                })
                // A layer's eye dims it, then hides it, and its name fades with it; its lock
                // locks it. Each state is a class, named in the button's title
                .story("LayerVisibilityAndLock", context -> render(context, fixtures(DOC_WITH_LAYER)))
                .withPlay(play -> {
                    final Query eye = play.findByRole("button", "desk layer is visible — click to dim to 30%");
                    final Query name = play.getByText("desk", ".floor-map-layers__name");
                    play.click(eye);
                    play.expect(play.getByRole("button", "desk layer is dimmed to 30% — click to hide"))
                            .toHaveClass("floor-map-layers__eye--dim");
                    play.expect(name).toHaveClass("floor-map-layers__name--dim");
                    play.click(play.getByRole("button", "desk layer is dimmed to 30% — click to hide"));
                    final Query hidden = play.getByRole("button", "desk layer is hidden — click to show");
                    play.expect(hidden).toHaveClass("floor-map-layers__eye--off");
                    play.expect(hidden).not().toHaveClass("floor-map-layers__eye--dim");
                    play.expect(name).toHaveClass("floor-map-layers__name--off");

                    play.click(play.getByRole("button", TextMatch.startingWith("desk layer is unlocked")));
                    play.expect(play.getByRole("button", TextMatch.startingWith("desk layer is locked")))
                            .toHaveClass("floor-map-layers__lock--locked");
                    DocEditors.expectNoProblems(play);
                })
                // A floor map the user may only view: the layers can't be reordered or restyled,
                // nor types discovered, the Fact and Time Lists can't add, and the canvas opens no
                // menu (it once followed no read-only state, so all of them could be changed,
                // though not saved). A layer's eye still works, as it changes only the view
                .story("ReadOnly", context -> render(context, fixtures(DOC_WITH_LAYER), true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query grip = play.findByRole("button", DESK_GRIP);
                    play.expect(grip).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", DESK_SWATCH)).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", DISCOVER)).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", "Add New Object")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", "Add")).toHaveAttribute("aria-disabled", "true");
                    // The arrow keys on the grip move nothing
                    play.run("focus the desk layer's grip", () -> grip.element().get().focus());
                    play.keyboard("{ArrowDown}");
                    play.expect(play.queryByText("desk layer moved to position 2 of 2")).toBeNull();
                    // The swatch opens no appearance
                    play.click(play.getByRole("button", DESK_SWATCH));
                    play.expect(screen.queryByText(APPEARANCE, StroomDom.DIALOG_TITLE)).toBeNull();
                    // The canvas opens no menu
                    final Query canvas = play.querySelector(CANVAS);
                    play.run("focus the canvas", () -> canvas.element().get().focus());
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    play.expect(screen.queryByRole("menu", MAP_ACTIONS)).toBeNull();
                    // The eye only changes the view, so it still works
                    play.click(play.getByRole("button", "desk layer is visible — click to dim to 30%"));
                    play.expect(play.getByRole("button", "desk layer is dimmed to 30% — click to hide"))
                            .toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // The document is locked and unlocked while it is open, without being read again:
                // the editing controls follow
                .story("LockChangeWhileOpen", context -> render(context, fixtures(DOC_WITH_LAYER)))
                .withPlay(play -> {
                    final Query grip = play.findByRole("button", DESK_GRIP);
                    play.expect(grip).not().toHaveAttribute("aria-disabled");
                    play.expect(play.getByRole("button", "Add New Object")).not().toHaveAttribute("aria-disabled");

                    play.run("lock the document", () -> tab().setReadOnly(true));
                    play.expect(play.getByRole("button", DESK_GRIP)).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", DESK_SWATCH)).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", DISCOVER)).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", "Add New Object")).toHaveAttribute("aria-disabled", "true");
                    play.expect(play.getByRole("button", "Add")).toHaveAttribute("aria-disabled", "true");

                    play.run("unlock the document", () -> tab().setReadOnly(false));
                    play.expect(play.getByRole("button", DESK_GRIP)).not().toHaveAttribute("aria-disabled");
                    play.expect(play.getByRole("button", DESK_SWATCH)).not().toHaveAttribute("aria-disabled");
                    play.expect(play.getByRole("button", DISCOVER)).not().toHaveAttribute("aria-disabled");
                    play.expect(play.getByRole("button", "Add New Object")).not().toHaveAttribute("aria-disabled");
                    play.expect(play.getByRole("button", "Add")).not().toHaveAttribute("aria-disabled");
                    // The canvas is in edit mode again: its menu opens
                    final Query canvas = play.querySelector(CANVAS);
                    play.run("focus the canvas", () -> canvas.element().get().focus());
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    expectMenuWithFocus(play, play.screen());
                    play.keyboard("{Escape}");
                    DocEditors.expectNoProblems(play);
                });
    }

    // The Editor tab the story shows, as its document holds it
    private static TabStory<FloorMapEditorTabController, FloorMapDoc> tab() {
        return ScreenHarness.shownComponent();
    }

    // Chooses Set Scale from the canvas's menu and drags a line across the canvas with the mouse,
    // which opens the Set Scale dialog
    private static Play measureLine(final Play play, final Play screen) {
        final Query canvas = play.querySelector(CANVAS);
        play.waitFor(() -> play.expect(canvas).toHaveAttribute("role", "group"));
        play.run("focus the canvas", () -> canvas.element().get().focus());
        play.keyboard("{Shift>}{F10}{/Shift}");
        play.click(screen.findByRole("menuitem", "Set Scale"));
        play.waitFor(() -> play.expect(canvas).toHaveClass("floor-map-canvas--crosshair"));
        play.fireEvent().mouseDown(canvas, EventInit.create().relativeTo(canvas, 100, 200).buttons(1));
        play.fireEvent().mouseMove(canvas, EventInit.create().relativeTo(canvas, 200, 200).buttons(1));
        play.fireEvent().mouseMove(canvas, EventInit.create().relativeTo(canvas, 300, 200).buttons(1));
        play.fireEvent().mouseUp(canvas, EventInit.create().relativeTo(canvas, 300, 200));
        final Query title = screen.findByText("Set Scale", StroomDom.DIALOG_TITLE);
        return screen.within(title.closest(StroomDom.DIALOG));
    }

    // The desk layer's appearance dialog, once it is shown
    private static Play appearance(final Play screen) {
        final Query title = screen.findByText(APPEARANCE, StroomDom.DIALOG_TITLE);
        return screen.within(title.closest(StroomDom.DIALOG));
    }

    // Discover's find of the page starting at offset
    private static RequestMatcher discoverPage(final int offset) {
        return RequestMatcher.post(FIND).withJsonBodyContaining(
                "{\"pageRequest\": {\"offset\": " + offset + ", \"length\": " + DISCOVER_PAGE_SIZE + "}, "
                + "\"expression\": {\"children\": [{\"field\": \"Map\", \"value\": \"office\"}]}}");
    }

    // One menu, named for the map, with the focus on its first item
    private static void expectMenuWithFocus(final Play play, final Play screen) {
        screen.findByRole("menu", MAP_ACTIONS);
        play.waitFor(() -> play.expect(screen.getByRole("menuitem", "Add Object Here")).toHaveFocus());
        play.expect(screen.getAllByRole("menu")).toHaveLength(1);
    }

    // The Editor tab on its own, as the document shows it
    private static TabStory<FloorMapEditorTabController, FloorMapDoc> editorTab(final ScreenHarness harness,
                                                                             final EditorsScreenGinjector injector) {
        final FloorMapEditorTabController editor = injector.getFloorMapEditorTab();
        harness.fn(ON_CHANGE);
        editor.setOnChange(() -> harness.spy(ON_CHANGE, "changed"));
        return new TabStory<>(harness.getEventBus(), editor,
                FloorMapEditorTabController::read, FloorMapEditorTabController::write);
    }

    private static Widget render(final StoryContext context) {
        return render(context, FIXTURES);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        return render(context, fixtures, false);
    }

    // The tab, for a user who may only view the floor map or may change it
    private static Widget render(final StoryContext context, final RestFixtures fixtures, final boolean readOnly) {
        final FloorMapResource resource = GWT.create(FloorMapResource.class);
        return DocEditors.render(context, fixtures, readOnly,
                (harness, injector) -> DocEditors.open(harness,
                        DOC_REF,
                        // Shown as the document shows its Editor tab
                        editorTab(harness, injector),
                        DocResource.of(
                                restFactory -> restFactory.create(resource)
                                        .method(res -> res.fetch(DOC_REF.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
