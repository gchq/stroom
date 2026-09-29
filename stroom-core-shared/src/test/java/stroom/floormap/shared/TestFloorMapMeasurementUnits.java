/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapMeasurementUnits {

    private static FloorMapMeasurementUnits metres(final double unitsPerMapUnit) {
        return new FloorMapMeasurementUnits(Unit.METRE, unitsPerMapUnit);
    }

    // ------------------------------------------------------------------------
    // Conversion
    // ------------------------------------------------------------------------

    @Test
    void testConvertsBothWays() {
        final FloorMapMeasurementUnits units = metres(0.5);

        assertThat(units.toDisplayUnits(10)).isCloseTo(5, within(1e-9));
        assertThat(units.toMapUnits(5)).isCloseTo(10, within(1e-9));
    }

    /// The calibration dialog lets a value be typed in a unit the map is not configured in.
    @Test
    void testConvertsFromAnotherUnit() {
        final FloorMapMeasurementUnits units = metres(1.0);

        // 10 ft is 3.048 m, and one map unit is one metre.
        assertThat(units.toMapUnits(10, Unit.FOOT)).isCloseTo(3.048, within(1e-9));
    }

    // ------------------------------------------------------------------------
    // Formatting and promotion
    // ------------------------------------------------------------------------

    @Test
    void testFormatsInConfiguredUnit() {
        assertThat(metres(1.0).format(850)).isEqualTo("850 m");
        assertThat(metres(1.0).format(12.5)).isEqualTo("12.5 m");
    }

    @Test
    void testAppliesScaleFactorBeforeFormatting() {
        // Half a metre per map unit, so ten map units is five metres.
        assertThat(metres(0.5).format(10)).isEqualTo("5 m");
        assertThat(metres(0.187).format(100)).isEqualTo("18.7 m");
    }

    @Test
    void testPromotesUpAtTheMetricBoundary() {
        assertThat(metres(1.0).format(999)).isEqualTo("999 m");
        assertThat(metres(1.0).format(1000)).isEqualTo("1 km");
        assertThat(metres(1.0).format(1200)).isEqualTo("1.2 km");
    }

    @Test
    void testPromotesDownForSmallMetricValues() {
        assertThat(metres(1.0).format(0.5)).isEqualTo("50 cm");
        assertThat(metres(1.0).format(0.005)).isEqualTo("5 mm");
    }

    /// Below the smallest unit in the family we stay there rather than inventing one.
    @Test
    void testFallsBackToSmallestUnitInFamily() {
        assertThat(metres(1.0).format(0.0001)).isEqualTo("0.1 mm");
    }

    @Test
    void testPromotesUpAtTheImperialBoundary() {
        final FloorMapMeasurementUnits feet = new FloorMapMeasurementUnits(Unit.FOOT, 1.0);

        assertThat(feet.format(900)).isEqualTo("900 ft");
        // 5280 ft is exactly one mile.
        assertThat(feet.format(5279)).isEqualTo("5279 ft");
        assertThat(feet.format(5280)).isEqualTo("1 mi");
        assertThat(feet.format(7392)).isEqualTo("1.4 mi");
    }

    /// Promotion must never cross families: an imperial map does not sprout millimetres.
    @Test
    void testPromotionStaysWithinItsFamily() {
        final FloorMapMeasurementUnits inches = new FloorMapMeasurementUnits(Unit.INCH, 1.0);

        assertThat(inches.format(0.5)).isEqualTo("0.5 in");
        assertThat(inches.format(24)).isEqualTo("2 ft");
    }

    @Test
    void testFormatsNegativeDistances() {
        assertThat(metres(1.0).format(-12.5)).isEqualTo("-12.5 m");
    }

    @Test
    void testFormatsZero() {
        assertThat(metres(1.0).format(0)).isEqualTo("0 mm");
    }

    // ------------------------------------------------------------------------
    // Uncalibrated documents
    // ------------------------------------------------------------------------

    /// A map unit is an internal abstraction and is never shown to a user, so an
    /// uncalibrated map measures in the default of one centimetre per map unit
    /// rather than in bare "map units".
    @Test
    void testUncalibratedMapsMeasureInTheDefaultScale() {
        assertThat(FloorMapMeasurementUnits.DEFAULT.getUnit()).isEqualTo(Unit.CENTIMETRE);
        assertThat(FloorMapMeasurementUnits.DEFAULT.getUnitsPerMapUnit()).isEqualTo(1.0);

        assertThat(FloorMapMeasurementUnits.format(null, 40)).isEqualTo("40 cm");
        assertThat(FloorMapMeasurementUnits.format(null, 1250)).isEqualTo("12.5 m");
    }

    /// No surface may render a bare number, or the units in which map space is counted.
    @Test
    void testNothingEverFormatsWithoutARealUnit() {
        for (final double distance : new double[]{0, 0.5, 1, 10, 1000, 123456}) {
            for (final FloorMapMeasurementUnits units :
                    new FloorMapMeasurementUnits[]{null, metres(0), metres(1), metres(0.187)}) {
                final String formatted = FloorMapMeasurementUnits.format(units, distance);

                assertThat(formatted)
                        .as("format(%s, %s)", units, distance)
                        .doesNotContain("map unit")
                        .matches(".*[0-9] (mm|cm|m|km|in|ft|mi)$");
            }
        }
    }

    /// The static entry point every surface uses: null and unusable units both
    /// fall back to the default scale, so no caller carries its own null branch.
    @Test
    void testStaticFormatFallsBackForNullOrInvalidUnits() {
        assertThat(FloorMapMeasurementUnits.format(null, 10)).isEqualTo("10 cm");
        assertThat(FloorMapMeasurementUnits.format(metres(0), 10)).isEqualTo("10 cm");
        assertThat(FloorMapMeasurementUnits.format(metres(Double.NaN), 10)).isEqualTo("10 cm");
        assertThat(FloorMapMeasurementUnits.format(metres(1.0), 10)).isEqualTo("10 m");
    }

    @Test
    void testOrDefaultResolvesUnusableUnits() {
        assertThat(FloorMapMeasurementUnits.orDefault(null))
                .isEqualTo(FloorMapMeasurementUnits.DEFAULT);
        assertThat(FloorMapMeasurementUnits.orDefault(metres(0)))
                .isEqualTo(FloorMapMeasurementUnits.DEFAULT);
        assertThat(FloorMapMeasurementUnits.orDefault(metres(2))).isEqualTo(metres(2));
    }

    // ------------------------------------------------------------------------
    // Number formatting
    // ------------------------------------------------------------------------

    @Test
    void testStripsTrailingZeros() {
        assertThat(FloorMapMeasurementUnits.formatNumber(1.50)).isEqualTo("1.5");
        assertThat(FloorMapMeasurementUnits.formatNumber(2.0)).isEqualTo("2");
        assertThat(FloorMapMeasurementUnits.formatNumber(0.10)).isEqualTo("0.1");
    }

    /// Left-padding matters: 5 thousandths is 0.005, not 0.5.
    @Test
    void testPadsFractionalDigits() {
        assertThat(FloorMapMeasurementUnits.formatNumber(0.005)).isEqualTo("0.005");
        assertThat(FloorMapMeasurementUnits.formatNumber(2.05)).isEqualTo("2.05");
        assertThat(FloorMapMeasurementUnits.formatNumber(10.05)).isEqualTo("10.1");
    }

    @Test
    void testRoundsToThreeSignificantFigures() {
        assertThat(FloorMapMeasurementUnits.formatNumber(1.23456)).isEqualTo("1.23");
        assertThat(FloorMapMeasurementUnits.formatNumber(12.3456)).isEqualTo("12.3");
        assertThat(FloorMapMeasurementUnits.formatNumber(0.00123456)).isEqualTo("0.00123");
    }

    /// Whole units are never rounded away — the reader can see those digits.
    @Test
    void testKeepsWholeUnitPrecision() {
        assertThat(FloorMapMeasurementUnits.formatNumber(5279)).isEqualTo("5279");
        assertThat(FloorMapMeasurementUnits.formatNumber(123456)).isEqualTo("123456");
    }

    /// No binary noise ("1.2000000000000002") and no exponent leaks into the UI.
    @Test
    void testAvoidsFloatingPointNoise() {
        assertThat(FloorMapMeasurementUnits.formatNumber(0.1 + 0.2)).isEqualTo("0.3");
        assertThat(FloorMapMeasurementUnits.formatNumber(0.0000001)).doesNotContain("E");
    }

    @Test
    void testFormatsNonFiniteWithoutThrowing() {
        assertThat(FloorMapMeasurementUnits.formatNumber(Double.NaN)).isEqualTo("NaN");
        assertThat(metres(1.0).format(Double.POSITIVE_INFINITY)).contains("Infinity");
    }

    // ------------------------------------------------------------------------
    // Validity
    // ------------------------------------------------------------------------

    /// A zero or non-finite factor produces NaN grid spacing, which renders an
    /// entirely blank canvas with no error — hence the guard.
    @Test
    void testRejectsUnusableScaleFactors() {
        assertThat(metres(0).checkUnitIsValid()).isFalse();
        assertThat(metres(-1).checkUnitIsValid()).isFalse();
        assertThat(metres(Double.NaN).checkUnitIsValid()).isFalse();
        assertThat(metres(Double.POSITIVE_INFINITY).checkUnitIsValid()).isFalse();
        assertThat(new FloorMapMeasurementUnits(null, 1).checkUnitIsValid()).isFalse();
        assertThat(metres(0.187).checkUnitIsValid()).isTrue();
    }

    // ------------------------------------------------------------------------
    // Calibration
    // ------------------------------------------------------------------------

    @Test
    void testCalibrateDerivesTheScaleFactor() {
        // A line 100 map units long spans 18.7 m in the real world.
        final FloorMapMeasurementUnits units =
                FloorMapMeasurementUnits.calibrate(100, 18.7, Unit.METRE, Unit.METRE);

        assertThat(units).isNotNull();
        assertThat(units.getUnit()).isEqualTo(Unit.METRE);
        assertThat(units.getUnitsPerMapUnit()).isCloseTo(0.187, within(1e-9));
    }

    /// Calibrating then converting back must return the line we measured.
    @Test
    void testCalibrateRoundTrips() {
        final FloorMapMeasurementUnits units =
                FloorMapMeasurementUnits.calibrate(240, 18.7, Unit.METRE, Unit.METRE);

        assertThat(units).isNotNull();
        assertThat(units.toMapUnits(18.7)).isCloseTo(240, within(1e-6));
        assertThat(units.format(240)).isEqualTo("18.7 m");
    }

    /// The typed unit and the display unit need not match.
    @Test
    void testCalibrateConvertsBetweenUnits() {
        // 10 ft measured, but the map is to be shown in metres.
        final FloorMapMeasurementUnits units =
                FloorMapMeasurementUnits.calibrate(100, 10, Unit.FOOT, Unit.METRE);

        assertThat(units).isNotNull();
        assertThat(units.getUnit()).isEqualTo(Unit.METRE);
        assertThat(units.getUnitsPerMapUnit()).isCloseTo(0.03048, within(1e-9));
    }

    /// Rather than writing a scale that would blank the canvas, an unusable
    /// measurement yields null for the caller to report.
    @Test
    void testCalibrateRejectsUnusableInput() {
        assertThat(FloorMapMeasurementUnits.calibrate(0, 10, Unit.METRE, Unit.METRE)).isNull();
        assertThat(FloorMapMeasurementUnits.calibrate(100, 0, Unit.METRE, Unit.METRE)).isNull();
        assertThat(FloorMapMeasurementUnits.calibrate(100, -5, Unit.METRE, Unit.METRE)).isNull();
        assertThat(FloorMapMeasurementUnits.calibrate(Double.NaN, 10, Unit.METRE, Unit.METRE))
                .isNull();
        assertThat(FloorMapMeasurementUnits.calibrate(100, 10, null, Unit.METRE)).isNull();
    }

    // ------------------------------------------------------------------------
    // Scale bar sizing
    // ------------------------------------------------------------------------

    @Test
    void testNiceRoundLengthWalksTheOneTwoFiveSeries() {
        assertThat(FloorMapMeasurementUnits.niceRoundLength(120)).isCloseTo(100, within(1e-9));
        assertThat(FloorMapMeasurementUnits.niceRoundLength(250)).isCloseTo(200, within(1e-9));
        assertThat(FloorMapMeasurementUnits.niceRoundLength(99)).isCloseTo(50, within(1e-9));
        assertThat(FloorMapMeasurementUnits.niceRoundLength(1)).isCloseTo(1, within(1e-9));
        assertThat(FloorMapMeasurementUnits.niceRoundLength(0.4)).isCloseTo(0.2, within(1e-9));
    }

    /// An exact decade must pick itself, not the next one down.
    @Test
    void testNiceRoundLengthKeepsExactDecades() {
        assertThat(FloorMapMeasurementUnits.niceRoundLength(100)).isCloseTo(100, within(1e-9));
        assertThat(FloorMapMeasurementUnits.niceRoundLength(500)).isCloseTo(500, within(1e-9));
    }

    @Test
    void testNiceRoundLengthRejectsUnusableInput() {
        assertThat(FloorMapMeasurementUnits.niceRoundLength(0)).isZero();
        assertThat(FloorMapMeasurementUnits.niceRoundLength(-5)).isZero();
        assertThat(FloorMapMeasurementUnits.niceRoundLength(Double.NaN)).isZero();
    }

    // ------------------------------------------------------------------------
    // Value semantics
    // ------------------------------------------------------------------------

    @Test
    void testEqualityIsByValue() {
        assertThat(metres(0.5)).isEqualTo(metres(0.5));
        assertThat(metres(0.5)).hasSameHashCodeAs(metres(0.5));
        assertThat(metres(0.5)).isNotEqualTo(metres(0.6));
        assertThat(metres(0.5)).isNotEqualTo(new FloorMapMeasurementUnits(Unit.FOOT, 0.5));
    }

    @Test
    void testWithersKeepTheOtherField() {
        assertThat(metres(0.5).withUnit(Unit.FOOT))
                .isEqualTo(new FloorMapMeasurementUnits(Unit.FOOT, 0.5));
        assertThat(metres(0.5).withUnitsPerMapUnit(2))
                .isEqualTo(metres(2));
    }

    @Test
    void testUnitLabelsAreUserFacing() {
        assertThat(Unit.METRE.getLabel()).isEqualTo("Metres (m)");
        assertThat(Unit.FOOT.getLabel()).isEqualTo("Feet (ft)");
    }

    // ------------------------------------------------------------------------
    // Gesture readouts
    // ------------------------------------------------------------------------

    /// The size shown while an object is being resized.
    @Test
    void testFormatsSize() {
        assertThat(FloorMapMeasurementUnits.formatSize(metres(1), 2.4, 1.1))
                .isEqualTo("2.4 m × 1.1 m");
        // Each dimension promotes on its own merits — a long thin wall is
        // legitimately "12.5 m × 20 cm".
        assertThat(FloorMapMeasurementUnits.formatSize(metres(1), 12.5, 0.2))
                .isEqualTo("12.5 m × 20 cm");
    }

    /// An uncalibrated map still reports a real size, at the default scale.
    @Test
    void testFormatsSizeWithoutUnits() {
        assertThat(FloorMapMeasurementUnits.formatSize(null, 240, 110))
                .isEqualTo("2.4 m × 1.1 m");
    }

    /// The position shown while an object is being moved, with both axes named.
    @Test
    void testFormatsPosition() {
        assertThat(FloorMapMeasurementUnits.formatPosition(metres(1), 4.5, 2.1))
                .isEqualTo("X 4.5 m, Y 2.1 m");
        // Map space has an origin, so negative coordinates are ordinary.
        assertThat(FloorMapMeasurementUnits.formatPosition(metres(1), -4.5, 0))
                .isEqualTo("X -4.5 m, Y 0 mm");
    }

    @Test
    void testFormatsPositionWithoutUnits() {
        assertThat(FloorMapMeasurementUnits.formatPosition(null, 450, 210))
                .isEqualTo("X 4.5 m, Y 2.1 m");
    }

    // ------------------------------------------------------------------------
    // Editable fields
    // ------------------------------------------------------------------------

    /// A specific unit, not the best-fitting one: an input box that changed
    /// between mm and km as the user typed would be unusable.
    @Test
    void testConvertsToASpecificUnit() {
        // The default scale: 450 map units is 450 cm, i.e. 4.5 m.
        assertThat(FloorMapMeasurementUnits.DEFAULT.toUnit(450, Unit.METRE))
                .isCloseTo(4.5, within(1e-9));
        assertThat(metres(0.5).toUnit(10, Unit.METRE)).isCloseTo(5, within(1e-9));
        assertThat(metres(1).toUnit(1, Unit.CENTIMETRE)).isCloseTo(100, within(1e-9));
    }

    /// toUnit and toMapUnits must be exact inverses, or a dialog edit drifts.
    @Test
    void testToUnitRoundTripsWithToMapUnits() {
        for (final FloorMapMeasurementUnits units :
                new FloorMapMeasurementUnits[]{FloorMapMeasurementUnits.DEFAULT,
                        metres(0.187), metres(1), metres(1000)}) {
            for (final double mapValue : new double[]{0, 1, 4.5, -12.25, 98765.4321}) {
                final double asMetres = units.toUnit(mapValue, Unit.METRE);

                assertThat(units.toMapUnits(asMetres, Unit.METRE))
                        .as("round trip of %s through %s", mapValue, units)
                        .isCloseTo(mapValue, within(1e-6));
            }
        }
    }

    /// Significant figures would destroy an editable value — 1234.5 would come
    /// back as 1230, moving the object several metres on OK.
    @Test
    void testInputFormattingKeepsEveryActionableDigit() {
        assertThat(FloorMapMeasurementUnits.formatForInput(1234.5)).isEqualTo("1234.5");
        assertThat(FloorMapMeasurementUnits.formatNumber(1234.5)).isEqualTo("1235");
    }

    @Test
    void testInputFormattingIsCleanButPrecise() {
        assertThat(FloorMapMeasurementUnits.formatForInput(4.5)).isEqualTo("4.5");
        assertThat(FloorMapMeasurementUnits.formatForInput(4.0)).isEqualTo("4");
        assertThat(FloorMapMeasurementUnits.formatForInput(0)).isEqualTo("0");
        assertThat(FloorMapMeasurementUnits.formatForInput(-2.25)).isEqualTo("-2.25");
        // Rounded at a tenth of a millimetre, and never in scientific notation.
        assertThat(FloorMapMeasurementUnits.formatForInput(0.000049)).isEqualTo("0");
        assertThat(FloorMapMeasurementUnits.formatForInput(0.0001)).isEqualTo("0.0001");
        assertThat(FloorMapMeasurementUnits.formatForInput(0.1 + 0.2)).isEqualTo("0.3");
    }

    /// Nothing in the window below [Long#MAX_VALUE] may emit corrupted digits.
    ///
    /// `formatForInput` rounds into a `long` after multiplying by 1e4, so the
    /// product saturates from about `9.2234e14`. The magnitude ceiling was set at
    /// `1e15` — above the saturation point — so this window assembled its digits from
    /// `Long.MAX_VALUE` and `formatForInput(9.5e14)` returned
    /// `"922337203685477.5807"`. No test covered it, and the value is one a bad scale
    /// factor can reach even though no real floor plan can.
    ///
    /// The assertion is a round-trip rather than an exact string, because what matters is
    /// that the text means the number — whether it is rendered as assembled digits or handed
    /// to `String.valueOf` is an implementation choice either side of the bound.
    @Test
    void testFormatForInputNeverEmitsCorruptDigitsNearTheLongLimit() {
        final double[] values = {
                9.0e14,      // below the bound: digits assembled
                9.2233e14,   // just below
                9.2234e14,   // just above: must fall back
                9.5e14,      // the value that used to return Long.MAX_VALUE's digits
                9.99e14};

        for (final double value : values) {
            final String text = FloorMapMeasurementUnits.formatForInput(value);
            assertThat(text)
                    .as("formatForInput(%s) must not leak Long.MAX_VALUE digits", value)
                    .doesNotContain("9223372036854775");
            assertThat(Double.parseDouble(text))
                    .as("formatForInput(%s) must round-trip", value)
                    .isEqualTo(value);
        }
    }

    /// A characterisation guard, not a proof: `formatNumber` was never affected by the
    /// overflow bug, because its decimal count collapses to 0 for large magnitudes and so its
    /// safe bound is around `9.2234e18`. This test passes with or without the guard in
    /// `formatToDecimals`.
    ///
    /// It is here because that safety is incidental — it falls out of the
    /// significant-figures arithmetic rather than being intended. Raising
    /// `SIGNIFICANT_FIGURES`, or changing how the decimal count is derived, could push
    /// this formatter into the same window without anything else noticing.
    @Test
    void testFormatNumberRoundTripsNearTheLongLimit() {
        for (final double value : new double[]{9.0e14, 9.5e14, 9.99e14}) {
            assertThat(Double.parseDouble(FloorMapMeasurementUnits.formatNumber(value)))
                    .as("formatNumber(%s) must round-trip", value)
                    .isEqualTo(value);
        }
    }
}
