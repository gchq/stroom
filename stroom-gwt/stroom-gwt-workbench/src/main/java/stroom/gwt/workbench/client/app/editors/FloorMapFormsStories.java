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

import stroom.floormap.client.component.FloorMapGroupEdit;
import stroom.floormap.client.component.FloorMapLayerStyle;
import stroom.floormap.client.component.FloorMapLayerStyle.GraphicMode;
import stroom.floormap.client.component.FloorMapObjectEdit;
import stroom.floormap.client.component.FloorMapSetScale;
import stroom.floormap.client.component.MemberCandidate;
import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.Collections;

/// The `App/Editors/FloorMapForms` stories, showing the real views of a floor map's dialogs whose
/// controls were GWT's own (a radio button, list box or check box), and are now Stroom's: the
/// layer style's graphic (a radio group), the set scale dialog's unit (a selection box), and the
/// tick boxes of the group and object editors.
public final class FloorMapFormsStories {

    private FloorMapFormsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/FloorMapForms", FloorMapFormsStories.class)
                .layout(StoryLayout.PADDED)
                // A layer's style: what to draw (Shape, Icon or Image) is a radio group, and the rows
                // the chosen graphic doesn't use are disabled
                .story("LayerStyle", context -> {
                    return layerStyle(GraphicMode.SHAPE);
                })
                .withPlay(play -> {
                    final Query group = play.getByRole("radiogroup", "Graphic");
                    play.expect(group).toHaveClass("radio-group");
                    play.expect(play.getByRole("radio", "Shape")).toBeChecked();
                    play.expect(play.getByRole("combobox", "Shape")).not().toBeDisabled();
                    // The arrow keys choose the next graphic, and its row is the one enabled
                    play.click(play.getByRole("radio", "Shape"));
                    play.keyboard("{ArrowRight}");
                    play.expect(play.getByRole("radio", "Icon")).toBeChecked();
                    play.expect(play.getByRole("combobox", "Shape")).toBeDisabled();
                    play.expect(play.querySelectorAll(".gwt-RadioButton")).toHaveLength(0);
                })
                // The icons are a radio group too, named by their row: one tab stop, the arrow keys
                // choosing (they were clickable cells the keyboard couldn't reach)
                .story("IconPicker", context -> {
                    return layerStyle(GraphicMode.ICON);
                })
                .withPlay(play -> {
                    final Query icons = play.getByRole("radiogroup", "Icon");
                    play.expect(icons).toHaveClass("floormap-icon-picker");
                    play.expect(play.within(icons).getAllByRole("radio")).toHaveLength(FloorMapIcon.values().length);
                    // From the graphic's Icon to the icons, the first having the focus as none is chosen
                    play.click(play.getByRole("radio", "Icon"));
                    play.tab();
                    final Query first = play.within(icons).getByRole("radio", FloorMapIcon.values()[0].getLabel());
                    play.expect(first).toHaveFocus();
                    play.keyboard("{ArrowRight}");
                    final Query second = play.within(icons).getByRole("radio", FloorMapIcon.values()[1].getLabel());
                    play.expect(second).toBeChecked();
                    play.expect(second).toHaveFocus();
                    // Disabled with the rest of the row when the graphic isn't an icon
                    play.click(play.getByRole("radio", "Shape"));
                    play.expect(second).toBeDisabled();
                })
                // Up and Down choose the icon above or below in the grid as it is laid out (Left and
                // Right the one before or after); at the top row Up keeps the choice
                .story("IconPickerRows", context -> {
                    return layerStyle(GraphicMode.ICON);
                })
                .withPlay(play -> {
                    final Query icons = play.getByRole("radiogroup", "Icon");
                    play.click(play.getByRole("radio", "Icon"));
                    play.tab();
                    final Query first = play.within(icons).getByRole("radio", FloorMapIcon.values()[0].getLabel());
                    play.expect(first).toHaveFocus();
                    play.keyboard("{ArrowDown}");
                    // The icon chosen, with the focus, is in the same column, in the next row
                    play.expect(Value.of("the icon below the first is chosen, with the focus",
                            () -> isBelow(focusedElement(), first.element().get()))).toBe(true);
                    play.keyboard("{ArrowUp}");
                    play.expect(first).toBeChecked();
                    play.expect(first).toHaveFocus();
                    play.keyboard("{ArrowUp}");
                    play.expect(first).toBeChecked();
                    play.expect(first).toHaveFocus();
                })
                // The set scale dialog: the distance's unit is a selection box of the metric units,
                // named for what it is
                .story("SetScale", context -> {
                    final FloorMapSetScale view = new FloorMapSetScale();
                    view.setCurrentReading("3.2 m");
                    view.setDistance("2.4");
                    view.setUnit(Unit.CENTIMETRE);
                    return view.asWidget();
                })
                .withPlay(play -> {
                    final Query unit = play.getByRole("combobox", "Unit of the distance you typed");
                    play.expect(unit).toHaveValue("Centimetres (cm)");
                    play.expect(play.querySelectorAll("select")).toHaveLength(0);
                })
                // A group's members: a tick box for each one seen on the map, named by its name
                .story("GroupMembers", context -> {
                    final FloorMapGroupEdit view = new FloorMapGroupEdit();
                    view.setName("Meeting rooms");
                    view.setCandidates(Arrays.asList(
                                    new MemberCandidate("room-1", "Room 1", "room", true),
                                    new MemberCandidate("room-2", "Room 2", "room", true)),
                            Collections.singletonList("room-1"));
                    return view.asWidget();
                })
                .withPlay(play -> {
                    play.expect(play.getByRole("checkbox", "Room 1")).toBeChecked();
                    play.expect(play.getByRole("checkbox", "Room 2")).not().toBeChecked();
                    play.click(play.getByText("Room 2"));
                    play.expect(play.getByRole("checkbox", "Room 2")).toBeChecked();
                    play.expect(play.getByRole("checkbox", "Room 2")).toHaveClass("tick-box__input");
                    play.expect(play.querySelectorAll(".gwt-CheckBox")).toHaveLength(0);
                })
                // An area's fill: 'Default' is a tick box, ticked to use its type's colour
                .story("AreaFill", context -> {
                    final FloorMapObjectEdit view = new FloorMapObjectEdit();
                    view.setAreaFieldsVisible(true);
                    view.setDefaultFill("#336699");
                    // No image chosen, as its controller shows it
                    view.getAssetBox().setText("None");
                    return view.asWidget();
                })
                .withPlay(play -> {
                    final Query fill = play.getByRole("group", "Area Fill Colour");
                    final Query useDefault = play.within(fill).getByRole("checkbox", "Default");
                    play.expect(useDefault).toBeChecked();
                    play.expect(useDefault).toHaveClass("tick-box__input");
                    play.click(useDefault);
                    play.expect(useDefault).not().toBeChecked();
                    // The colour beside it is a colour box (colour-box.css), named for what it colours
                    play.expect(play.querySelector("input[type='color']")).toHaveClass("colour-box");
                    play.expect(play.querySelector("input[type='color']"))
                            .toHaveAttribute("aria-label", "Area fill colour");
                    play.expect(play.querySelectorAll(".gwt-CheckBox")).toHaveLength(0);
                });
    }

    // The layer style dialog's content, drawing a graphic, with no image chosen, as its presenter
    // shows it
    private static Widget layerStyle(final GraphicMode mode) {
        final FloorMapLayerStyle view = new FloorMapLayerStyle();
        view.setMode(mode);
        view.getAssetBox().setText("None");
        return view.asWidget();
    }

    private static native Element focusedElement() /*-{
        return $doc.activeElement;
    }-*/;

    // Whether a radio button is checked and its icon's cell is directly below another's: in the same
    // column (the same left edge), in a lower row
    private static native boolean isBelow(Element radio, Element above) /*-{
        if (!radio || !radio.checked) {
            return false;
        }
        var cell = radio.closest('label').getBoundingClientRect();
        var upper = above.closest('label').getBoundingClientRect();
        return Math.abs(cell.left - upper.left) < 1 && cell.top > upper.top;
    }-*/;
}
