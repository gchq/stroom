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

import stroom.test.common.TestUtil;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

/// Tests for [FloorMapTransformationMatrix] — construction, rotation,
/// inversion (including rejection of singular matrices), SVG formatting,
/// equality and JSON serialisation round-trip.
class TestFloorMapTransformationMatrix {

    private static final double TOLERANCE = 1e-9;

    @Test
    void testConstructionAndGetters() {
        final FloorMapTransformationMatrix matrix =
                new FloorMapTransformationMatrix(1.5, 2.5, 3.5, 4.5, 5.5, 6.5);

        assertThat(matrix.getA()).isEqualTo(1.5);
        assertThat(matrix.getB()).isEqualTo(2.5);
        assertThat(matrix.getC()).isEqualTo(3.5);
        assertThat(matrix.getD()).isEqualTo(4.5);
        assertThat(matrix.getE()).isEqualTo(5.5);
        assertThat(matrix.getF()).isEqualTo(6.5);
    }

    @Test
    void testIdentity() {
        final FloorMapTransformationMatrix identity = FloorMapTransformationMatrix.identity();

        // Applying the identity must leave a point unchanged.
        assertThat(apply(identity, 12.5, -7.25)).containsExactly(12.5, -7.25);
    }

    @Test
    void testToSvgMatrix() {
        final FloorMapTransformationMatrix matrix =
                new FloorMapTransformationMatrix(1.5, 2.5, 3.5, 4.5, 5.5, 6.5);

        assertThat(matrix.toSvgMatrix())
                .isEqualTo("matrix(1.5,2.5,3.5,4.5,5.5,6.5)");
    }

    @Test
    void testEqualsAndHashCode() {
        final FloorMapTransformationMatrix matrix1a =
                new FloorMapTransformationMatrix(1, 0, 0, 1, 10, 20);
        final FloorMapTransformationMatrix matrix1b =
                new FloorMapTransformationMatrix(1, 0, 0, 1, 10, 20);
        final FloorMapTransformationMatrix matrix2 =
                new FloorMapTransformationMatrix(1, 0, 0, 1, 10, 21);

        assertThat(matrix1a).isEqualTo(matrix1b);
        assertThat(matrix1a.hashCode()).isEqualTo(matrix1b.hashCode());
        assertThat(matrix1a).isNotEqualTo(matrix2);
    }

    // -----------------------------------------------------------------------
    // Rotation
    // -----------------------------------------------------------------------

    @Test
    void testRotateZeroDegrees() {
        final FloorMapTransformationMatrix rotation = FloorMapTransformationMatrix.rotate(0);

        // Component-wise rather than equals() — rotate(0) produces c = -0.0,
        // which Double.compare treats as distinct from 0.0.
        assertThat(rotation.getA()).isCloseTo(1, within(TOLERANCE));
        assertThat(rotation.getB()).isCloseTo(0, within(TOLERANCE));
        assertThat(rotation.getC()).isCloseTo(0, within(TOLERANCE));
        assertThat(rotation.getD()).isCloseTo(1, within(TOLERANCE));
        assertThat(rotation.getE()).isCloseTo(0, within(TOLERANCE));
        assertThat(rotation.getF()).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void testRotateNinetyDegrees() {
        final FloorMapTransformationMatrix rotation = FloorMapTransformationMatrix.rotate(90);

        // Counter-clockwise positive: (1, 0) maps to (0, 1).
        final double[] point = apply(rotation, 1, 0);
        assertThat(point[0]).isCloseTo(0, within(TOLERANCE));
        assertThat(point[1]).isCloseTo(1, within(TOLERANCE));
    }

    @Test
    void testRotateInverseMatchesNegativeRotation() {
        final FloorMapTransformationMatrix inverse =
                FloorMapTransformationMatrix.rotate(37).inverse();
        final FloorMapTransformationMatrix negativeRotation =
                FloorMapTransformationMatrix.rotate(-37);

        assertMatrixCloseTo(inverse, negativeRotation);
    }

    // -----------------------------------------------------------------------
    // Inversion
    // -----------------------------------------------------------------------

    @Test
    void testInverseOfTranslation() {
        final FloorMapTransformationMatrix inverse =
                new FloorMapTransformationMatrix(1, 0, 0, 1, 10, 20).inverse();

        assertMatrixCloseTo(inverse, new FloorMapTransformationMatrix(1, 0, 0, 1, -10, -20));
    }

    @Test
    void testInverseOfScale() {
        final FloorMapTransformationMatrix inverse =
                new FloorMapTransformationMatrix(4, 0, 0, 5, 0, 0).inverse();

        assertMatrixCloseTo(inverse, new FloorMapTransformationMatrix(0.25, 0, 0, 0.2, 0, 0));
    }

    /// The property the drag-and-drop maths relies on (FloorMapNotes.md
    /// section 3): transforming a point and then applying the inverse must
    /// recover the original point exactly (within floating-point tolerance).
    @TestFactory
    Stream<DynamicTest> testInverseRecoversOriginalPoint() {
        final double[] originalPoint = {12.5, -7.25};
        return TestUtil.buildDynamicTestStream()
                .withInputType(FloorMapTransformationMatrix.class)
                .withOutputType(double[].class)
                .withTestFunction(testCase -> {
                    final FloorMapTransformationMatrix matrix = testCase.getInput();
                    final double[] transformed = apply(matrix, originalPoint[0], originalPoint[1]);
                    return apply(matrix.inverse(), transformed[0], transformed[1]);
                })
                .withAssertions(outcome -> {
                    assertThat(outcome.getActualOutput()[0])
                            .isCloseTo(outcome.getExpectedOutput()[0], within(TOLERANCE));
                    assertThat(outcome.getActualOutput()[1])
                            .isCloseTo(outcome.getExpectedOutput()[1], within(TOLERANCE));
                })
                .addNamedCase("Identity",
                        FloorMapTransformationMatrix.identity(), originalPoint)
                .addNamedCase("Translation",
                        new FloorMapTransformationMatrix(1, 0, 0, 1, 100, -50), originalPoint)
                .addNamedCase("Uniform scale",
                        new FloorMapTransformationMatrix(2.5, 0, 0, 2.5, 0, 0), originalPoint)
                .addNamedCase("Rotation",
                        FloorMapTransformationMatrix.rotate(37), originalPoint)
                .addNamedCase("Shear",
                        new FloorMapTransformationMatrix(1, 0.5, 0.3, 1, 0, 0), originalPoint)
                .addNamedCase("Scale, rotate and translate",
                        new FloorMapTransformationMatrix(1.2, 0.9, -0.9, 1.2, 100, 50), originalPoint)
                .build();
    }

    /// A genuinely singular matrix cannot be inverted, and `inverse()` must
    /// say so rather than return a plausible-looking answer.
    ///
    /// This used to assert the opposite — that `inverse()` falls back to
    /// the identity. That fallback was the quiet middle link in a chain that
    /// ended in corrupted stored geometry: the identity is indistinguishable from
    /// a successful inversion, so callers converted coordinates through it,
    /// got their input back unchanged, and persisted it as though it had been
    /// transformed.
    @TestFactory
    Stream<DynamicTest> testInverseOfSingularMatrixThrows() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(FloorMapTransformationMatrix.class)
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    final FloorMapTransformationMatrix matrix = testCase.getInput();
                    assertThat(matrix.hasInverse())
                            .as("hasInverse must agree with inverse()")
                            .isFalse();
                    assertThatThrownBy(matrix::inverse)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("not invertible");
                    return Boolean.TRUE;
                })
                .withSimpleEqualityAssertion()
                .addNamedCase("Zero matrix",
                        new FloorMapTransformationMatrix(0, 0, 0, 0, 0, 0), Boolean.TRUE)
                .addNamedCase("Linearly dependent rows",
                        new FloorMapTransformationMatrix(2, 4, 1, 2, 5, 6), Boolean.TRUE)
                .addNamedCase("Zero scale on one axis",
                        new FloorMapTransformationMatrix(3, 0, 0, 0, 5, 6), Boolean.TRUE)
                .addNamedCase("NaN component",
                        new FloorMapTransformationMatrix(Double.NaN, 0, 0, 1, 0, 0), Boolean.TRUE)
                .addNamedCase("Infinite component",
                        new FloorMapTransformationMatrix(
                                Double.POSITIVE_INFINITY, 0, 0, 1, 0, 0), Boolean.TRUE)
                .build();
    }

    /// A very small *uniform* scale is invertible and must be inverted, not
    /// written off as singular.
    ///
    /// This is the case the old absolute tolerance (`|det| < 1e-9`) got
    /// wrong. The matrix below scales by one part in a million, giving a
    /// determinant of `1e-12` — small, but exactly representable and
    /// perfectly invertible. Asserting the real inverse here is what forces the
    /// singularity test to be *relative* to the magnitude of the terms
    /// rather than an absolute floor.
    @Test
    void testInverseOfVerySmallUniformScaleIsExact() {
        final FloorMapTransformationMatrix matrix =
                new FloorMapTransformationMatrix(1e-6, 0, 0, 1e-6, 5, 6);

        assertThat(matrix.hasInverse()).isTrue();

        final FloorMapTransformationMatrix inverse = matrix.inverse();
        // Inverse of scale(s) with translation t is scale(1/s) with translation -t/s.
        assertThat(inverse.getA()).isCloseTo(1e6, withinPercentage(1e-6));
        assertThat(inverse.getB()).isCloseTo(0, within(TOLERANCE));
        assertThat(inverse.getC()).isCloseTo(0, within(TOLERANCE));
        assertThat(inverse.getD()).isCloseTo(1e6, withinPercentage(1e-6));
        assertThat(inverse.getE()).isCloseTo(-5e6, withinPercentage(1e-6));
        assertThat(inverse.getF()).isCloseTo(-6e6, withinPercentage(1e-6));

        // And the defining property still holds: the round trip recovers the point.
        final double[] transformed = apply(matrix, 12.5, -7.25);
        final double[] recovered = apply(inverse, transformed[0], transformed[1]);
        assertThat(recovered[0]).isCloseTo(12.5, within(1e-6));
        assertThat(recovered[1]).isCloseTo(-7.25, within(1e-6));
    }

    // -----------------------------------------------------------------------
    // Composition, factories and point transform
    // -----------------------------------------------------------------------

    @Test
    void testTranslateFactory() {
        assertMatrixCloseTo(FloorMapTransformationMatrix.translate(3, 4),
                new FloorMapTransformationMatrix(1, 0, 0, 1, 3, 4));
    }

    @Test
    void testScaleFactory() {
        assertMatrixCloseTo(FloorMapTransformationMatrix.scale(2, 5),
                new FloorMapTransformationMatrix(2, 0, 0, 5, 0, 0));
    }

    @Test
    void testTransformPoint() {
        // (2,0,0,3,10,20) applied to (5,7): x'=2*5+10=20, y'=3*7+20=41.
        final double[] p = new FloorMapTransformationMatrix(2, 0, 0, 3, 10, 20)
                .transformPoint(5, 7);
        assertThat(p[0]).isCloseTo(20, within(TOLERANCE));
        assertThat(p[1]).isCloseTo(41, within(TOLERANCE));
    }

    @Test
    void testMultiplyThisTimesOther() {
        // translate(10,20) · scale(2,3): scale applied first, then translate.
        final FloorMapTransformationMatrix result =
                FloorMapTransformationMatrix.translate(10, 20)
                        .multiply(FloorMapTransformationMatrix.scale(2, 3));
        assertMatrixCloseTo(result, new FloorMapTransformationMatrix(2, 0, 0, 3, 10, 20));
    }

    @Test
    void testMultiplyOrderMatters() {
        // scale(2,3) · translate(10,20): translation is scaled → (20,60).
        final FloorMapTransformationMatrix result =
                FloorMapTransformationMatrix.scale(2, 3)
                        .multiply(FloorMapTransformationMatrix.translate(10, 20));
        assertMatrixCloseTo(result, new FloorMapTransformationMatrix(2, 0, 0, 3, 20, 60));
    }

    @Test
    void testMultiplyIdentityIsUnit() {
        final FloorMapTransformationMatrix m =
                new FloorMapTransformationMatrix(1.2, 0.9, -0.9, 1.2, 100, 50);
        assertMatrixCloseTo(m.multiply(FloorMapTransformationMatrix.identity()), m);
        assertMatrixCloseTo(FloorMapTransformationMatrix.identity().multiply(m), m);
    }

    @Test
    void testRotateAboutNonOriginPivot() {
        // 90° CCW about (10,10): (5,10) → (10,5); the pivot is left fixed.
        final FloorMapTransformationMatrix t = FloorMapTransformationMatrix.rotateAbout(90, 10, 10);
        final double[] p = t.transformPoint(5, 10);
        assertThat(p[0]).isCloseTo(10, within(TOLERANCE));
        assertThat(p[1]).isCloseTo(5, within(TOLERANCE));
        final double[] pivot = t.transformPoint(10, 10);
        assertThat(pivot[0]).isCloseTo(10, within(TOLERANCE));
        assertThat(pivot[1]).isCloseTo(10, within(TOLERANCE));
    }

    @Test
    void testScaleAboutNonOriginPivot() {
        // 2× about (10,10): (5,10) → (0,10); the pivot is left fixed.
        final FloorMapTransformationMatrix t = FloorMapTransformationMatrix.scaleAbout(2, 2, 10, 10);
        final double[] p = t.transformPoint(5, 10);
        assertThat(p[0]).isCloseTo(0, within(TOLERANCE));
        assertThat(p[1]).isCloseTo(10, within(TOLERANCE));
        final double[] pivot = t.transformPoint(10, 10);
        assertThat(pivot[0]).isCloseTo(10, within(TOLERANCE));
        assertThat(pivot[1]).isCloseTo(10, within(TOLERANCE));
    }

    @Test
    void testRotateAboutZeroDegreesIsIdentity() {
        assertMatrixCloseTo(FloorMapTransformationMatrix.rotateAbout(0, 7, 7),
                FloorMapTransformationMatrix.identity());
    }

    // -----------------------------------------------------------------------
    // Serialisation
    // -----------------------------------------------------------------------

    @Test
    void testJsonRoundTrip() {
        final FloorMapTransformationMatrix original =
                new FloorMapTransformationMatrix(1.2, 0.9, -0.9, 1.2, 100.5, 50.25);

        final String json = JsonUtil.writeValueAsString(original);
        final FloorMapTransformationMatrix deserialized =
                JsonUtil.readValue(json, FloorMapTransformationMatrix.class);

        assertThat(deserialized).isEqualTo(original);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /// Applies the affine transform to a point, mirroring the rendering maths
    /// in FloorMapCanvasPresenter:
    ///
    /// ```
    /// x' = a * x + c * y + e
    /// y' = b * x + d * y + f
    /// ```
    private static double[] apply(final FloorMapTransformationMatrix matrix,
                                  final double x,
                                  final double y) {
        return new double[]{
                matrix.getA() * x + matrix.getC() * y + matrix.getE(),
                matrix.getB() * x + matrix.getD() * y + matrix.getF()};
    }

    private static void assertMatrixCloseTo(final FloorMapTransformationMatrix actual,
                                            final FloorMapTransformationMatrix expected) {
        assertThat(actual.getA()).isCloseTo(expected.getA(), within(TOLERANCE));
        assertThat(actual.getB()).isCloseTo(expected.getB(), within(TOLERANCE));
        assertThat(actual.getC()).isCloseTo(expected.getC(), within(TOLERANCE));
        assertThat(actual.getD()).isCloseTo(expected.getD(), within(TOLERANCE));
        assertThat(actual.getE()).isCloseTo(expected.getE(), within(TOLERANCE));
        assertThat(actual.getF()).isCloseTo(expected.getF(), within(TOLERANCE));
    }

    /// Repositioning must leave scale and rotation alone and put the fact's own
    /// point exactly where it was asked for — the arithmetic shared by a canvas
    /// drag and a typed position.
    @Test
    void testPlacingPutsTheStoredPointAtTheGivenMapPosition() {
        final FloorMapTransformationMatrix m =
                FloorMapTransformationMatrix.scale(2, 2)
                        .multiply(FloorMapTransformationMatrix.rotate(30));

        final FloorMapTransformationMatrix placed = m.placing(3, 4, 100, 50);

        assertThat(placed.transformPoint(3, 4)[0]).isCloseTo(100, within(1e-9));
        assertThat(placed.transformPoint(3, 4)[1]).isCloseTo(50, within(1e-9));
        // Scale and rotation are untouched.
        assertThat(placed.getA()).isCloseTo(m.getA(), within(1e-9));
        assertThat(placed.getB()).isCloseTo(m.getB(), within(1e-9));
        assertThat(placed.getC()).isCloseTo(m.getC(), within(1e-9));
        assertThat(placed.getD()).isCloseTo(m.getD(), within(1e-9));
    }

    /// With coordinates at the origin the translation simply is the position.
    @Test
    void testPlacingAtOriginCoordsSetsTheTranslation() {
        final FloorMapTransformationMatrix placed =
                FloorMapTransformationMatrix.identity().placing(0, 0, 12, -7);

        assertThat(placed.getE()).isCloseTo(12, within(1e-9));
        assertThat(placed.getF()).isCloseTo(-7, within(1e-9));
    }

    /// Placing a fact where it already is must change nothing.
    @Test
    void testPlacingAtTheCurrentPositionIsANoOp() {
        final FloorMapTransformationMatrix m =
                new FloorMapTransformationMatrix(1.5, 0.2, -0.2, 1.5, 40, 60);
        final double[] where = m.transformPoint(5, 6);

        assertThat(m.placing(5, 6, where[0], where[1])).isEqualTo(m);
    }

    // -----------------------------------------------------------------------
    // hasInverse must screen every component, not just the linear four
    // -----------------------------------------------------------------------

    /// A non-finite translation used to pass hasInverse(), because only a, b, c and d reach the
    /// determinant. inverse() then "succeeded" and returned a matrix whose invE/invF were NaN or
    /// infinite - which is exactly the plausible-looking wrong answer inverse() documents as being
    /// worse than throwing. The vertex editor writes converted coordinates back to the document,
    /// so a silently non-finite inverse corrupts what gets saved.
    @TestFactory
    Stream<DynamicTest> testHasInverseRejectsAnyNonFiniteComponent() {
        return Stream.of(
                        new double[]{Double.NaN, 0, 0, 1, 0, 0},
                        new double[]{1, Double.NaN, 0, 1, 0, 0},
                        new double[]{1, 0, Double.NaN, 1, 0, 0},
                        new double[]{1, 0, 0, Double.NaN, 0, 0},
                        new double[]{1, 0, 0, 1, Double.NaN, 0},
                        new double[]{1, 0, 0, 1, 0, Double.NaN},
                        new double[]{1, 0, 0, 1, Double.POSITIVE_INFINITY, 0},
                        new double[]{1, 0, 0, 1, 0, Double.NEGATIVE_INFINITY})
                .map(v -> DynamicTest.dynamicTest(
                        "a=" + v[0] + " b=" + v[1] + " c=" + v[2]
                        + " d=" + v[3] + " e=" + v[4] + " f=" + v[5],
                        () -> {
                            final FloorMapTransformationMatrix m =
                                    new FloorMapTransformationMatrix(v[0], v[1], v[2], v[3], v[4], v[5]);
                            assertThat(m.hasInverse()).isFalse();
                            assertThatThrownBy(m::inverse)
                                    .isInstanceOf(IllegalStateException.class);
                        }));
    }

    /// The guard must not reject matrices that are perfectly invertible.
    @Test
    void testHasInverseStillAcceptsAFiniteInvertibleMatrix() {
        final FloorMapTransformationMatrix m =
                new FloorMapTransformationMatrix(1.5, 0.2, -0.2, 1.5, 40, 60);

        assertThat(m.hasInverse()).isTrue();
        assertThat(m.inverse()).isNotNull();
    }

    /// A large but finite translation is invertible; only non-finite values are rejected.
    @Test
    void testHasInverseAcceptsALargeFiniteTranslation() {
        final FloorMapTransformationMatrix m =
                new FloorMapTransformationMatrix(1, 0, 0, 1, 1e12, -1e12);

        assertThat(m.hasInverse()).isTrue();
    }
}
