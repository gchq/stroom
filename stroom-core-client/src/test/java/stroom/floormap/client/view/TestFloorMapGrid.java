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

import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapGrid {

    private static FloorMapMeasurementUnits metres(final double unitsPerMapUnit) {
        return new FloorMapMeasurementUnits(Unit.METRE, unitsPerMapUnit);
    }


    @Test
    void majorSpacingStaysInComfortableRange() {
        // Simulate various effective scales (matrixScale × userZoom)
        // and verify major grid spacing produces screen pixels within
        // the [TARGET_MIN_PX, TARGET_MAX_PX] comfort range.
        for (final double effectiveScale : new double[]{0.5, 1, 5, 20, 50, 200, 1000}) {
            final double[] params = FloorMapGrid.computeGridParams(effectiveScale);
            final double majorWorldSpacing = params[0];
            final double screenPx = majorWorldSpacing * effectiveScale;

            assertThat(screenPx)
                    .as("effectiveScale=%s → majorWorldSpacing=%s → screenPx=%s",
                            effectiveScale, majorWorldSpacing, screenPx)
                    .isGreaterThanOrEqualTo(FloorMapGrid.TARGET_MIN_PX)
                    .isLessThanOrEqualTo(FloorMapGrid.TARGET_MAX_PX);
        }
    }

    @Test
    void minorOpacityIsZeroAtDecadeFloor() {
        // At exactly TARGET_MIN_PX screen spacing, minor opacity should be ~0.
        // effectiveScale = 4.0 → majorWorldSpacing = 10 → screenPx = 40 = TARGET_MIN_PX
        final double[] params = FloorMapGrid.computeGridParams(4.0);
        final double minorOpacity = params[1];
        assertThat(minorOpacity).isLessThan(0.01);
    }

    @Test
    void minorOpacityIncreasesWithZoomWithinSameDecade() {
        // Two effective scales that produce the same decade but different
        // positions within it. Higher scale → larger screenPx → higher opacity.
        // effectiveScale=5 → majorWorldSpacing=10 → screenPx=50 (low in decade)
        // effectiveScale=30 → majorWorldSpacing=10 → screenPx=300 (high in decade)
        final double[] paramsLow = FloorMapGrid.computeGridParams(5.0);
        final double[] paramsHigh = FloorMapGrid.computeGridParams(30.0);

        // Both should select majorWorldSpacing = 10
        assertThat(paramsLow[0])
                .as("Low-zoom major spacing")
                .isEqualTo(10.0);
        assertThat(paramsHigh[0])
                .as("High-zoom major spacing")
                .isEqualTo(10.0);

        // Higher zoom should have higher minor opacity
        assertThat(paramsHigh[1])
                .as("Minor opacity at higher zoom")
                .isGreaterThan(paramsLow[1]);
    }

    @Test
    void decadesAreAlwaysPowerOf10() {
        for (final double effectiveScale : new double[]{0.1, 1, 10, 100, 1000}) {
            final double[] params = FloorMapGrid.computeGridParams(effectiveScale);
            final double majorWorldSpacing = params[0];
            final double log = Math.log10(majorWorldSpacing);
            assertThat(Math.abs(log - Math.round(log)))
                    .as("majorWorldSpacing=%s should be a power of 10", majorWorldSpacing)
                    .isLessThan(1e-9);
        }
    }

    @Test
    void minorOpacityNeverExceedsMaximum() {
        // At very high zoom the minor opacity should be clamped
        for (final double effectiveScale : new double[]{1000, 5000, 100000}) {
            final double[] params = FloorMapGrid.computeGridParams(effectiveScale);
            final double minorOpacity = params[1];
            assertThat(minorOpacity)
                    .as("Minor opacity at effectiveScale=%s", effectiveScale)
                    .isLessThanOrEqualTo(0.25 + 1e-9);  // MINOR_MAX_OPACITY = 0.25
        }
    }

    @Test
    void veryLowScaleStillProducesValidSpacing() {
        // Extremely zoomed out — should still produce a valid power-of-10 spacing
        final double[] params = FloorMapGrid.computeGridParams(0.001);
        final double majorWorldSpacing = params[0];
        assertThat(majorWorldSpacing).isGreaterThan(0);
        final double screenPx = majorWorldSpacing * 0.001;
        assertThat(screenPx)
                .isGreaterThanOrEqualTo(FloorMapGrid.TARGET_MIN_PX)
                .isLessThanOrEqualTo(FloorMapGrid.TARGET_MAX_PX);
    }

    @Test
    void extremeScalesProduceValidResults() {
        // The algorithm should work across the full zoom clamp range (1e-12 to 1e12)
        // without producing NaN or Infinity.
        for (final double effectiveScale : new double[]{1e-12, 1e-8, 1e-4, 1e4, 1e8, 1e12}) {
            final double[] params = FloorMapGrid.computeGridParams(effectiveScale);
            final double majorWorldSpacing = params[0];
            final double minorOpacity = params[1];

            assertThat(majorWorldSpacing)
                    .as("majorWorldSpacing at effectiveScale=%s", effectiveScale)
                    .isGreaterThan(0)
                    .isFinite();
            assertThat(minorOpacity)
                    .as("minorOpacity at effectiveScale=%s", effectiveScale)
                    .isGreaterThanOrEqualTo(0.0)
                    .isFinite();

            // Screen pixels should still be in the comfort range
            final double screenPx = majorWorldSpacing * effectiveScale;
            assertThat(screenPx)
                    .as("screenPx at effectiveScale=%s", effectiveScale)
                    .isGreaterThanOrEqualTo(FloorMapGrid.TARGET_MIN_PX)
                    .isLessThanOrEqualTo(FloorMapGrid.TARGET_MAX_PX);
        }
    }

    @Test
    void zeroScaleReturnsDefaults() {
        final double[] params = FloorMapGrid.computeGridParams(0.0);
        assertThat(params[0]).isEqualTo(1.0);
        assertThat(params[1]).isEqualTo(0.0);
    }

    @Test
    void negativeScaleReturnsDefaults() {
        final double[] params = FloorMapGrid.computeGridParams(-5.0);
        assertThat(params[0]).isEqualTo(1.0);
        assertThat(params[1]).isEqualTo(0.0);
    }

    @Test
    void nanScaleReturnsDefaults() {
        final double[] params = FloorMapGrid.computeGridParams(Double.NaN);
        assertThat(params[0]).isEqualTo(1.0);
        assertThat(params[1]).isEqualTo(0.0);
    }

    @Test
    void infiniteScaleReturnsDefaults() {
        final double[] params = FloorMapGrid.computeGridParams(Double.POSITIVE_INFINITY);
        assertThat(params[0]).isEqualTo(1.0);
        assertThat(params[1]).isEqualTo(0.0);
    }

    @Test
    void gridAdaptsCorrectlyAcrossMultipleDecades() {
        // Walk through 6 orders of magnitude and verify each decade transition
        // produces a new power-of-10 major spacing.
        double previousSpacing = Double.MAX_VALUE;
        for (int exp = -3; exp <= 3; exp++) {
            final double effectiveScale = Math.pow(10, exp);
            final double[] params = FloorMapGrid.computeGridParams(effectiveScale);
            final double majorWorldSpacing = params[0];

            // As effectiveScale increases, majorWorldSpacing should decrease
            // (finer grid at higher zoom)
            if (exp > -3) {
                assertThat(majorWorldSpacing)
                        .as("Spacing should decrease as zoom increases (exp=%s)", exp)
                        .isLessThanOrEqualTo(previousSpacing);
            }
            previousSpacing = majorWorldSpacing;
        }
    }

    @Test
    void minorWorldSpacingIsOneTenthOfMajor() {
        // Minor spacing is always 1/10th of the adaptive major spacing.
        for (final double effectiveScale : new double[]{0.001, 0.3, 1, 2, 5, 30, 1000}) {
            final double expected =
                    FloorMapGrid.computeGridParams(effectiveScale)[0] / 10.0;
            assertThat(FloorMapGrid.minorWorldSpacing(effectiveScale))
                    .as("minorWorldSpacing at effectiveScale=%s", effectiveScale)
                    .isEqualTo(expected);
        }
    }

    @Test
    void fiveMinorDivisionsEqualsFiftyAtDefaultZoom() {
        // At the default zoom (effectiveScale = 1) the major division is 100
        // world units, so a minor division is 10 and five of them is 50 — the
        // duplicate-object offset, derived from the grid so it adapts to zoom
        // rather than being a fixed distance.
        assertThat(FloorMapGrid.minorWorldSpacing(1.0)).isEqualTo(10.0);
        assertThat(5.0 * FloorMapGrid.minorWorldSpacing(1.0)).isEqualTo(50.0);
    }

    // ------------------------------------------------------------------------
    // Decade selection in display units
    // ------------------------------------------------------------------------

    /// The decade is chosen so the label is a round number of *display* units.
    /// At 0.5 m per map unit and 100 % zoom, a 100-map-unit division would be
    /// "50 m" — so the grid instead picks a 200-unit division, which is 100 m.
    @Test
    void decadeIsAPowerOfTenInDisplayUnits() {
        for (final double unitsPerMapUnit : new double[]{0.5, 0.187, 2.5, 1000}) {
            for (final double effectiveScale : new double[]{0.05, 1, 7, 250}) {
                final double majorWorldSpacing =
                        FloorMapGrid.computeGridParams(effectiveScale, unitsPerMapUnit)[0];
                final double displaySpacing = majorWorldSpacing * unitsPerMapUnit;
                final double log = Math.log10(displaySpacing);

                assertThat(Math.abs(log - Math.round(log)))
                        .as("displaySpacing=%s (scale=%s, factor=%s) should be a power of 10",
                                displaySpacing, effectiveScale, unitsPerMapUnit)
                        .isLessThan(1e-9);
            }
        }
    }

    /// Whatever the scale factor, the grid stays a comfortable size on screen.
    @Test
    void displayUnitDecadesStayInComfortableRange() {
        for (final double unitsPerMapUnit : new double[]{0.001, 0.187, 1, 3.7, 5280}) {
            for (final double effectiveScale : new double[]{0.5, 1, 5, 200, 1000}) {
                final double majorWorldSpacing =
                        FloorMapGrid.computeGridParams(effectiveScale, unitsPerMapUnit)[0];
                final double screenPx = majorWorldSpacing * effectiveScale;

                assertThat(screenPx)
                        .as("screenPx at scale=%s, factor=%s", effectiveScale, unitsPerMapUnit)
                        .isGreaterThanOrEqualTo(FloorMapGrid.TARGET_MIN_PX)
                        .isLessThanOrEqualTo(FloorMapGrid.TARGET_MAX_PX);
            }
        }
    }

    /// An uncalibrated map's grid must be bit-for-bit what it always was.
    @Test
    void factorOfOneChangesNothing() {
        for (final double effectiveScale : new double[]{0.001, 0.3, 1, 2, 5, 30, 1000}) {
            assertThat(FloorMapGrid.computeGridParams(effectiveScale, 1.0))
                    .as("effectiveScale=%s", effectiveScale)
                    .isEqualTo(FloorMapGrid.computeGridParams(effectiveScale));
        }
    }

    /// A zero or non-finite factor would put NaN in every pattern coordinate, and
    /// an SVG with NaN coordinates renders nothing at all.
    @Test
    void unusableScaleFactorsFallBackToUnscaled() {
        for (final double factor : new double[]{0, -2, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThat(FloorMapGrid.computeGridParams(1.0, factor))
                    .as("factor=%s", factor)
                    .isEqualTo(FloorMapGrid.computeGridParams(1.0));
        }
    }

    /// The grid-relative helpers must agree with the drawn grid, or the initial
    /// pan inset and the duplicate-object nudge drift away from the lines.
    @Test
    void gridRelativeHelpersFollowTheSameDecade() {
        final FloorMapMeasurementUnits units = metres(0.5);
        for (final double effectiveScale : new double[]{0.3, 1, 5, 200}) {
            final double majorWorldSpacing =
                    FloorMapGrid.computeGridParams(effectiveScale, 0.5)[0];

            assertThat(FloorMapGrid.majorDivisionScreenPx(effectiveScale, units))
                    .as("majorDivisionScreenPx at effectiveScale=%s", effectiveScale)
                    .isEqualTo(majorWorldSpacing * effectiveScale);
            assertThat(FloorMapGrid.minorWorldSpacing(effectiveScale, units))
                    .as("minorWorldSpacing at effectiveScale=%s", effectiveScale)
                    .isEqualTo(majorWorldSpacing / 10.0);
        }
    }

    /// Passing no units is the same as the single-argument form.
    @Test
    void gridRelativeHelpersDefaultToUnscaled() {
        assertThat(FloorMapGrid.majorDivisionScreenPx(1.0, null))
                .isEqualTo(FloorMapGrid.majorDivisionScreenPx(1.0));
        assertThat(FloorMapGrid.minorWorldSpacing(1.0, null))
                .isEqualTo(FloorMapGrid.minorWorldSpacing(1.0));
    }

    // ------------------------------------------------------------------------
    // Scale bar
    // ------------------------------------------------------------------------

    /// The bar never exceeds the width it is given, and is never zero-length.
    @Test
    void scaleBarFitsTheSpaceAvailable() {
        for (final double effectiveScale : new double[]{0.01, 0.3, 1, 7, 250, 5000}) {
            for (final FloorMapMeasurementUnits units :
                    new FloorMapMeasurementUnits[]{null, metres(1), metres(0.187), metres(1000)}) {
                final double[] bar = FloorMapGrid.scaleBar(effectiveScale, 120, units);

                assertThat(bar[1])
                        .as("width at scale=%s, units=%s", effectiveScale, units)
                        .isGreaterThan(0)
                        .isLessThanOrEqualTo(120);
                assertThat(bar[0])
                        .as("map length at scale=%s, units=%s", effectiveScale, units)
                        .isGreaterThan(0);
            }
        }
    }

    /// The width drawn must be exactly what the labelled distance is worth.
    @Test
    void scaleBarWidthMatchesItsLabelledDistance() {
        final double[] bar = FloorMapGrid.scaleBar(2.0, 120, metres(0.5));

        // 120 px at 2 px per map unit is 60 map units, which is 30 m; the largest
        // 1-2-5 value that fits is 20 m — 40 units of map space, 80 px.
        assertThat(bar[0]).isEqualTo(40.0);
        assertThat(bar[1]).isEqualTo(80.0);
        assertThat(FloorMapMeasurementUnits.format(metres(0.5), bar[0])).isEqualTo("20 m");
    }

    /// An uncalibrated map's bar measures in the default scale, never in map units.
    @Test
    void scaleBarWithoutUnitsUsesTheDefaultScale() {
        final double[] bar = FloorMapGrid.scaleBar(1.0, 120, null);

        assertThat(bar[0]).isEqualTo(100.0);
        assertThat(bar[1]).isEqualTo(100.0);
        assertThat(FloorMapMeasurementUnits.format(null, bar[0])).isEqualTo("1 m");
    }

    @Test
    void scaleBarRejectsUnusableInput() {
        assertThat(FloorMapGrid.scaleBar(0, 120, null)).containsExactly(0.0, 0.0);
        assertThat(FloorMapGrid.scaleBar(1, 0, null)).containsExactly(0.0, 0.0);
        assertThat(FloorMapGrid.scaleBar(Double.NaN, 120, null)).containsExactly(0.0, 0.0);
        assertThat(FloorMapGrid.scaleBar(1, Double.POSITIVE_INFINITY, null))
                .containsExactly(0.0, 0.0);
    }

    @Test
    void majorDivisionScreenPxMatchesSpacingTimesScale() {
        // At the default zoom (effectiveScale = 1) the major division is 100
        // world units, which is 100 screen pixels — so half a division (the
        // default origin inset) is 50 px, i.e. the bottom-left corner reads as
        // (-50,-50) in map space.
        assertThat(FloorMapGrid.majorDivisionScreenPx(1.0)).isEqualTo(100.0);
        assertThat(FloorMapGrid.majorDivisionScreenPx(1.0) / 2.0).isEqualTo(50.0);

        // It always equals majorWorldSpacing × effectiveScale, so it tracks the
        // drawn grid at any zoom.
        for (final double effectiveScale : new double[]{0.001, 0.3, 2, 5, 30, 1000}) {
            final double expected =
                    FloorMapGrid.computeGridParams(effectiveScale)[0] * effectiveScale;
            assertThat(FloorMapGrid.majorDivisionScreenPx(effectiveScale))
                    .as("majorDivisionScreenPx at effectiveScale=%s", effectiveScale)
                    .isEqualTo(expected);
        }
    }
}
