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
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
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
/// which are empty here: the map has no objects.
public final class FloorMapEditorStories {

    private static final DocRef DOC_REF = new DocRef(FloorMapDoc.TYPE, "floormap-1", "Office");

    // FloorMapResource.fetch(): a floor map whose facts are in the store 'office' (the editor shows
    // no menu without one)
    private static final String DOC = """
            {"type": "FloorMap", "uuid": "floormap-1", "name": "Office",
              "factsStoreRef": {"type": "SqlTemporalStore", "uuid": "store-1", "name": "office"}}""";

    private static final RestFixtures FIXTURES = DocEditors.docSelectionRoutes(
                    DocEditors.permissionRoutes(RestFixtures.builder()))
            .get("/floormap/v1/floormap-1", RestReply.json(DOC))
            .put("/floormap/v1/floormap-1", request -> RestReply.json(request.getBody()))
            // An empty store: no time range and no facts
            .post("/sqltemporalstore/v1/timeRange", RestReply.json("{}"))
            .post("/sqltemporalstore/v1/fetchAtTime", RestReply.json("[]"))
            .post("/sqltemporalstore/v1/fetchAll", RestReply.json("[]"))
            .post("/sqltemporalstore/v1/find", RestReply.json(
                    "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, \"total\": 0, "
                    + "\"exact\": true}}"))
            .build();

    // The canvas's focusable element (FloorMapCanvasViewImpl's focus panel)
    private static final String CANVAS = ".stroom-floormap-canvas-focus";

    // The canvas's context menu with nothing selected, named for what it acts on
    private static final String MAP_ACTIONS = "Map Actions";

    private FloorMapEditorStories() {
        // Static utility
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
                });
    }

    // One menu, named for the map, with the focus on its first item
    private static void expectMenuWithFocus(final Play play, final Play screen) {
        screen.findByRole("menu", MAP_ACTIONS);
        play.waitFor(() -> play.expect(screen.getByRole("menuitem", "Add Object Here")).toHaveFocus());
        play.expect(screen.getAllByRole("menu")).toHaveLength(1);
    }

    private static Widget render(final StoryContext context) {
        final FloorMapResource resource = GWT.create(FloorMapResource.class);
        return DocEditors.render(context, FIXTURES, false,
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
