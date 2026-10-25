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
import stroom.floormap.client.presenter.FloorMapEditorPresenter;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapResource;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.security.SecurityPlays;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// The `App/Editors/FloorMapEditor` stories, showing Stroom's real [FloorMapEditorPresenter] (a
/// Floor Map's 'Editor' tab: the map canvas, its timeline and its Layers panel) with fake REST
/// replies.
///
/// As `FloorMapPresenter` does for its Editor tab, the story fetches the document
/// (`GET /floormap/v1/{uuid}`) and reads it into the editor ([DocEditors#open]). The editor then
/// asks the map's facts store (`SqlTemporalStoreResource`, `/sqltemporalstore/v1`) for its
/// `timeRange`, and the facts at the selected time (`fetchAtTime`, `fetchAll` and `find`), all of
/// which are empty here: the map has no objects. The Layers panel's Discover scans the whole store
/// with `find`, a page of 10,000 entries at a time, until a page's response is exact;
/// the store has two pages, the first with two desks and the second with a gate.
public final class FloorMapEditorStories {

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

    private static final String FIND = "/sqltemporalstore/v1/find";

    // FloorMapEditorPresenter.DISCOVER_PAGE_SIZE
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

    // The canvas's focusable element (FloorMapCanvasViewImpl's focus panel)
    private static final String CANVAS = ".stroom-floormap-canvas-focus";

    // The canvas's context menu with nothing selected, named for what it acts on
    private static final String MAP_ACTIONS = "Map Actions";

    // The desk layer's swatch, which opens its appearance
    // The desk layer's appearance dialog's title
    private static final String APPEARANCE = "Appearance — desk";

    private static final String DESK_SWATCH = "Edit the appearance of the desk layer (shape & colour)";

    private FloorMapEditorStories() {
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
        registry.component("App/Editors/FloorMapEditor", FloorMapEditorStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Shift+F10 and the context menu key, the standard keys for a context menu, open the
                // focused canvas's menu, as Enter and Space do. The menu is named for what it acts
                // on and takes the focus, and the browser's own contextmenu event for the key
                // does not open a second menu
                .story("ContextMenuByKeyboard", FloorMapEditorStories::render)
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
                // Discover reads the store a page at a time: a second page is asked for from where
                // the first ended, because the first wasn't exact, and the types of both pages
                // become layers
                .story("DiscoverPages", FloorMapEditorStories::render)
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
                });
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

    private static Widget render(final StoryContext context) {
        return render(context, FIXTURES);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final FloorMapResource resource = GWT.create(FloorMapResource.class);
        return DocEditors.render(context, fixtures, false,
                (harness, injector) -> DocEditors.open(harness,
                        DOC_REF,
                        injector.getFloorMapEditorPresenter(),
                        DocResource.of(
                                restFactory -> restFactory.create(resource)
                                        .method(res -> res.fetch(DOC_REF.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
