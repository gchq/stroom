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

/**
 * Plain 2D polygon geometry — the single containment algorithm shared by the
 * FloorMap client and the {@code pointIsInsideXYPolygon} XSLT function.
 *
 * <p>Containment is an <strong>even-odd</strong> (ray-cast) test with an
 * axis-aligned bounding box prefilter, matching the {@code fill-rule="evenodd"}
 * the area renderer paints with — so what looks filled on the canvas is exactly
 * what tests as inside.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM and compiled
 * to JavaScript.</p>
 *
 * <p><strong>Coordinate space:</strong> every method here is space-agnostic —
 * it simply compares numbers. Callers are responsible for making the polygon
 * and the point share one space; for FloorMap that space is always
 * <strong>map space</strong>.</p>
 */
public final class FloorMapGeometry {

    private FloorMapGeometry() {
        // Utility class.
    }

    /**
     * Tests whether the point {@code (x, y)} lies inside the polygon, using an
     * even-odd ray cast behind an AABB prefilter.
     *
     * <p>Points exactly on an edge are not guaranteed either way (the usual
     * caveat for this family of tests) — the result is stable, but which side a
     * boundary point falls on depends on the edge's orientation.</p>
     *
     * @param polygon the polygon vertices {@code [[x,y], ...]}; a polygon with
     *                fewer than 3 usable vertices contains nothing
     * @param x       the test point's x
     * @param y       the test point's y
     * @return {@code true} if the point is inside
     */
    public static boolean contains(final double[][] polygon, final double x, final double y) {
        if (polygon == null || polygon.length < 3) {
            return false;
        }

        // AABB prefilter — cheap rejection for the common "nowhere near" case.
        final double[] bounds = aabb(polygon);
        if (bounds == null
                || x < bounds[0] || x > bounds[2]
                || y < bounds[1] || y > bounds[3]) {
            return false;
        }

        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            final double[] pi = polygon[i];
            final double[] pj = polygon[j];
            if (pi == null || pj == null) {
                continue;
            }
            if ((pi[1] > y) != (pj[1] > y)
                    && x < (pj[0] - pi[0]) * (y - pi[1]) / (pj[1] - pi[1]) + pi[0]) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Returns the axis-aligned bounding box of the polygon as
     * {@code {minX, minY, maxX, maxY}}, or {@code null} if it has no usable
     * vertices.
     *
     * @param polygon the polygon vertices {@code [[x,y], ...]}; may be {@code null}
     * @return the bounds, or {@code null}
     */
    public static double[] aabb(final double[][] polygon) {
        if (polygon == null || polygon.length == 0) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (final double[] v : polygon) {
            if (v != null && v.length >= 2) {
                minX = Math.min(minX, v[0]);
                minY = Math.min(minY, v[1]);
                maxX = Math.max(maxX, v[0]);
                maxY = Math.max(maxY, v[1]);
                any = true;
            }
        }
        return any
                ? new double[]{minX, minY, maxX, maxY}
                : null;
    }

    /**
     * Returns the unsigned area of the polygon via the shoelace formula.
     *
     * <p>Used to rank nested areas: when a point falls inside several
     * overlapping areas, the smallest one is the most specific answer to
     * "which area is it in?".</p>
     *
     * @param polygon the polygon vertices {@code [[x,y], ...]}; may be {@code null}
     * @return the unsigned area, or {@code 0} for a degenerate polygon
     */
    public static double area(final double[][] polygon) {
        if (polygon == null || polygon.length < 3) {
            return 0;
        }
        double sum = 0;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            final double[] pi = polygon[i];
            final double[] pj = polygon[j];
            if (pi == null || pj == null) {
                continue;
            }
            sum += (pj[0] * pi[1]) - (pi[0] * pj[1]);
        }
        return Math.abs(sum) / 2.0;
    }

    /// Returns the vertex centroid of the polygon — the mean of its vertices — as
    /// `{x, y}`, or `null` if it has no usable vertices.
    ///
    /// This is the vertex mean rather than the area-weighted centroid. It is
    /// what an area's local frame is centred on when it is stored, and where its
    /// occupant badge and camera anchor sit, so every caller has to agree on it —
    /// which is why there is exactly one implementation.
    ///
    /// @param polygon the polygon vertices `[[x,y], ...]`; may be `null`.
    ///         Rows that are `null` or shorter than two elements are
    ///         skipped, as in [#aabb(double\[\]\[\])]
    /// @return the centroid, or `null`
    public static double[] centroid(final double[][] polygon) {
        if (polygon == null) {
            return null;
        }
        double sumX = 0;
        double sumY = 0;
        int count = 0;
        for (final double[] v : polygon) {
            if (v != null && v.length >= 2) {
                sumX += v[0];
                sumY += v[1];
                count++;
            }
        }
        return count > 0
                ? new double[]{sumX / count, sumY / count}
                : null;
    }

    /// Returns the straight-line distance between two points.
    ///
    /// @param x1 the first point's x
    /// @param y1 the first point's y
    /// @param x2 the second point's x
    /// @param y2 the second point's y
    /// @return the distance
    public static double distance(final double x1,
                                  final double y1,
                                  final double x2,
                                  final double y2) {
        return Math.sqrt(distanceSquared(x1, y1, x2, y2));
    }

    /// Returns the square of the straight-line distance between two points — the
    /// cheaper form for comparisons, since it needs no square root.
    ///
    /// @param x1 the first point's x
    /// @param y1 the first point's y
    /// @param x2 the second point's x
    /// @param y2 the second point's y
    /// @return the squared distance
    public static double distanceSquared(final double x1,
                                         final double y1,
                                         final double x2,
                                         final double y2) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        return (dx * dx) + (dy * dy);
    }
}
