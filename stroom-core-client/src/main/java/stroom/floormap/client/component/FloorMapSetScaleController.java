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

import stroom.alert.client.event.AlertEvent;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The dialog that turns a line measured on the canvas into the map's scale ([FloorMapSetScale]),
/// shown for one use.
///
/// The user has just dragged across something whose real length they know;
/// this asks what that length is, and
/// [FloorMapMeasurementUnits#calibrate] does the arithmetic. It is the
/// **only** way to scale a map: there is no numeric scale-factor
/// field anywhere, because for a background image placed by eye nobody knows
/// that number.
///
/// The unit drop-down says what unit the *typed* distance is in — a
/// doorway is naturally given in metres, a desk in centimetres. It is not a
/// display preference: maps are always measured in metric, promoting through
/// mm/cm/m/km as the value warrants, so the scale is stored in
/// [#STORAGE_UNIT] regardless of what was typed.
public class FloorMapSetScaleController extends Controller {

    /// The unit the distance is assumed to be typed in until changed.
    private static final Unit DEFAULT_ENTRY_UNIT = Unit.METRE;

    /// The unit every calibration is stored in, matching
    /// [FloorMapMeasurementUnits#DEFAULT]. Which unit a scale is stored in
    /// has no effect on what is displayed — formatting promotes through the
    /// metric ladder either way — so storing them all alike keeps saved documents
    /// comparable.
    private static final Unit STORAGE_UNIT = Unit.CENTIMETRE;

    private final FloorMapSetScale view;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus The application's event bus, which the dialog is shown on.
    /// @param view     The dialog's content.
    @Inject
    public FloorMapSetScaleController(final EventBus eventBus,
                                      final FloorMapSetScale view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog for a measurement just taken on the canvas, owned by `opener` until it is
    /// closed.
    ///
    /// @param opener    What shows it.
    /// @param mapLength the measured length in map units; must be `> 0`
    /// @param current   the map's current units, used to show what the line measures today; may be
    ///         `null` on an uncalibrated map
    /// @param onOk      called with the calibrated units when the user confirms
    public void show(final HasControllerNode opener,
                     final double mapLength,
                     final FloorMapMeasurementUnits current,
                     final Consumer<FloorMapMeasurementUnits> onOk) {

        view.setUnit(DEFAULT_ENTRY_UNIT);
        view.setDistance("");
        // What the map thinks this line measures right now — the quantity the
        // user is about to correct.
        view.setCurrentReading(FloorMapMeasurementUnits.format(current, mapLength));

        //noinspection unused e
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .caption("Set Scale")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final Double distance = parseDistance(view.getDistance());
                        if (distance == null) {
                            AlertEvent.fireWarn(this,
                                    "Enter the real distance this line spans, "
                                    + "as a positive number.", e::reset);
                            return;
                        }
                        final FloorMapMeasurementUnits calibrated =
                                FloorMapMeasurementUnits.calibrate(
                                        mapLength, distance, view.getUnit(), STORAGE_UNIT);
                        if (calibrated == null) {
                            // calibrate() refuses anything that would produce an
                            // unusable scale, which would render a blank canvas.
                            AlertEvent.fireWarn(this,
                                    "That distance cannot be used to scale the map. "
                                    + "Try measuring a longer line.", e::reset);
                            return;
                        }
                        onOk.accept(calibrated);
                    }
                    e.hide();
                })
                .fire();
    }

    /// The typed distance, or `null` if it is not a usable positive number.
    ///
    /// @param text The distance, as typed.
    /// @return The distance, or null.
    static Double parseDistance(final String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            final double value = Double.parseDouble(text.trim());
            return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0
                    ? value
                    : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
