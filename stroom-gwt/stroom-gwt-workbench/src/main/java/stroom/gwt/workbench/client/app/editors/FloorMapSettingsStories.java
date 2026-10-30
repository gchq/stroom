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
import stroom.floormap.client.component.FloorMapSettingsController;
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


/// The `App/Editors/FloorMapSettings` stories, showing Stroom's real [FloorMapSettingsController] (a
/// Floor Map's 'Settings' tab, shown as `FloorMapEditor` shows it) with fake REST replies, for
/// its Value Schema grid: a row per field mapping, each with a selection box for its role
/// ([stroom.item.client.SelectionBoxCell]) and text fields for its path, display name and default
/// ([stroom.cell.textfield.client.TextFieldCell]), each named for its column and the row's path.
public final class FloorMapSettingsStories {

    private static final DocRef DOC_REF = new DocRef(FloorMapDoc.TYPE, "floormap-1", "Office");

    // FloorMapResource.fetch(): a floor map with three field mappings
    private static final String DOC = """
            {"type": "FloorMap", "uuid": "floormap-1", "name": "Office",
              "valueFormat": "JSON",
              "valueSchema": [
                {"path": "$.type", "role": "TYPE", "displayName": "Type"},
                {"path": "$.label", "role": "LABEL", "displayName": "Label"},
                {"path": "$.temp", "role": "CUSTOM", "displayName": "Temperature", "defaultValue": "0"}
              ]}""";

    private static final RestFixtures FIXTURES = DocEditors.docSelectionRoutes(
                    DocEditors.permissionRoutes(RestFixtures.builder()))
            .get("/floormap/v1/floormap-1", RestReply.json(DOC))
            .put("/floormap/v1/floormap-1", request -> RestReply.json(request.getBody()))
            .build();

    private static final String TEMPERATURE_ROLE = "Role for $.temp";

    private FloorMapSettingsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/FloorMapSettings", FloorMapSettingsStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Each row's role is a selection box and its path, name and default text fields, all
                // named for their column and the row's path
                .story("Schema", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query role = screen.findByRole("combobox", "Role for $.type");
                    play.expect(role).toHaveValue("TYPE");
                    play.expect(role).toHaveAttribute("aria-expanded", "false");
                    play.expect(screen.getByRole("combobox", TEMPERATURE_ROLE)).toHaveValue("CUSTOM");
                    final Query path = screen.getByRole("textbox", "Path for $.label");
                    play.expect(path).toHaveValue("$.label");
                    play.expect(path).toHaveClass("text-field", "text-field--cell");
                    play.expect(screen.getByRole("textbox", "Default for $.temp")).toHaveValue("0");
                    DocEditors.expectNoProblems(play);
                })
                // Enter opens a row's list, named for the box; choosing gives the row its new role,
                // and the focus comes back to the row's box, closed
                .story("ChooseARole", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query role = screen.findByRole("combobox", TEMPERATURE_ROLE);
                    play.run("focus the box", () -> role.element().get().focus());
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.getByRole("listbox", TEMPERATURE_ROLE)).toBeVisible());
                    play.expect(screen.getByRole("combobox", TEMPERATURE_ROLE))
                            .toHaveAttribute("aria-expanded", "true");
                    play.click(screen.getByRole("option", "STATUS"));
                    play.waitFor(() -> play.expect(screen.queryByRole("listbox", TEMPERATURE_ROLE)).toBeNull());
                    play.waitFor(() -> play.expect(screen.getByRole("combobox", TEMPERATURE_ROLE))
                            .toHaveValue("STATUS"));
                    play.waitFor(() -> play.expect(screen.getByRole("combobox", TEMPERATURE_ROLE)).toHaveFocus());
                    play.expect(screen.getByRole("combobox", TEMPERATURE_ROLE))
                            .toHaveAttribute("aria-expanded", "false");
                    DocEditors.expectNoProblems(play);
                })
                // On a row's box, Home and End are the grid's (the agreed grid keys): the row's last
                // cell (the Default field's cell) and back to its first (the box); no list opens
                .story("HomeAndEndMoveAlongTheRow", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query role = screen.findByRole("combobox", TEMPERATURE_ROLE);
                    play.run("focus the box", () -> role.element().get().focus());
                    play.keyboard("{End}");
                    play.waitFor(() -> play.expect("the focused element's column",
                            FloorMapSettingsStories::focusedColumn).toBe("3"));
                    play.expect(screen.queryByRole("listbox", TEMPERATURE_ROLE)).toBeNull();
                    play.keyboard("{Home}");
                    play.waitFor(() -> play.expect(role).toHaveFocus());
                    play.expect(screen.queryByRole("listbox", TEMPERATURE_ROLE)).toBeNull();
                    play.expect(role).toHaveValue("CUSTOM");
                    DocEditors.expectNoProblems(play);
                })
                // Alt+Down opens the list (Down alone is the grid's: the next row), and Escape
                // closes it, keeping the role, with the focus back on the box
                .story("EscapeKeepsTheRole", context -> render(context, false))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query role = screen.findByRole("combobox", TEMPERATURE_ROLE);
                    play.run("focus the box", () -> role.element().get().focus());
                    play.keyboard("{Alt>}{ArrowDown}{/Alt}");
                    play.waitFor(() -> play.expect(screen.getByRole("listbox", TEMPERATURE_ROLE)).toBeVisible());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByRole("listbox", TEMPERATURE_ROLE)).toBeNull());
                    play.expect(role).toHaveValue("CUSTOM");
                    play.waitFor(() -> play.expect(role).toHaveFocus());
                    DocEditors.expectNoProblems(play);
                })
                // A user who may only view the floor map can read every row and reach its controls,
                // but no list opens and no field can be changed
                .story("ReadOnly", context -> render(context, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query role = screen.findByRole("combobox", TEMPERATURE_ROLE);
                    play.expect(role).toHaveAttribute("aria-readonly", "true");
                    play.expect(screen.getByRole("textbox", "Path for $.temp")).toHaveAttribute("readonly");
                    play.run("focus the box", () -> role.element().get().focus());
                    play.keyboard("{Enter}");
                    play.sleep(300);
                    play.expect(screen.queryByRole("listbox", TEMPERATURE_ROLE)).toBeNull();
                    DocEditors.expectNoProblems(play);
                });
    }

    // The grid column (data-col) of the cell holding the focus, or null
    private static native String focusedColumn() /*-{
        var active = $doc.activeElement;
        var cell = active && active.closest ? active.closest("[data-col]") : null;
        return cell ? cell.getAttribute("data-col") : null;
    }-*/;

    private static Widget render(final StoryContext context, final boolean readOnly) {
        final FloorMapResource resource = GWT.create(FloorMapResource.class);
        return DocEditors.render(context, FIXTURES, readOnly,
                (harness, injector) -> DocEditors.open(harness,
                        DOC_REF,
                        new TabStory<>(harness.getEventBus(),
                                injector.getFloorMapSettingsController(),
                                FloorMapSettingsController::read,
                                FloorMapSettingsController::write),
                        DocResource.of(
                                restFactory -> restFactory.create(resource)
                                        .method(res -> res.fetch(DOC_REF.getUuid())),
                                (restFactory, doc) -> restFactory.create(resource)
                                        .method(res -> res.update(doc.getUuid(), doc)))));
    }
}
