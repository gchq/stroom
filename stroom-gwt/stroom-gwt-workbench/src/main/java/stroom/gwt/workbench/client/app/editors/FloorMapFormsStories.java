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

import stroom.floormap.client.presenter.FloorMapGroupEditPresenter.MemberCandidate;
import stroom.floormap.client.presenter.FloorMapLayerStylePresenter.FloorMapLayerStyleView.GraphicMode;
import stroom.floormap.client.view.FloorMapGroupEditViewImpl;
import stroom.floormap.client.view.FloorMapLayerStyleViewImpl;
import stroom.floormap.client.view.FloorMapObjectEditViewImpl;
import stroom.floormap.client.view.FloorMapSetScaleViewImpl;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;

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
                    final FloorMapLayerStyleViewImpl view = new FloorMapLayerStyleViewImpl();
                    view.setMode(GraphicMode.SHAPE);
                    return view.asWidget();
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
                // The set scale dialog: the distance's unit is a selection box of the metric units,
                // named for what it is
                .story("SetScale", context -> {
                    final FloorMapSetScaleViewImpl view = new FloorMapSetScaleViewImpl();
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
                    final FloorMapGroupEditViewImpl view = new FloorMapGroupEditViewImpl();
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
                    final FloorMapObjectEditViewImpl view = new FloorMapObjectEditViewImpl(
                            GWT.create(FloorMapObjectEditViewImpl.Binder.class),
                            () -> null);
                    view.setAreaFieldsVisible(true);
                    view.setDefaultFill("#336699");
                    return view.asWidget();
                })
                .withPlay(play -> {
                    final Query fill = play.getByRole("group", "Area Fill Colour");
                    final Query useDefault = play.within(fill).getByRole("checkbox", "Default");
                    play.expect(useDefault).toBeChecked();
                    play.expect(useDefault).toHaveClass("tick-box__input");
                    play.click(useDefault);
                    play.expect(useDefault).not().toBeChecked();
                    play.expect(play.querySelectorAll(".gwt-CheckBox")).toHaveLength(0);
                });
    }
}
