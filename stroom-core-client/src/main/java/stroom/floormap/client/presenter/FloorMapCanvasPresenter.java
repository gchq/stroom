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

package stroom.floormap.client.presenter;

import stroom.floormap.client.cluster.FloorMapCluster;
import stroom.floormap.client.cluster.FloorMapClusterLabel;
import stroom.floormap.client.cluster.FloorMapClusterOverlay;
import stroom.floormap.client.event.MapClusterSelectedEvent;
import stroom.floormap.client.event.MapContextMenuEvent;
import stroom.floormap.client.event.MapObjectSelectedEvent;
import stroom.floormap.client.geometry.FloorMapScreenGeometry;
import stroom.floormap.client.geometry.FloorMapViewport;
import stroom.floormap.client.geometry.FloorMapZOrder;
import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.floormap.client.overlay.FloorMapAreaOverlay;
import stroom.floormap.client.overlay.FloorMapGroupOverlay;
import stroom.floormap.client.overlay.FloorMapHighlight;
import stroom.floormap.client.overlay.FloorMapHoverDetail;
import stroom.floormap.client.playback.FloorMapEntityAnimator;
import stroom.floormap.client.presenter.FloorMapCanvasPresenter.FloorMapCanvasView;
import stroom.floormap.client.view.FloorMapGrid;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.TypeStyle;

import com.google.gwt.animation.client.AnimationScheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ContextMenuEvent;
import com.google.gwt.event.dom.client.HasMouseMoveHandlers;
import com.google.gwt.event.dom.client.HasMouseUpHandlers;
import com.google.gwt.event.dom.client.HasMouseWheelHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;

/// Presenter for the interactive SVG map canvas.
///
/// Manages zoom, pan, object selection and drag-move in edit mode, and
/// smooth entity-movement animations with fading trails during timeline
/// playback.  Static floor-plan content comes from the `facts` list
/// (rendered by type z-order); event-driven entities come from
/// `eventObjects`.  An entity that exists as both (its positions are
/// recorded in the facts store AND it streams events) renders once: the
/// animated event overlay suppresses its static fact twin.
///
/// The canvas view renders the z-ordered facts plus the event draw list
/// produced by [#buildAnimatedDrawList(double)] via its
/// [draw()][FloorMapCanvasView#draw] method.
public class FloorMapCanvasPresenter extends MyPresenterWidget<FloorMapCanvasView> {


    // -------------------------------------------------------------------------
    /// Minimum/maximum zoom scale — the single source of truth lives on
    /// [FloorMapViewport] (shared, unit-tested) so the clamp used here and
    /// in the viewport maths cannot drift apart. Beyond these (~1e±12) the grid
    /// decade selection and SVG coordinate values lose precision.
    private static final double MIN_SCALE = FloorMapViewport.MIN_SCALE;
    private static final double MAX_SCALE = FloorMapViewport.MAX_SCALE;

    /// The zoom level a freshly opened map starts at (100 %).
    private static final double DEFAULT_SCALE = 1.0;

    /// The map→screen "background" matrix handed to [FloorMapViewport] for
    /// projection: map space is Y-up, the SVG render pipeline is Y-down, so the
    /// only fixed transform on top of pan/zoom is a Y flip. With this as the
    /// viewport's background, `viewport.mapToScreen`/`screenToMap`
    /// reproduce this presenter's projection exactly, so the (unit-tested)
    /// viewport maths is the single source of the projection formula.
    private static final FloorMapTransformationMatrix Y_FLIP =
            FloorMapTransformationMatrix.scale(1, -1);

    /// How far the origin (0,0) is inset from the bottom-left corner in the
    /// default view, expressed in major grid divisions. Half a division places
    /// the axis indicator comfortably clear of the corner (e.g. the bottom-left
    /// of the screen reads as (-50,-50) when the major division is 100).
    private static final double ORIGIN_INSET_MAJOR_DIVISIONS = 0.5;

    /// Fraction of the viewport kept as empty margin on each side when the
    /// initial view is zoomed to fit all content.
    private static final double FIT_MARGIN = 0.08;

    /// How close together (screen px) two entities must be before they are merged
    /// into one summary glyph.
    ///
    /// Computed from the glyph rather than picked: point glyphs occupy a
    /// [FloorMapScreenGeometry#POINT_GLYPH_SIZE_PX] box, so their ink starts
    /// overlapping the moment they are closer than that. It was previously
    /// three-quarters of the box on the reasoning that only badly-obscured pairs
    /// needed merging — which guaranteed the survivors overlapped, since the
    /// merged glyph is a full box wide and its neighbour could sit at 45 px. The
    /// clearance above the box gives the count pill and the caption somewhere to
    /// go. Being a *screen* distance is what makes one constant serve every
    /// zoom level and every document.
    private static final double CLUSTER_RADIUS_PX =
            FloorMapScreenGeometry.POINT_GLYPH_SIZE_PX * 1.2;

    /// How close (screen px) the pointer must be to a cluster's centre to count as
    /// hovering it — the glyph's own half-width, so the hit area is the glyph.
    ///
    /// This is the radius for a cluster drawn at the base size;
    /// [FloorMapClusterOverlay#clusterNear] scales it by each cluster's own
    /// [FloorMapCluster#getSizeFactor()], so a badge that has grown stays
    /// hoverable right to its edge.
    private static final double CLUSTER_HIT_RADIUS_PX =
            FloorMapScreenGeometry.POINT_GLYPH_SIZE_PX / 2.0;

    /// The most member names listed in a hover tooltip before it summarises the
    /// rest. A cluster can hold hundreds; a tooltip taller than the canvas helps
    /// nobody, and the full list is a click away.
    private static final int CLUSTER_TOOLTIP_MAX_NAMES = 20;

    // Zoom and pan state
    private double scale = DEFAULT_SCALE;
    private double offsetX = 0;
    private double offsetY = 0;

    /// Whether the initial view (zoom-to-fit, or the bottom-left origin fallback)
    /// has been applied yet. Applied once, when the canvas first has both a real
    /// size and — for the fit — content or an injected view; user pan/zoom
    /// afterwards is left untouched.
    private boolean initialViewApplied = false;

    /// An initial view `{scale, offsetX, offsetY}` handed over from another
    /// tab (Map → Editor) so the first frame matches exactly and nothing jumps.
    /// `null` when this canvas must compute its own fit. Consulted once, on
    /// the first [#maybeApplyInitialView()].
    private double[] injectedInitialView;

    /// Notified once with this canvas's computed initial view
    /// `{scale, offsetX, offsetY}` so another tab can reuse it. `null`
    /// when nothing is listening.
    private Consumer<double[]> initialViewListener;

    // Dragging state
    private DragHandler dragHandler;
    private GeometryHandler geometryHandler;
    private SelectionHandler selectionHandler;
    private boolean isDragging = false;

    /// True only if the mouse actually moved while dragging an object (distinguishes click-to-select from drag).
    private boolean hasMoved = false;
    private double lastMouseX;
    private double lastMouseY;

    /// Accumulated drag delta in map space (Y-up) for the current drag gesture.
    private double dragDxMap;
    private double dragDyMap;

    /// The kind of pointer gesture currently in progress.
    private enum Gesture {
        NONE, PANNING, MOVING, MARQUEE, SCALING, ROTATING, DRAWING_AREA, MOVING_VERTEX,
        MEASURING_SCALE
    }

    private Gesture gesture = Gesture.NONE;

    // Area vertex-edit state (valid while gesture == MOVING_VERTEX).
    /// Key of the area whose vertices are being edited.
    private String editingAreaKey;

    /// The area's world-to-map at edit start (to map screen ↔ local frame).
    private FloorMapTransformationMatrix editingWorldToMap;

    /// Working copy of the area's local-frame vertices during the edit.
    private double[][] workingVertices;

    /// Index of the vertex being dragged, or -1.
    private int editingVertexIndex = -1;

    /// True when the current vertex edit inserted a new vertex (persist even if not dragged).
    private boolean vertexInserted;

    /// Rubber-band marquee corners in element-pixel space (valid while MARQUEE).
    private double marqueeStartX;
    private double marqueeStartY;
    private double marqueeCurX;
    private double marqueeCurY;

    /// Minimum vertices needed to close an area polygon.
    private static final int AREA_MIN_VERTICES = 3;

    /// Screen-pixel radius around vertex 0 within which a click closes the
    /// polygon, and the radius of the close-target ring the view draws. Public
    /// so `FloorMapCanvasViewImpl` shares this single value (the hit test
    /// and the drawn ring must match).
    public static final double AREA_CLOSE_RADIUS_PX = 10;

    /// Committed draft vertices for the in-progress DRAWING_AREA gesture, in
    /// map space (so panning/zooming mid-draw doesn't shear the draft).
    private final List<double[]> areaDraftMap = new ArrayList<>();

    /// Live cursor position in element pixels (valid while DRAWING_AREA).
    private double areaCursorX;
    private double areaCursorY;

    /// The anchor of the in-progress Set Scale measurement, in map space (so a
    /// mid-measure zoom cannot stretch it), or `null` before the press.
    private double[] measureStartMap;

    /// Live cursor position in element pixels (valid while MEASURING_SCALE).
    private double measureCursorX;
    private double measureCursorY;

    /// Shortest measurement worth acting on, in screen pixels. Below this the
    /// derived scale would be dominated by where the pointer happened to land, so
    /// the gesture stays live and the user can simply drag again.
    private static final double MIN_MEASURE_PX = 8;
    private AreaHandler areaHandler;
    private ScaleHandler scaleHandler;

    /// On a plain press over the background fact or empty canvas, the fact key to
    /// select if the press turns out to be a click rather than a pan
    /// (`null` for empty canvas). Lets a click select the background image
    /// while a drag still pans.
    private String pendingClickSelectId;

    /// The in-progress map-space transform for the current MOVING/SCALING/ROTATING
    /// gesture (composed onto each selected fact for live preview and committed on
    /// release). `null` when no transform gesture is active.
    private FloorMapTransformationMatrix pendingTransform;

    /// Scale pivot in map space (opposite corner / edge midpoint), for SCALING.
    private double gesturePivotX;
    private double gesturePivotY;

    /// The grabbed scale handle's map-space position at gesture start, for SCALING.
    private double gestureRefX;
    private double gestureRefY;

    /// Rotation centre in map space, for ROTATING.
    private double gestureCentreX;
    private double gestureCentreY;

    /// Pointer position in map space at gesture start, for ROTATING.
    private double gestureStartMapX;
    private double gestureStartMapY;

    /// Smallest scale factor a handle drag may produce (avoids zero/flip/singular).
    private static final double MIN_SCALE_FACTOR = 0.05;

    /// Keys of facts that behave like the canvas background: a plain drag over
    /// them pans (matching the read-only Map viewer) rather than moving them.
    /// Recomputed on each [#setFacts(List)].
    private final Set<String> backgroundKeys = new HashSet<>();

    /// Keys of area (polygon) facts. Their whole interior is clickable, so a
    /// press on an *unselected* area gets the background treatment —
    /// drag pans, plain click selects — otherwise a large area would make the
    /// map impossible to pan. Once selected, a drag moves it like any object.
    /// Recomputed on each [#setFacts(List)].
    private final Set<String> areaKeys = new HashSet<>();

    private boolean editMode = false;

    /// Currently selected object ids, in selection order. Backed as a set so a
    /// future rubber-band / modifier-key UI can select many; the current UI
    /// selects exactly one (see [#setSelectedObjectId(String)]). The view
    /// highlights every id in this set; a drag translates the whole selection.
    private final Set<String> selectedObjectIds = new LinkedHashSet<>();

    /// Whether the grid overlay is drawn. The grid is a non-interactive UI aid
    /// (it visualises map space) and is independent of [#editMode] and of
    /// whether a background image is present. The Editor tab enables it; other
    /// tabs (e.g. the Map tab) can opt in via [#setShowGrid(boolean)].
    private boolean showGrid = false;

    /// Whether entities too close together on screen are merged into one summary
    /// glyph. Enabled by the Map tab; never applied in edit mode (see
    /// [#clusterOverlay]).
    ///
    /// Transient view state, like [#showGrid] — not a document field, so
    /// it neither dirties the document nor needs carrying through
    /// `FloorMapDoc.copy()`.
    private boolean clusterNearbyEntities = false;

    /// The clusters drawn by the most recent frame, retained so a hover can ask
    /// what the pointer is over.
    ///
    /// Assigned inside [#clusterOverlay], which is the one place both
    /// draw paths go through — the static redraw and the animation loop. Setting
    /// it at the call sites instead would leave it stale for exactly as long as
    /// anything was moving.
    private FloorMapClusterOverlay lastClusterOverlay = FloorMapClusterOverlay.EMPTY;

    /// The cluster key pressed on mousedown, resolved on mouseup by the same
    /// click-versus-pan test the background press uses — so a drag that starts on
    /// a cluster still pans the map rather than opening a dialog.
    private String pendingClickClusterKey;

    /// The key of the cluster the pointer is currently over, or `null`.
    /// Tracked so the tooltip's contents are rebuilt when the hovered cluster
    /// changes rather than on every mouse move.
    private String hoveredClusterKey;

    /// The id of the single entity the pointer is currently over, or `null`.
    /// Tracked for the same reason as [#hoveredClusterKey], and mutually
    /// exclusive with it: one panel, describing whatever is under the pointer.
    private String hoveredObjectId;

    /// Resolves an entity id to the name shown to users. Supplied by the owning
    /// tab, which owns the roster; without one the tooltip falls back to ids.
    private Function<String, String> entityNameResolver;

    /// Per-type presentation settings (z-order + default graphic); may be null.
    private List<TypeStyle> typeStyles;

    /// What one map unit means in the real world; `null` on a map with no
    /// scale set, which is the normal state.
    private FloorMapMeasurementUnits measurementUnits;

    /// The facts to render (backgrounds + static facts), from the parser.
    private List<Fact> facts = new ArrayList<>();

    /// Image-bearing facts by key, for decorating animated entities with their configured icon.
    ///
    /// Derived purely from [#facts], so it is rebuilt in [#setFacts] rather than on
    /// every animation frame - which is where it used to be built, scanning every fact 60 times a
    /// second to produce a map that only changes when the facts do. Null means "not yet built".
    private Map<String, Fact> imageFactsByKey = new HashMap<>();

    /// Types the Layers panel has hidden: not drawn and not hit-tested.
    private final Set<String> hiddenTypes = new HashSet<>();
    /// Types the Layers panel has dimmed to 30% opacity.
    private final Set<String> dimmedTypes = new HashSet<>();
    /// Types the Layers panel has locked (Editor): their items can't be moved.
    private final Set<String> lockedTypes = new HashSet<>();
    /// Fact keys belonging to a locked type — recomputed whenever facts change.
    private final Set<String> lockedKeys = new HashSet<>();

    // -------------------------------------------------------------------------
    // Entity tracking (Map tab)
    // -------------------------------------------------------------------------

    /// How far (px) the mouse must move with the button down before the gesture
    /// counts as a deliberate pan rather than click jitter. Without this, the
    /// couple of pixels of movement inside an ordinary click would pause
    /// following the instant it was enabled.
    private static final double PAN_INTENT_THRESHOLD_PX = 4.0;

    /// Below this outstanding follow correction (px) the camera snaps the
    /// remainder instead of gliding, so a damped follow terminates rather than
    /// trailing sub-pixel movements forever.
    private static final double FOLLOW_SNAP_PX = 0.5;

    /// Id of the entity the camera is following, or `null` when not tracking.
    private String trackedObjectId = null;

    /// Which entities are inside which areas at the current timeline instant,
    /// supplied by the owning tab (see [#setAreaMembership]). Drives the
    /// reciprocal containment highlight and the occupant-count badges; empty
    /// until the tab pushes a snapshot, so nothing is decorated by default.
    private FloorMapAreaMembership areaMembership = FloorMapAreaMembership.EMPTY;

    /// Which entities belong to a group the user has switched on in the Groups
    /// panel, and in what colour (see [#setGroupOverlay]). Empty until the tab
    /// pushes one, and empty again whenever no group is highlighted — which is the
    /// default for every group.
    ///
    /// Purely a decoration: group highlight never affects the camera, so nothing
    /// here touches [#trackedObjectId].
    private FloorMapGroupOverlay groupOverlay = FloorMapGroupOverlay.EMPTY;

    /// `true` after a deliberate manual pan while tracking — the highlight
    /// stays but the camera stops following until the user re-selects (or
    /// re-clicks) the tracked entity, which calls
    /// [#setTrackedObjectId(String)] again and clears this flag.
    private boolean followPaused = false;

    /// When `true`, the next follow with a known position hard-centres the
    /// tracked entity instead of dead-zone panning. Set on (re-)selection so
    /// tracking gives immediate visible feedback even when the entity is
    /// already somewhere on screen.
    private boolean centreOnNextFollow = false;

    /// Manual pan distance accumulated since the last mousedown, in px.
    private double manualPanPx = 0;

    // -------------------------------------------------------------------------
    // Playback / animation state
    // -------------------------------------------------------------------------

    /// The entity animation data machine (interpolation, trails, teleport). This
    /// presenter owns only the scheduler loop, camera-follow and drawing; all the
    /// per-entity bookkeeping lives in the shared, unit-tested animator.
    private final FloorMapEntityAnimator animator = new FloorMapEntityAnimator();

    /// `true` while the [#animationCallback] loop is scheduled.
    private boolean animationLoopRunning = false;

    /// The `AnimationScheduler` timestamp of the most recently executed animation
    /// frame.  Used to compute the per-frame delta for advancing animation progress.
    /// Reset to `0` when the loop terminates or is cleared.
    private double lastAnimationTimestamp = 0;

    // -------------------------------------------------------------------------

    @Inject
    public FloorMapCanvasPresenter(final EventBus eventBus,
                                   final FloorMapCanvasView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();
        handleMouseEvents();

        if (getView() != null) {
            getView().setRedrawListener(this::redraw);
            // Apply the bottom-left default view as soon as the canvas has a
            // real size. onResize() self-defers until layout completes, then
            // fires this back.
            getView().setResizeListener(this::maybeApplyInitialView);
            getView().onResize();
        }

        // Perform initial draw
        redraw();
    }

    /// Applies the one-time initial view the first time it can, then leaves the
    /// view alone. Ordering of layout vs. fact loading is not guaranteed, so this
    /// is called from both the resize listener and [#setFacts]; it runs at
    /// most once. Priority:
    ///
    /// 1. An injected view from another tab (Map → Editor) — applied verbatim
    ///    so the first frame matches and nothing jumps.
    /// 2. Zoom-to-fit all content, when facts are present.
    /// 3. The bottom-left origin fallback, when there is no content (does
    ///    *not* lock in, so a later [#setFacts] can still fit).
    private void maybeApplyInitialView() {
        if (initialViewApplied) {
            return;
        }
        final int height = getView().getFocusPanel().getElement().getOffsetHeight();
        if (height <= 0) {
            // Canvas not laid out yet — a later onResize will call back.
            return;
        }

        if (injectedInitialView != null) {
            scale = injectedInitialView[0];
            offsetX = injectedInitialView[1];
            offsetY = injectedInitialView[2];
            initialViewApplied = true;
            redraw();
            return;
        }

        final double[] bounds = getView().getContentMapBounds();
        if (bounds != null && applyFitView(bounds, height)) {
            initialViewApplied = true;
            if (initialViewListener != null) {
                initialViewListener.accept(new double[]{scale, offsetX, offsetY});
            }
            return;
        }

        // No content yet — show the bottom-left origin view but leave the gate
        // open so the first setFacts can still fit.
        applyOriginView(height);
    }

    /// Sets `scale`/`offsetX`/`offsetY` so the given map-space
    /// content bounds are centred and fill the viewport with a [#FIT_MARGIN]
    /// border. A degenerate (zero-extent) axis keeps [#DEFAULT_SCALE] rather
    /// than zooming to the clamp. Does not redraw on its own when it returns
    /// `false`.
    ///
    /// @param b      the content bounds `{minX, minY, maxX, maxY}`
    /// @param height the current viewport height (already known to be `> 0`)
    /// @return `true` if a view was applied
    private boolean applyFitView(final double[] b, final int height) {
        final int width = getView().getFocusPanel().getElement().getOffsetWidth();
        if (width <= 0) {
            return false;
        }
        final double cw = b[2] - b[0];
        final double ch = b[3] - b[1];
        final double usableW = width * (1 - 2 * FIT_MARGIN);
        final double usableH = height * (1 - 2 * FIT_MARGIN);

        double fit = DEFAULT_SCALE;
        final double sx = cw > 1e-9 ? usableW / cw : Double.MAX_VALUE;
        final double sy = ch > 1e-9 ? usableH / ch : Double.MAX_VALUE;
        final double candidate = Math.min(sx, sy);
        if (candidate != Double.MAX_VALUE) {
            fit = candidate;
        }
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));

        // Centre the content's map-space midpoint in the viewport. Screen Y grows
        // downward and screenY = offsetY - scale·mapY, so offsetY gets +scale·cy.
        final double cx = (b[0] + b[2]) / 2;
        final double cy = (b[1] + b[3]) / 2;
        offsetX = width / 2.0 - scale * cx;
        offsetY = height / 2.0 + scale * cy;
        redraw();
        return true;
    }

    /// Positions the view so the map origin (0,0) sits near the bottom-left
    /// corner, inset by [#ORIGIN_INSET_MAJOR_DIVISIONS] of a major grid
    /// division at the default zoom. The empty-map fallback.
    ///
    /// @param height the current viewport height (already known to be `> 0`)
    private void applyOriginView(final int height) {
        // Half a major grid division, in screen pixels, at the default zoom.
        // The grid is drawn with an identity world-to-map matrix, so its
        // effective scale is simply the user zoom (DEFAULT_SCALE).
        final double insetPx = ORIGIN_INSET_MAJOR_DIVISIONS
                * FloorMapGrid.majorDivisionScreenPx(DEFAULT_SCALE, measurementUnits);

        // The origin (0,0) renders at screen pixel (offsetX, offsetY). Inset it
        // from the left and up from the bottom (SVG Y grows downward), so the
        // bottom-left corner reads as (-inset, -inset) in map space.
        scale = DEFAULT_SCALE;
        offsetX = insetPx;
        offsetY = height - insetPx;
        redraw();
    }

    // =========================================================================
    // Public API — called by FloorMapMapPresenter
    // =========================================================================

    /// Notifies the canvas that the timeline is playing or has been paused.
    /// When transitioning to paused, any in-flight animations are allowed to
    /// finish naturally (they will terminate on the next loop iteration).
    ///
    /// @param playing `true` when playback starts, `false` when it stops.
    public void setPlaying(final boolean playing) {
        this.playing = playing;
        animator.setPlaying(playing);
        // Playback state is otherwise visible only as the play button's icon
        // changing, over on the timeline.
        getView().announce(playing
                ? "Playing"
                : "Paused");
    }

    /// Discards all in-flight movement animations and trail data.  Call this
    /// whenever the timeline time jumps non-continuously (scrub, step, loop-around,
    /// stop-at-end) so stale animation state does not carry over.
    ///
    /// Arms a teleport (via [FloorMapEntityAnimator#clear()]) so the
    /// *next* [#setEventObjects] places entities at their new
    /// positions instantly rather than animating from stale positions, even
    /// during playback.
    public void clearAnimationState() {
        animator.clear();
        // Deliberately do NOT force animationLoopRunning = false here. A frame
        // may already be scheduled; clearing the flag and then calling
        // ensureAnimationLoop() (e.g. the teleport path re-arming camera-follow)
        // would start a SECOND loop while the old frame is still pending,
        // double-driving trails/draws. With the data cleared above, any running
        // loop simply finds nothing to do and self-terminates on its next frame
        // (which also resets lastAnimationTimestamp); a stopped loop stays
        // stopped.
        redraw();
    }

    // =========================================================================
    // Mouse event handling
    // =========================================================================

    private void handleMouseEvents() {

        // Check if we clicked on an object in edit mode.
        // Only react to the primary (left) mouse button — right-click is
        // handled by the contextmenu event handler below.
        registerHandler(getView().getFocusPanel().addMouseDownHandler(event -> {
            // Ignore right-click (button 2 in the W3C DOM spec).
            // Without this guard the mousedown sets isDragging = true, but
            // the subsequent mouseup lands on the context menu popup (outside
            // the canvas), leaving isDragging permanently stuck.
            if (event.getNativeEvent().getButton() == 2) {
                return;
            }

            // Shift, Ctrl or Meta (⌘) all act as the multi-select modifier, so a
            // modifier-click toggles an object in/out of the selection — matching
            // the Fact List grid, whose native selection manager also treats
            // Ctrl/Meta as the toggle key.
            final boolean modifier = event.getNativeEvent().getShiftKey()
                    || event.getNativeEvent().getCtrlKey()
                    || event.getNativeEvent().getMetaKey();
            final String hitId = hitObjectId(event.getNativeEvent().getEventTarget());

            // Whatever this press turns out to be, it is not hovering.
            hideHoverTooltip();

            if (editMode) {
                // Area drawing is modal: every LEFT press is either a vertex
                // click or a pan, resolved on mouseup by the PANNING
                // click-vs-pan logic (a press-drag pans, so large rooms can be
                // traced). Other buttons (e.g. middle) are ignored so a
                // habitual middle-click can't commit a spurious vertex.
                if (gesture == Gesture.DRAWING_AREA) {
                    if (event.getNativeEvent().getButton() == NativeEvent.BUTTON_LEFT) {
                        isDragging = true;
                        manualPanPx = 0;
                        lastMouseX = event.getX();
                        lastMouseY = event.getY();
                    }
                    return;
                }

                // Set Scale is modal too: a LEFT press anchors the measuring
                // line, which is then dragged out and released over the far end
                // of the known distance. Panning is suspended for the duration —
                // the whole gesture is one press-drag.
                if (gesture == Gesture.MEASURING_SCALE) {
                    if (event.getNativeEvent().getButton() == NativeEvent.BUTTON_LEFT) {
                        measureStartMap = screenToMapCoords(event.getX(), event.getY());
                        measureCursorX = event.getX();
                        measureCursorY = event.getY();
                        isDragging = true;
                        redraw();
                    }
                    return;
                }

                // (1) A transform handle takes priority over object/background
                // hit-testing so a handle drag starts a scale/rotate gesture.
                // Point glyphs (no image, no area geometry) are fixed screen-size
                // and can't be scaled/rotated, so their handles are drawn greyed
                // and are inert — the press is consumed but starts no gesture.
                final String handle = handleRole(event.getNativeEvent().getEventTarget());
                if (handle != null && !selectedObjectIds.isEmpty()) {
                    if (selectionTransformable()) {
                        beginHandleGesture(handle, event.getX(), event.getY());
                    }
                    return;
                }

                // A real (draggable) object is any non-background, non-area
                // fact, OR a background/area fact that is already selected —
                // so an unselected background or area press still pans (their
                // clickable surface can cover most of the map), but once you
                // have selected one (by clicking it) you can drag it to move it.
                final boolean pansWhenUnselected = hitId != null
                        && (backgroundKeys.contains(hitId) || areaKeys.contains(hitId));
                final boolean onObject = hitId != null
                        && (!pansWhenUnselected || selectedObjectIds.contains(hitId));

                if (onObject) {
                    // A locked layer's items stay selectable but can't be moved:
                    // update the selection like a normal click, but do not begin
                    // a MOVING gesture.
                    if (lockedKeys.contains(hitId)) {
                        if (modifier) {
                            if (!selectedObjectIds.remove(hitId)) {
                                selectedObjectIds.add(hitId);
                            }
                        } else if (!selectedObjectIds.contains(hitId)) {
                            selectedObjectIds.clear();
                            selectedObjectIds.add(hitId);
                        }
                        fireSelectionChanged();
                        gesture = Gesture.NONE;
                        isDragging = false;
                        return;
                    }
                    if (modifier) {
                        // Shift/Ctrl-click toggles the object in the selection.
                        if (!selectedObjectIds.remove(hitId)) {
                            selectedObjectIds.add(hitId);
                        }
                        fireSelectionChanged();
                        gesture = Gesture.NONE;
                        isDragging = false;
                        return;
                    }
                    // Plain click: select just this object (unless it is already
                    // part of a multi-selection — then keep the group) and begin
                    // moving the whole selection.
                    if (!selectedObjectIds.contains(hitId)) {
                        selectedObjectIds.clear();
                        selectedObjectIds.add(hitId);
                        fireSelectionChanged();
                    }
                    gesture = Gesture.MOVING;
                    isDragging = true;
                    hasMoved = false;
                    dragDxMap = 0;
                    dragDyMap = 0;
                    lastMouseX = event.getX();
                    lastMouseY = event.getY();
                    // Report from the press, so the object's current position is
                    // visible before it has been dragged anywhere.
                    updateGestureReadout(event.getX(), event.getY());
                    return;
                }

                // Empty canvas or the background fact.
                if (modifier) {
                    // Shift/Ctrl-drag draws a rubber-band selection marquee.
                    gesture = Gesture.MARQUEE;
                    isDragging = true;
                    hasMoved = false;
                    marqueeStartX = marqueeCurX = event.getX();
                    marqueeStartY = marqueeCurY = event.getY();
                    return;
                }
                // Plain press: a drag pans (matching the read-only Map viewer); a
                // click (no drag) selects the background fact under the cursor, or
                // clears the selection on truly empty canvas. Resolved on mouseup
                // by whether the pointer actually panned, so the selection is left
                // intact during a pan.
                pendingClickSelectId = hitId;
                gesture = Gesture.PANNING;
                isDragging = true;
                manualPanPx = 0;
                lastMouseX = event.getX();
                lastMouseY = event.getY();
                return;
            }

            // A press on a cluster glyph opens its member list — but only if it
            // turns out to be a click. Recorded here and resolved on mouseup, so
            // dragging away from a cluster still pans.
            pendingClickClusterKey = clusterKey(event.getNativeEvent().getEventTarget());

            // Read-only (Map tab) mode: pressing an object shape announces it so
            // the parent presenter can select/track it (the parent filters to
            // its roster). Backgrounds and areas are excluded — their clickable
            // surface can cover most of the map, and this fires on mousedown,
            // so a press over them must stay a plain pan; they remain trackable
            // from the tracking panel. Empty-canvas presses also just pan.
            if (hitId != null
                    && !backgroundKeys.contains(hitId)
                    && !areaKeys.contains(hitId)) {
                MapObjectSelectedEvent.fire(this, hitId);
                // Remember it so this same click's mouseup re-affirms the
                // selection instead of clearing it. The parent responds to the
                // event above by highlighting the object; without this the
                // non-panning-click branch on mouseup treated the click as
                // "clicked empty canvas" and immediately un-highlighted it.
                pendingClickSelectId = hitId;
            }
            gesture = Gesture.PANNING;
            isDragging = true;
            manualPanPx = 0;
            lastMouseX = event.getX();
            lastMouseY = event.getY();
        }));

        // The pointer can leave the canvas without a final mousemove inside it
        // (straight onto the dock, or off the window), which would strand the
        // tooltip over a canvas the user is no longer pointing at.
        registerHandler(getView().getFocusPanel().addMouseOutHandler(event -> {
            // mouseout bubbles from every descendant, so it also fires when the
            // pointer merely crosses between elements INSIDE the canvas — the SVG
            // root to a glyph's shape and back. Those are not exits, and treating
            // them as such would tear the tooltip down and rebuild it repeatedly
            // while the pointer sat on one glyph. A real exit is one where the
            // pointer has gone somewhere outside this panel (or nowhere at all,
            // which is what leaving the window reports).
            final EventTarget related = event.getRelatedTarget();
            final Element panel = getView().getFocusPanel().getElement();
            if (!Element.is(related)
                || !panel.isOrHasChild(Element.as(related))) {
                hideHoverTooltip();
            }
        }));

        registerHandler(getView().getMouseMoveHandlers().addMouseMoveHandler(event -> {
            // Guard: if no mouse button is actually pressed, cancel any
            // stale drag state. This catches the case where mousedown fired
            // on the canvas but mouseup landed outside (on a toolbar button
            // or dialog), so the canvas never received the mouseup event.
            // The DOM 'buttons' property returns a bitmask of currently held
            // buttons (W3C spec); 0 means nothing is pressed.
            if (isDragging && nativeButtons(event.getNativeEvent()) == 0) {
                if (gesture == Gesture.MEASURING_SCALE) {
                    // The release landed off-canvas, so this measurement is lost —
                    // but the mode stays live so the user can simply drag again.
                    isDragging = false;
                    measureStartMap = null;
                    redraw();
                    return;
                }
                if (gesture == Gesture.DRAWING_AREA) {
                    // Only the mid-draw pan is stale — the modal drawing gesture
                    // and its draft survive (no button is held between vertex
                    // clicks by design).
                    isDragging = false;
                    manualPanPx = 0;
                } else {
                    isDragging = false;
                    hasMoved = false;
                    hideGestureReadout();
                    // A vertex drag whose mouseup was lost off-canvas must clear
                    // its editing state too, else factsForDraw() keeps rendering
                    // the never-persisted working vertices forever.
                    if (gesture == Gesture.MOVING_VERTEX) {
                        clearVertexEditState();
                    }
                    gesture = Gesture.NONE;
                    pendingTransform = null;
                    return;
                }
            }

            // Hovering describes what is under the pointer — a cluster's members,
            // the question the count on the glyph raises but cannot answer, or a
            // single entity's details. Only while nothing else is in progress:
            // during a drag the pointer is committed to something else, and a
            // panel trailing the gesture would be in the way.
            if (!isDragging && gesture == Gesture.NONE) {
                updateHover(event.getNativeEvent().getEventTarget(),
                        event.getX(), event.getY());
            } else {
                hideHoverTooltip();
            }

            // Track the live cursor for the measuring line, and keep the map
            // still: dragging here draws the line rather than panning.
            if (gesture == Gesture.MEASURING_SCALE) {
                measureCursorX = event.getX();
                measureCursorY = event.getY();
                if (measureStartMap != null) {
                    redraw();
                }
                return;
            }

            // Track the live cursor for the area-draft rubber band (both while
            // hovering between vertex clicks and during a mid-draw pan).
            if (gesture == Gesture.DRAWING_AREA) {
                areaCursorX = event.getX();
                areaCursorY = event.getY();
                if (!isDragging) {
                    if (!areaDraftMap.isEmpty()) {
                        redraw();
                    }
                    return;
                }
            }

            if (isDragging) {
                final double deltaX = event.getX() - lastMouseX;
                final double deltaY = event.getY() - lastMouseY;

                switch (gesture) {
                    case MOVING:
                        // Accumulate the drag in map space (Y-up) for live feedback
                        // and a single transform applied to the selection on drop.
                        // The shared viewport converts the screen delta to a map
                        // delta (scale + Y flip) — one projection code path.
                        final double[] dMap = new FloorMapViewport(scale, offsetX, offsetY)
                                .dragItemMapDelta(Y_FLIP, deltaX, deltaY);
                        dragDxMap += dMap[0];
                        dragDyMap += dMap[1];
                        pendingTransform = FloorMapTransformationMatrix.translate(
                                dragDxMap, dragDyMap);
                        hasMoved = true;
                        redraw();
                        // After the redraw: the readout measures the geometry the
                        // view has just laid out.
                        updateGestureReadout(event.getX(), event.getY());
                        break;
                    case MARQUEE:
                        marqueeCurX = event.getX();
                        marqueeCurY = event.getY();
                        hasMoved = true;
                        redraw();
                        break;
                    case SCALING:
                        pendingTransform = computeScaleTransform(event.getX(), event.getY());
                        hasMoved = true;
                        redraw();
                        updateGestureReadout(event.getX(), event.getY());
                        break;
                    case MOVING_VERTEX:
                        if (workingVertices != null && editingWorldToMap != null
                                && editingVertexIndex >= 0
                                && editingVertexIndex < workingVertices.length) {
                            final double[] map = screenToMapCoords(event.getX(), event.getY());
                            final double[] local = editingWorldToMap.inverse()
                                    .transformPoint(map[0], map[1]);
                            workingVertices[editingVertexIndex] = new double[]{local[0], local[1]};
                            hasMoved = true;
                            redraw();
                        }
                        break;
                    case ROTATING: {
                        final double[] cur = screenToMapCoords(event.getX(), event.getY());
                        final double a0 = Math.atan2(gestureStartMapY - gestureCentreY,
                                gestureStartMapX - gestureCentreX);
                        final double a1 = Math.atan2(cur[1] - gestureCentreY,
                                cur[0] - gestureCentreX);
                        double deg = Math.toDegrees(a1 - a0);
                        if (event.getNativeEvent().getShiftKey()) {
                            // Snap to 15° increments.
                            deg = Math.round(deg / 15.0) * 15.0;
                        }
                        pendingTransform = FloorMapTransformationMatrix.rotateAbout(
                                deg, gestureCentreX, gestureCentreY);
                        hasMoved = true;
                        redraw();
                        break;
                    }
                    case PANNING:
                    default:
                        // Pan the map. A deliberate manual pan takes the camera off
                        // the tracked entity, so pause following until it is
                        // re-selected — but ignore the few pixels of jitter inside
                        // an ordinary click, which would otherwise pause following
                        // the moment click-to-track enabled it.
                        manualPanPx += Math.abs(deltaX) + Math.abs(deltaY);
                        if (manualPanPx > PAN_INTENT_THRESHOLD_PX) {
                            followPaused = true;
                        }
                        offsetX += deltaX;
                        offsetY += deltaY;
                        redraw();
                        break;
                }

                lastMouseX = event.getX();
                lastMouseY = event.getY();
            }
        }));

        //noinspection unused event
        registerHandler(getView().getMouseUpHandlers().addMouseUpHandler(event -> {
            // Set Scale: the release ends the measurement and hands its map-space
            // length to the handler, which asks what that distance really is.
            if (gesture == Gesture.MEASURING_SCALE) {
                isDragging = false;
                if (measureStartMap != null) {
                    finishScaleMeasurement(event.getX(), event.getY());
                }
                return;
            }

            // Area drawing is modal: resolve this press as vertex-click vs pan
            // and keep the gesture alive (the generic reset below must not run).
            if (gesture == Gesture.DRAWING_AREA) {
                final boolean click = isDragging && manualPanPx <= PAN_INTENT_THRESHOLD_PX;
                isDragging = false;
                hasMoved = false;
                manualPanPx = 0;
                if (click) {
                    if (areaDraftMap.size() >= AREA_MIN_VERTICES
                            && isNearFirstDraftVertex(event.getX(), event.getY())) {
                        finishAreaDrawing();
                    } else {
                        areaDraftMap.add(screenToMapCoords(event.getX(), event.getY()));
                        redraw();
                    }
                }
                return;
            }

            hideGestureReadout();

            final Gesture finished = gesture;
            final FloorMapTransformationMatrix transform = pendingTransform;
            final boolean moved = hasMoved;
            final double panned = manualPanPx;
            final String clickSelectId = pendingClickSelectId;
            final String clickClusterKey = pendingClickClusterKey;

            dragDxMap = 0;
            dragDyMap = 0;
            isDragging = false;
            hasMoved = false;
            gesture = Gesture.NONE;
            pendingTransform = null;
            pendingClickSelectId = null;
            pendingClickClusterKey = null;

            if (finished == Gesture.MOVING
                    || finished == Gesture.SCALING
                    || finished == Gesture.ROTATING) {
                // Persist the move/scale/rotate as a single map-space transform
                // of the whole selection.
                if (moved && transform != null && !selectedObjectIds.isEmpty()
                        && dragHandler != null) {
                    // Never transform items on a locked layer (covers the case
                    // where a layer was locked after its items were selected).
                    final List<String> movable = new ArrayList<>(selectedObjectIds.size());
                    for (final String id : selectedObjectIds) {
                        if (!lockedKeys.contains(id)) {
                            movable.add(id);
                        }
                    }
                    if (!movable.isEmpty()) {
                        dragHandler.onTransform(movable, transform);
                    }
                }
            } else if (finished == Gesture.MOVING_VERTEX) {
                // Persist the edited/inserted vertex (skip a pure click that
                // didn't move an existing vertex, and locked layers).
                //
                // The selection check matters: it is what an abandoned edit is
                // recognised by everywhere else on this path, and without it a
                // vertex drag that was cancelled — or whose area was deselected
                // while the button was held — still wrote its new geometry here.
                if ((moved || vertexInserted) && editingAreaKey != null
                        && selectedObjectIds.contains(editingAreaKey)
                        && workingVertices != null && workingVertices.length >= AREA_MIN_VERTICES
                        && geometryHandler != null
                        && !lockedKeys.contains(editingAreaKey)) {
                    geometryHandler.onGeometryEdited(editingAreaKey, workingVertices);
                }
                clearVertexEditState();
            } else if (finished == Gesture.MARQUEE) {
                // Select every fact the rubber-band touched, adding to the
                // existing selection (the marquee is a Shift/Ctrl gesture).
                final double[] rect = {
                        Math.min(marqueeStartX, marqueeCurX),
                        Math.min(marqueeStartY, marqueeCurY),
                        Math.max(marqueeStartX, marqueeCurX),
                        Math.max(marqueeStartY, marqueeCurY)};
                // The marquee never selects items on a locked layer, nor the
                // background: its screen bounds cover the whole floor plan, so
                // every marquee would silently include it and the following drag
                // would translate the entire plan along with the real selection.
                for (final String id : getView().hitTestScreenRect(rect)) {
                    if (!lockedKeys.contains(id) && !backgroundKeys.contains(id)) {
                        selectedObjectIds.add(id);
                    }
                }
                fireSelectionChanged();
                redraw();
            } else if (finished == Gesture.PANNING
                    && panned <= PAN_INTENT_THRESHOLD_PX) {
                // A press that didn't pan is a click. A cluster is not an entity,
                // so clicking one lists its members and deliberately leaves the
                // selection and any tracking alone — it is a request to look
                // inside, not to change what is being followed.
                if (clickClusterKey != null) {
                    final FloorMapCluster clicked =
                            lastClusterOverlay.getCluster(clickClusterKey);
                    if (clicked != null) {
                        MapClusterSelectedEvent.fire(this, clicked);
                    }
                } else if (clickSelectId != null) {
                    selectedObjectIds.clear();
                    selectedObjectIds.add(clickSelectId);
                    fireSelectionChanged();
                } else if (!selectedObjectIds.isEmpty()) {
                    if (trackedObjectId != null) {
                        // Tracking owns the highlight, so a click on empty canvas must not
                        // clear it. Doing so left trackedObjectId set with nothing
                        // highlighted: the camera kept following, and the Tracking panel's
                        // row stayed selected while the map showed no selection. That is
                        // exactly the state handleViewKeys() declines to create, which is
                        // why Escape is not handled on the Map tab — stopping a follow goes
                        // through the Tracking panel, which keeps both ends in step.
                        // A stray click should not be a second, silent way in.
                        return;
                    }
                    selectedObjectIds.clear();
                    fireSelectionChanged();
                    // Repaint explicitly rather than relying on the selection handler:
                    // only the Editor tab installs one (FloorMapEditorPresenter:293), so on
                    // the Map tab fireSelectionChanged() notifies nobody and the cleared
                    // highlight stayed painted until something unrelated redrew.
                    redraw();
                }
            }
        }));

        // Mouse Wheel (Zoom towards cursor)
        registerHandler(getView().getMouseWheelHandlers().addMouseWheelHandler(event -> {
            event.preventDefault();
            // The zoom moves the map out from under the tooltip's anchor, and
            // re-clusters at the new scale, so what it names may not survive.
            hideHoverTooltip();

            // Note: zooming deliberately does NOT pause following — zooming in
            // on a tracked entity is the natural way to watch it, and the
            // dead-zone follow simply keeps it in view at the new zoom level.
            // Delegate the zoom-towards-cursor + clamp maths to the shared,
            // unit-tested viewport, then read the updated pan/zoom back.
            final boolean zoomIn = event.getNativeDeltaY() <= 0;
            final FloorMapViewport vp = new FloorMapViewport(scale, offsetX, offsetY);
            vp.zoom(event.getX(), event.getY(), zoomIn);
            scale = vp.getScale();
            offsetX = vp.getOffsetX();
            offsetY = vp.getOffsetY();

            redraw();
        }));

        // Right-click context menu — suppress the browser default and fire a MapContextMenuEvent.
        // Only fires in edit mode; in read-only (Map tab) mode the browser default is allowed.
        registerHandler(getView().getFocusPanel().addDomHandler(event -> {
            if (!editMode) {
                return;
            }
            event.preventDefault();
            event.stopPropagation();

            // While drawing an area, right-click undoes the last vertex
            // (misclicks are the dominant error when tracing) — or cancels an
            // empty draft. The context menu never opens mid-draw.
            // Right-click leaves the Set Scale mode: it is the natural "get me
            // out of this" gesture, and a context menu opening mid-measure would
            // strand the mode with no obvious way back.
            if (gesture == Gesture.MEASURING_SCALE) {
                cancelScaleMeasurement();
                return;
            }

            if (gesture == Gesture.DRAWING_AREA) {
                isDragging = false;
                hasMoved = false;
                if (areaDraftMap.isEmpty()) {
                    cancelAreaDrawing();
                } else {
                    //noinspection SequencedCollectionMethodCanBeUsed
                    areaDraftMap.remove(areaDraftMap.size() - 1);
                    redraw();
                }
                return;
            }

            // Abandon any in-flight gesture. The mouseup that would normally end
            // it lands on the popup, not the canvas, so without this a
            // right-click mid-drag leaves the gesture (and any working vertices)
            // live indefinitely — see abortGesture().
            abortGesture();

            final int clientX = event.getNativeEvent().getClientX();
            final int clientY = event.getNativeEvent().getClientY();

            // Right-clicking an area vertex handle opens a vertex-specific menu
            // (offering "Delete Vertex") rather than the object/canvas menu.
            final String contextRole = handleRole(event.getNativeEvent().getEventTarget());
            if (contextRole != null && contextRole.startsWith("vertex-")) {
                final Fact area = selectedAreaFact();
                if (area != null) {
                    final Element panel = getView().getFocusPanel().getElement();
                    final double[] vertexMap = screenToMapCoords(
                            clientX - panel.getAbsoluteLeft(),
                            clientY - panel.getAbsoluteTop());
                    MapContextMenuEvent.fireVertex(this, area.getKey(),
                            vertexMap[0], vertexMap[1], clientX, clientY,
                            parseHandleIndex(contextRole, "vertex-"));
                }
                return;
            }

            // Convert viewport-relative client coordinates to element-relative
            // coordinates, matching the coordinate space used by event.getX()/getY()
            // and the zoom/pan model (offsetX, offsetY, scale).
            final Element panelElement = getView().getFocusPanel().getElement();
            final double elementX = clientX - panelElement.getAbsoluteLeft();
            final double elementY = clientY - panelElement.getAbsoluteTop();

            // Determine whether an object was right-clicked. Shares the
            // mousedown hit-test rather than repeating its prefix rules: a
            // wrapper group, a selection handle and a cluster glyph all carry
            // ids that are not object keys, and right-clicking one must not open
            // an object menu targeting a key that does not exist. (The rules were
            // duplicated here and drifted — the cluster prefix was missing.)
            final String objectId = hitObjectId(event.getNativeEvent().getEventTarget());

            // Convert element-relative position to map-space coordinates
            final double[] mapCoords = screenToMapCoords(elementX, elementY);

            MapContextMenuEvent.fire(this, objectId, mapCoords[0], mapCoords[1], clientX, clientY);
        }, ContextMenuEvent.getType()));

        // Escape clears the selection, so you can pan again after selecting the
        // full-canvas background (an unselected background press pans).
        registerHandler(getView().getFocusPanel().addKeyDownHandler(event -> {
            // While drawing an area: Escape discards the draft, Enter closes
            // the polygon. (No clash with Escape-clears-selection — starting a
            // draw clears the selection.)
            if (editMode && gesture == Gesture.MEASURING_SCALE
                    && event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                cancelScaleMeasurement();
                return;
            }
            if (editMode && gesture == Gesture.DRAWING_AREA) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                    cancelAreaDrawing();
                    return;
                }
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER
                        && areaDraftMap.size() >= AREA_MIN_VERTICES) {
                    finishAreaDrawing();
                    return;
                }
            }
            // Escape during a transform or vertex drag cancels the gesture, before the
            // clear-selection branch below can see it.
            //
            // Without this, those gestures fell through to clear-selection, which only
            // *accidentally* cancelled MOVING, SCALING and ROTATING - their mouseup commit
            // happens to check the selection, so emptying it suppressed the write. The
            // vertex commit does not check the selection (it gates on moved/inserted, the
            // editing key and locked layers), so Escape mid-vertex-drag looked like a
            // cancel and then persisted the new geometry anyway on mouseup. Cancelling
            // explicitly makes all four behave the same and stops the correctness of the
            // other three resting on a coincidence.
            //
            // A second Escape then clears the selection, as before.
            if (editMode
                    && event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE
                    && isAbortableGesture(gesture)) {
                abortGesture();
                // The abandoned preview - a displaced object, or a dragged vertex - is
                // still painted until something repaints.
                redraw();
                return;
            }

            if (editMode
                    && event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE
                    && !selectedObjectIds.isEmpty()) {
                selectedObjectIds.clear();
                fireSelectionChanged();
                return;
            }

            // Everything above is edit-mode gesture handling. Below is the map's
            // general keyboard operation, which applies on both tabs — panning and
            // zooming were previously reachable only by mouse drag and scroll
            // wheel, so a keyboard user could focus the map and then not move it.
            handleViewKeys(event);
        }));

        // Double-click closes the in-progress area polygon. The dblclick's two
        // component clicks have already appended two near-coincident vertices,
        // so drop the duplicate before closing.
        registerHandler(getView().getFocusPanel().addDoubleClickHandler(event -> {
            if (gesture != Gesture.DRAWING_AREA) {
                return;
            }
            event.preventDefault();
            if (areaDraftMap.size() >= 2) {
                //noinspection SequencedCollectionMethodCanBeUsed
                final double[] last = mapToScreen(
                        areaDraftMap.get(areaDraftMap.size() - 1));
                final double[] prev = mapToScreen(
                        areaDraftMap.get(areaDraftMap.size() - 2));
                final double dx = last[0] - prev[0];
                final double dy = last[1] - prev[1];
                if (dx * dx + dy * dy
                        <= AREA_CLOSE_RADIUS_PX * AREA_CLOSE_RADIUS_PX) {
                    //noinspection SequencedCollectionMethodCanBeUsed
                    areaDraftMap.remove(areaDraftMap.size() - 1);
                }
            }
            if (areaDraftMap.size() >= AREA_MIN_VERTICES) {
                finishAreaDrawing();
            } else {
                redraw();
            }
        }));
    }

    /// On-screen distance one arrow key pans the map.
    private static final double KEY_PAN_PX = 40;

    /// Multiplier applied to [#KEY_PAN_PX] when Shift is held, so crossing a
    /// large floor plan does not take fifty keypresses.
    private static final double KEY_PAN_FAST_FACTOR = 5;

    /// Keyboard operation of the view: pan, zoom, reset, clear selection, and open
    /// the context menu.
    ///
    /// Bindings deliberately mirror every mouse capability the canvas has, since
    /// a map you can focus but not move is no more usable than one you cannot focus
    /// at all:
    ///
    /// - **Arrows** pan; with **Shift**, five times as
    ///   far.
    /// - **+** / **-** (main row or numeric keypad)
    ///   zoom about the centre of the viewport. The wheel zooms towards the
    ///   cursor, but a keyboard user has no cursor to zoom towards, and the
    ///   viewport centre is the one point they can be sure of.
    /// - **0** resets to the fit-everything view — the escape hatch
    ///   from having panned or zoomed into empty space, which is easy to do
    ///   without a visible scrollbar to say where you are.
    ///
    /// Escape is deliberately *not* handled here. In edit mode the caller
    /// has already dealt with it (cancel gesture, else clear selection). On the Map
    /// tab, where selection is the tracking highlight, clearing it here would leave
    /// `trackedObjectId` set and the Tracking panel's row still selected —
    /// tracking with no highlight, and a grid disagreeing with the map. Stopping a
    /// follow has to go through the Tracking panel, whose Stop Tracking button is
    /// already keyboard-reachable and keeps both ends in step.
    ///
    /// - **Enter** / **Space** opens the context menu
    ///   for the selection — see [#openKeyboardContextMenu()]. Edit mode
    ///   only, and only when no gesture is in progress, matching the mouse
    ///   path: `MapContextMenuEvent` is handled by the Editor tab alone,
    ///   and a menu must never open mid-draw.
    ///
    /// Tab is untouched, so it always leaves the map: entity-by-entity traversal
    /// lives in the Tracking panel's grid, which is a real navigable list and is
    /// named as this map's text alternative. Spending the map's own arrow keys on
    /// walking entities would have cost panning and gained a worse version of a
    /// control that already exists.
    private void handleViewKeys(final KeyDownEvent event) {
        final double step = event.isShiftKeyDown()
                ? KEY_PAN_PX * KEY_PAN_FAST_FACTOR
                : KEY_PAN_PX;

        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_LEFT:
                panByKeyboard(event, step, 0);
                break;
            case KeyCodes.KEY_RIGHT:
                panByKeyboard(event, -step, 0);
                break;
            case KeyCodes.KEY_UP:
                panByKeyboard(event, 0, step);
                break;
            case KeyCodes.KEY_DOWN:
                panByKeyboard(event, 0, -step);
                break;
            case KeyCodes.KEY_ENTER:
            case KeyCodes.KEY_SPACE:
                // Same guards as the mouse contextmenu handler: only the Editor tab
                // handles MapContextMenuEvent, and no menu opens mid-gesture. Note
                // the preventDefault sits *inside* the guard — swallowing Enter on
                // the Map tab, where nothing would open, would be taking a key away
                // for nothing.
                if (editMode && gesture == Gesture.NONE) {
                    event.preventDefault();
                    openKeyboardContextMenu();
                }
                break;
            default:
                handleZoomKeys(event);
                break;
        }
    }

    /// `keyCode` for the main-row `=`/`+` key in Chrome, Safari
    /// and Edge.
    ///
    /// These have to be spelled out because [KeyCodes] has no constants for
    /// the punctuation keys, and because a `keydown` carries a key code rather
    /// than a character: there is no `'+'` to compare against, only the code
    /// of the physical key that produces `+` when shifted. Firefox
    /// historically reports different values for exactly these two keys, so both
    /// sets are accepted.
    private static final int KEY_EQUALS = 187;
    private static final int KEY_EQUALS_FIREFOX = 61;
    /// `keyCode` for the main-row `-`/`_` key.
    private static final int KEY_DASH = 189;
    private static final int KEY_DASH_FIREFOX = 173;

    /// Handles the zoom and reset-view keys.
    private void handleZoomKeys(final KeyDownEvent event) {
        switch (event.getNativeKeyCode()) {
            case KEY_EQUALS:
            case KEY_EQUALS_FIREFOX:
            case KeyCodes.KEY_NUM_PLUS:
                zoomByKeyboard(event, true);
                break;
            case KEY_DASH:
            case KEY_DASH_FIREFOX:
            case KeyCodes.KEY_NUM_MINUS:
                zoomByKeyboard(event, false);
                break;
            case KeyCodes.KEY_ZERO:
            case KeyCodes.KEY_NUM_ZERO:
                event.preventDefault();
                // Re-arm the one-shot initial view and let the normal path apply
                // it, so "reset" lands on exactly the view the map opened with
                // rather than a second, subtly different idea of "fit".
                initialViewApplied = false;
                maybeApplyInitialView();
                getView().announce("View reset. Zoom " + zoomPercentText());
                refreshAccessibleSummary();
                redraw();
                break;
            default:
                // Not ours — leave it to the browser.
                break;
        }
    }

    /// Pans the view by a keyboard step.
    ///
    /// A deliberate pan pauses camera-follow, exactly as a mouse drag does: the
    /// user has just said where they want to look, and having the camera drag them
    /// back to the tracked entity would override that. This is why the pan goes
    /// through the same `followPaused` flag rather than only moving the
    /// offsets.
    private void panByKeyboard(final KeyDownEvent event,
                               final double dxPx,
                               final double dyPx) {
        event.preventDefault();
        // Otherwise the arrow also scrolls whatever the map sits inside, moving the
        // map twice for one keypress.
        event.stopPropagation();

        hideHoverTooltip();
        if (trackedObjectId != null) {
            followPaused = true;
        }

        final FloorMapViewport vp = new FloorMapViewport(scale, offsetX, offsetY);
        vp.pan(dxPx, dyPx);
        offsetX = vp.getOffsetX();
        offsetY = vp.getOffsetY();
        redraw();
    }

    /// Zooms one step about the centre of the viewport.
    private void zoomByKeyboard(final KeyDownEvent event, final boolean zoomIn) {
        event.preventDefault();
        event.stopPropagation();

        hideHoverTooltip();

        final Element panel = getView().getFocusPanel().getElement();
        final FloorMapViewport vp = new FloorMapViewport(scale, offsetX, offsetY);
        // Zoom about the viewport centre: the wheel path uses the cursor position,
        // which does not exist here.
        vp.zoom(panel.getOffsetWidth() / 2.0, panel.getOffsetHeight() / 2.0, zoomIn);
        scale = vp.getScale();
        offsetX = vp.getOffsetX();
        offsetY = vp.getOffsetY();

        getView().announce("Zoom " + zoomPercentText());
        refreshAccessibleSummary();
        redraw();
    }

    /// Opens the context menu from the keyboard, anchored to the selection.
    ///
    /// The mouse path takes its target and its position from the pointer. A
    /// keyboard-triggered menu has neither, so the selection supplies both: the
    /// selected object is the target, and its on-screen frame is where the menu
    /// opens. Previously a keyboard `contextmenu` (Shift+F10 or the Menu key)
    /// hit-tested the focus panel itself, found no object id, and so could only ever
    /// open the canvas menu — the per-object actions were unreachable without a
    /// mouse.
    ///
    /// With nothing selected this opens the canvas menu at the viewport centre,
    /// which is the keyboard equivalent of right-clicking empty space.
    ///
    /// Callers must have checked `editMode` and that no gesture is in
    /// progress — see [#handleViewKeys].
    private void openKeyboardContextMenu() {
        final Element panel = getView().getFocusPanel().getElement();
        final String objectId = selectedObjectIds.isEmpty()
                ? null
                : selectedObjectIds.iterator().next();

        // Anchor on the selection frame's centre when there is one, so the menu
        // opens beside the thing it acts on rather than in the middle of the map.
        final double[] frame = objectId != null
                ? getView().getSelectionFrame()
                : null;
        final double elementX = frame != null
                ? (frame[0] + frame[2]) / 2
                : panel.getOffsetWidth() / 2.0;
        final double elementY = frame != null
                ? (frame[1] + frame[3]) / 2
                : panel.getOffsetHeight() / 2.0;

        final double[] mapCoords = screenToMapCoords(elementX, elementY);
        // MapContextMenuEvent positions the popup in viewport coordinates, so the
        // element-relative anchor has to be offset by the panel's own position.
        final int clientX = (int) Math.round(elementX + panel.getAbsoluteLeft());
        final int clientY = (int) Math.round(elementY + panel.getAbsoluteTop());

        MapContextMenuEvent.fire(this, objectId, mapCoords[0], mapCoords[1],
                clientX, clientY);
    }

    /// Converts element-relative coordinates to map-space coordinates by
    /// reversing the zoom/pan transform and then applying the inverse of
    /// the background transformation matrix.
    ///
    /// The input coordinates should be relative to the FocusPanel element
    /// (matching the coordinate space of `MouseEvent.getX()/getY()`
    /// and the zoom/pan model's `offsetX`/`offsetY`).
    /// Viewport-relative client coordinates must be converted first by
    /// subtracting the element's absolute position.
    ///
    /// @param screenX the X coordinate relative to the FocusPanel element
    /// @param screenY the Y coordinate relative to the FocusPanel element
    /// @return a two-element array `{mapX, mapY}` in map space
    private double[] screenToMapCoords(final double screenX, final double screenY) {
        // Delegate to the shared, unit-tested viewport maths (Y_FLIP is the
        // Y-up→Y-down background); this is the inverse of the draw pipeline.
        return new FloorMapViewport(scale, offsetX, offsetY)
                .screenToMap(screenX, screenY, Y_FLIP);
    }

    /// Returns the W3C DOM `buttons` property from a native mouse event.
    ///
    /// GWT's `NativeEvent` doesn't expose `getButtons()`, so we
    /// access it via JSNI. The `buttons` property is a bitmask of
    /// currently pressed buttons (1 = primary, 2 = secondary, 4 = auxiliary).
    /// Returns `0` when no button is pressed.
    ///
    /// @param event the native event to query
    /// @return the `buttons` bitmask, or 0 if unsupported
    private static native int nativeButtons(com.google.gwt.dom.client.NativeEvent event) /*-{
        return event.buttons || 0;
    }-*/;

    // =========================================================================
    // Drawing
    // =========================================================================

    private void redraw() {
        final List<FloorMapObject> overlay =
                buildAnimatedDrawList(/* no scheduler timestamp here; the animator ages the fade */ 0.0);
        final List<Fact> drawFacts =
                FloorMapZOrder.sort(visibleFacts(factsExcludingOverlay(overlay)), typeStyles);
        final List<FloorMapObject> drawEvents = visibleEvents(overlay);
        final FloorMapAreaOverlay areas = areaOverlay();
        getView().draw(scale, offsetX, offsetY,
                drawFacts,
                drawEvents, selectedObjectIds, typeStyles, showGrid, dimmedTypes,
                gesture == Gesture.MARQUEE ? currentMarqueeRect() : null,
                editMode && !selectedObjectIds.isEmpty() && gesture != Gesture.MARQUEE,
                selectionTransformable(),
                gesture == Gesture.DRAWING_AREA ? currentAreaDraftPx() : null,
                areas,
                clusterOverlay(drawFacts, drawEvents),
                FloorMapHighlight.of(groupOverlay, areas),
                currentMeasureLinePx());
    }

    /// Which entities this frame merges into summary glyphs, because they are
    /// closer together on screen than a glyph is wide.
    ///
    /// Computed from the **already-filtered** draw lists, so a
    /// hidden layer's entities are not counted, and from the live `scale`,
    /// so the merge distance tracks the zoom without any per-document
    /// configuration.
    ///
    /// Off in edit mode regardless of the toggle: the Editor tab's whole job is
    /// placing individual objects, and an object merged into a cluster cannot be
    /// dragged.
    ///
    /// @param drawFacts  the facts about to be drawn
    /// @param drawEvents the event entities about to be drawn
    /// @return the overlay; never `null`
    private FloorMapClusterOverlay clusterOverlay(final List<Fact> drawFacts,
                                                  final List<FloorMapObject> drawEvents) {
        final FloorMapClusterOverlay computed;
        if (!clusterNearbyEntities || editMode) {
            computed = FloorMapClusterOverlay.EMPTY;
        } else {
            // The focused entity is clustered along with everything else; its
            // cluster is then anchored on it and drawn as it (ring, name), which
            // is what keeps one glyph on screen instead of two overlapping ones.
            final Set<String> focused = new HashSet<>(selectedObjectIds);
            if (trackedObjectId != null) {
                focused.add(trackedObjectId);
            }
            computed = FloorMapClusterOverlay.compute(drawFacts, drawEvents,
                    FloorMapClusterOverlay.mapThreshold(CLUSTER_RADIUS_PX, scale),
                    focused);
        }
        lastClusterOverlay = computed;
        // A tooltip naming a cluster that this frame dissolved is stale, and no
        // mouse movement is needed to reach that state — a data refresh alone
        // can.
        if (hoveredClusterKey != null && computed.getCluster(hoveredClusterKey) == null) {
            hideHoverTooltip();
        }
        return computed;
    }

    /// Describes whatever the pointer is over: a cluster, a single entity, or
    /// nothing.
    ///
    /// Clusters are asked first because they are drawn on top and stand in for
    /// the entities beneath them — a glyph the user can see must win over one they
    /// cannot.
    ///
    /// @param target    the event target under the pointer, for the object hit test
    /// @param cursorXPx the cursor position in element pixels
    /// @param cursorYPx the cursor position in element pixels
    private void updateHover(final EventTarget target,
                             final double cursorXPx,
                             final double cursorYPx) {
        if (!updateClusterHover(cursorXPx, cursorYPx)) {
            updateObjectHover(target);
        }
    }

    /// Shows, moves or hides the cluster tooltip for the pointer's position.
    ///
    /// Only the *change* of hovered cluster does any work: the panel is
    /// anchored to the glyph, not the cursor, so moving within one glyph needs no
    /// update, and rebuilding a list of names on every mouse move would be real
    /// DOM churn for no visible difference.
    ///
    /// Does **not** hide the panel when the pointer is over no
    /// cluster — a single entity may be under it, and tearing the panel down here
    /// only to rebuild it in [#updateObjectHover] would flicker.
    ///
    /// @param cursorXPx the cursor position in element pixels
    /// @param cursorYPx the cursor position in element pixels
    /// @return `true` if the pointer is over a cluster, which has now been
    ///         described
    private boolean updateClusterHover(final double cursorXPx, final double cursorYPx) {
        if (lastClusterOverlay.isEmpty()) {
            return false;
        }
        final double[] mapPoint = screenToMapCoords(cursorXPx, cursorYPx);
        // The glyph's own half-width, converted by the same route the merge
        // distance takes, so the hit area tracks the zoom exactly as the glyph
        // does.
        final FloorMapCluster cluster = lastClusterOverlay.clusterNear(
                mapPoint[0], mapPoint[1],
                FloorMapClusterOverlay.mapThreshold(CLUSTER_HIT_RADIUS_PX, scale));

        if (cluster == null) {
            return false;
        }
        if (cluster.getKey().equals(hoveredClusterKey)) {
            return true;
        }

        hoveredClusterKey = cluster.getKey();
        hoveredObjectId = null;
        final List<String> names = new ArrayList<>(cluster.size());
        for (final String memberId : cluster.getMemberIds()) {
            names.add(displayNameOrId(memberId));
        }
        // Names are resolved before capping, so the cap counts what the user
        // would have read rather than what the roster happened to know.
        final double[] anchorPx = mapToScreen(
                new double[]{cluster.getMapX(), cluster.getMapY()});
        getView().setHoverTooltip(
                FloorMapClusterLabel.captionFor(cluster, entityNameResolver),
                FloorMapClusterLabel.hoverNames(names, CLUSTER_TOOLTIP_MAX_NAMES),
                anchorPx[0], anchorPx[1]);
        return true;
    }

    /// Shows, moves or hides the tooltip describing the single entity under the
    /// pointer — the same question the cluster tooltip answers for a crowd, asked
    /// of one glyph: what is this, and where is it standing?
    ///
    /// Hit-tested through the DOM rather than geometrically, so the pointer has
    /// to be over the drawn shape itself — exactly the surface a click acts on.
    /// Backgrounds and areas are excluded for the reason the read-only click
    /// handler excludes them: their clickable surface can cover most of the map,
    /// so a tooltip on them would appear almost wherever the pointer rested.
    ///
    /// Like the cluster tooltip, only a *change* of hovered entity
    /// rebuilds anything.
    ///
    /// @param target the event target under the pointer
    private void updateObjectHover(final EventTarget target) {
        final String id = hitObjectId(target);
        if (id == null
                || backgroundKeys.contains(id)
                || areaKeys.contains(id)) {
            hideHoverTooltip();
            return;
        }
        if (id.equals(hoveredObjectId)) {
            return;
        }
        // Anchored on the entity's own position, so the panel holds still while
        // the pointer moves across the glyph. No position means nothing to
        // describe or anchor to.
        final double[] anchorMap = entityMapPosition(id);
        if (anchorMap == null) {
            hideHoverTooltip();
            return;
        }

        hoveredObjectId = id;
        hoveredClusterKey = null;
        final String caption = FloorMapHoverDetail.caption(id, displayName(id));
        final double[] anchorPx = mapToScreen(anchorMap);
        getView().setHoverTooltip(
                caption,
                FloorMapHoverDetail.lines(
                        entityType(id),
                        containingAreaNames(id),
                        FloorMapMeasurementUnits.formatPosition(
                                measurementUnits, anchorMap[0], anchorMap[1]),
                        id,
                        caption),
                anchorPx[0], anchorPx[1]);
    }

    /// Hides the hover tooltip — cluster or single entity — if one is showing.
    private void hideHoverTooltip() {
        if (hoveredClusterKey != null || hoveredObjectId != null) {
            hoveredClusterKey = null;
            hoveredObjectId = null;
            getView().setHoverTooltip(null, null, 0, 0);
        }
    }

    /// The name shown for an entity: the owning tab's resolver first (so a name
    /// here matches the name in every grid), then a fact's own label — which is
    /// all the Editor tab has, since it wires no resolver. `null` when the
    /// entity has no name at all.
    private String displayName(final String id) {
        final String resolved = entityNameResolver != null
                ? entityNameResolver.apply(id)
                : null;
        if (resolved != null && !resolved.trim().isEmpty()) {
            return resolved;
        }
        final Fact fact = factFor(id);
        return fact != null
                ? fact.getLabelOrNull()
                : null;
    }

    /// [#displayName] with the id as the last resort, for use in a list.
    private String displayNameOrId(final String id) {
        final String name = displayName(id);
        return name != null
                ? name
                : id;
    }

    /// An entity's type: the live event entity's, else the static fact's. Same
    /// precedence as [#entityMapPosition], so the type and the position
    /// describing one entity cannot come from two different sources.
    private String entityType(final String id) {
        final String eventType = animator.typeOf(id);
        if (eventType != null) {
            return eventType;
        }
        final Fact fact = factFor(id);
        return fact != null
                ? fact.getType()
                : null;
    }

    /// The names of every area containing an entity, innermost (most specific)
    /// first — the same order and the same names the Tracking panel and the
    /// cluster dialog use.
    ///
    /// @param id the entity id
    /// @return the names, empty when the entity is in no area, or `null`
    ///         when the map has no areas at all (see [FloorMapHoverDetail])
    private List<String> containingAreaNames(final String id) {
        if (areaMembership.getAreaKeys().isEmpty()) {
            return null;
        }
        final List<String> keys = areaMembership.getAreaKeys(id);
        final List<String> names = new ArrayList<>(keys.size());
        for (final String key : keys) {
            names.add(displayNameOrId(key));
        }
        return names;
    }

    /// The static fact with this key, or `null` if there is none.
    private Fact factFor(final String id) {
        if (id != null) {
            for (final Fact fact : facts) {
                if (id.equals(fact.getKey())) {
                    return fact;
                }
            }
        }
        return null;
    }

    /// Updates the readout pill that follows the cursor during a move or resize.
    ///
    /// Answers the question the gesture raises — "how big is this?" while
    /// scaling, "where is it?" while moving — in real-world units, which is
    /// otherwise unknowable from a canvas whose only other scale cues are the
    /// grid and the corner bar.
    ///
    /// **Call after [#redraw()]**: the size is read back
    /// from the geometry the view just laid out, which is where the in-progress
    /// transform has been applied.
    ///
    /// @param cursorXPx the cursor position in element pixels
    /// @param cursorYPx the cursor position in element pixels
    private void updateGestureReadout(final double cursorXPx, final double cursorYPx) {
        final String text;
        if (gesture == Gesture.SCALING) {
            text = selectionSizeText();
        } else if (gesture == Gesture.MOVING) {
            text = selectionPositionText();
        } else {
            text = null;
        }
        getView().setGestureReadout(text, cursorXPx, cursorYPx);
    }

    /// Clears the gesture readout, if one is showing.
    private void hideGestureReadout() {
        getView().setGestureReadout(null, 0, 0);
    }

    /// The selection's size as `"2.4 m × 1.1 m"`, or `null` if it
    /// cannot be measured.
    ///
    /// Read from the drawn bounds rather than from the pending transform, so
    /// it reports what is actually on screen — including an image's aspect ratio,
    /// which only the view knows.
    private String selectionSizeText() {
        final double[] bounds = getView().getSelectionBoundsPx();
        if (bounds == null || scale <= 0) {
            return null;
        }
        return FloorMapMeasurementUnits.formatSize(
                measurementUnits,
                (bounds[2] - bounds[0]) / scale,
                (bounds[3] - bounds[1]) / scale);
    }

    /// The selection's position as `"X 4.5 m, Y 2.1 m"`, or `null` if
    /// it cannot be measured.
    ///
    /// The centre of the selection, which is the point that visibly tracks the
    /// pointer — and the only meaningful single position for a multi-selection.
    /// Axes are named because a bare pair of numbers on a Y-up map is exactly the
    /// kind of cell that gets queried.
    private String selectionPositionText() {
        final double[] bounds = getView().getSelectionBoundsPx();
        if (bounds == null) {
            return null;
        }
        final double[] centreMap = screenToMapCoords(
                (bounds[0] + bounds[2]) / 2,
                (bounds[1] + bounds[3]) / 2);
        return FloorMapMeasurementUnits.formatPosition(
                measurementUnits, centreMap[0], centreMap[1]);
    }

    /// The in-progress Set Scale line as `{x0, y0, x1, y1}` in element
    /// pixels, or `null` when not measuring or before the press.
    ///
    /// The anchor is held in map space and projected here, so a mid-measure
    /// zoom moves the line with the floor plan rather than stretching it.
    private double[] currentMeasureLinePx() {
        if (gesture != Gesture.MEASURING_SCALE || measureStartMap == null) {
            return null;
        }
        final double[] startPx = mapToScreen(measureStartMap);
        return new double[]{startPx[0], startPx[1], measureCursorX, measureCursorY};
    }

    /// The area-containment decorations for this frame: badges for every occupied
    /// area, plus the reciprocal highlight for whatever is currently focused.
    ///
    /// Focus is the tracked entity when the camera is following one, otherwise
    /// a lone selected object — so the highlight works on the Editor tab (which
    /// selects but never tracks) as well as the Map tab. A multi-selection has no
    /// single subject, so it highlights nothing.
    private FloorMapAreaOverlay areaOverlay() {
        final String focusId = trackedObjectId != null
                ? trackedObjectId
                : selectedObjectIds.size() == 1
                        ? selectedObjectIds.iterator().next()
                        : null;
        return FloorMapAreaOverlay.of(areaMembership, focusId);
    }

    /// The current rubber-band rectangle in element-pixel space as
    /// `{minX, minY, maxX, maxY}`. Only meaningful while a MARQUEE gesture
    /// is in progress.
    private double[] currentMarqueeRect() {
        return new double[]{
                Math.min(marqueeStartX, marqueeCurX),
                Math.min(marqueeStartY, marqueeCurY),
                Math.max(marqueeStartX, marqueeCurX),
                Math.max(marqueeStartY, marqueeCurY)};
    }

    /// Resolves the id of the map object under an event target, or `null`
    /// if the target is not a real object shape. Wrapper `<g>` groups
    /// (prefixed [FloorMapJsonKeys#SVG_GROUP_PREFIX]), transform handles
    /// (prefixed [FloorMapJsonKeys#HANDLE_PREFIX]) and cluster glyphs
    /// (prefixed [FloorMapJsonKeys#CLUSTER_PREFIX]) are not objects.
    ///
    /// The cluster exclusion is load-bearing: a cluster's key is one of its
    /// members' ids, so without it a press on a cluster would announce that member
    /// as the thing that was clicked — selecting an entity the user cannot see and
    /// did not aim at.
    private String hitObjectId(final EventTarget target) {
        if (Element.is(target)) {
            final String id = Element.as(target).getId();
            if (id != null && !id.isEmpty()
                    && !id.startsWith(FloorMapJsonKeys.SVG_GROUP_PREFIX)
                    && !id.startsWith(FloorMapJsonKeys.HANDLE_PREFIX)
                    && !id.startsWith(FloorMapJsonKeys.CLUSTER_PREFIX)) {
                return id;
            }
        }
        return null;
    }

    /// Returns the key of the cluster glyph under an event target, or `null`
    /// if the target is not one. Mirrors [#handleRole]: the prefix both
    /// excludes the glyph from the object hit-test and identifies it here.
    private String clusterKey(final EventTarget target) {
        if (Element.is(target)) {
            final String id = Element.as(target).getId();
            if (id != null && id.startsWith(FloorMapJsonKeys.CLUSTER_PREFIX)) {
                return id.substring(FloorMapJsonKeys.CLUSTER_PREFIX.length());
            }
            // The glyph's wrapper group is the prefixed id again behind the
            // group prefix, and a press can land on either.
            final String groupPrefixed = FloorMapJsonKeys.SVG_GROUP_PREFIX
                    + FloorMapJsonKeys.CLUSTER_PREFIX;
            if (id != null && id.startsWith(groupPrefixed)) {
                return id.substring(groupPrefixed.length());
            }
        }
        return null;
    }

    /// Returns the transform-handle role under an event target (the id suffix
    /// after [FloorMapJsonKeys#HANDLE_PREFIX], e.g. `"scale-nw"` or
    /// `"rotate"`), or `null` if the target is not a handle.
    private String handleRole(final EventTarget target) {
        if (Element.is(target)) {
            final String id = Element.as(target).getId();
            if (id != null && id.startsWith(FloorMapJsonKeys.HANDLE_PREFIX)) {
                return id.substring(FloorMapJsonKeys.HANDLE_PREFIX.length());
            }
        }
        return null;
    }

    /// True if the current selection contains at least one fact that can be
    /// meaningfully scaled or rotated — an image fact or an area (which has real
    /// geometry). Bare point glyphs are drawn at a fixed screen size, so
    /// transforming them has no visible effect; their handles are greyed and inert.
    ///
    /// Facts on a **locked** layer never count. The mouseup that
    /// ends a scale/rotate filters them out before persisting, so a locked-only
    /// selection would otherwise draw live handles that follow the pointer through
    /// the whole gesture and then snap back on release — which reads as a glitch
    /// rather than as "locked". Excluding them here means no handles are offered
    /// and [#beginHandleGesture] is never entered, so the vertex handles of a
    /// locked area go with them.
    private boolean selectionTransformable() {
        for (final Fact fact : facts) {
            // At least one *unlocked* fact must be transformable. A locked one does not
            // count: the mouseup commit filters it out, so handles shown for an
            // all-locked selection were live to the touch and could never do anything -
            // a full gesture with a live preview, then nothing persisted. A mixed
            // selection still gets handles, correctly, for its unlocked members.
            if (selectedObjectIds.contains(fact.getKey())
                    && !lockedKeys.contains(fact.getKey())
                    && (fact.hasImage() || fact.hasVertices())) {
                return true;
            }
        }
        return false;
    }

    /// Begins a scale or rotate gesture from the given handle, snapshotting the
    /// pivot/centre (in map space) from the current selection frame.
    ///
    /// @param role the handle role (`"scale-*"` or `"rotate"`)
    /// @param px   the pointer X at gesture start (element pixels)
    /// @param py   the pointer Y at gesture start (element pixels)
    private void beginHandleGesture(final String role, final double px, final double py) {
        isDragging = true;
        hasMoved = false;
        pendingTransform = null;
        lastMouseX = px;
        lastMouseY = py;

        // The readout is wanted from the press: the size before the drag is
        // exactly what the user is about to change.
        updateGestureReadout(px, py);

        // Area vertex editing takes priority over the scale/rotate frame.
        if (role.startsWith("vertex-")) {
            beginVertexEdit(parseHandleIndex(role, "vertex-"), false);
            return;
        }
        if (role.startsWith("insert-")) {
            beginVertexEdit(parseHandleIndex(role, "insert-"), true);
            return;
        }

        final double[] frame = getView().getSelectionFrame();
        if (frame == null) {
            isDragging = false;
            gesture = Gesture.NONE;
            return;
        }
        final double minX = frame[0];
        final double minY = frame[1];
        final double maxX = frame[2];
        final double maxY = frame[3];
        final double cx = (minX + maxX) / 2;
        final double cy = (minY + maxY) / 2;

        if ("rotate".equals(role)) {
            gesture = Gesture.ROTATING;
            final double[] centre = screenToMapCoords(cx, cy);
            gestureCentreX = centre[0];
            gestureCentreY = centre[1];
            final double[] start = screenToMapCoords(px, py);
            gestureStartMapX = start[0];
            gestureStartMapY = start[1];
        } else {
            gesture = Gesture.SCALING;
            final String dir = role.startsWith("scale-")
                    ? role.substring("scale-".length())
                    : role;
            // Scale about the opposite corner/edge; the grabbed handle is the
            // moving reference point.
            final double[] pivotScreen = pointForDir(oppositeDir(dir), minX, minY, maxX, maxY, cx, cy);
            final double[] refScreen = pointForDir(dir, minX, minY, maxX, maxY, cx, cy);
            final double[] pivot = screenToMapCoords(pivotScreen[0], pivotScreen[1]);
            final double[] ref = screenToMapCoords(refScreen[0], refScreen[1]);
            gesturePivotX = pivot[0];
            gesturePivotY = pivot[1];
            gestureRefX = ref[0];
            gestureRefY = ref[1];
        }
    }

    /// Parses the integer index from a handle role like `"vertex-3"`.
    private static int parseHandleIndex(final String role, final String prefix) {
        try {
            return Integer.parseInt(role.substring(prefix.length()));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    /// The single selected area fact (imageless, with vertices), or `null`
    /// when the selection is empty, multiple, or not an area.
    private Fact selectedAreaFact() {
        if (selectedObjectIds.size() != 1) {
            return null;
        }
        final String id = selectedObjectIds.iterator().next();
        for (final Fact fact : facts) {
            if (id.equals(fact.getKey()) && fact.hasVertices() && !fact.hasImage()) {
                return fact;
            }
        }
        return null;
    }

    /// Begins a [Gesture#MOVING_VERTEX] edit. For `insert`, a new
    /// vertex is spliced at the midpoint of edge `index` and immediately
    /// dragged; otherwise the existing vertex `index` is dragged.
    private void beginVertexEdit(final int index, final boolean insert) {
        final Fact area = selectedAreaFact();
        if (area == null || index < 0 || lockedKeys.contains(area.getKey())) {
            isDragging = false;
            gesture = Gesture.NONE;
            return;
        }
        // Dragging a vertex converts screen coordinates into the fact's local space
        // through the inverse of this matrix, and persists the result. A
        // non-invertible matrix would make that conversion meaningless, so refuse the
        // edit rather than write coordinates in the wrong space back to the document.
        // FloorMapEntryParser rejects non-invertible matrices at parse time, so this is
        // defence in depth against a matrix composed in the UI.
        if (!area.getWorldToMap().hasInverse()) {
            isDragging = false;
            gesture = Gesture.NONE;
            return;
        }
        final double[][] v = area.getVertices();
        editingAreaKey = area.getKey();
        editingWorldToMap = area.getWorldToMap();
        vertexInserted = insert;

        if (insert) {
            if (index >= v.length) {
                isDragging = false;
                gesture = Gesture.NONE;
                return;
            }
            final int n = v.length;
            final int j = (index + 1) % n;
            final double[] mid = {(v[index][0] + v[j][0]) / 2.0, (v[index][1] + v[j][1]) / 2.0};
            final double[][] nv = new double[n + 1][];
            for (int i = 0; i <= index; i++) {
                nv[i] = new double[]{v[i][0], v[i][1]};
            }
            nv[index + 1] = mid;
            for (int i = index + 1; i < n; i++) {
                nv[i + 1] = new double[]{v[i][0], v[i][1]};
            }
            workingVertices = nv;
            editingVertexIndex = index + 1;
        } else {
            if (index >= v.length) {
                isDragging = false;
                gesture = Gesture.NONE;
                return;
            }
            final double[][] nv = new double[v.length][];
            for (int i = 0; i < v.length; i++) {
                nv[i] = new double[]{v[i][0], v[i][1]};
            }
            workingVertices = nv;
            editingVertexIndex = index;
        }
        gesture = Gesture.MOVING_VERTEX;
        redraw();
    }

    /// Deletes the vertex at `index` from the single selected area,
    /// enforcing the 3-vertex minimum and skipping locked layers. Persists via
    /// the geometry handler. No-op when there is no single-area selection.
    ///
    /// @param index the vertex index to delete
    public void deleteVertex(final int index) {
        final Fact area = selectedAreaFact();
        if (area == null || geometryHandler == null || lockedKeys.contains(area.getKey())) {
            return;
        }
        final double[][] v = area.getVertices();
        if (index < 0 || index >= v.length || v.length <= AREA_MIN_VERTICES) {
            return;
        }
        final double[][] nv = new double[v.length - 1][];
        for (int i = 0, k = 0; i < v.length; i++) {
            if (i != index) {
                nv[k++] = new double[]{v[i][0], v[i][1]};
            }
        }
        geometryHandler.onGeometryEdited(area.getKey(), nv);
    }

    /// Clears all transient vertex-editing state. Called on every path that ends
    /// a `MOVING_VERTEX` gesture (normal mouseup and the lost-mouseup
    /// recovery) so the [#factsForDraw()] live preview cannot outlive the
    /// gesture.
    private void clearVertexEditState() {
        editingAreaKey = null;
        editingWorldToMap = null;
        workingVertices = null;
        editingVertexIndex = -1;
        vertexInserted = false;
    }

    /// The gestures Escape can abandon: the editing gestures, whose only other
    /// ending is the mouseup that commits them.
    ///
    /// Panning is not one of them — it changes nothing that needs undoing, and
    /// Escape during a pan is wanted for its other job of clearing the selection.
    /// The two modal gestures (drawing an area, measuring a scale) have their own
    /// Escape handling earlier in the key handler, since leaving them also means
    /// leaving the mode.
    private static boolean isAbortableGesture(final Gesture gesture) {
        return gesture == Gesture.MOVING
               || gesture == Gesture.SCALING
               || gesture == Gesture.ROTATING
               || gesture == Gesture.MOVING_VERTEX
               || gesture == Gesture.MARQUEE;
    }

    /// Abandons any in-flight gesture *without persisting it*, returning the
    /// canvas to a neutral state.
    ///
    /// Needed because a gesture is normally ended by the mouseup that completes
    /// it, and some interactions steal that mouseup — most obviously a right-click,
    /// whose mouseup lands on the popup rather than the canvas. Resetting only part
    /// of the state left the [#factsForDraw()] live preview substituting
    /// never-persisted working vertices indefinitely, so the canvas showed geometry
    /// that did not match what was stored.
    ///
    /// The current **selection** is deliberately left alone: it is
    /// not gesture state, and the vertex context menu resolves its target from the
    /// selection (see [#selectedAreaFact()]).
    private void abortGesture() {
        isDragging = false;
        hasMoved = false;
        hideGestureReadout();
        hideHoverTooltip();
        gesture = Gesture.NONE;
        pendingTransform = null;
        pendingClickSelectId = null;
        pendingClickClusterKey = null;
        dragDxMap = 0;
        dragDyMap = 0;
        clearVertexEditState();
    }

    /// Computes the map-space scale transform for the current SCALING gesture
    /// given the current pointer position. Scaling is always uniform
    /// (aspect-preserving): the factor is the projection of the pointer-from-pivot
    /// vector onto the grabbed-corner-from-pivot vector. Uniform scale never
    /// shears, so it is correct even for a rotated fact. The factor is clamped to
    /// [#MIN_SCALE_FACTOR] to avoid zero/flip/singular.
    private FloorMapTransformationMatrix computeScaleTransform(final double px, final double py) {
        final double[] cur = screenToMapCoords(px, py);
        final double dx0 = gestureRefX - gesturePivotX;
        final double dy0 = gestureRefY - gesturePivotY;
        final double denom = dx0 * dx0 + dy0 * dy0;
        final double s = denom > 1e-9
                ? ((cur[0] - gesturePivotX) * dx0 + (cur[1] - gesturePivotY) * dy0) / denom
                : 1;
        final double clamped = clampScale(s);
        return FloorMapTransformationMatrix.scaleAbout(
                clamped, clamped, gesturePivotX, gesturePivotY);
    }

    private static double clampScale(final double s) {
        return Math.max(s, MIN_SCALE_FACTOR);
    }

    /// Screen-space position of a corner frame handle by direction (nw/ne/se/sw).
    private static double[] pointForDir(final String dir,
                                        final double minX, final double minY,
                                        final double maxX, final double maxY,
                                        final double cx, final double cy) {
        //noinspection EnhancedSwitchMigration
        switch (dir) {
            case "nw":
                return new double[]{minX, minY};
            case "ne":
                return new double[]{maxX, minY};
            case "se":
                return new double[]{maxX, maxY};
            case "sw":
                return new double[]{minX, maxY};
            default:
                return new double[]{cx, cy};
        }
    }

    private static String oppositeDir(final String dir) {
        //noinspection EnhancedSwitchMigration
        switch (dir) {
            case "nw":
                return "se";
            case "ne":
                return "sw";
            case "se":
                return "nw";
            case "sw":
                return "ne";
            default:
                return dir;
        }
    }

    /// Filters the facts draw list down to those NOT currently owned by the
    /// event overlay. An entity whose positions are recorded in the facts store
    /// AND streamed as events would otherwise render twice — a static fact glyph
    /// under the animated overlay glyph; the animated one wins.
    private List<Fact> factsExcludingOverlay(final List<FloorMapObject> overlay) {
        final List<Fact> base = factsForDraw();
        if (overlay.isEmpty()) {
            return base;
        }
        final Set<String> overlayIds = new HashSet<>();
        for (final FloorMapObject obj : overlay) {
            overlayIds.add(obj.getId());
        }
        final List<Fact> out = new ArrayList<>(base.size());
        for (final Fact fact : base) {
            if (!overlayIds.contains(fact.getKey())) {
                out.add(fact);
            }
        }
        return out;
    }

    /// Builds the event-overlay draw list, substituting animated entities at
    /// their current interpolated positions and attaching trail data.
    ///
    /// @param nowMs Current wall-clock time in ms (used to compute trail alpha).
    ///         Pass `0.0` when there are no active animations.
    private List<FloorMapObject> buildAnimatedDrawList(final double nowMs) {
        final List<FloorMapObject> combined = animator.buildDrawList(nowMs);

        // Decorate each entity with its image-bearing fact twin (if any) so the
        // view renders the entity's configured icon at the live position instead
        // of the generic type glyph. The static twin is suppressed by
        // factsExcludingOverlay, so without this the icon would vanish the moment
        // the entity appears in the events stream. This is a rendering concern
        // (it needs `facts`), so it stays in the presenter rather than the
        // shared animator. Set unconditionally (null clears). The lookup map is
        // maintained by setFacts, not rebuilt here - this runs every frame.
        for (final FloorMapObject obj : combined) {
            obj.setImageFact(imageFactsByKey.get(obj.getId()));
        }
        return combined;
    }

    // =========================================================================
    // Animation loop
    // =========================================================================

    private final AnimationScheduler.AnimationCallback animationCallback =
            new AnimationScheduler.AnimationCallback() {
                @Override
                public void execute(final double timestamp) {
                    // Compute the time elapsed since the previous frame.  On the very first
                    // frame after the loop starts, lastAnimationTimestamp is 0 so we use a
                    // nominal 16 ms (one 60 fps frame) to avoid a stalled first step.
                    final double deltaMs = lastAnimationTimestamp > 0
                            ? timestamp - lastAnimationTimestamp
                            : 16.0;
                    lastAnimationTimestamp = timestamp;

                    // Advance entity animations + trail fades (shared animator),
                    // then glide the damped camera-follow (presenter-owned).
                    final boolean animActive = animator.advanceFrame(timestamp, deltaMs);
                    final boolean cameraMoved = followStep(deltaMs);

                    if (!cameraMoved && !animActive) {
                        // Nothing left to animate, fade, or glide — let the loop terminate.
                        animationLoopRunning = false;
                        lastAnimationTimestamp = 0;
                        return;
                    }

                    // Draw the current frame. No marquee/handles/draft during playback.
                    final List<FloorMapObject> overlay = buildAnimatedDrawList(timestamp);
                    final List<Fact> drawFacts = FloorMapZOrder.sort(
                            visibleFacts(factsExcludingOverlay(overlay)), typeStyles);
                    final List<FloorMapObject> drawEvents = visibleEvents(overlay);
                    final FloorMapAreaOverlay areas = areaOverlay();
                    getView().draw(scale, offsetX, offsetY,
                            drawFacts,
                            drawEvents, selectedObjectIds, typeStyles, showGrid, dimmedTypes,
                            null, false, false, null, areas,
                            // Recomputed per frame, like everything else here: as
                            // entities move they cross the merge distance, so a
                            // cluster computed once would be wrong the moment
                            // anything moved — which is exactly when it matters.
                            clusterOverlay(drawFacts, drawEvents),
                            // Group highlight has to be resolved on animated frames
                            // too, or a highlighted entity would lose its ring for
                            // exactly as long as it is moving.
                            FloorMapHighlight.of(groupOverlay, areas),
                            // Likewise the measuring line: playback can be running
                            // while the user measures, and a decoration passed on
                            // only one of the two draw paths flickers out for
                            // exactly as long as anything is moving.
                            currentMeasureLinePx());

                    // A tooltip is anchored to a glyph and only re-anchored when
                    // the pointer moves, so anything that moves the scene — entity
                    // playback or a camera glide — would strand it beside a glyph
                    // that is no longer there.
                    hideHoverTooltip();

                    // Keep looping.
                    AnimationScheduler.get().requestAnimationFrame(this);
                }
            };

    /// Starts the animation loop if it is not already running.
    private void ensureAnimationLoop() {
        if (!animationLoopRunning) {
            animationLoopRunning = true;
            AnimationScheduler.get().requestAnimationFrame(animationCallback);
        }
    }

    // =========================================================================
    // Setters
    // =========================================================================

    /// Sets the area-containment snapshot used to decorate the canvas — the
    /// reciprocal highlight (areas holding the focused entity, entities inside
    /// the focused area) and the per-area occupant-count badges.
    ///
    /// Recomputed and pushed by the owning tab whenever the facts or event
    /// entities change (a query refresh), never per animation frame. An unchanged
    /// snapshot does not redraw — the tab pushes one on every refresh, and the
    /// accompanying `setFacts`/`setEventObjects` has already
    /// triggered a redraw of its own.
    ///
    /// @param areaMembership the snapshot, or `null` to clear the
    ///         decorations
    public void setAreaMembership(final FloorMapAreaMembership areaMembership) {
        final FloorMapAreaMembership next = areaMembership != null
                ? areaMembership
                : FloorMapAreaMembership.EMPTY;
        if (!next.equals(this.areaMembership)) {
            this.areaMembership = next;
            // The summary names the area a followed entity is in, and counts the
            // areas, so both move with this.
            refreshAccessibleSummary();
            redraw();
        }
    }

    /// Sets which entities carry a group highlight, and in what colour.
    ///
    /// Pushed by the owning tab when the user switches a group's highlight on or
    /// off, or edits a highlighted group's membership — not on query refreshes,
    /// since group membership does not move with the data.
    ///
    /// This is a decoration only: it never moves the camera and never changes the
    /// tracked entity, so highlighting a group leaves an in-progress follow
    /// undisturbed.
    ///
    /// @param groupOverlay the highlight, or `null` to clear it
    public void setGroupOverlay(final FloorMapGroupOverlay groupOverlay) {
        final FloorMapGroupOverlay next = groupOverlay != null
                ? groupOverlay
                : FloorMapGroupOverlay.EMPTY;
        if (!next.equals(this.groupOverlay)) {
            this.groupOverlay = next;
            redraw();
        }
    }

    /// Single-select façade: highlights exactly one object (or clears the
    /// highlight when `null`) and redraws.
    ///
    /// @param selectedObjectId the object ID to highlight, or `null` to clear
    public void setSelectedObjectId(final String selectedObjectId) {
        selectedObjectIds.clear();
        if (selectedObjectId != null) {
            selectedObjectIds.add(selectedObjectId);
        }
        redraw();
    }

    /// Replaces the highlighted selection with the given object ids (multi-select
    /// facade beside [#setSelectedObjectId]) and redraws. Selection order is
    /// preserved so the first id is treated as the primary. Does *not* fire
    /// the [SelectionHandler] — this is the inbound path used by callers
    /// (e.g. the editor) that already hold the selection.
    ///
    /// @param objectIds the ids to select; `null` clears the selection
    public void setSelectedObjectIds(final Collection<String> objectIds) {
        selectedObjectIds.clear();
        if (objectIds != null) {
            selectedObjectIds.addAll(objectIds);
        }
        redraw();
    }

    /// Starts tracking the given entity: highlights it via the selection
    /// mechanism, immediately centres the camera on it, and follows it as it
    /// moves. Passing `null` stops tracking and clears the highlight.
    /// Calling this again with the same id re-centres and resumes following
    /// after a manual pan paused it.
    ///
    /// @param trackedObjectId the entity's object id, or `null` to stop tracking
    public void setTrackedObjectId(final String trackedObjectId) {
        this.trackedObjectId = trackedObjectId;
        this.followPaused = false;
        // Hard-centre on (re-)selection so tracking visibly engages even when
        // the entity is already on screen. If its position isn't known yet
        // (no events loaded), the flag holds until the first update that has one.
        this.centreOnNextFollow = trackedObjectId != null;
        followStep(0);
        setSelectedObjectId(trackedObjectId);
        // Tracking is otherwise announced only by the camera moving, which is
        // nothing at all if you cannot see it.
        announceTracking();
    }

    /// Applies one damped camera-follow step: computes the pan needed to bring
    /// the tracked entity back inside the view's central dead zone (via
    /// [FloorMapViewport#followDelta]) and applies a time-proportional
    /// fraction of it, so the camera glides after the entity instead of
    /// snapping. The full correction is applied at once when hard-centring on
    /// (re-)selection ([#centreOnNextFollow]) or when the remainder is
    /// sub-pixel.
    ///
    /// No-op when nothing is tracked, following is paused, or the entity's
    /// position is unknown. Callers are responsible for redrawing afterwards.
    ///
    /// @param deltaMs elapsed time since the previous step (ms); `0`
    ///         applies the full correction immediately
    /// @return `true` if the camera moved (a glide is in progress), so
    ///         the animation loop keeps running until the correction is spent
    private boolean followStep(final double deltaMs) {
        if (trackedObjectId == null || followPaused) {
            return false;
        }
        final double[] pos = trackedPosition();
        if (pos == null) {
            // Keep centreOnNextFollow armed until we have a position fix.
            return false;
        }
        // Project the map-space position to the on-screen point via the shared
        // projection helper.
        final double[] screen = mapToScreen(pos);
        final double screenX = screen[0];
        final double screenY = screen[1];
        final Element panel = getView().getFocusPanel().getElement();
        // Margin 0.5 collapses the dead zone to the centre point (hard-centre).
        final boolean centre = centreOnNextFollow;
        centreOnNextFollow = false;
        final double margin = centre
                ? 0.5
                : FloorMapViewport.DEFAULT_FOLLOW_MARGIN;
        final double[] delta = FloorMapViewport.followDelta(screenX, screenY,
                panel.getOffsetWidth(), panel.getOffsetHeight(), margin);
        if (delta[0] == 0 && delta[1] == 0) {
            return false;
        }

        final boolean snap = centre
                || deltaMs <= 0
                || (Math.abs(delta[0]) < FOLLOW_SNAP_PX && Math.abs(delta[1]) < FOLLOW_SNAP_PX);
        final double factor = snap
                ? 1.0
                : FloorMapViewport.dampingFactor(deltaMs, FloorMapViewport.DEFAULT_FOLLOW_DAMPING_MS);
        offsetX += delta[0] * factor;
        offsetY += delta[1] * factor;
        return true;
    }

    /// Resolves the tracked entity's current map-space position.
    ///
    /// @return `{mapX, mapY}`, or `null` if the entity is unknown
    private double[] trackedPosition() {
        return entityMapPosition(trackedObjectId);
    }

    /// Resolves an entity's current map-space position, preferring the live
    /// interpolated animation position, then the last known rendered position,
    /// then the event draw list, then — for static facts (objects, backgrounds,
    /// areas), which never move — the fact's placement anchor.
    ///
    /// @param id the entity id
    /// @return `{mapX, mapY}`, or `null` if the entity is unknown
    private double[] entityMapPosition(final String id) {
        // The animator knows live/animated/last-known and current-overlay positions.
        final double[] pos = animator.positionOf(id);
        if (pos != null) {
            return pos;
        }
        // Fall back to a static fact placed in the facts store.
        final Fact fact = factFor(id);
        // Image anchors need the aspect ratio, known only to the view.
        return fact != null
                ? getView().getFactMapAnchor(fact)
                : null;
    }

    /// Returns the facts to render, applying any in-progress transform gesture:
    /// each selected, unlocked fact's world-to-map matrix is composed as
    /// `pendingTransform · oldMatrix` so a move/scale/rotate is shown live.
    /// On release the same transform is persisted via [DragHandler#onTransform].
    ///
    /// Items on a locked layer are excluded, matching the mouseup commit, which
    /// filters [#lockedKeys] out of the transform. Without that the preview and
    /// the commit disagreed: a selection containing both locked and unlocked items -
    /// reachable by locking a layer after selecting, or by Shift-clicking a locked
    /// item, which is deliberately allowed - showed the locked ones tracking the drag
    /// and then snapping back on release, with nothing said. Locked now means locked
    /// from the first pixel.
    private List<Fact> factsForDraw() {
        final boolean editingVertices = editingAreaKey != null && workingVertices != null;
        if ((pendingTransform == null || selectedObjectIds.isEmpty()) && !editingVertices) {
            return facts;
        }
        final List<Fact> out = new ArrayList<>(facts.size());
        for (final Fact fact : facts) {
            if (editingVertices && editingAreaKey.equals(fact.getKey())) {
                // Live vertex-edit preview.
                out.add(fact.withVertices(workingVertices));
            } else if (pendingTransform != null
                       && selectedObjectIds.contains(fact.getKey())
                       && !lockedKeys.contains(fact.getKey())) {
                out.add(fact.withWorldToMap(
                        pendingTransform.multiply(fact.getWorldToMap())));
            } else {
                out.add(fact);
            }
        }
        return out;
    }


    /// Returns the map-space coordinates of the centre of the currently
    /// visible canvas area.
    ///
    /// This is useful for placing new objects at "the middle of what the
    /// user can see" when no specific click position is available (e.g. when
    /// using a toolbar Add button rather than a canvas right-click).
    ///
    /// @return a two-element array `{mapX, mapY}` representing the
    ///         visible centre in map space
    public double[] getVisibleCentreMapCoords() {
        final Element panel = getView().getFocusPanel().getElement();
        final double centreX = panel.getOffsetWidth() / 2.0;
        final double centreY = panel.getOffsetHeight() / 2.0;
        return screenToMapCoords(centreX, centreY);
    }

    /// Converts a number of minor grid divisions into a map-space distance at
    /// the current zoom level, using the same adaptive-decade sizing that draws
    /// the grid ([FloorMapGrid]). Because the grid's spacing is chosen to
    /// keep grid cells a comfortable on-screen size at any zoom, an offset
    /// expressed this way stays visually consistent regardless of magnification.
    ///
    /// The grid is drawn with an identity world-to-map matrix (see the
    /// `appendGrid` call in the view), so its effective scale is simply
    /// the user zoom and its world space coincides with map space.
    ///
    /// @param minorDivisions the number of minor grid divisions
    /// @return the equivalent distance in map-space units
    public double minorGridDivisionsToMapUnits(final double minorDivisions) {
        return minorDivisions * FloorMapGrid.minorWorldSpacing(scale, measurementUnits);
    }

    /// Sets the event-driven entity overlays (events query result).
    ///
    /// When the timeline is *not* playing, all objects are placed at their
    /// target positions immediately (teleport behaviour).
    ///
    /// When the timeline *is* playing, every entity is animated from its
    /// last known position to the new one, regardless of type.
    public void setEventObjects(final List<FloorMapObject> objects) {
        // Entities move on a refresh, and the tooltip is anchored to where one
        // was and describes where it was — both stale the moment this lands, and
        // no mouse movement is needed to reach that state.
        hideHoverTooltip();
        lastEventObjects = objects != null
                ? objects
                : new ArrayList<>();
        // Content change, so the summary is now stale. Here rather than in
        // redraw(), which runs per animation frame.
        refreshAccessibleSummary();
        final boolean teleported = animator.onEventObjects(objects);
        // Run the loop for the animate path (it advances animations + glides the
        // camera and repaints); on the teleport path only if tracking, so the
        // damped camera-follow glides to the new position.
        if (!teleported || (trackedObjectId != null && !followPaused)) {
            ensureAnimationLoop();
        }
        // Force a paint so an update that starts no animation still repaints (the
        // loop returns without drawing when there is nothing to animate or glide).
        redraw();
    }

    /// Toggles edit mode. When disabled, the object selection is cleared.
    ///
    /// @param editMode `true` to enter edit mode, `false` to leave
    public void setEditMode(final boolean editMode) {
        this.editMode = editMode;
        if (!editMode) {
            selectedObjectIds.clear();
            hideGestureReadout();
            if (gesture == Gesture.DRAWING_AREA) {
                cancelAreaDrawing();
            }
            if (gesture == Gesture.MEASURING_SCALE) {
                cancelScaleMeasurement();
            }
        }
        redraw();
    }

    /// Enters the modal area-drawing gesture: subsequent clicks append polygon
    /// vertices, a press-drag pans, right-click undoes the last vertex, Escape
    /// cancels, and the polygon closes on Enter, double-click, or a click near
    /// the first vertex (≥ 3 vertices). The finished polygon is delivered to
    /// the [AreaHandler].
    public void startAreaDrawing() {
        selectedObjectIds.clear();
        fireSelectionChanged();
        areaDraftMap.clear();
        gesture = Gesture.DRAWING_AREA;
        // Make the modal mode visible immediately: crosshair cursor (the
        // instruction banner is drawn by the view). Without this the mode is
        // indistinguishable from nothing having happened.
        getView().getFocusPanel().getElement().getStyle()
                .setProperty("cursor", "crosshair");
        // The triggering context-menu click leaves keyboard focus on the popup;
        // without this, Enter/Escape are dead until the first canvas click.
        getView().getFocusPanel().setFocus(true);
        redraw();
    }

    /// Enters the modal Set Scale gesture: the user presses at one end of a
    /// distance they know, drags to the other, and releases. The measured
    /// map-space length is delivered to the [ScaleHandler], which asks what
    /// that distance really is and calibrates the map from the answer.
    ///
    /// Escape and right-click both leave the mode; a drag shorter than
    /// [#MIN_MEASURE_PX] is ignored and the mode stays live.
    public void startScaleMeasurement() {
        selectedObjectIds.clear();
        fireSelectionChanged();
        measureStartMap = null;
        gesture = Gesture.MEASURING_SCALE;
        getView().setMeasuringScale(true);
        // Make the modal mode visible the instant it starts — a mode with no
        // cursor or banner is indistinguishable from nothing having happened.
        getView().getFocusPanel().getElement().getStyle()
                .setProperty("cursor", "crosshair");
        // The triggering context-menu click leaves keyboard focus on the popup;
        // without this, Escape is dead until the first canvas click.
        getView().getFocusPanel().setFocus(true);
        redraw();
    }

    /// Discards any in-progress measurement and leaves the Set Scale gesture.
    public void cancelScaleMeasurement() {
        measureStartMap = null;
        isDragging = false;
        hasMoved = false;
        gesture = Gesture.NONE;
        getView().setMeasuringScale(false);
        getView().getFocusPanel().getElement().getStyle().clearProperty("cursor");
        redraw();
    }

    /// Ends a measurement at the given element-pixel position, delivering its
    /// map-space length to the [ScaleHandler].
    ///
    /// A drag too short to be meaningful leaves the gesture live rather than
    /// opening a dialog about a distance the user did not mean to measure.
    private void finishScaleMeasurement(final double screenX, final double screenY) {
        final double[] startPx = mapToScreen(measureStartMap);
        final double dx = screenX - startPx[0];
        final double dy = screenY - startPx[1];
        if (Math.sqrt(dx * dx + dy * dy) < MIN_MEASURE_PX) {
            measureStartMap = null;
            redraw();
            return;
        }

        final double[] endMap = screenToMapCoords(screenX, screenY);
        final double mapDx = endMap[0] - measureStartMap[0];
        final double mapDy = endMap[1] - measureStartMap[1];
        final double mapLength = Math.sqrt(mapDx * mapDx + mapDy * mapDy);

        measureStartMap = null;
        gesture = Gesture.NONE;
        getView().setMeasuringScale(false);
        getView().getFocusPanel().getElement().getStyle().clearProperty("cursor");
        redraw();

        if (scaleHandler != null && mapLength > 0) {
            scaleHandler.onScaleMeasured(mapLength);
        }
    }

    /// Discards any in-progress area draft and leaves the drawing gesture.
    public void cancelAreaDrawing() {
        areaDraftMap.clear();
        gesture = Gesture.NONE;
        getView().getFocusPanel().getElement().getStyle().clearProperty("cursor");
        redraw();
    }

    /// Closes the in-progress polygon and delivers it to the
    /// [AreaHandler]. No-ops (keeps drawing) below the vertex minimum.
    private void finishAreaDrawing() {
        if (areaDraftMap.size() < AREA_MIN_VERTICES) {
            return;
        }
        final List<double[]> mapVertices = new ArrayList<>(areaDraftMap.size());
        for (final double[] v : areaDraftMap) {
            mapVertices.add(new double[]{v[0], v[1]});
        }
        areaDraftMap.clear();
        gesture = Gesture.NONE;
        getView().getFocusPanel().getElement().getStyle().clearProperty("cursor");
        redraw();
        if (areaHandler != null) {
            areaHandler.onAreaDrawn(mapVertices);
        }
    }

    /// `true` if the given element-pixel position falls within the
    /// close radius of the draft's first vertex.
    private boolean isNearFirstDraftVertex(final double screenX, final double screenY) {
        if (areaDraftMap.isEmpty()) {
            return false;
        }
        //noinspection SequencedCollectionMethodCanBeUsed
        final double[] first = mapToScreen(areaDraftMap.get(0));
        final double dx = screenX - first[0];
        final double dy = screenY - first[1];
        return dx * dx + dy * dy <= AREA_CLOSE_RADIUS_PX * AREA_CLOSE_RADIUS_PX;
    }

    /// Projects a map-space point to element-pixel coordinates — the inverse of
    /// [#screenToMapCoords] (pan/zoom plus the Y-up flip).
    private double[] mapToScreen(final double[] mapPoint) {
        return new FloorMapViewport(scale, offsetX, offsetY)
                .mapToScreen(mapPoint[0], mapPoint[1], Y_FLIP);
    }

    /// The in-progress area draft as a flat element-pixel polyline
    /// `{x0, y0, ..., xn, yn}` whose last point is the live cursor. An
    /// empty draft yields just the cursor point — still non-null, so the view
    /// shows the drawing-mode banner from the moment the mode starts.
    private double[] currentAreaDraftPx() {
        final double[] draft = new double[(areaDraftMap.size() + 1) * 2];
        for (int i = 0; i < areaDraftMap.size(); i++) {
            final double[] px = mapToScreen(areaDraftMap.get(i));
            draft[i * 2] = px[0];
            draft[i * 2 + 1] = px[1];
        }
        draft[draft.length - 2] = areaCursorX;
        draft[draft.length - 1] = areaCursorY;
        return draft;
    }

    /// Controls whether the grid overlay is drawn. The grid is a non-interactive
    /// UI aid and is independent of edit mode and of whether a background image is
    /// present.
    ///
    /// @param showGrid `true` to always draw the grid, `false` to hide it
    public void setShowGrid(final boolean showGrid) {
        this.showGrid = showGrid;
        redraw();
    }

    /// Turns the merging of crowded entities on or off.
    ///
    /// Worth being able to switch off, not just polish: zooming in separates
    /// entities that are merely close, but nothing separates entities at the
    /// *same* position, so without this there would be no way to confirm
    /// how many are really there — or to reach one of them on the canvas.
    ///
    /// @param clusterNearbyEntities `true` to merge crowded entities
    public void setClusterNearbyEntities(final boolean clusterNearbyEntities) {
        this.clusterNearbyEntities = clusterNearbyEntities;
        redraw();
    }

    /// Supplies the resolver that turns an entity id into the name shown to
    /// users, for the cluster hover tooltip.
    ///
    /// Comes from the owning tab because the roster lives there — the same
    /// resolver the tracking panel and the Groups panel name entities through, so
    /// a name in a tooltip matches the name in every grid.
    ///
    /// @param entityNameResolver the resolver, or `null` to fall back to ids
    public void setEntityNameResolver(final Function<String, String> entityNameResolver) {
        this.entityNameResolver = entityNameResolver;
        // The canvas caption needs it too, and must not word a cluster differently
        // from the tooltip describing the same cluster.
        getView().setEntityNameResolver(entityNameResolver);
    }

    // -----------------------------------------------------------------------
    // Accessibility: the map's text equivalent and spoken commentary
    //
    // Everything the canvas communicates it communicates by painting, which
    // reaches exactly one kind of user. These two mechanisms carry the same
    // information by other means:
    //
    //   * a standing summary, exposed as the map image's accessible name, which
    //     answers "what is on this map right now?" on demand; and
    //   * a live region, which answers "what just changed?" as it happens.
    //
    // Both are deliberately kept out of redraw(): that runs once per animation
    // frame, and rewriting the accessibility tree at 60 Hz would flood a screen
    // reader with announcements while burning time on strings nobody reads. They
    // are refreshed from the handful of methods where the map's *content* changes
    // instead.
    // -----------------------------------------------------------------------

    /// The event objects last handed to [#setEventObjects], kept for the
    /// accessible summary's entity counts.
    ///
    /// Read from here rather than from the animator's draw list because the
    /// draw list is a per-frame interpolation: asking it "how many people are
    /// there?" mid-animation can answer differently on consecutive frames.
    private List<FloorMapObject> lastEventObjects = new ArrayList<>();

    /// The time currently shown, as already-formatted text, or `null`.
    private String currentTimeText;

    /// The standing "why is this empty" text, or `null`.
    ///
    /// Held rather than only pushed to the view because it also belongs in the accessible
    /// summary. A screen-reader user gets the map's state from the summary, not from looking at the
    /// corner of the canvas, so the explanation has to reach both or it reaches only sighted
    /// users.
    private String emptyStatusText;

    /// Whether the timeline is playing. Held here so time announcements can be
    /// suppressed during playback — see [#setCurrentTimeText(String)].
    private boolean playing;

    /// Rebuilds the map's accessible name.
    ///
    /// Summarises rather than enumerates. A list of every entity and its
    /// coordinates would be a faithful transcription of the canvas and no use to
    /// anybody: it is unlistenable, and it duplicates the Tracking grid, which is
    /// already a navigable row-per-entity view of the same data (and is wired up as
    /// this element's `aria-describedby`). What a sighted user takes from a
    /// glance at the map is the population, roughly where the interest is, and what
    /// is being followed — so that is what this says.
    private void refreshAccessibleSummary() {
        final StringBuilder sb = new StringBuilder("Floor map");

        if (currentTimeText != null && !currentTimeText.isEmpty()) {
            sb.append(" at ").append(currentTimeText);
        }

        // Before the counts, not after: for a map that is empty for a nameable reason, the reason
        // is the headline and "no moving entities" is the detail.
        if (emptyStatusText != null) {
            sb.append(". ").append(emptyStatusText);
        }

        // Entity counts by type, alphabetical so the sentence does not reshuffle
        // between updates — a summary whose word order changes on every refresh is
        // hard to re-read and hard to diff by ear.
        final Map<String, Integer> countsByType = new TreeMap<>();
        for (final FloorMapObject object : lastEventObjects) {
            final String type = object.getType();
            if (type != null && !hiddenTypes.contains(type)) {
                countsByType.merge(type, 1, Integer::sum);
            }
        }

        if (countsByType.isEmpty()) {
            sb.append(". No moving entities");
        } else {
            sb.append(". ");
            boolean first = true;
            for (final Map.Entry<String, Integer> entry : countsByType.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(entry.getValue()).append(' ').append(entry.getKey());
                // Bare pluralisation: types are user-chosen strings, so anything
                // cleverer would be guessing at their grammar.
                if (entry.getValue() != 1) {
                    sb.append('s');
                }
            }
        }

        final int areaCount = areaMembership.getAreaKeys().size();
        if (areaCount > 0) {
            sb.append(". ").append(areaCount)
                    .append(areaCount == 1
                            ? " area"
                            : " areas");
        }

        if (trackedObjectId != null) {
            sb.append(". Following ").append(describeEntity(trackedObjectId));
        } else if (!selectedObjectIds.isEmpty()) {
            sb.append(". ");
            if (selectedObjectIds.size() == 1) {
                sb.append(describeEntity(selectedObjectIds.iterator().next()))
                        .append(" selected");
            } else {
                sb.append(selectedObjectIds.size()).append(" objects selected");
            }
        }

        sb.append(". Zoom ").append(zoomPercentText());
        getView().setMapSummary(sb.toString());
    }

    /// Names an entity for speech, adding the area it is in when that is known.
    ///
    /// "Alice, in Meeting Room A" rather than "Alice at 12.4, 8.1": map
    /// coordinates are meaningless read aloud, whereas the containing area is the
    /// same answer a sighted user reads off the map.
    private String describeEntity(final String id) {
        final String name = entityNameResolver != null
                ? entityNameResolver.apply(id)
                : id;
        final String displayName = name != null
                ? name
                : id;
        final String areaKey = areaMembership.getInnermostAreaKey(id);
        // Resolve the area's key to its name. The key is an opaque generated id, so
        // reading it aloud ("in area-7f3a...") is noise; the name is what a sighted
        // user reads off the map, which is the whole point of this method. The
        // sibling containingAreaNames already resolves the same way.
        return areaKey != null
                ? displayName + ", in " + displayNameOrId(areaKey)
                : displayName;
    }

    /// The current zoom as a rounded percentage, e.g. `"150%"`.
    private String zoomPercentText() {
        return Math.round(scale * 100) + "%";
    }

    /// Sets or clears the line explaining why the map is empty, and folds it into the accessible
    /// summary.
    ///
    /// Announced only when the text *changes*, which [FloorMapCanvasView#announce]
    /// enforces anyway by dropping repeats — a line that re-announced on every tick while the map
    /// stayed legitimately empty would be worse than not saying it at all.
    ///
    /// @param text  what to say, or `null` to clear it
    /// @param fault whether this is a fault rather than merely an absence
    public void setEmptyStatus(final String text, final boolean fault) {
        final String normalised = text == null || text.isEmpty() ? null : text;
        final boolean changed = normalised == null
                ? emptyStatusText != null
                : !normalised.equals(emptyStatusText);
        emptyStatusText = normalised;
        getView().setEmptyStatus(normalised, fault);
        refreshAccessibleSummary();
        if (changed && normalised != null) {
            getView().announce(normalised);
        }
    }

    /// Sets the time the map is showing, for the summary and the live region.
    ///
    /// Announced only when the map is *not* playing. During playback the
    /// time changes several times a second, and announcing each one would make the
    /// live region useless — a screen reader would do nothing but read timestamps,
    /// drowning out selection and tracking messages that actually need to be heard.
    /// The summary still carries the current time, so "where am I now?" remains
    /// answerable on demand throughout playback.
    ///
    /// Whether playback is running is read from [#playing] rather than
    /// taken as an argument, so no caller can get the distinction wrong.
    ///
    /// @param timeText the formatted time now shown
    public void setCurrentTimeText(final String timeText) {
        this.currentTimeText = timeText;
        refreshAccessibleSummary();
        if (!playing) {
            getView().announce("Showing " + timeText);
        }
    }

    /// Announces, and re-summarises after, a change of tracked entity.
    private void announceTracking() {
        if (trackedObjectId != null) {
            getView().announce("Following " + describeEntity(trackedObjectId));
        } else {
            getView().announce("Stopped following");
        }
        refreshAccessibleSummary();
    }

    /// Points the map's accessible description at the Tracking panel's grid, which
    /// lists one row per entity with its type and containing area.
    ///
    /// The grid is the map's real text equivalent — it is navigable, it updates
    /// with the timeline, and selecting a row tracks that entity. Naming it as the
    /// map's description is what tells a screen-reader user that the detail behind
    /// the summary exists and where to find it.
    ///
    /// @param elementId the grid's element id
    public void setTextAlternativeId(final String elementId) {
        getView().setMapDescribedBy(elementId);
    }

    /// Sets the per-type presentation settings (z-order + default graphic shape
    /// and colour). Used by the view to render imageless facts.
    ///
    /// @param typeStyles the ordered type styles, or `null`
    public void setTypeStyles(final List<TypeStyle> typeStyles) {
        this.typeStyles = typeStyles;
        redraw();
    }

    /// Sets what one map unit means in the real world, so the grid labels, the
    /// scale bar and any distance the canvas reports carry real units.
    ///
    /// Held here as well as pushed to the view because the presenter sizes
    /// offsets in grid divisions ([#minorGridDivisionsToMapUnits]), and the
    /// grid's decade now depends on the scale — given different units the two
    /// would disagree about where the lines are.
    ///
    /// @param measurementUnits the document's units, or `null` when the map
    ///         has no scale set
    public void setMeasurementUnits(final FloorMapMeasurementUnits measurementUnits) {
        this.measurementUnits = measurementUnits;
        getView().setMeasurementUnits(measurementUnits);
        redraw();
    }

    /// The width/height ratio of an image this canvas has already loaded, or
    /// `null` if it has not.
    ///
    /// Exposed so the properties dialog can state an image's real-world size
    /// without probing the image a second time — by the time a fact is being
    /// edited the canvas has almost always drawn it.
    ///
    /// @param imageUrl the image URL
    /// @return the aspect ratio, or `null` when unknown
    public Double getImageAspectRatio(final String imageUrl) {
        return getView().getImageAspectRatio(imageUrl);
    }

    /// Sets per-type layer visibility from the Layers panel. Types in
    /// `hidden` are neither drawn nor hit-tested; types in `dimmed`
    /// render at reduced opacity.
    ///
    /// @param hidden types to hide; `null` treated as empty
    /// @param dimmed types to render dimmed; `null` treated as empty
    public void setLayerVisibility(final Set<String> hidden, final Set<String> dimmed) {
        hiddenTypes.clear();
        if (hidden != null) {
            hiddenTypes.addAll(hidden);
        }
        dimmedTypes.clear();
        if (dimmed != null) {
            dimmedTypes.addAll(dimmed);
        }
        redraw();
    }

    /// Sets the types whose items are locked against movement (Editor Layers
    /// panel). Locked items stay visible and selectable but cannot be dragged.
    ///
    /// @param locked the locked types; `null` treated as empty
    public void setLockedTypes(final Set<String> locked) {
        lockedTypes.clear();
        if (locked != null) {
            lockedTypes.addAll(locked);
        }
        recomputeLockedKeys();
    }

    /// Recomputes [#lockedKeys] from the current facts and locked types.
    private void recomputeLockedKeys() {
        lockedKeys.clear();
        if (lockedTypes.isEmpty()) {
            return;
        }
        for (final Fact fact : facts) {
            if (lockedTypes.contains(fact.getType())) {
                lockedKeys.add(fact.getKey());
            }
        }
    }

    /// Facts minus those whose type is a hidden layer.
    private List<Fact> visibleFacts(final List<Fact> in) {
        if (in == null || hiddenTypes.isEmpty()) {
            return in;
        }
        final List<Fact> out = new ArrayList<>(in.size());
        for (final Fact fact : in) {
            if (!hiddenTypes.contains(fact.getType())) {
                out.add(fact);
            }
        }
        return out;
    }

    /// Event objects minus those whose type is a hidden layer.
    private List<FloorMapObject> visibleEvents(final List<FloorMapObject> in) {
        if (in == null || hiddenTypes.isEmpty()) {
            return in;
        }
        final List<FloorMapObject> out = new ArrayList<>(in.size());
        for (final FloorMapObject ev : in) {
            if (!hiddenTypes.contains(ev.getType())) {
                out.add(ev);
            }
        }
        return out;
    }

    /// Sets the facts to render (backgrounds + static facts) as produced by the
    /// parser. Replaces the legacy background-image/matrix/objects inputs.
    ///
    /// @param facts the facts; `null` is treated as empty
    public void setFacts(final List<Fact> facts) {
        // A tooltip describing a fact this load may have moved, renamed or
        // dropped altogether cannot survive it.
        hideHoverTooltip();
        this.facts = facts != null ? facts : new ArrayList<>();
        // Recompute which facts act as the background (plain drag over them
        // pans rather than moving them), keyed by the BACKGROUND key or type,
        // and which are areas (same pan-when-unselected press handling).
        backgroundKeys.clear();
        areaKeys.clear();
        imageFactsByKey = new HashMap<>();
        for (final Fact fact : this.facts) {
            if (FloorMapJsonKeys.BACKGROUND.equals(fact.getKey())
                    || FloorMapJsonKeys.BACKGROUND.equals(fact.getType())) {
                backgroundKeys.add(fact.getKey());
            } else if (!fact.hasImage() && fact.hasVertices()) {
                areaKeys.add(fact.getKey());
            }
            if (fact.hasImage()) {
                imageFactsByKey.put(fact.getKey(), fact);
            }
        }
        recomputeLockedKeys();
        // Draw first so the view's content bounds reflect these facts, THEN try
        // the initial fit — getContentMapBounds() reads the last-drawn facts.
        // Facts may arrive before or after first layout; this fits as soon as
        // both are available (no-op once the view has been applied).
        redraw();
        maybeApplyInitialView();
    }

    /// Injects an initial view `{scale, offsetX, offsetY}` from another tab
    /// (Map → Editor) so this canvas's first frame matches exactly and nothing
    /// jumps on the tab switch. Only honoured before the initial view is applied;
    /// user pan/zoom afterwards is independent per canvas.
    ///
    /// @param view the view state, or `null` to compute a fit locally
    public void setInitialViewState(final double[] view) {
        this.injectedInitialView = view;
    }

    /// Registers a listener notified once with this canvas's computed initial
    /// view `{scale, offsetX, offsetY}`, so another tab can reuse it.
    ///
    /// @param listener the callback, or `null` to remove
    public void setInitialViewListener(final Consumer<double[]> listener) {
        this.initialViewListener = listener;
    }


    /// Sets the handler that persists a completed move/scale/rotate gesture.
    ///
    /// Called once, when the gesture finishes - not during it. The live preview while dragging
    /// is handled internally via `pendingTransform`.
    ///
    /// @param dragHandler the callback, or `null` to remove
    public void setDragHandler(final DragHandler dragHandler) {
        this.dragHandler = dragHandler;
    }

    /// Sets the handler that persists an area's edited geometry.
    ///
    /// @param geometryHandler the callback, or `null` to remove
    public void setGeometryHandler(final GeometryHandler geometryHandler) {
        this.geometryHandler = geometryHandler;
    }

    /// Sets the handler that receives a finished area-drawing polygon.
    ///
    /// @param areaHandler the callback, or `null` to remove
    public void setAreaHandler(final AreaHandler areaHandler) {
        this.areaHandler = areaHandler;
    }

    /// Sets the handler that receives a finished Set Scale measurement.
    ///
    /// @param scaleHandler the callback, or `null` to remove
    public void setScaleHandler(final ScaleHandler scaleHandler) {
        this.scaleHandler = scaleHandler;
    }

    /// Sets the handler notified when the selection changes as a result of a
    /// canvas interaction (click, Shift-click toggle, or rubber-band marquee).
    ///
    /// @param selectionHandler the callback, or `null` to remove
    public void setSelectionHandler(final SelectionHandler selectionHandler) {
        this.selectionHandler = selectionHandler;
    }

    /// Notifies the [SelectionHandler] of the current selection, if one is
    /// set. The primary key is the first-selected id, or `null` when empty.
    private void fireSelectionChanged() {
        if (selectionHandler != null) {
            final String primary = selectedObjectIds.isEmpty()
                    ? null
                    : selectedObjectIds.iterator().next();
            selectionHandler.onSelectionChanged(
                    new ArrayList<>(selectedObjectIds), primary);
        }
        announceSelection();
    }

    /// Announces what is now selected.
    ///
    /// Selection is otherwise shown only as an orange stroke around a shape,
    /// which conveys nothing without sight — and, being colour alone, little to
    /// some users who do have it.
    private void announceSelection() {
        if (selectedObjectIds.isEmpty()) {
            getView().announce("Selection cleared");
        } else if (selectedObjectIds.size() == 1) {
            getView().announce(
                    describeEntity(selectedObjectIds.iterator().next()) + " selected");
        } else {
            getView().announce(selectedObjectIds.size() + " objects selected");
        }
        refreshAccessibleSummary();
    }

    /// Callback invoked when a transform gesture (move/rotate/scale) completes,
    /// to persist the change as a single map-space affine applied to the whole
    /// selection. A plain move is just a translation transform.
    public interface DragHandler {

        /// Persists an arbitrary map-space transform applied to the whole
        /// selection.
        ///
        /// @param keys the selected fact keys being transformed
        /// @param mapSpaceTransform the accumulated map-space affine to compose
        ///         onto each fact (`newWorldToMap = T · old`)
        void onTransform(Collection<String> keys, FloorMapTransformationMatrix mapSpaceTransform);
    }

    /// Callback invoked when an area's geometry is edited (a vertex moved,
    /// inserted or deleted), to persist the new local-frame vertices.
    public interface GeometryHandler {

        /// Persists the area's new vertices (local frame).
        ///
        /// @param key      the area fact's key
        /// @param vertices the new local-frame vertices (`>= 3`)
        void onGeometryEdited(String key, double[][] vertices);
    }

    /// Callback invoked when the user finishes drawing an area polygon (see
    /// [#startAreaDrawing()]).
    public interface AreaHandler {

        /// @param mapVertices the polygon vertices in map space, in click order;
        ///         always at least 3
        void onAreaDrawn(List<double[]> mapVertices);
    }

    /// Callback invoked when the user finishes measuring a distance with the Set
    /// Scale tool (see [#startScaleMeasurement()]).
    public interface ScaleHandler {

        /// @param mapLength the measured length in map units; always `> 0`.
        ///         The handler asks the user what that distance really is
        ///         and derives the scale from the two.
        void onScaleMeasured(double mapLength);
    }

    /// Callback invoked when a canvas interaction changes the selection.
    public interface SelectionHandler {

        /// @param keys    the full selection in selection order
        /// @param primary the first-selected id (the "primary"), or `null`
        void onSelectionChanged(Collection<String> keys, String primary);
    }


    // =========================================================================
    // View interface
    // =========================================================================

    /// View contract for the floor map canvas.
    ///
    /// The canvas is an SVG-based rendering surface that displays a
    /// background image (the floor plan), overlaid with draggable map objects
    /// (gates, doors, cameras, people, etc.). It supports zoom, pan, object
    /// selection, and right-click context menus.
    ///
    /// The view is responsible for rendering; all interaction logic
    /// (drag handling, selection, coordinate transforms) lives in
    /// [FloorMapCanvasPresenter].
    public interface FloorMapCanvasView extends View, RequiresResize {

        /// Returns the [FocusPanel] that wraps the SVG canvas.
        ///
        /// The presenter registers mouse and context-menu handlers on this
        /// panel. `FocusPanel` is returned (rather than a narrower
        /// `Has*Handlers` type) so that the presenter can also attach
        /// DOM-level handlers via `addDomHandler` (e.g. for the native
        /// `contextmenu` event).
        ///
        /// @return the focus panel containing the SVG canvas
        FocusPanel getFocusPanel();

        /// Returns the handler source for mouse-move events on the canvas.
        ///
        /// @return the mouse-move handler source
        HasMouseMoveHandlers getMouseMoveHandlers();

        /// Returns the handler source for mouse-up events on the canvas.
        ///
        /// @return the mouse-up handler source
        HasMouseUpHandlers getMouseUpHandlers();

        /// Returns the handler source for mouse-wheel events on the canvas.
        ///
        /// @return the mouse-wheel handler source
        HasMouseWheelHandlers getMouseWheelHandlers();

        /// Renders the complete SVG canvas contents.
        ///
        /// This is called on every state change (zoom, pan, object move,
        /// selection change, data load) and rebuilds the entire SVG DOM.
        /// The rendering layers are, from back to front:
        ///
        /// 1. Grid overlay (drawn when `showGrid` is set)
        /// 2. Facts — image facts (incl. backgrounds) and imageless default graphics,
        ///    in the supplied paint (z) order
        /// 3. Events (people) drawn on top
        ///
        /// @param scale           the current zoom scale factor
        /// @param x               the current pan offset X (pixels)
        /// @param y               the current pan offset Y (pixels)
        /// @param facts           the facts to render, already in paint (z) order
        /// @param events          the event entity overlay objects (map coordinates)
        /// @param selectedObjectIds the IDs of the currently selected objects (all
        ///         highlighted); empty if nothing is selected
        /// @param typeStyles      per-type presentation settings (default graphic
        ///         shape/colour for imageless facts); may be `null`
        /// @param showGrid        `true` to draw the (non-interactive) grid overlay
        /// @param dimmedTypes     the types the user has pushed into the background from the
        ///         Layers panel, drawn at reduced opacity. Applies to facts,
        ///         events *and* cluster glyphs, so a dimmed layer does
        ///         not reappear at full strength once its members merge. May
        ///         be `null`, meaning nothing is dimmed
        /// @param marqueeRectPx   the rubber-band selection rectangle
        ///         `{minX, minY, maxX, maxY}` in element pixels, already
        ///         normalised so the mins are the mins whichever way the drag
        ///         went, or `null` when no marquee is in progress
        /// @param drawSelectionHandles
        ///         `true` to draw the selection frame and its handles.
        ///         Distinct from the selection being non-empty: the caller
        ///         suppresses it while a marquee is being dragged, so the
        ///         frame does not fight the rubber band for the user's
        ///         attention
        /// @param scaleRotateEnabled
        ///         `true` when the selection contains at least one
        ///         *unlocked* fact with an image or an outline, so
        ///         scale and rotate handles are worth offering. False leaves
        ///         the move-only frame - handles that cannot act on anything
        ///         are worse than no handles, because they invite a gesture
        ///         that then does nothing
        /// @param areaDraftPx     the in-progress area-drawing polyline in element
        ///         pixels (`{x0, y0, x1, y1, ...}`, last point =
        ///         live cursor), or `null` when not drawing
        /// @param areaOverlay     area-containment decorations — which facts/entities
        ///         carry the "related to the focused entity" highlight
        ///         and what each area's occupant-count badge reads;
        ///         never `null`
        /// @param clusterOverlay  which entities are merged into summary glyphs
        ///         because they are too close together on screen to
        ///         tell apart. Members must **not** be
        ///         drawn individually — the cluster glyph stands in
        ///         for them. Never `null`
        /// @param highlight       resolves the non-selection highlight for each entity -
        ///         a group's own colour, or area-containment green when the
        ///         entity is inside an area holding the tracked entity,
        ///         whichever the resolver says wins. Selection styling still
        ///         takes precedence over both; never `null`
        /// @param measureLinePx   the in-progress Set Scale line
        ///         `{x0, y0, x1, y1}` in element pixels, or
        ///         `null` when not measuring
        void draw(double scale, double x, double y, List<Fact> facts,
                List<FloorMapObject> events, Set<String> selectedObjectIds,
                List<TypeStyle> typeStyles, boolean showGrid, Set<String> dimmedTypes,
                double[] marqueeRectPx, boolean drawSelectionHandles, boolean scaleRotateEnabled,
                double[] areaDraftPx, FloorMapAreaOverlay areaOverlay,
                FloorMapClusterOverlay clusterOverlay,
                FloorMapHighlight highlight, double[] measureLinePx);

        /// Sets what one map unit means in the real world, used to label the grid
        /// and size the scale bar. `null` on a map that has never been
        /// calibrated, which measures in the default scale.
        ///
        /// A setter rather than a [#draw] parameter: it changes only when
        /// the document is read or recalibrated, not per frame.
        ///
        /// @param measurementUnits the document's units, or `null`
        void setMeasurementUnits(FloorMapMeasurementUnits measurementUnits);

        /// Tells the view the Set Scale mode is active, so its instruction pill
        /// can announce the mode before the first press — at which point there is
        /// no measuring line for the view to infer it from.
        ///
        /// @param measuringScale `true` while the mode is active
        void setMeasuringScale(boolean measuringScale);

        /// Shows a readout pill at the cursor during a move or resize, or hides
        /// it.
        ///
        /// @param text      what to show, or `null` to hide
        /// @param cursorXPx cursor position in element pixels
        /// @param cursorYPx cursor position in element pixels
        void setGestureReadout(String text, double cursorXPx, double cursorYPx);

        /// Shows the panel describing whatever is under the pointer — a cluster's
        /// members, or one entity's details — or hides it.
        ///
        /// One panel serves both: only one thing can be under the pointer, and
        /// naming ten entities and describing one are the same job.
        ///
        /// Anchored to the glyph rather than to the cursor, so it holds still
        /// while the pointer moves across the glyph — which also means the
        /// presenter only needs to call this when the hovered glyph changes, not
        /// on every mouse move.
        ///
        /// @param caption   the heading — a cluster's `"10 users"` or an
        ///         entity's name — or `null` to hide
        /// @param lines     the lines to list under it: member names (already
        ///         capped) or an entity's details
        /// @param anchorXPx the glyph's centre in element pixels
        /// @param anchorYPx the glyph's centre in element pixels
        void setHoverTooltip(String caption, List<String> lines,
                double anchorXPx, double anchorYPx);

        /// Supplies the resolver used to caption a cluster drawn around the tracked
        /// entity, which needs that entity's display name.
        ///
        /// A setter rather than a [#draw] parameter, for the same reason
        /// as the measurement units: it is wired once by the owning tab and never
        /// changes per frame.
        ///
        /// @param entityNameResolver resolves an entity id to its display name, or
        ///         `null` to fall back to ids
        void setEntityNameResolver(Function<String, String> entityNameResolver);

        /// Returns the screen-space bounding box `{minX, minY, maxX, maxY}`
        /// of the selected facts, **without** the minimum-size
        /// padding [#getSelectionFrame()] applies — that padding exists to
        /// keep drag handles separable and would overstate a small object's size.
        ///
        /// Reflects the last [#draw], so during a gesture it already
        /// includes the live transform.
        ///
        /// @return the bounds, or `null` when nothing is selected or laid out
        double[] getSelectionBoundsPx();

        /// The width/height ratio of an image the canvas has already loaded, or
        /// `null` if it has not. Never starts a load.
        ///
        /// @param imageUrl the image URL
        /// @return the aspect ratio, or `null` when unknown
        Double getImageAspectRatio(String imageUrl);

        /// Returns the keys of facts whose on-screen bounds intersect the given
        /// rubber-band rectangle (element-pixel space, `{minX, minY, maxX,
        /// maxY}`). Uses the geometry of the last [#draw] call — including
        /// image aspect ratios, which are known only to the view.
        ///
        /// @param rectPx the marquee rectangle in element pixels
        /// @return the intersecting fact keys; never `null`
        Set<String> hitTestScreenRect(double[] rectPx);

        /// Returns the fact's map-space anchor point (the point the camera
        /// centres on when the fact is tracked). Delegates to
        /// [Fact#mapAnchor] with the view's image display width and the
        /// fact's aspect ratio — which is known only to the view.
        ///
        /// @param fact the fact to anchor; must not be `null`
        /// @return the anchor `{mapX, mapY}`; never `null`
        double[] getFactMapAnchor(Fact fact);

        /// Returns the screen-space bounding box `{minX, minY, maxX, maxY}`
        /// of the current selection (from the last [#draw]), or `null`
        /// if nothing is selected. Used to seed a scale/rotate gesture.
        ///
        /// @return the selection frame in element pixels, or `null`
        double[] getSelectionFrame();

        /// Returns the map-space bounding box `{minX, minY, maxX, maxY}` of
        /// all facts from the last [#draw] call, or `null` if there is
        /// no content. Used to compute the initial zoom-to-fit view. Independent
        /// of the current scale/pan (unlike [#getSelectionFrame()]).
        ///
        /// @return the content bounds in map space, or `null`
        double[] getContentMapBounds();

        /// Registers a listener that is called whenever the view needs to
        /// trigger a redraw from outside the normal presenter flow (e.g.
        /// after an asynchronous image aspect-ratio calculation completes).
        ///
        /// @param redrawListener the callback to invoke, typically
        ///         `FloorMapCanvasPresenter::redraw`
        void setRedrawListener(Runnable redrawListener);

        /// Registers a listener that is called once the canvas has a real
        /// (non-zero) on-screen size — i.e. after layout completes. The
        /// presenter uses this to apply its size-dependent initial view (which
        /// needs the canvas dimensions to fit or place the content).
        ///
        /// @param resizeListener the callback to invoke, typically
        ///         `FloorMapCanvasPresenter::maybeApplyInitialView`
        void setResizeListener(Runnable resizeListener);

        /// Sets the map's accessible name — a one-line summary of what is currently
        /// drawn, for a user who cannot see it.
        ///
        /// The canvas is exposed to assistive technology as a single image with a
        /// generated description, not as a tree of shapes. Read in DOM order the
        /// SVG's own text captions are a stream of disconnected names and numbers:
        /// they are positioned for the eye, and their paint order is a z-order, not
        /// a reading order.
        ///
        /// @param summary the summary; replaces any previous one
        void setMapSummary(String summary);

        /// Points the map's accessible description at another element — in practice
        /// the Tracking panel's grid, which is the map's row-by-row text equivalent.
        ///
        /// @param elementId the id of the describing element
        void setMapDescribedBy(String elementId);

        /// Announces a change through the canvas's live region.
        ///
        /// For things the map says only by redrawing itself: what is selected,
        /// what is being followed, that the followed entity has left the timeline
        /// range. Repeats of the current message are dropped.
        ///
        /// @param message the message to announce
        void announce(String message);

        /// Shows or clears the on-canvas line saying why the map is empty.
        ///
        /// @param text  what to say, or `null` to clear it
        /// @param fault whether this is a fault rather than merely an absence
        void setEmptyStatus(String text, boolean fault);
    }

}
