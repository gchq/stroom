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

package stroom.floormap.client.geometry;

import stroom.floormap.shared.FloorMapTransformationMatrix;

/// The pan/zoom state of the floor-map canvas and the pure coordinate maths that
/// depends on it.
///
/// The viewport applies a uniform `scale` and a translation
/// (`offsetX`, `offsetY`) on top of the active background's
/// map-to-screen transform. The full screen mapping for a map-space point is:
///
/// ```
/// unzoomed = background · [mapX, mapY]      (the map-to-screen matrix)
/// screen   = offset + scale · unzoomed
/// ```
///
/// This class holds no GWT or DOM types so it can be unit-tested on the JVM.
/// The presenter is responsible for pulling coordinates out of native mouse
/// events and pushing the resulting state into the view; all the arithmetic
/// lives here. See `FloorMapNotes.md` section 3.
public final class FloorMapViewport {

    /// Lower bound on [#scale] to avoid floating-point breakdown.
    public static final double MIN_SCALE = 1e-12;
    /// Upper bound on [#scale] to avoid floating-point breakdown.
    public static final double MAX_SCALE = 1e12;
    /// Multiplicative zoom step applied per wheel notch.
    public static final double ZOOM_STEP = 1.1;
    /// Default dead-zone margin for camera-follow, passed as the
    /// `marginFraction` of [#followDelta], as a fraction of each view
    /// dimension. 0.35 means the tracked point may roam the central 30% of the view
    /// before the camera starts panning — small movements leave the camera still,
    /// larger ones pull it along.
    ///
    /// Callers wanting to hard-centre pass 0.5 instead, which collapses the dead
    /// zone to the centre point; `FloorMapCanvasPresenter` does this for the
    /// first follow after tracking is switched on.
    public static final double DEFAULT_FOLLOW_MARGIN = 0.35;

    /// Default time constant (ms) for damped camera-follow movement: the camera
    /// covers ~63% of the outstanding follow correction per this interval, so
    /// it glides after the tracked point instead of snapping.
    public static final double DEFAULT_FOLLOW_DAMPING_MS = 300.0;

    private double scale;
    private double offsetX;
    private double offsetY;

    /// Creates a viewport at the default state (scale 1, no offset).
    public FloorMapViewport() {
        this(1.0, 0.0, 0.0);
    }

    public FloorMapViewport(final double scale,
                            final double offsetX,
                            final double offsetY) {
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public double getScale() {
        return scale;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    // -----------------------------------------------------------------------
    // Coordinate conversions
    // -----------------------------------------------------------------------

    /// Converts an element-relative screen point to map space by reversing the
    /// zoom/pan transform and then applying the inverse of the background matrix.
    ///
    /// @param screenX    element-relative X (matching `MouseEvent.getX()`)
    /// @param screenY    element-relative Y (matching `MouseEvent.getY()`)
    /// @param background the active background's map-to-screen matrix; may be
    ///         `null` (treated as identity)
    /// @return `{mapX, mapY}` in map space
    public double[] screenToMap(final double screenX,
                                final double screenY,
                                final FloorMapTransformationMatrix background) {
        final double unzoomedX = (screenX - offsetX) / scale;
        final double unzoomedY = (screenY - offsetY) / scale;

        final FloorMapTransformationMatrix inverse = inverseOrIdentityIfAbsent(background);
        final double mapX = inverse.getA() * unzoomedX + inverse.getC() * unzoomedY + inverse.getE();
        final double mapY = inverse.getB() * unzoomedX + inverse.getD() * unzoomedY + inverse.getF();
        return new double[]{mapX, mapY};
    }

    /// Converts a map-space point to an element-relative screen point. This is
    /// the exact inverse of [#screenToMap] and is used for hit-testing and
    /// round-trip verification.
    ///
    /// @param mapX       map-space X
    /// @param mapY       map-space Y
    /// @param background the active background's map-to-screen matrix; may be
    ///         `null` (treated as identity)
    /// @return `{screenX, screenY}` in element-relative screen space
    public double[] mapToScreen(final double mapX,
                                final double mapY,
                                final FloorMapTransformationMatrix background) {
        final FloorMapTransformationMatrix matrix = background != null
                ? background
                : FloorMapTransformationMatrix.identity();
        final double unzoomedX = matrix.getA() * mapX + matrix.getC() * mapY + matrix.getE();
        final double unzoomedY = matrix.getB() * mapX + matrix.getD() * mapY + matrix.getF();
        return new double[]{
                offsetX + scale * unzoomedX,
                offsetY + scale * unzoomedY};
    }

    // -----------------------------------------------------------------------
    // Mutating gestures
    // -----------------------------------------------------------------------

    /// Pans the viewport by a raw screen delta (as reported by mouse-move).
    public void pan(final double screenDeltaX, final double screenDeltaY) {
        offsetX += screenDeltaX;
        offsetY += screenDeltaY;
    }

    /// Zooms towards the cursor by one [#ZOOM_STEP] notch, keeping the
    /// map point under the cursor fixed on screen. The resulting scale is
    /// clamped between [#MIN_SCALE] and [#MAX_SCALE].
    ///
    /// @param cursorX element-relative cursor X to zoom towards
    /// @param cursorY element-relative cursor Y to zoom towards
    /// @param zoomIn  `true` to zoom in, `false` to zoom out
    public void zoom(final double cursorX, final double cursorY, final boolean zoomIn) {
        final double zoomFactor = zoomIn
                ? ZOOM_STEP
                : 1.0 / ZOOM_STEP;

        // Clamp the scale first, then shift the offset by the ratio ACTUALLY
        // applied — so the point under the cursor stays fixed even at the
        // MIN/MAX clamp. Shifting by the raw factor before clamping would drift
        // the view under the cursor on every wheel tick once a clamp is hit.
        final double newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * zoomFactor));
        final double effectiveFactor = scale != 0 ? newScale / scale : 1.0;
        offsetX = cursorX - (cursorX - offsetX) * effectiveFactor;
        offsetY = cursorY - (cursorY - offsetY) * effectiveFactor;
        scale = newScale;
    }

    /// Pure dead-zone camera maths, independent of any coordinate convention:
    /// given a tracked point's current *screen* position, returns the pan
    /// delta needed to bring it back inside the view's central dead-zone
    /// rectangle. Returns `{0, 0}` when the point is already inside the
    /// zone or the view has no size — callers apply the delta to their own
    /// offsets however their draw pipeline is oriented.
    ///
    /// @param screenX        the tracked point's on-screen X
    /// @param screenY        the tracked point's on-screen Y
    /// @param viewWidth      the visible canvas width in screen pixels
    /// @param viewHeight     the visible canvas height in screen pixels
    /// @param marginFraction the dead-zone margin as a fraction of each view
    ///         dimension, clamped to `[0, 0.5]`; 0.5
    ///         collapses the dead zone to the centre point
    ///         (hard-centring)
    /// @return `{deltaX, deltaY}` to add to the pan offsets
    public static double[] followDelta(final double screenX,
                                       final double screenY,
                                       final double viewWidth,
                                       final double viewHeight,
                                       final double marginFraction) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return new double[]{0, 0};
        }
        final double margin = Math.max(0.0, Math.min(0.5, marginFraction));
        final double marginX = viewWidth * margin;
        final double marginY = viewHeight * margin;

        double deltaX = 0;
        double deltaY = 0;
        if (screenX < marginX) {
            deltaX = marginX - screenX;
        } else if (screenX > viewWidth - marginX) {
            deltaX = (viewWidth - marginX) - screenX;
        }
        if (screenY < marginY) {
            deltaY = marginY - screenY;
        } else if (screenY > viewHeight - marginY) {
            deltaY = (viewHeight - marginY) - screenY;
        }
        return new double[]{deltaX, deltaY};
    }

    /// Exponential damping factor for frame-based camera movement: the fraction
    /// of an outstanding correction to apply after `deltaMs` elapsed, such
    /// that repeated application converges on the target at a rate set by the
    /// time constant (~63% covered per `timeConstantMs`) independently of
    /// frame rate.
    ///
    /// @param deltaMs        elapsed time since the previous step, in ms
    /// @param timeConstantMs damping time constant, in ms (larger = floatier)
    /// @return a factor in `[0, 1]`; `0` for non-positive
    ///         `deltaMs`, `1` for a non-positive time constant
    public static double dampingFactor(final double deltaMs, final double timeConstantMs) {
        if (deltaMs <= 0) {
            return 0;
        }
        if (timeConstantMs <= 0) {
            return 1;
        }
        return 1 - Math.exp(-deltaMs / timeConstantMs);
    }

    // -----------------------------------------------------------------------
    // Drag maths (used in Edit Mode)
    // -----------------------------------------------------------------------

    /// Converts a raw screen drag delta into the equivalent delta in map space,
    /// for repositioning a plotted (non-background) item. The delta is reduced by
    /// the zoom scale and then rotated/scaled by the inverse of the background's
    /// linear part; translation is irrelevant to a delta and is not applied.
    ///
    /// @param background   the active background's map-to-screen matrix; may be
    ///         `null` (treated as identity)
    /// @param screenDeltaX raw screen X delta
    /// @param screenDeltaY raw screen Y delta
    /// @return `{deltaMapX, deltaMapY}` to add to the item's map coordinates
    public double[] dragItemMapDelta(final FloorMapTransformationMatrix background,
                                     final double screenDeltaX,
                                     final double screenDeltaY) {
        final double deltaUnzoomedX = screenDeltaX / scale;
        final double deltaUnzoomedY = screenDeltaY / scale;

        final FloorMapTransformationMatrix inverse = inverseOrIdentityIfAbsent(background);
        final double deltaMapX = inverse.getA() * deltaUnzoomedX + inverse.getC() * deltaUnzoomedY;
        final double deltaMapY = inverse.getB() * deltaUnzoomedX + inverse.getD() * deltaUnzoomedY;
        return new double[]{deltaMapX, deltaMapY};
    }

    /// Inverts `matrix`, treating a `null` matrix as the identity —
    /// "no background transform" is a legitimate state and inverts to itself.
    ///
    /// Named for what it actually does: it is *not* safe against a
    /// singular matrix, and deliberately so. Every background matrix the UI
    /// composes is invertible by construction (the Y-flip alone has determinant
    /// -1), so a non-invertible one here is a bug and
    /// [FloorMapTransformationMatrix#inverse()] will say so rather than
    /// silently returning the identity and mis-converting every drag delta.
    private static FloorMapTransformationMatrix inverseOrIdentityIfAbsent(
            final FloorMapTransformationMatrix matrix) {
        return matrix != null
                ? matrix.inverse()
                : FloorMapTransformationMatrix.identity();
    }
}
