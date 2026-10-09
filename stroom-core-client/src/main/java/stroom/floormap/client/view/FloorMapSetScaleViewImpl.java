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

package stroom.floormap.client.view;

import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.presenter.FloorMapSetScalePresenter.FloorMapSetScaleView;
import stroom.floormap.shared.FloorMapMeasurementUnits.Family;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/// View implementation for the Set Scale dialog.
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
public class FloorMapSetScaleViewImpl extends ViewImpl implements FloorMapSetScaleView {

    private static final int ROW_CURRENT = 0;
    private static final int ROW_DISTANCE = 1;

    private final FlowPanel root;
    private final Label currentReading = new Label();
    private final TextBox distanceBox = new TextBox();
    private final ListBox unitListBox = new ListBox();

    @Inject
    public FloorMapSetScaleViewImpl() {
        root = new FlowPanel();
        root.addStyleName("floormap-set-scale");

        for (final Unit unit : Unit.values()) {
            if (unit.getFamily() == Family.METRIC) {
                unitListBox.addItem(unit.getLabel(), unit.name());
            }
        }

        distanceBox.addStyleName("stroom-control");
        distanceBox.addStyleName("floormap-set-scale-distance");
        distanceBox.getElement().setPropertyString("placeholder", "e.g. 2.4");

        final FlowPanel distanceRow = new FlowPanel();
        distanceRow.addStyleName("floormap-set-scale-row");
        distanceRow.add(distanceBox);
        distanceRow.add(unitListBox);

        final Grid grid = new Grid(2, 2);
        grid.addStyleName("floormap-set-scale-fields");
        grid.setText(ROW_CURRENT, 0, "The line you measured is currently");
        grid.setWidget(ROW_CURRENT, 1, currentReading);
        grid.setText(ROW_DISTANCE, 0, "It is really");
        grid.setWidget(ROW_DISTANCE, 1, distanceRow);
        root.add(grid);

        // The label text lives in a <td>, which names nothing on its own — see
        // FloorMapAria.labelledByCell. Without this the dialog's only input is
        // announced as an unnamed edit box.
        FloorMapAria.labelledByCell(grid, ROW_DISTANCE, 0, distanceBox);
        // The unit list shares the row, so the row label alone would not say what
        // it selects.
        FloorMapAria.label(unitListBox, "Unit of the distance you typed");
    }

    @Override
    public Widget asWidget() {
        return root;
    }

    @Override
    public void setCurrentReading(final String reading) {
        currentReading.setText(reading != null
                ? reading
                : "");
    }

    @Override
    public void setDistance(final String distance) {
        distanceBox.setValue(distance != null
                ? distance
                : "");
    }

    @Override
    public String getDistance() {
        return distanceBox.getValue();
    }

    @Override
    public void setUnit(final Unit unit) {
        final String value = unit != null
                ? unit.name()
                : Unit.METRE.name();
        for (int i = 0; i < unitListBox.getItemCount(); i++) {
            if (unitListBox.getValue(i).equals(value)) {
                unitListBox.setSelectedIndex(i);
                return;
            }
        }
    }

    @Override
    public Unit getUnit() {
        try {
            return Unit.valueOf(unitListBox.getSelectedValue());
        } catch (final IllegalArgumentException | NullPointerException e) {
            // The list is built from the enum, so this cannot normally happen;
            // falling back keeps a broken selection from blocking the dialog.
            return Unit.METRE;
        }
    }

    @Override
    public void focus() {
        distanceBox.setFocus(true);
    }
}
