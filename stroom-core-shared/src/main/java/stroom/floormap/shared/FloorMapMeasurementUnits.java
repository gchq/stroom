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

package stroom.floormap.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// What one *map unit* means in the real world, held on the
/// [FloorMapDoc] so that anything displaying a size can label it.
///
/// Map space is otherwise a pure abstraction: the canvas draws its grid with
/// the identity matrix, so one map unit is one screen pixel at 100 % zoom, and a
/// background image is scaled by dragging until it looks right. Nothing in the
/// document has ever said that the loading bay is 40 m across. This class is that
/// statement — a [Unit] to display in, and [#getUnitsPerMapUnit()]
/// of that unit per map unit.
///
/// **Every map has a scale.** A `null`
/// `FloorMapMeasurementUnits` — the stored state of any document that has
/// not been calibrated — resolves to [#DEFAULT], one centimetre per map
/// unit, wherever a distance is displayed. The concept of a "map unit" is
/// internal: it is never shown to a user, who sees only real-world
/// measurements.
///
/// Values **auto-promote within their family** when formatted, so
/// the default centimetres read `40 cm` but `12.5 m` and
/// `1.2 km`. The canvas spans several decades of zoom; a fixed unit would
/// show `0.05 m` at one end and `12000 m` at the other.
///
/// Immutable, holds no GWT or DOM types, and does all its own number
/// formatting — `String.format` and `NumberFormat` are both
/// unavailable to GWT-compiled shared code, and this class must also run under
/// plain JUnit.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FloorMapMeasurementUnits {

    /// The unit families. Promotion never crosses a family: a map configured in
    /// feet promotes to miles, never to kilometres.
    public enum Family {
        METRIC,
        IMPERIAL
    }

    /// The units a map can be measured in.
    ///
    /// Each carries `metresPerUnit`, the pivot every conversion and
    /// promotion goes through, so no pairwise conversion table is needed.
    ///
    /// Yards are deliberately absent: in/ft/mi is the ladder maps actually
    /// use, and every extra rung is another promotion boundary to reason
    /// about.
    public enum Unit {
        MILLIMETRE("mm", "Millimetres", Family.METRIC, 0.001),
        CENTIMETRE("cm", "Centimetres", Family.METRIC, 0.01),
        METRE("m", "Metres", Family.METRIC, 1.0),
        KILOMETRE("km", "Kilometres", Family.METRIC, 1000.0),
        INCH("in", "Inches", Family.IMPERIAL, 0.0254),
        FOOT("ft", "Feet", Family.IMPERIAL, 0.3048),
        MILE("mi", "Miles", Family.IMPERIAL, 1609.344);

        private final String symbol;
        private final String displayName;
        private final Family family;
        private final double metresPerUnit;

        Unit(final String symbol,
             final String displayName,
             final Family family,
             final double metresPerUnit) {
            this.symbol = symbol;
            this.displayName = displayName;
            this.family = family;
            this.metresPerUnit = metresPerUnit;
        }

        /// The symbol appended to formatted values — `"m"`, `"ft"`.
        public String getSymbol() {
            return symbol;
        }

        /// The name shown in the Settings drop-down — `"Metres"`.
        public String getDisplayName() {
            return displayName;
        }

        /// The drop-down label, e.g. `"Metres (m)"`.
        public String getLabel() {
            return displayName + " (" + symbol + ")";
        }

        public Family getFamily() {
            return family;
        }

        /// How many metres one of this unit spans; the conversion pivot.
        public double getMetresPerUnit() {
            return metresPerUnit;
        }

        /// Converts `value`, expressed in this unit, to metres.
        public double toMetres(final double value) {
            return value * metresPerUnit;
        }

        /// Converts `metres` to this unit.
        public double fromMetres(final double metres) {
            return metres / metresPerUnit;
        }
    }

    /// The scale every map has until it is calibrated: one centimetre per map
    /// unit, displayed in metric.
    ///
    /// A default rather than an "unset" state because a map unit is an
    /// internal abstraction — there is no honest way to show one to a user, and
    /// a size with no unit on it is the thing this whole feature exists to
    /// remove. Calibrating with the Set Scale tool replaces it.
    public static final FloorMapMeasurementUnits DEFAULT =
            new FloorMapMeasurementUnits(Unit.CENTIMETRE, 1.0);

    /// Significant figures every displayed value is rounded to.
    private static final int SIGNIFICANT_FIGURES = 3;

    /// Decimal places kept by [#formatForInput]. Four places of a metre is
    /// a tenth of a millimetre — finer than any floor plan is drawn to, so a
    /// value round-trips through the field unchanged in practice.
    private static final int INPUT_DECIMALS = 4;

    /// A policy ceiling, not an overflow bound: above this magnitude the formatters stop
    /// pretending to render exact digits and fall back to
    /// [String#valueOf(double)], so a nonsense scale factor produces obviously
    /// nonsense text. No real map is anywhere near it.
    ///
    /// This used to be documented as the point where "the digit-assembly path would
    /// overflow a `long`", and it is not — it was about four orders of magnitude too
    /// high for [#INPUT_DECIMALS]. With four decimal places
    /// `Math.round(abs * 1e4)` saturates `long` from roughly `9.2234e14`,
    /// which is *below* this ceiling, so the window in between assembled its digits
    /// from [Long#MAX_VALUE]: `formatForInput(9.5e14)` returned
    /// `"922337203685477.5807"` — exactly the silently-wrong output the guard was
    /// meant to prevent. The real overflow guard now lives in
    /// [#formatToDecimals(double, int)], derived from the arithmetic it protects, so
    /// this constant can no longer be set to a wrong value and break correctness.
    private static final double MAX_EXACT_MAGNITUDE = 1.0e15;

    @JsonProperty
    private final Unit unit;
    @JsonProperty
    private final double unitsPerMapUnit;

    @JsonCreator
    public FloorMapMeasurementUnits(@JsonProperty("unit") final Unit unit,
                                    @JsonProperty("unitsPerMapUnit") final double unitsPerMapUnit) {
        this.unit = unit;
        this.unitsPerMapUnit = unitsPerMapUnit;
    }

    /// Convenience for the common "1 map unit = 1 unit" case.
    public static FloorMapMeasurementUnits of(final Unit unit) {
        return new FloorMapMeasurementUnits(unit, 1.0);
    }

    /// The unit distances are configured in, before promotion.
    public Unit getUnit() {
        return unit;
    }

    /// How many [#getUnit()] one map unit spans.
    public double getUnitsPerMapUnit() {
        return unitsPerMapUnit;
    }

    /// Whether this is safe to compute with.
    ///
    /// A zero or non-finite `unitsPerMapUnit` divides straight into the
    /// grid's decade calculation, where it produces NaN spacing, NaN pattern
    /// coordinates and a canvas that renders *nothing* — no error, just a
    /// blank map. Every consumer checks this first.
    ///
    /// Cannot be named 'isValid' as this would trigger TestJsonSerialisation.testNoExtraProps()
    public boolean checkUnitIsValid() {
        return unit != null
               && Double.isFinite(unitsPerMapUnit)
               && unitsPerMapUnit > 0;
    }

    /// Returns a copy measured in `newUnit`, keeping the scale factor.
    public FloorMapMeasurementUnits withUnit(final Unit newUnit) {
        return new FloorMapMeasurementUnits(newUnit, unitsPerMapUnit);
    }

    /// Returns a copy with the given scale factor, keeping the unit.
    public FloorMapMeasurementUnits withUnitsPerMapUnit(final double newUnitsPerMapUnit) {
        return new FloorMapMeasurementUnits(unit, newUnitsPerMapUnit);
    }

    // ------------------------------------------------------------------------
    // Conversion
    // ------------------------------------------------------------------------

    /// Converts a map-space distance to the configured unit.
    public double toDisplayUnits(final double mapDistance) {
        return mapDistance * unitsPerMapUnit;
    }

    /// Converts a distance in the configured unit back to map space.
    public double toMapUnits(final double displayDistance) {
        return displayDistance / unitsPerMapUnit;
    }

    /// Converts a distance expressed in `enteredIn` to map space, going via
    /// metres so the entered unit need not be the configured one — the
    /// calibration dialog lets the user type "2.4 m" on a map configured in feet.
    public double toMapUnits(final double distance, final Unit enteredIn) {
        if (enteredIn == null || unit == null) {
            return toMapUnits(distance);
        }
        return toMapUnits(unit.fromMetres(enteredIn.toMetres(distance)));
    }

    /// Converts a map-space distance into a specific unit, the inverse of
    /// [#toMapUnits(double, Unit)].
    ///
    /// Used where a value must be shown in one fixed unit rather than the
    /// best-fitting one — an editable field, where a box that silently changed
    /// between mm and km as you typed would be unusable.
    ///
    /// @param mapDistance the distance in map units
    /// @param target      the unit to express it in
    /// @return the distance in `target` units
    public double toUnit(final double mapDistance, final Unit target) {
        if (target == null || unit == null) {
            return toDisplayUnits(mapDistance);
        }
        return target.fromMetres(unit.toMetres(toDisplayUnits(mapDistance)));
    }

    // ------------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------------

    /// The single entry point for every surface that displays a size: hands back
    /// a labelled real-world measurement whether or not the document has been
    /// calibrated.
    ///
    /// Surfaces call this rather than testing for null themselves, so exactly
    /// one place knows what an uncalibrated map measures in.
    ///
    /// @param units       the document's units; `null` (uncalibrated) and
    ///         invalid both fall back to [#DEFAULT]
    /// @param mapDistance the distance in map units
    /// @return e.g. `"40 cm"`, `"12.5 m"`, `"1.2 km"`
    public static String format(final FloorMapMeasurementUnits units, final double mapDistance) {
        return orDefault(units).format(mapDistance);
    }

    /// A width and height as one measurement — `"2.4 m × 1.1 m"` — for the
    /// readout shown while an object is being resized.
    ///
    /// @param units          the document's units; may be `null`
    /// @param widthMapUnits  the width in map units
    /// @param heightMapUnits the height in map units
    /// @return the formatted size
    public static String formatSize(final FloorMapMeasurementUnits units,
                                    final double widthMapUnits,
                                    final double heightMapUnits) {
        return format(units, widthMapUnits) + " × " + format(units, heightMapUnits);
    }

    /// A position as one measurement — `"X 4.5 m, Y 2.1 m"` — for the
    /// readout shown while an object is being moved.
    ///
    /// The axes are named rather than left as a bare pair of numbers: map
    /// space is Y-up, and an unlabelled pair invites the reader to guess which
    /// way round it is.
    ///
    /// @param units      the document's units; may be `null`
    /// @param xMapUnits  the X position in map units
    /// @param yMapUnits  the Y position in map units
    /// @return the formatted position
    public static String formatPosition(final FloorMapMeasurementUnits units,
                                        final double xMapUnits,
                                        final double yMapUnits) {
        return "X " + format(units, xMapUnits) + ", Y " + format(units, yMapUnits);
    }

    /// The units to measure with: the document's own, or [#DEFAULT] when it
    /// has none or its stored scale is unusable.
    ///
    /// @param units the document's units; may be `null`
    /// @return usable units; never `null`
    public static FloorMapMeasurementUnits orDefault(final FloorMapMeasurementUnits units) {
        return units != null && units.checkUnitIsValid()
                ? units
                : DEFAULT;
    }

    /// Formats a map-space distance in the configured unit, promoted to the
    /// largest unit of the same family that leaves a value of at least one.
    ///
    /// @param mapDistance the distance in map units
    /// @return e.g. `"850 m"`, `"1.2 km"`
    public String format(final double mapDistance) {
        if (!checkUnitIsValid()) {
            return DEFAULT.format(mapDistance);
        }
        if (!Double.isFinite(mapDistance)) {
            return formatNumber(mapDistance) + " " + unit.getSymbol();
        }
        final double metres = unit.toMetres(toDisplayUnits(mapDistance));
        final Unit best = promote(metres, unit.getFamily());
        return formatNumber(best.fromMetres(metres)) + " " + best.getSymbol();
    }

    /// The largest unit in `family` that leaves `metres` at a value of
    /// one or more, falling back to the family's smallest unit for distances below
    /// all of them.
    static Unit promote(final double metres, final Family family) {
        final double abs = Math.abs(metres);
        Unit smallest = null;
        Unit best = null;
        for (final Unit candidate : Unit.values()) {
            if (candidate.getFamily() != family) {
                continue;
            }
            if (smallest == null || candidate.getMetresPerUnit() < smallest.getMetresPerUnit()) {
                smallest = candidate;
            }
            if (abs >= candidate.getMetresPerUnit()
                && (best == null || candidate.getMetresPerUnit() > best.getMetresPerUnit())) {
                best = candidate;
            }
        }
        return best != null
                ? best
                : smallest;
    }

    /// Rounds to [#SIGNIFICANT_FIGURES] significant figures and renders
    /// without trailing zeros or an exponent — `"1.2"`, `"850"`,
    /// `"0.05"`.
    ///
    /// Whole units are never rounded away: `5279` renders as
    /// `"5279"`, not `"5280"`. Significant figures here buy back
    /// decimal places on small values; they must not throw away precision the
    /// reader can see the point of.
    ///
    /// Assembles the digits from a `long` rather than going through
    /// [Double#toString], which would leak binary-representation noise
    /// (`"1.2000000000000002"`) and scientific notation into the UI.
    ///
    /// Public because unit-less numbers need the same treatment: the Settings
    /// tab renders the stored scale factor with it.
    ///
    /// @param value the number to render
    /// @return the rendered number, without a unit
    public static String formatNumber(final double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        final double abs = Math.abs(value);
        if (abs == 0) {
            return "0";
        }
        if (abs >= MAX_EXACT_MAGNITUDE) {
            return String.valueOf(value);
        }

        // Decimal places that leave SIGNIFICANT_FIGURES significant digits:
        // 850 -> 0, 12.5 -> 1, 1.23 -> 2, 0.0123 -> 4. Capped so a tiny value
        // cannot ask for an unrenderable number of places.
        final int exponent = (int) Math.floor(Math.log10(abs));
        return formatToDecimals(value, Math.max(0, Math.min(9, SIGNIFICANT_FIGURES - 1 - exponent)));
    }

    /// Renders a number for an *editable* field: fixed precision rather
    /// than significant figures, without trailing zeros or an exponent.
    ///
    /// [#formatNumber] must not be used for this. Rounding to three
    /// significant figures turns a position of 1234.5 into "1230", so opening a
    /// dialog and pressing OK without touching anything would move the object
    /// several metres. Fixed decimals keep every digit the user could act on;
    /// [#INPUT_DECIMALS] places is a tenth of a millimetre.
    ///
    /// @param value the number to render
    /// @return the rendered number, without a unit
    public static String formatForInput(final double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        if (value == 0) {
            return "0";
        }
        if (Math.abs(value) >= MAX_EXACT_MAGNITUDE) {
            return String.valueOf(value);
        }
        return formatToDecimals(value, INPUT_DECIMALS);
    }

    /// Rounds to `decimals` places and assembles the digits from a
    /// `long`, so no binary-representation noise or scientific notation can
    /// reach the UI.
    ///
    /// Falls back to [String#valueOf(double)] when the scaled value would not fit
    /// in a `long`. The bound is computed from `decimals` rather than being a
    /// fixed number, because it depends on it: at `decimals = 4` the limit is about
    /// `9.2234e14`, at `decimals = 0` it is about `9.2234e18`. A single
    /// hardcoded ceiling cannot be right for both, and [Math#round(double)] does not report
    /// saturation — it silently returns [Long#MAX_VALUE], which the digit assembly
    /// below would then format as though it were the user's number.
    private static String formatToDecimals(final double value, final int decimals) {
        final double abs = Math.abs(value);
        final double multiplier = Math.pow(10, decimals);
        if (abs > Long.MAX_VALUE / multiplier) {
            return String.valueOf(value);
        }
        final long scaled = Math.round(abs * multiplier);
        final long divisor = (long) multiplier;
        final long whole = scaled / divisor;
        final long fraction = scaled % divisor;

        final StringBuilder sb = new StringBuilder();
        if (value < 0) {
            sb.append('-');
        }
        sb.append(whole);
        if (fraction > 0) {
            // Left-pad the fraction to `decimals` digits, then drop trailing
            // zeros: 5 with 3 decimals is ".005", not ".5".
            final StringBuilder frac = new StringBuilder(String.valueOf(fraction));
            while (frac.length() < decimals) {
                frac.insert(0, '0');
            }
            while (!frac.isEmpty() && frac.charAt(frac.length() - 1) == '0') {
                frac.setLength(frac.length() - 1);
            }
            if (!frac.isEmpty()) {
                sb.append('.').append(frac);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------
    // Calibration
    // ------------------------------------------------------------------------

    /// Derives a scale from a measured line: the user drags across something whose
    /// real length they know and types that length in.
    ///
    /// @param mapLength  the length of the drawn line in map units; must be finite
    ///         and positive
    /// @param realLength the real-world length the user typed; must be finite and
    ///         positive
    /// @param enteredIn  the unit the user typed the length in
    /// @param displayIn  the unit the map should be displayed in afterwards —
    ///         usually `enteredIn`, but they can differ
    /// @return the calibrated units, or `null` if either length is not a
    ///         usable positive number (the caller shows a validation message rather than
    ///         writing a scale that would blank the canvas)
    public static FloorMapMeasurementUnits calibrate(final double mapLength,
                                                     final double realLength,
                                                     final Unit enteredIn,
                                                     final Unit displayIn) {
        if (enteredIn == null || displayIn == null
            || isUnusableLength(mapLength) || isUnusableLength(realLength)) {
            return null;
        }
        // The real length in the display unit, per map unit.
        final double realInDisplayUnit = displayIn.fromMetres(enteredIn.toMetres(realLength));
        final FloorMapMeasurementUnits calibrated =
                new FloorMapMeasurementUnits(displayIn, realInDisplayUnit / mapLength);
        return calibrated.checkUnitIsValid()
                ? calibrated
                : null;
    }

    /// `true` if `length` cannot be used as a calibration length — i.e. it is
    /// NaN, infinite, zero or negative. Reports the *unusable* case, so
    /// `calibrate` reads as "reject if unusable".
    private static boolean isUnusableLength(final double length) {
        return !Double.isFinite(length) || !(length > 0);
    }

    // ------------------------------------------------------------------------
    // Scale bar sizing
    // ------------------------------------------------------------------------

    /// The largest "nice" length not exceeding `maxLength` — one of
    /// 1, 2 or 5 times a power of ten.
    ///
    /// Used by the scale bar, which unlike the grid cannot settle for powers of
    /// ten alone: a bar allowed only to decade-step would spend most of the zoom
    /// range at a tenth of its available width. Expressed in whatever unit the
    /// caller is working in, so it serves display units and map units alike.
    ///
    /// @param maxLength the longest the bar may be; non-finite or non-positive
    ///         input yields `0`
    /// @return the chosen length, or `0` when none fits
    public static double niceRoundLength(final double maxLength) {
        if (!Double.isFinite(maxLength) || maxLength <= 0) {
            return 0;
        }
        final double decade = Math.pow(10, Math.floor(Math.log10(maxLength)));
        // Walk down the 1-2-5 series within this decade. Compared with a small
        // tolerance so a value that is a hair under its own decade through
        // floating-point error still picks that decade.
        final double[] steps = {5, 2, 1};
        for (final double step : steps) {
            final double candidate = step * decade;
            if (candidate <= maxLength * (1 + 1e-9)) {
                return candidate;
            }
        }
        return decade;
    }

    // ------------------------------------------------------------------------
    // Equality
    // ------------------------------------------------------------------------

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FloorMapMeasurementUnits that = (FloorMapMeasurementUnits) o;
        return Double.compare(unitsPerMapUnit, that.unitsPerMapUnit) == 0
               && unit == that.unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, unitsPerMapUnit);
    }

    @Override
    public String toString() {
        return "FloorMapMeasurementUnits{"
               + "unit=" + unit
               + ", unitsPerMapUnit=" + unitsPerMapUnit
               + '}';
    }
}
