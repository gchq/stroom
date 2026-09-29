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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/// A 2D affine transformation matrix used to position and transform floor map
/// images and coordinates.
///
/// The six components `(a, b, c, d, e, f)` correspond to the SVG/CSS
/// `matrix(a, b, c, d, e, f)` notation, representing the transformation:
///
/// ```
/// | a  c  e |
/// | b  d  f |
/// | 0  0  1 |
/// ```
@JsonInclude(Include.NON_NULL)
public class FloorMapTransformationMatrix {

    /// Relative tolerance used to decide whether the determinant is
    /// indistinguishable from zero.
    ///
    /// The test is **relative**, not absolute. An absolute
    /// threshold rejects legitimately invertible matrices: a uniform scale of
    /// one part in a million has determinant `1e-12`, which is tiny but
    /// perfectly representable, and inverting it is both well-defined and
    /// necessary. Scaling the tolerance by the magnitude of the terms that
    /// formed the determinant keeps the test meaningful at any scale.
    private static final double SINGULARITY_RELATIVE_TOLERANCE = 1e-12;

    @JsonProperty
    private final double a;
    @JsonProperty
    private final double b;
    @JsonProperty
    private final double c;
    @JsonProperty
    private final double d;
    @JsonProperty
    private final double e;
    @JsonProperty
    private final double f;

    @JsonCreator
    public FloorMapTransformationMatrix(@JsonProperty("a") final double a,
                                        @JsonProperty("b") final double b,
                                        @JsonProperty("c") final double c,
                                        @JsonProperty("d") final double d,
                                        @JsonProperty("e") final double e,
                                        @JsonProperty("f") final double f) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
    }

    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }

    public double getC() {
        return c;
    }

    public double getD() {
        return d;
    }

    public double getE() {
        return e;
    }

    public double getF() {
        return f;
    }

    /// Formats this matrix as an SVG `matrix(a,b,c,d,e,f)` string.
    ///
    /// @return the SVG matrix attribute value
    public String toSvgMatrix() {
        return "matrix(" + a + "," + b + "," + c + "," + d + "," + e + "," + f + ")";
    }

    /// Returns the identity transformation (no scaling, rotation, or translation).
    ///
    /// @return the identity matrix
    public static FloorMapTransformationMatrix identity() {
        return new FloorMapTransformationMatrix(1, 0, 0, 1, 0, 0);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FloorMapTransformationMatrix that = (FloorMapTransformationMatrix) o;
        return Double.compare(that.a, a) == 0 &&
                Double.compare(that.b, b) == 0 &&
                Double.compare(that.c, c) == 0 &&
                Double.compare(that.d, d) == 0 &&
                Double.compare(that.e, e) == 0 &&
                Double.compare(that.f, f) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c, d, e, f);
    }

    @Override
    public String toString() {
        return toSvgMatrix();
    }

    /// Creates a rotation-only transformation matrix for the given angle.
    ///
    /// @param degrees the rotation angle in degrees (counter-clockwise positive)
    /// @return a new rotation matrix
    public static FloorMapTransformationMatrix rotate(final double degrees) {
        final double radians = Math.toRadians(degrees);
        final double cos = Math.cos(radians);
        final double sin = Math.sin(radians);

        // a=cos, b=sin, c=-sin, d=cos, e=0, f=0
        return new FloorMapTransformationMatrix(cos, sin, -sin, cos, 0, 0);
    }

    /// Returns a pure translation matrix.
    ///
    /// @param dx the x translation
    /// @param dy the y translation
    /// @return `(1,0,0,1,dx,dy)`
    public static FloorMapTransformationMatrix translate(final double dx, final double dy) {
        return new FloorMapTransformationMatrix(1, 0, 0, 1, dx, dy);
    }

    /// Returns a pure scale matrix (about the origin).
    ///
    /// @param sx the x scale factor
    /// @param sy the y scale factor
    /// @return `(sx,0,0,sy,0,0)`
    public static FloorMapTransformationMatrix scale(final double sx, final double sy) {
        return new FloorMapTransformationMatrix(sx, 0, 0, sy, 0, 0);
    }

    /// Multiplies this matrix by `o`, returning `this · o`.
    ///
    /// Convention (matching SVG/DOMMatrix `.multiply()` and CSS): for a
    /// column vector `v`, `this.multiply(o).transformPoint(v)` applies
    /// `o` **first**, then `this`. So to apply a map-space
    /// transform `T` to a fact whose placement is `worldToMap`, use
    /// `T.multiply(worldToMap)` (= `T · worldToMap`).
    ///
    /// @param o the matrix to apply before this one
    /// @return the composed matrix `this · o`
    public FloorMapTransformationMatrix multiply(final FloorMapTransformationMatrix o) {
        return new FloorMapTransformationMatrix(
                a * o.a + c * o.b,
                b * o.a + d * o.b,
                a * o.c + c * o.d,
                b * o.c + d * o.d,
                a * o.e + c * o.f + e,
                b * o.e + d * o.f + f);
    }

    /// Applies this matrix to the point `(x, y)`.
    ///
    /// @param x the x coordinate
    /// @param y the y coordinate
    /// @return `{a*x + c*y + e, b*x + d*y + f}`
    public double[] transformPoint(final double x, final double y) {
        return new double[]{a * x + c * y + e, b * x + d * y + f};
    }

    /// Returns a copy whose translation places the fact's own point
    /// `(worldX, worldY)` at map position `(mapX, mapY)`, keeping the
    /// scale and rotation (`a, b, c, d`) untouched.
    ///
    /// The inverse of [#transformPoint]: solving
    /// `mapX = a·worldX + c·worldY + e` for the translation. This is how a
    /// fact is repositioned without disturbing how it is sized or turned — the
    /// arithmetic behind both a canvas drag and a typed position, which is why
    /// the two agree.
    ///
    /// @param worldX the fact's stored x coordinate, in its own frame
    /// @param worldY the fact's stored y coordinate, in its own frame
    /// @param mapX   where that point should land, in map space
    /// @param mapY   where that point should land, in map space
    /// @return the repositioned matrix
    public FloorMapTransformationMatrix placing(final double worldX,
                                                final double worldY,
                                                final double mapX,
                                                final double mapY) {
        return new FloorMapTransformationMatrix(
                a, b, c, d,
                mapX - (a * worldX + c * worldY),
                mapY - (b * worldX + d * worldY));
    }

    /// Returns a rotation about an arbitrary pivot `(px, py)` — i.e.
    /// `translate(px,py) · rotate(degrees) · translate(-px,-py)`. The pivot
    /// is left fixed by the resulting transform.
    ///
    /// @param degrees the rotation angle in degrees (counter-clockwise positive)
    /// @param px      the pivot x
    /// @param py      the pivot y
    /// @return the rotation-about-pivot matrix
    public static FloorMapTransformationMatrix rotateAbout(final double degrees,
                                                           final double px,
                                                           final double py) {
        return translate(px, py).multiply(rotate(degrees)).multiply(translate(-px, -py));
    }

    /// Returns a scale about an arbitrary pivot `(px, py)` — i.e.
    /// `translate(px,py) · scale(sx,sy) · translate(-px,-py)`. The pivot is
    /// left fixed by the resulting transform.
    ///
    /// @param sx the x scale factor
    /// @param sy the y scale factor
    /// @param px the pivot x
    /// @param py the pivot y
    /// @return the scale-about-pivot matrix
    public static FloorMapTransformationMatrix scaleAbout(final double sx,
                                                          final double sy,
                                                          final double px,
                                                          final double py) {
        return translate(px, py).multiply(scale(sx, sy)).multiply(translate(-px, -py));
    }

    /// Whether this matrix can be inverted.
    ///
    /// A matrix is non-invertible only if its determinant is zero (to within
    /// [#SINGULARITY_RELATIVE_TOLERANCE], measured relative to the terms
    /// that formed it) or if any of its components is not finite. Within
    /// FloorMap that should never happen: every matrix the UI composes is
    /// invertible by construction, and matrices read from stored data are
    /// rejected at parse time by
    /// `FloorMapEntryParser`. Use this where you need to check data
    /// provenance rather than let [#inverse()] throw.
    ///
    /// Named `hasInverse` rather than `isInvertible` so Jackson cannot
    /// mistake it for a property: this class is serialised, and Jackson auto-detects
    /// `getXxx`/`isXxx`. A `hasXxx` name keeps it invisible without an
    /// `@JsonIgnore`, matching [TypeStyle#hasGraphic()] and the other
    /// `has*` helpers — and `TestJsonSerialisation` fails the build both on an
    /// undeclared extra getter and on a redundant `@JsonIgnore`.
    ///
    /// @return `true` if [#inverse()] will succeed
    public boolean hasInverse() {
        // Check all six components, not just the four the determinant is built from. The
        // translation pair only reaches the inverse via invE/invF, so a non-finite e or f used
        // to sail through this test and produce a non-finite inverse - the plausible-looking
        // wrong answer that inverse() documents at length as being worse than no answer.
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c)
            || !Double.isFinite(d) || !Double.isFinite(e) || !Double.isFinite(f)) {
            return false;
        }
        final double det = a * d - b * c;
        if (!Double.isFinite(det)) {
            return false;
        }
        final double magnitude = Math.abs(a * d) + Math.abs(b * c);
        if (Double.isInfinite(magnitude)) {
            return false;
        }
        return Math.abs(det) > SINGULARITY_RELATIVE_TOLERANCE * magnitude;
    }

    /// Computes the inverse of this transformation matrix.
    ///
    /// **Throws rather than degrading.** An earlier version of
    /// this method returned [#identity()] for a singular matrix. That was
    /// actively harmful: the identity is a perfectly plausible-looking answer,
    /// so every subsequent coordinate conversion appeared to succeed while
    /// silently returning its input unchanged — and in the vertex editor those
    /// unconverted coordinates were written back to the document. A wrong
    /// answer that looks right is worse than no answer.
    ///
    /// There is no circumstance in the FloorMap UI where a matrix legitimately
    /// cannot be inverted, so reaching the exception means bad data got past the
    /// parser or a caller composed a degenerate transform — both bugs worth
    /// surfacing. Call [#hasInverse()] first if you are handling data of
    /// uncertain provenance.
    ///
    /// @return the inverse matrix; never `null`
    /// @throws IllegalStateException if this matrix is not invertible
    public FloorMapTransformationMatrix inverse() {
        if (!hasInverse()) {
            throw new IllegalStateException(
                    "Matrix is not invertible (determinant is zero or a component is "
                            + "not finite): " + this);
        }
        final double det = a * d - b * c;
        final double invDet = 1.0 / det;
        final double invA = d * invDet;
        final double invB = -b * invDet;
        final double invC = -c * invDet;
        final double invD = a * invDet;
        final double invE = (c * f - d * e) * invDet;
        final double invF = (b * e - a * f) * invDet;
        return new FloorMapTransformationMatrix(invA, invB, invC, invD, invE, invF);
    }
}
