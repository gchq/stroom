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

package stroom.floormap.client.model;

import stroom.floormap.shared.FloorMapGeometry;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.util.shared.NullSafe;

/// A single parsed floor-map fact — the unified model behind backgrounds,
/// static facts and events (see the FloorMap coordinate/rendering redesign).
///
/// Every renderable thing on a floor map is a `Fact`. A background is
/// simply a fact that carries an image and sits at a low z-order; an event
/// (person) is a fact from the event stream that may or may not carry an
/// image.
///
/// Instances are immutable, produced by `FloorMapEntryParser` from a
/// [stroom.util.shared.TemporalEntry]. This is a plain, GWT-friendly value
/// object with no serialisation dependencies — it never crosses the wire (the
/// `TemporalEntry` does).
///
/// [#getWorldToMap()] holds the affine that places this fact into map
/// space. Every fact — backgrounds included — uses `WORLD_TO_MAP`; a
/// background is not special-cased, it is simply an image fact placed by its own
/// matrix and painted early (low z-order).
public final class Fact {

    private final String key;
    private final String type;
    private final String image;
    private final FloorMapTransformationMatrix worldToMap;
    private final double[] position;
    private final double[][] vertices;
    private final String fill;
    private final Double opacity;
    private final String label;

    /// @param key        the temporal-store key (fact identity within a map)
    /// @param type       the fact type (`""` if unset); also drives z-order
    /// @param image      the Asset Store image URL, or `null` if none
    /// @param worldToMap the affine placing this fact into map space; never `null`
    /// @param position   world-space coordinates `{x, y}` for a point fact,
    ///         or `null` (e.g. for a background)
    /// @throws IllegalArgumentException if the position does not hold exactly two
    ///         elements
    public Fact(final String key,
                final String type,
                final String image,
                final FloorMapTransformationMatrix worldToMap,
                final double[] position) {
        this(key, type, image, worldToMap, position, null, null, null);
    }

    /// @param key        the temporal-store key (fact identity within a map)
    /// @param type       the fact type (`""` if unset); also drives z-order
    /// @param image      the Asset Store image URL, or `null` if none
    /// @param worldToMap the affine placing this fact into map space; never `null`
    /// @param position   world-space coordinates `{x, y}` for a point fact,
    ///         or `null` (e.g. for a background)
    /// @param vertices   area polygon vertices `{{x, y}, ...}` in the fact's
    ///         local frame (placed by `worldToMap`), or `null`
    ///         for a non-area fact
    /// @param fill       area fill colour (hex string), or `null` to use the
    ///         type's default colour
    /// @param opacity    area fill opacity in `[0, 1]`, or `null` for
    ///         the default
    /// @throws IllegalArgumentException if the position, or any vertex, does not
    ///         hold exactly two elements, or a vertex is
    ///         `null`
    public Fact(final String key,
                final String type,
                final String image,
                final FloorMapTransformationMatrix worldToMap,
                final double[] position,
                final double[][] vertices,
                final String fill,
                final Double opacity) {
        this(key, type, image, worldToMap, position, vertices, fill, opacity, null);
    }

    /// @param key        the temporal-store key (fact identity within a map)
    /// @param type       the fact type (`""` if unset); also drives z-order
    /// @param image      the Asset Store image URL, or `null` if none
    /// @param worldToMap the affine placing this fact into map space; never `null`
    /// @param position   world-space coordinates `{x, y}` for a point fact,
    ///         or `null` (e.g. for a background)
    /// @param vertices   area polygon vertices `{{x, y}, ...}` in the fact's
    ///         local frame (placed by `worldToMap`), or `null`
    ///         for a non-area fact
    /// @param fill       area fill colour (hex string), or `null` to use the
    ///         type's default colour
    /// @param opacity    area fill opacity in `[0, 1]`, or `null` for
    ///         the default
    /// @param label      the user-facing name from the `LABEL` role, or
    ///         `null` when the schema does not map it (or the
    ///         fact has no name)
    /// @throws IllegalArgumentException if the position, or any vertex, does not
    ///         hold exactly two elements, or a vertex is
    ///         `null`
    public Fact(final String key,
                final String type,
                final String image,
                final FloorMapTransformationMatrix worldToMap,
                final double[] position,
                final double[][] vertices,
                final String fill,
                final Double opacity,
                final String label) {
        this.key = key;
        this.type = type != null ? type : "";
        this.image = image;
        this.worldToMap = worldToMap != null
                ? worldToMap
                : FloorMapTransformationMatrix.identity();
        this.position = copyPosition(position);
        this.vertices = copyVertices(vertices);
        this.fill = fill;
        this.opacity = opacity;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getType() {
        return type;
    }

    /// The image URL, or `null` if this fact has no image.
    public String getImage() {
        return image;
    }

    /// `true` if this fact has an image (and so scales in map space).
    public boolean hasImage() {
        return NullSafe.isNonEmptyString(image);
    }

    /// The affine that places this fact into map space; never `null`.
    public FloorMapTransformationMatrix getWorldToMap() {
        return worldToMap;
    }

    /// World coordinates `{x, y}` for a point fact, or `null`.
    public double[] getPosition() {
        return copyPosition(position);
    }

    /// Area polygon vertices `{{x, y}, ...}` in the fact's local frame,
    /// or `null` if this fact is not an area.
    public double[][] getVertices() {
        return copyVertices(vertices);
    }

    /// `true` if this fact is a renderable area polygon (≥ 3 vertices).
    public boolean hasVertices() {
        return vertices != null && vertices.length >= 3;
    }

    /// Returns `true` if this fact is painted, and so tested, as an area.
    ///
    /// The image check mirrors the renderer's image-first dispatch: a fact
    /// carrying its own image renders as that image, never as a polygon.
    ///
    /// @return `true` if this fact has polygon vertices and no image
    public boolean isArea() {
        return hasVertices() && !hasImage();
    }

    /// The area fill colour (hex string), or `null` for the type default.
    public String getFill() {
        return fill;
    }

    /// The area fill opacity in `[0, 1]`, or `null` for the default.
    public Double getOpacity() {
        return opacity;
    }

    /// The user-facing name from the `LABEL` role, or `null` when the
    /// schema does not map it or the fact is unnamed. Editable via the object
    /// properties dialog's Name field; distinct from [#getKey()], which is
    /// the fact's identity and never changes.
    public String getLabel() {
        return label;
    }

    /// The label if this fact has a non-blank one, otherwise `null` — the
    /// form callers want when falling back to a key-derived display name.
    public String getLabelOrNull() {
        return NullSafe.isNonBlankString(label)
                ? label
                : null;
    }

    /// Returns this fact's anchor point in map space — the point a camera
    /// should centre on when the fact is tracked. Dispatch matches the
    /// renderer's (an image wins over vertices):
    ///
    /// - Image fact: the centre of the placed image rectangle. Images render
    ///   at a fixed width with height derived from the aspect ratio; the
    ///   render wrapper transform is
    ///   `worldToMap · translate(0,h) · scale(1,-1)`, and applying it
    ///   to the image centre `(w/2, h/2)` reduces to
    ///   `worldToMap · (w/2, h/2)`.
    /// - Area fact: the local-frame vertex centroid pushed through
    ///   `worldToMap`.
    /// - Point fact: its position pushed through `worldToMap`.
    ///
    /// @param imageDisplayWidth the fixed map-space width images render at
    /// @param aspectRatio       the image's width/height ratio, or `null`
    ///         when not yet known (treated as square, matching
    ///         the renderer's pre-load fallback)
    /// @return the anchor `{mapX, mapY}`; never `null`
    public double[] mapAnchor(final double imageDisplayWidth, final Double aspectRatio) {
        if (hasImage()) {
            final double aspect = aspectRatio != null ? aspectRatio : 1.0;
            return worldToMap.transformPoint(
                    imageDisplayWidth / 2,
                    imageDisplayWidth / aspect / 2);
        }
        // Without an image the anchor and the containment point agree, so the
        // centroid and position rules live in one place.
        return mapTestPoint();
    }

    /// Returns the map-space point at which this fact is tested for containment.
    ///
    /// Deliberately _not_ [#mapAnchor(double, Double)]: that
    /// needs the rendered image width and aspect ratio, which only the view
    /// knows. Containment instead uses the fact's own placement, which is
    /// view-independent and stable:
    /// - an area → its local vertex centroid through `worldToMap`;
    /// - anything else → its `position` through `worldToMap`.
    ///
    /// For an image fact this is its placement origin rather than the centre
    /// of the drawn image. That is the documented trade-off for keeping the test
    /// free of view state; it only matters for facts whose image is large
    /// relative to the areas around it.
    ///
    /// @return the map-space test point `{mapX, mapY}`; never `null`
    public double[] mapTestPoint() {
        if (hasVertices()) {
            // Every vertex is a non-null [x, y] pair - the constructor rejects anything
            // else - so a renderable area always has a centroid.
            final double[] centroid = FloorMapGeometry.centroid(vertices);
            return worldToMap.transformPoint(centroid[0], centroid[1]);
        }
        return worldToMap.transformPoint(
                position != null ? position[0] : 0,
                position != null ? position[1] : 0);
    }

    /// Projects this area's vertices from its local frame into map space by
    /// pushing each through its `WORLD_TO_MAP` matrix.
    ///
    /// This is the load-bearing step for correctness: area vertices are
    /// stored centred on their centroid in a local frame, so an untransformed
    /// comparison against a map-space point is meaningless.
    ///
    /// @return the map-space vertices, or `null` if this fact is not an area
    public double[][] toMapVertices() {
        if (!hasVertices()) {
            return null;
        }
        final double[][] out = new double[vertices.length][];
        for (int i = 0; i < vertices.length; i++) {
            out[i] = worldToMap.transformPoint(vertices[i][0], vertices[i][1]);
        }
        return out;
    }

    /// Whether this fact's `worldToMap` can actually place it.
    ///
    /// A singular matrix - the all-zero one being the common case - collapses the fact to
    /// nothing, and it fails in two different silent ways depending on the fact. An image or
    /// area emits `matrix(0,0,0,0,0,0)`, a non-invertible CTM, so the browser declines
    /// to draw it while it stays hit-testable as a zero-size box: an object the user can
    /// select but cannot see. An imageless point instead transforms to `(0,0)`
    /// whatever its own coordinates say, so it piles up at the origin and looks like an
    /// object legitimately placed there.
    ///
    /// Both are worth refusing rather than drawing wrongly, so the renderer skips these
    /// and hit-testing ignores them. This predicate exists so those two decisions cannot
    /// drift apart - a fact that is not drawn must not be selectable, which is precisely the
    /// combination that made the original behaviour so hard to make sense of.
    ///
    /// @return `true` when the matrix is usable
    public boolean hasUsablePlacement() {
        return worldToMap.hasInverse();
    }

    /// Returns a copy of this fact with a different placement matrix — used for
    /// live transform previews. All other fields (including area geometry) are
    /// carried over unchanged.
    public Fact withWorldToMap(final FloorMapTransformationMatrix newWorldToMap) {
        return new Fact(key, type, image, newWorldToMap, position, vertices, fill, opacity, label);
    }

    /// Returns a copy of this fact with different area vertices (local frame) —
    /// used for live vertex-edit previews. All other fields are carried over
    /// unchanged.
    ///
    /// @throws IllegalArgumentException if any new vertex is `null` or does
    ///         not hold exactly two elements
    public Fact withVertices(final double[][] newVertices) {
        return new Fact(key, type, image, worldToMap, position, newVertices, fill, opacity, label);
    }

    /// Defensively copies a position, checking that it really is an `{x, y}` pair.
    /// Like a malformed vertex this is a caller bug rather than bad user data, and
    /// left unchecked it either reads whichever coordinates happen to be there or
    /// throws an `ArrayIndexOutOfBoundsException` from the middle of a constructor,
    /// saying nothing about which argument was wrong.
    ///
    /// @param source the position to copy; may be `null`
    /// @return a copy, or `null` if `source` is `null`
    /// @throws IllegalArgumentException if `source` does not hold exactly two
    ///         elements
    private static double[] copyPosition(final double[] source) {
        if (source == null) {
            return null;
        }
        if (source.length != 2) {
            throw new IllegalArgumentException(
                    "Position has " + source.length + " element(s), "
                            + "expecting an [x, y] pair");
        }
        return new double[]{source[0], source[1]};
    }

    /// Defensively copies a vertex array, checking that every row really is an
    /// `{x, y}` pair. A malformed row is a programming error in the caller rather
    /// than bad user data - the parsers only ever emit two-element rows - so it
    /// fails loudly here instead of surviving as a vertex that silently drops out
    /// of hit-testing and rendering further downstream.
    ///
    /// @param source the vertices to copy; may be `null`
    /// @return a deep copy, or `null` if `source` is `null`
    /// @throws IllegalArgumentException if any row is `null` or does not hold
    ///         exactly two elements
    private static double[][] copyVertices(final double[][] source) {
        if (source == null) {
            return null;
        }
        final double[][] copy = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            final double[] vertex = source[i];
            if (vertex == null) {
                throw new IllegalArgumentException(
                        "Vertex " + i + " is null, expecting an [x, y] pair");
            }
            if (vertex.length != 2) {
                throw new IllegalArgumentException(
                        "Vertex " + i + " has " + vertex.length + " element(s), "
                                + "expecting an [x, y] pair");
            }
            copy[i] = new double[]{vertex[0], vertex[1]};
        }
        return copy;
    }
}
