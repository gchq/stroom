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

package stroom.floormap.client.component;

import stroom.floormap.client.FloorMapAria;
import stroom.floormap.shared.FloorMapMeasurementUnits.Family;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;
import stroom.item.client.SelectionBox;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Set Scale dialog's content, which its [FloorMapSetScaleController] shows.
///
/// ```
/// The line you measured is currently   1 m
///
/// It is really   [ 2.4 ] [ Metres (m) v ]
/// ```
///
/// The current reading is shown because it is the quantity being corrected —
/// it says how wrong the map's present scale is.
///
/// The unit list is **metric only**: it names the unit of the
/// number being typed, not a display preference, and maps are always measured in
/// metric.
public class FloorMapSetScale extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label currentReading;
    @UiField
    TableCellElement distanceLabel;
    @UiField
    TextBox distanceBox;
    @UiField
    SelectionBox<Unit> unitListBox;

    /// Makes the dialog's content, for a distance in metres.
    public FloorMapSetScale() {
        initWidget(BINDER.createAndBindUi(this));
        for (final Unit unit : Unit.values()) {
            if (unit.getFamily() == Family.METRIC) {
                unitListBox.addItem(unit);
            }
        }
        unitListBox.setDisplayValueFunction(Unit::getLabel);

        // The label text lives in a <td>, which names nothing on its own. Without this the dialog's
        // only input is announced as an unnamed edit box
        distanceLabel.setId(FloorMapAria.uniqueId("floormap-label"));
        FloorMapAria.labelledBy(distanceBox, distanceLabel.getId());
        // The unit list shares the row, so the row label alone would not say what it selects
        FloorMapAria.labelInnerControl(unitListBox, "Unit of the distance you typed");
    }

    /// @param reading What the line measures at the map's current scale.
    public void setCurrentReading(final String reading) {
        currentReading.setText(reading != null
                ? reading
                : "");
    }

    /// @param distance The real distance, as typed.
    public void setDistance(final String distance) {
        distanceBox.setValue(distance != null
                ? distance
                : "");
    }

    /// @return The real distance, as typed.
    public String getDistance() {
        return distanceBox.getValue();
    }

    /// @param unit The unit of the distance typed; only metric units are listed, so another is
    ///             metres.
    public void setUnit(final Unit unit) {
        unitListBox.setValue(unit != null && unitListBox.getItems().contains(unit)
                ? unit
                : Unit.METRE);
    }

    /// @return The unit of the distance typed.
    public Unit getUnit() {
        return unitListBox.getValue() != null
                ? unitListBox.getValue()
                : Unit.METRE;
    }

    /// Puts the focus in the distance field.
    public void focus() {
        distanceBox.setFocus(true);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapSetScale> {

    }
}
