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

import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.cluster.FloorMapCluster;
import stroom.floormap.client.cluster.FloorMapClusterLabel;
import stroom.floormap.client.cluster.FloorMapClusterOverlay;
import stroom.floormap.client.geometry.FloorMapLabelPlacement;
import stroom.floormap.client.geometry.FloorMapMarker;
import stroom.floormap.client.geometry.FloorMapScreenGeometry;
import stroom.floormap.client.geometry.FloorMapShapes;
import stroom.floormap.client.geometry.FloorMapZOrder;
import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapEntityList;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.floormap.client.overlay.FloorMapAreaOverlay;
import stroom.floormap.client.overlay.FloorMapHighlight;
import stroom.floormap.client.presenter.FloorMapCanvasPresenter;
import stroom.floormap.client.presenter.FloorMapCanvasPresenter.FloorMapCanvasView;
import stroom.floormap.shared.FloorMapIcon;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.TypeStyle;
import stroom.util.client.Console;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.HasMouseMoveHandlers;
import com.google.gwt.event.dom.client.HasMouseUpHandlers;
import com.google.gwt.event.dom.client.HasMouseWheelHandlers;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/// GWT view implementation for the interactive SVG floor-map canvas.
///
/// Renders the map from a list of [Fact]s — each fact placed by its own
/// `world-to-map` matrix — plus an event entity overlay:
///
/// - Facts with an image are drawn as scaled images (multiple backgrounds are
///   simply several image facts).
/// - Imageless facts and event entities are drawn as the same fixed-size
///   default graphic whose shape and colour come from the per-type settings
///   ([TypeStyle]); event entities additionally carry movement trails
///   tinted with the type colour.
///
/// Facts are painted in the order supplied by the presenter (the configured
/// type z-order); events paint on top. Pan and zoom are applied via a wrapping
/// SVG `<g>` group.
public class FloorMapCanvasViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements FloorMapCanvasView, ReadOnlyChangeHandler {

    /// The local display width of an image fact in SVG user-units. The height is
    /// derived from this and the image's aspect ratio; the fact's world-to-map
    /// matrix then places and scales the image in map space.
    private static final double IMAGE_DISPLAY_WIDTH =
            FloorMapScreenGeometry.DEFAULT_IMAGE_DISPLAY_WIDTH;

    /// On-screen size (SVG user-units, i.e. pixels at the fixed-size transform) of
    /// an imageless default graphic and of an event marker. 0.6× the original
    /// 100 — the label `font-size` is deliberately left unchanged.
    private static final int OBJECT_SIZE = FloorMapScreenGeometry.POINT_GLYPH_SIZE_PX;

    /// On-screen size (px) of a square scale handle.
    private static final double HANDLE_SIZE_PX = 8;
    /// On-screen radius (px) of the round rotation handle.
    private static final double ROTATE_HANDLE_RADIUS_PX = 5;
    /// Gap (px) between the top edge of the selection frame and the rotation handle.
    private static final double ROTATE_HANDLE_OFFSET_PX = 24;
    /// Minimum on-screen frame size (px) so the handles stay separable on tiny objects.
    private static final double MIN_FRAME_PX = 24;
    /// On-screen radius (px) of a round edge-midpoint "+" insert handle.
    private static final double INSERT_HANDLE_RADIUS_PX = 6;
    /// Edges shorter than this on-screen (px) omit their "+" insert handle.
    private static final double MIN_INSERT_EDGE_PX = 24;
    /// Primary accent (blue) used for handles, the marquee and the area draft.
    private static final String ACCENT_BLUE = "#1e88e5";
    /// Selection highlight (orange) drawn around a selected fact.
    private static final String SELECTION_STROKE = "#ff9800";

    /// The outline round a marker, and the fill of the icon inside it.
    ///
    /// A literal white rather than a theme colour, in both themes: the marker's
    /// job is to separate itself from an arbitrary floor plan underneath, and that
    /// plan is an uploaded image whose colours no theme knows about. A dark
    /// outline would vanish against a dark plan.
    private static final String MARKER_OUTLINE = "#ffffff";
    // The containment-highlight green lives in FloorMapHighlight, which resolves
    // every non-selection highlight colour (group vs containment) in one place.
    /// Opacity applied to a dimmed (0.3) layer group.
    private static final String DIMMED_LAYER_OPACITY = "0.3";
    private static final String HANDLE_STROKE = ACCENT_BLUE;
    private static final String HANDLE_FILL = "#ffffff";
    /// Fill for the round "+" insert handle (light blue tint).
    private static final String INSERT_HANDLE_FILL = "#e3f2fd";
    /// Greyed handle colours, shown when the selection can't be scaled/rotated.
    private static final String HANDLE_DISABLED_STROKE = "#9e9e9e";
    private static final String HANDLE_DISABLED_FILL = "#e0e0e0";
    /// Tooltip explaining why the handles are inert for a non-transformable fact.
    private static final String HANDLE_DISABLED_TOOLTIP =
            "Only image facts and areas can be scaled or rotated";

    /// Default translucency of an area fact's fill when no opacity is stored.
    private static final double DEFAULT_AREA_FILL_OPACITY = 0.3;
    /// On-screen radius (px) of an area's occupant-count badge.
    private static final double OCCUPANT_BADGE_RADIUS_PX = 10;

    /// Gap (screen px) between the bottom of a glyph and its caption, so the text
    /// does not touch the thing it names.
    private static final double GLYPH_CAPTION_GAP_PX = 4;

    /// Style hook for the caption under a glyph — an entity's name, or a cluster's
    /// "10 users". A CSS class rather than baked-in attributes because this text is
    /// drawn *outside* the glyph, over whatever floor plan is beneath it: it
    /// needs the theme's text colour and a halo to stay legible in both light and
    /// dark themes, and neither can be hard-coded here.
    private static final String GLYPH_CAPTION_CLASS = "stroom-floormap-glyph-caption";

    /// Average glyph advance (px) used to estimate a caption's width for collision
    /// testing, at the caption's 11px font. Deliberately a little generous:
    /// underestimating lets two captions touch, which is the thing being prevented,
    /// whereas overestimating only drops a caption that would just have fitted.
    ///
    /// An estimate rather than a measurement because measuring text means
    /// inserting it in the DOM and reading it back — a synchronous reflow per
    /// caption, per frame.
    private static final double CAPTION_CHAR_WIDTH_PX = 6.0;

    /// Line box (px) a caption occupies vertically, for collision testing.
    private static final double CAPTION_HEIGHT_PX = 14;

    /// How many constant-opacity bands a movement trail is drawn as.
    ///
    /// SVG cannot vary stroke opacity along one path, so the animator's per-point alpha ramp is
    /// approximated by splitting the trail into this many sub-paths, each drawn at the opacity of
    /// its newest point. Fixed rather than proportional, so a long trail costs no more elements
    /// than a short one; consecutive bands share an endpoint so there is no gap between them.
    ///
    /// Bands are drawn with butt caps rather than round ones. A round cap extends half the
    /// stroke width past the endpoint, so where two bands met their caps overlapped and the two
    /// semi-transparent strokes composited into a bright blob - one at every band boundary. Butt
    /// caps stop exactly at the shared vertex, so the bands abut without painting the same pixels
    /// twice. Corners *within* a band still round off via `stroke-linejoin`.
    private static final int TRAIL_BANDS = 12;

    /// Caption priorities — **lower is placed first** and so survives
    /// crowding. Clusters sit between the tracked entity and lone entities, and a
    /// bigger cluster outranks a smaller one because its caption speaks for more
    /// entities (see [#clusterCaptionPriority]).
    private static final int CAPTION_PRIORITY_FOCUSED = 0;
    private static final int CAPTION_PRIORITY_CLUSTER_BASE = 1000;
    private static final int CAPTION_PRIORITY_EVENT = 2000;
    private static final int CAPTION_PRIORITY_FACT = 3000;

    /// Added to a caption's priority when its layer is dimmed, so every undimmed
    /// caption is placed first. Larger than the whole undimmed range, so a dimmed
    /// cluster can never outrank an undimmed fact.
    private static final int CAPTION_PRIORITY_DIMMED_PENALTY = 10000;
    /// On-screen radius (px) of the vertex-0 close-target ring in the area
    /// drawing draft — shares the presenter's single constant so the drawn ring
    /// and the click hit-test always match.
    private static final double AREA_DRAFT_CLOSE_RADIUS_PX =
            FloorMapCanvasPresenter.AREA_CLOSE_RADIUS_PX;

    private final Widget widget;

    /// Natural `{width, height}` in pixels per image URL, as reported by the
    /// browser once loaded.
    ///
    /// The intrinsic *size* is cached rather than just the aspect ratio
    /// because [#appendScaledImage] needs it to scale SVGs that cannot scale
    /// themselves. A present entry of `{0, 0}` records "loaded but the browser
    /// would not report a usable size", which stops the image being probed forever.
    private final Map<String, double[]> imageNaturalSizeCache = new HashMap<>();
    private final Set<String> loadingImages = new HashSet<>();
    /// Keys of facts already reported as unplaceable, so the warning is not repeated every frame.
    private final Set<String> unplaceableFactsReported = new HashSet<>();
    private Runnable redrawListener;
    private Runnable resizeListener;

    // Geometry of the last draw(), captured so hitTestScreenRect() can project
    // fact bounds to screen after the fact (map→screen uses these + pan/zoom).
    private double lastScale = 1;
    private double lastOffsetX;
    private double lastOffsetY;
    private List<Fact> lastFacts;

    /// The type styles of the last draw. Held so [#geometry()] can measure a
    /// layer that draws an image at the box that image actually occupies, rather
    /// than assuming every imageless fact is a square glyph.
    private List<TypeStyle> lastTypeStyles;
    private Set<String> lastSelectedIds;

    /// What one map unit means in the real world, or `null` on a map with
    /// no scale set. Set from the presenter rather than passed per draw: it
    /// changes only when the document is read or recalibrated.
    private FloorMapMeasurementUnits measurementUnits;

    /// ID for this canvas's grid `<pattern>`, minted once per view.
    ///
    /// Per instance rather than a shared constant because inline SVG resolves ids
    /// document-wide: with two canvases attached — two open floor maps, or one
    /// document's Map and Editor tabs — the second would fill its background with the
    /// first's pattern and inherit its pan and zoom. Stable across frames rather than
    /// minted per draw, so redrawing does not leak ids.
    private final String gridPatternId = FloorMapAria.uniqueId("floormap-grid-major");

    /// Resolves an entity id to its display name, for captioning a cluster drawn
    /// around the tracked entity. Supplied by the presenter; `null` until
    /// then, which falls the caption back to the id.
    private Function<String, String> entityNameResolver;

    /// Captions collected during the current frame's draw, resolved together at the
    /// end so no two are written on top of each other.
    ///
    /// Collected rather than drawn in place because whether a caption fits can
    /// only be known once every other caption's position is known — and because a
    /// caption drawn with its own glyph can be painted over by a later one.
    private final List<PendingCaption> pendingCaptions = new ArrayList<>();

    @UiField
    HTML svgContainer;

    @UiField
    FocusPanel focusPanel;

    /// Instruction pill overlaid on the canvas while the area-drawing mode is
    /// active — an HTML element (not SVG text) so it uses the theme variables
    /// and reads clearly over any floor plan, matching the timeline scrub
    /// tooltip's visual language.
    @UiField
    Label areaDrawHint;

    private static final String AREA_DRAW_HINT_VISIBLE =
            "stroom-floormap-area-draw-hint--visible";

    /// Pill that follows the cursor while an object is moved or resized, showing
    /// its position or size in real-world units.
    @UiField
    Label gestureReadout;

    private static final String GESTURE_READOUT_VISIBLE =
            "stroom-floormap-gesture-readout--visible";

    /// Offset of the readout from the cursor, so the pointer never covers it.
    private static final int READOUT_OFFSET_X_PX = 16;
    private static final int READOUT_OFFSET_Y_PX = 18;

    /// Room left for the readout when deciding whether it fits to the right of,
    /// or below, the cursor. Its real size is not known until it has been laid
    /// out, and reading that back mid-drag would force a synchronous reflow on
    /// every mouse move.
    private static final int READOUT_ASSUMED_WIDTH_PX = 150;
    private static final int READOUT_ASSUMED_HEIGHT_PX = 24;

    /// Panel describing the glyph under the pointer — the members of a cluster,
    /// or the details of a single entity. What the glyph itself cannot say.
    @UiField
    FlowPanel hoverTooltip;

    private static final String HOVER_TOOLTIP_VISIBLE =
            "stroom-floormap-hover-tooltip--visible";
    private static final String HOVER_TOOLTIP_CAPTION_CLASS =
            "stroom-floormap-hover-tooltip__caption";
    private static final String HOVER_TOOLTIP_LINE_CLASS =
            "stroom-floormap-hover-tooltip__line";

    /// Gap between the glyph's centre and the tooltip's near corner.
    private static final int HOVER_TOOLTIP_OFFSET_PX = 34;

    /// The scale bar: a labelled rule fixed in the canvas corner, showing what a
    /// given on-screen distance is worth in real units.
    ///
    /// It is the canvas's only standing statement of scale: the grid draws no
    /// text, so without this bar the size of a grid square — and therefore of
    /// anything on the map — would be unknowable without starting a drag.
    @UiField
    FlowPanel scaleBar;

    @UiField
    Label scaleBarLabel;

    @UiField
    SimplePanel scaleBarLine;

    /// Line naming why the map is empty, when it is empty for a reason worth naming.
    ///
    /// See [#setEmptyStatus(String, boolean)] for why it is styled in two registers
    /// rather than one.
    @UiField
    Label emptyStatus;

    private static final String EMPTY_STATUS_VISIBLE =
            "stroom-floormap-empty-status--visible";

    private static final String EMPTY_STATUS_FAULT =
            "stroom-floormap-empty-status--fault";

    /// Visually-hidden live region carrying the map's spoken commentary. See
    /// [#announce(String)].
    @UiField
    Label statusRegion;

    /// The last thing announced, so an unchanged message can be skipped.
    ///
    /// Writing the same text into a live region twice may or may not re-announce
    /// depending on the screen reader, and the repeat is never what the user wants:
    /// the point of an announcement is that something changed.
    private String lastAnnouncement = "";

    /// The most recent map summary, re-applied to the focus panel when it loses focus.
    private String lastSummary;

    /// Whether the focus panel currently holds focus — see [#setMapSummary(String)].
    private boolean focusPanelHasFocus;

    /// The widest the scale bar may be drawn. The chosen distance is the largest
    /// 1-2-5 value that fits within this, so the bar is typically 40–120 px.
    private static final double SCALE_BAR_MAX_WIDTH_PX = 120;

    /// Half-length of the tick drawn across each end of the measuring line.
    private static final double MEASURE_TICK_PX = 6;
    /// Gap between the measuring line and its running length readout.
    private static final double MEASURE_LABEL_GAP_PX = 8;

    /// Whether the Set Scale mode is active, so the hint pill can announce it
    /// before the first press — at which point there is no line to infer it from,
    /// and the mode would otherwise look like nothing having happened.
    private boolean measuringScale;

    /// Constructs the canvas view, inflating the UiBinder template.
    ///
    /// @param binder the UiBinder that produces the widget tree
    @Inject
    public FloorMapCanvasViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // The map is a picture of its data, so it is named and summarised as one
        // image rather than exposed as a tree of shapes.
        //
        // The role and label go on the container, not on the <svg>: draw() replaces
        // the SVG wholesale on every frame, so anything written onto that element
        // is destroyed up to 60 times a second, whereas the container is stable for
        // the life of the view. setMapSummary() then costs one attribute write.
        //
        // role="img" also makes the subtree presentational, which is the point: the
        // SVG is full of <text> captions positioned for the eye, and read in DOM
        // (paint) order they are a stream of disconnected names and numbers. The
        // summary and the Tracking grid say the same thing usefully.
        svgContainer.getElement().setAttribute("role", "img");

        // The focus panel needs a name of its own. A screen reader announces the
        // element that has focus, and focus lands here rather than on svgContainer, so
        // the name and role above are never reached: GWT's FocusPanel is a bare
        // div[tabindex="0"], which computes to `generic`, and ARIA 1.2 prohibits naming a
        // generic element. Browsers vary in how strictly they enforce that — Chromium has
        // historically exposed such labels anyway — so "the label is dropped" is the spec
        // position rather than a guarantee about every screen reader. Either way an explicit
        // role is the only way to be sure the name is exposed.
        //
        // role="img" cannot simply move up here to fix that. It makes its whole
        // subtree presentational, and this panel contains statusRegion — the live
        // region carrying every announcement the map makes — plus the hover tooltip
        // and scale bar. Moving it would silence all three. So role="img" stays
        // scoped to the SVG, and the focusable element is named as a group instead,
        // which takes a name while leaving its descendants exposed.
        focusPanel.getElement().setAttribute("role", "group");

        // Tracked so setMapSummary can avoid renaming the element while it holds focus —
        // see there for why. Blur re-applies whatever the summary became meanwhile.
        //noinspection unused e
        focusPanel.addFocusHandler(e -> focusPanelHasFocus = true);
        //noinspection unused e
        focusPanel.addBlurHandler(e -> {
            focusPanelHasFocus = false;
            if (lastSummary != null) {
                focusPanel.getElement().setAttribute("aria-label", lastSummary);
            }
        });

        setMapSummary("Floor map");

        FloorMapAria.liveRegion(statusRegion);
    }

    /// {@inheritDoc}
    @Override
    public Widget asWidget() {
        return widget;
    }

    /// {@inheritDoc}
    @Override
    public void setMapSummary(final String summary) {
        // The container is named unconditionally: it is never focused, so rewriting its
        // label is inert until something reads it.
        svgContainer.getElement().setAttribute("aria-label", summary);

        // The focus panel is different, because renaming the element that currently has
        // focus is an event: screen readers may announce a focused element's new name. The
        // summary embeds the current time and is refreshed several times a second during
        // playback, so writing it here unconditionally would narrate the clock at a keyboard
        // user panning a playing map — reintroducing exactly the chatter that the live
        // region's `playing` guard exists to suppress.
        //
        // So it is only written while the panel does not hold focus, and re-synced on blur.
        // A focused user's name going briefly stale costs nothing: the live region is already
        // telling them what changed, and they get the current summary the moment they leave
        // and come back.
        lastSummary = summary;
        if (!focusPanelHasFocus) {
            focusPanel.getElement().setAttribute("aria-label", summary);
        }
    }

    /// {@inheritDoc}
    @Override
    public void setMapDescribedBy(final String elementId) {
        // Container only — deliberately NOT the focus panel. The id names the whole
        // Tracking / Fact List grid, and an accessible description is that element's
        // flattened text, re-read after name and role on every visit. Put it on the
        // focusable element and tabbing to the map recites the entire grid, one row per
        // entity, every single time. On the container it is reached in browse mode, where
        // the user is moving through content and can leave whenever they like.
        //
        // If the route to the grid should be spoken on focus, it wants a short fixed
        // sentence of its own, not this id.
        svgContainer.getElement().setAttribute("aria-describedby", elementId);
    }

    /// Shows or clears the line explaining why the map is empty.
    ///
    /// Two registers, and the distinction matters more than the wording does. A map with no
    /// events at the selected time is **usually correct** — the timeline is simply outside the
    /// data — so that case reads as a quiet statement of fact. The three configuration faults are
    /// styled to draw the eye. Given one uniform "warning" look, the common harmless case would
    /// read as breakage, and people would learn to ignore the line that matters.
    ///
    /// Never a modal, toast or anything that takes focus: an empty map is a frequent and often
    /// correct state, and interrupting for it would be worse than the silence this replaces.
    ///
    /// @param text  what to say, or `null`/blank to clear the line
    /// @param fault whether this is a fault rather than merely an absence
    @Override
    public void setEmptyStatus(final String text, final boolean fault) {
        if (text == null || text.isEmpty()) {
            emptyStatus.setText("");
            emptyStatus.removeStyleName(EMPTY_STATUS_VISIBLE);
            emptyStatus.removeStyleName(EMPTY_STATUS_FAULT);
            return;
        }
        emptyStatus.setText(text);
        emptyStatus.addStyleName(EMPTY_STATUS_VISIBLE);
        if (fault) {
            emptyStatus.addStyleName(EMPTY_STATUS_FAULT);
        } else {
            emptyStatus.removeStyleName(EMPTY_STATUS_FAULT);
        }
    }

    /// {@inheritDoc}
    @Override
    public void announce(final String message) {
        if (message == null || message.isEmpty() || message.equals(lastAnnouncement)) {
            return;
        }
        lastAnnouncement = message;
        statusRegion.setText(message);
    }

    /// {@inheritDoc} — no read-only visual changes required for the canvas.
    @Override
    public void onReadOnly(final boolean readOnly) {
    }

    /// Handles resize events. If the parent container has no size yet (e.g. during
    /// initial attachment), the call is deferred until layout completes.
    ///
    /// The retry stops if the widget is detached. Without that check the deferred
    /// call re-queued itself unconditionally, so a canvas that never gets laid out —
    /// a document opened into a hidden or closed tab — rescheduled forever and kept
    /// the view, and through it the presenter, reachable for the rest of the
    /// session.
    @Override
    public void onResize() {
        if (!svgContainer.isAttached()) {
            // Nothing to size, and nothing will ever size it. A later attach fires
            // onResize again through the normal widget lifecycle.
            return;
        }

        final Element parent = svgContainer.getElement().getParentElement();
        if (parent == null) {
            return;
        }
        final int width = parent.getOffsetWidth();
        final int height = parent.getOffsetHeight();

        // Defer if the parent hasn't been laid out yet — retry once the browser
        // gives it a size. Guarded by the isAttached() check above, so a canvas that
        // is never laid out stops retrying instead of spinning for the session.
        if (width <= 0 || height <= 0) {
            Scheduler.get().scheduleDeferred((this::onResize));
            return;
        }

        // SVG handles its own responsiveness via 100% width/height. Notify the
        // presenter that the canvas now has a real size so it can apply its
        // size-dependent default view (see setResizeListener).
        if (resizeListener != null) {
            resizeListener.run();
        }
    }

    /// {@inheritDoc}
    @Override
    public FocusPanel getFocusPanel() {
        return focusPanel;
    }

    /// {@inheritDoc}
    @Override
    public HasMouseMoveHandlers getMouseMoveHandlers() {
        return focusPanel;
    }

    /// {@inheritDoc}
    @Override
    public HasMouseUpHandlers getMouseUpHandlers() {
        return focusPanel;
    }

    /// {@inheritDoc}
    @Override
    public HasMouseWheelHandlers getMouseWheelHandlers() {
        return focusPanel;
    }

    /// {@inheritDoc}
    ///
    /// Rebuilds the entire SVG DOM. The structure is
    /// `<svg>` → `<g pan/zoom>` → facts, then events. Each image fact is wrapped in its
    /// own `<g matrix>` and scales with the map; imageless facts and events are
    /// anchored in map space but drawn at a fixed screen size (see
    /// [#fixedSizeTransform]).
    ///
    /// The parameters are documented once, on
    /// `FloorMapCanvasPresenter.FloorMapCanvasView#draw`.
    @Override
    public void draw(final double scale,
                     final double x,
                     final double y,
                     final List<Fact> facts,
                     final List<FloorMapObject> events,
                     final Set<String> selectedObjectIds,
                     final List<TypeStyle> typeStyles,
                     final boolean showGrid,
                     final Set<String> dimmedTypes,
                     final double[] marqueeRectPx,
                     final boolean drawSelectionHandles,
                     final boolean scaleRotateEnabled,
                     final double[] areaDraftPx,
                     final FloorMapAreaOverlay areaOverlay,
                     final FloorMapClusterOverlay clusterOverlay,
                     final FloorMapHighlight highlight,
                     final double[] measureLinePx) {
        final HtmlBuilder htmlBuilder = new HtmlBuilder();

        // Cache this frame's geometry so hitTestScreenRect()/getSelectionFrame()
        // can project fact bounds to screen afterwards (image sizes live only in
        // this view).
        this.lastScale = scale;
        this.lastOffsetX = x;
        this.lastOffsetY = y;
        this.lastFacts = facts;
        this.lastSelectedIds = selectedObjectIds;
        this.lastTypeStyles = typeStyles;
        // Per-frame, and cleared here rather than at the end so an exception part
        // way through a draw cannot leave last frame's captions to be re-placed.
        pendingCaptions.clear();

        htmlBuilder.elem(svg -> {

            // Grid overlay — a non-interactive UI aid, drawn at the SVG root.
            if (showGrid) {
                FloorMapGrid.appendGrid(
                        svg, FloorMapTransformationMatrix.identity(), scale, x, y,
                        measurementUnits, gridPatternId);
            }

            // Pan/zoom group.
            svg.elem(panGroup ->
                // Y-up flip: map space is Y-up; scale(1,-1) maps it to the SVG's
                // Y-down space. Imageless facts and events counter-flip AND
                // counter-scale so their glyphs stay upright and fixed screen
                // size; image facts carry the flip in their matrix and scale
                // with the map.
                panGroup.elem(flipGroup -> {
                    // ---- Facts (paint order = z-order supplied by the presenter) ----
                    if (facts != null) {
                        for (final Fact fact : facts) {
                            // Merged into a cluster: the cluster glyph stands in
                            // for it, so drawing it too would put the crowd back.
                            if (clusterOverlay.isClustered(fact.getKey())) {
                                continue;
                            }
                            final boolean isSelected = selectedObjectIds.contains(fact.getKey());
                            // A dimmed layer wraps its facts in a group at 30%
                            // opacity; otherwise the fact is drawn directly.
                            if (dimmedTypes != null && dimmedTypes.contains(fact.getType())) {
                                flipGroup.elem(g -> renderFact(
                                                g, fact, isSelected, typeStyles, scale, highlight),
                                        SafeHtmlUtil.from("g"), new Attribute("opacity", DIMMED_LAYER_OPACITY));
                            } else {
                                renderFact(flipGroup, fact, isSelected, typeStyles, scale, highlight);
                            }
                        }
                    }

                    // ---- Event entities drawn on top ----
                    if (events != null) {
                        for (final FloorMapObject ev : events) {
                            // A clustered entity's glyph is replaced by the
                            // cluster's — and so is its trail, deliberately: ten
                            // trails converging on one spot is the mess the cluster
                            // is replacing. The exception is the tracked entity,
                            // whose trail is the reason the user is watching, and
                            // whose glyph the cluster is standing in for anyway.
                            if (clusterOverlay.isClustered(ev.getId())) {
                                final FloorMapCluster owner =
                                        clusterOverlay.getClusterFor(ev.getId());
                                if (ev.getId().equals(owner.getFocusedMemberId())) {
                                    appendEventTrail(flipGroup, ev, typeStyles);
                                }
                                continue;
                            }
                            final boolean evSelected = selectedObjectIds.contains(ev.getId());
                            final String evHighlight = highlight.colourFor(ev.getId());
                            if (dimmedTypes != null && dimmedTypes.contains(ev.getType())) {
                                flipGroup.elem(g -> appendEvent(
                                                g, ev, evSelected, evHighlight, typeStyles, scale),
                                        SafeHtmlUtil.from("g"), new Attribute("opacity", DIMMED_LAYER_OPACITY));
                            } else {
                                appendEvent(flipGroup, ev, evSelected, evHighlight, typeStyles, scale);
                            }
                        }
                    }

                    // ---- Cluster glyphs, over the entities they stand in for ----
                    final List<FloorMapCluster> clusters =
                            paintOrdered(clusterOverlay.getClusters(), typeStyles);
                    for (final FloorMapCluster cluster : clusters) {
                        if (dimmedTypes != null && dimmedTypes.contains(cluster.getType())) {
                            flipGroup.elem(g -> appendClusterGlyph(
                                            g, cluster, typeStyles, scale, highlight),
                                    SafeHtmlUtil.from("g"), new Attribute("opacity", DIMMED_LAYER_OPACITY));
                        } else {
                            appendClusterGlyph(flipGroup, cluster, typeStyles, scale, highlight);
                        }
                    }

                    // ---- Area occupant badges, last so nothing covers them ----
                    // Areas paint first (low z-order) and entities cluster near
                    // an area's centre, so a badge drawn with its area would sit
                    // under the very glyphs it is counting.
                    if (facts != null) {
                        for (final Fact fact : facts) {
                            if (!fact.hasImage() && fact.hasVertices()) {
                                final Integer count = areaOverlay.getOccupantCount(fact.getKey());
                                if (count != null && count > 0) {
                                    appendOccupantBadge(flipGroup, fact, count,
                                            areaColour(fact, typeStyles), scale);
                                }
                            }
                        }
                    }

                    // ---- Cluster counts and captions, after every badge ----
                    // Same reason the occupant badges come late, one step further:
                    // a cluster's own count must not end up under an area badge
                    // drawn at a centroid the crowd is sitting on. Dimming is not
                    // applied to these: a count that says "10 users" at 30 %
                    // opacity over a floor plan is unreadable, and the number is
                    // the whole point of the glyph.
                    for (final FloorMapCluster cluster : clusters) {
                        appendClusterCount(flipGroup, cluster, typeStyles, scale);
                    }

                    // ---- Captions, last of all ----
                    // Every glyph, badge and pill is now placed, so this can both
                    // paint over them and know where every caption wants to go —
                    // which is what lets overlapping ones be resolved rather than
                    // written on top of each other.
                    appendPlacedCaptions(flipGroup, scale, x, y, dimmedTypes);
                }, SafeHtmlUtil.from("g"), new Attribute("transform", "scale(1,-1)")),
                SafeHtmlUtil.from("g"),
                    new Attribute("transform", "translate(" + x + "," + y + ") scale(" + scale + ")"));

            // Rubber-band selection marquee — screen space, drawn at the SVG
            // root (no pan/zoom transform), painted on top of the scene.
            if (marqueeRectPx != null) {
                appendMarquee(svg, marqueeRectPx);
            }

            // In-progress area-drawing draft — screen space, on top.
            if (areaDraftPx != null && areaDraftPx.length >= 2) {
                appendAreaDraft(svg, areaDraftPx);
            }

            // In-progress Set Scale measuring line — screen space, on top.
            if (measureLinePx != null && measureLinePx.length >= 4) {
                appendMeasureLine(svg, measureLinePx, scale);
            }

            // Selection frame + scale/rotate handles — screen space, on top.
            if (drawSelectionHandles) {
                final Fact areaFact = singleSelectedArea();
                appendSelectionHandles(svg, scaleRotateEnabled, areaFact != null);
                // Per-vertex move + midpoint insert handles for a single area,
                // painted over the frame so they win the mousedown. Suppressed
                // along with the scale/rotate handles: scaleRotateEnabled is
                // false for a locked layer, and a handle that cannot edit
                // anything should not be offered.
                if (areaFact != null && scaleRotateEnabled) {
                    appendAreaHandles(svg, areaFact);
                }
            }
        },
            SafeHtmlUtil.from("svg"),
            new Attribute("width", "100%"),
            new Attribute("height", "100%"),
            new Attribute("xmlns", "http://www.w3.org/2000/svg"),
            // The container above is the accessible image (role="img" plus the
            // generated summary), so this subtree is decoration. Hiding it stops a
            // screen reader walking hundreds of <text> captions in paint order,
            // which is neither the reading order nor a description of anything.
            new Attribute("aria-hidden", "true"),
            // Keeps the SVG and its shapes out of the tab sequence in the browsers
            // that make SVG focusable by default (legacy IE/Edge behaviour), so
            // Tab continues to leave the map in one press.
            new Attribute("focusable", "false")
        );

        svgContainer.setHTML(htmlBuilder.toSafeHtml());
        updateCanvasHint(areaDraftPx, measureLinePx);
        updateScaleBar(scale);
    }

    /// Draws the in-progress Set Scale measuring line at the SVG root in screen
    /// space: the line itself, a tick across each end, and a running readout of
    /// what it currently measures.
    ///
    /// The readout is in whatever units the map already has — the default
    /// scale if it has never been calibrated — so the user can see the very
    /// quantity they are about to correct.
    ///
    /// @param svg     the SVG root builder to append to
    /// @param linePx  `{x0, y0, x1, y1}` in element pixels
    /// @param scale   the current zoom factor, i.e. pixels per map unit
    private void appendMeasureLine(final HtmlBuilder svg,
                                   final double[] linePx,
                                   final double scale) {
        final double x0 = linePx[0];
        final double y0 = linePx[1];
        final double x1 = linePx[2];
        final double y1 = linePx[3];
        final double dx = x1 - x0;
        final double dy = y1 - y0;
        final double lengthPx = Math.sqrt(dx * dx + dy * dy);

        svg.elem(SafeHtmlUtil.from("line"),
                new Attribute("x1", String.valueOf(x0)),
                new Attribute("y1", String.valueOf(y0)),
                new Attribute("x2", String.valueOf(x1)),
                new Attribute("y2", String.valueOf(y1)),
                new Attribute("stroke", ACCENT_BLUE),
                new Attribute("stroke-width", "2"),
                new Attribute("pointer-events", "none"));

        // End ticks, perpendicular to the line, so both ends read as deliberate
        // endpoints rather than as somewhere the line happens to stop.
        if (lengthPx > 0) {
            final double nx = -dy / lengthPx * MEASURE_TICK_PX;
            final double ny = dx / lengthPx * MEASURE_TICK_PX;
            appendMeasureTick(svg, x0, y0, nx, ny);
            appendMeasureTick(svg, x1, y1, nx, ny);
        }

        // Running readout, offset above the midpoint so the cursor never sits on
        // top of it.
        if (scale > 0) {
            final String text = FloorMapMeasurementUnits.format(
                    measurementUnits, lengthPx / scale);
            svg.elem(text,
                    SafeHtmlUtil.from("text"),
                    new Attribute("x", String.valueOf((x0 + x1) / 2)),
                    new Attribute("y", String.valueOf((y0 + y1) / 2 - MEASURE_LABEL_GAP_PX)),
                    new Attribute("text-anchor", "middle"),
                    new Attribute("fill", ACCENT_BLUE),
                    new Attribute("font-size", "12"),
                    new Attribute("font-family", "sans-serif"),
                    new Attribute("font-weight", "600"),
                    new Attribute("paint-order", "stroke"),
                    new Attribute("style", "stroke: var(--page__background-color)"),
                    new Attribute("stroke-width", "3"),
                    new Attribute("pointer-events", "none"));
        }
    }

    /// One end tick of the measuring line, centred on `(x, y)`.
    private void appendMeasureTick(final HtmlBuilder svg,
                                   final double x,
                                   final double y,
                                   final double nx,
                                   final double ny) {
        svg.elem(SafeHtmlUtil.from("line"),
                new Attribute("x1", String.valueOf(x - nx)),
                new Attribute("y1", String.valueOf(y - ny)),
                new Attribute("x2", String.valueOf(x + nx)),
                new Attribute("y2", String.valueOf(y + ny)),
                new Attribute("stroke", ACCENT_BLUE),
                new Attribute("stroke-width", "2"),
                new Attribute("pointer-events", "none"));
    }

    /// Sizes and labels the scale bar for this frame.
    ///
    /// Drawn on every map, calibrated or not: an uncalibrated map measures in
    /// the default scale (one centimetre per map unit), so the bar always states
    /// a real-world distance.
    ///
    /// Lives here rather than in the rebuilt SVG so it can use the theme's CSS
    /// variables, and so it never moves with pan or zoom. Being inside
    /// `draw` covers both of the presenter's redraw paths — the static one
    /// and the animation-frame loop — so it cannot go stale while entities
    /// move.
    ///
    /// @param scale the current zoom factor; the grid is drawn with an identity
    ///         matrix, so this is also pixels-per-map-unit
    private void updateScaleBar(final double scale) {
        final double[] bar =
                FloorMapGrid.scaleBar(scale, SCALE_BAR_MAX_WIDTH_PX, measurementUnits);
        if (bar[1] <= 0) {
            scaleBar.setVisible(false);
            return;
        }
        scaleBar.setVisible(true);
        scaleBarLabel.setText(FloorMapMeasurementUnits.format(measurementUnits, bar[0]));
        scaleBarLine.setWidth(bar[1] + "px");
    }

    /// {@inheritDoc}
    @Override
    public void setMeasurementUnits(final FloorMapMeasurementUnits measurementUnits) {
        this.measurementUnits = measurementUnits;
    }

    /// {@inheritDoc}
    @Override
    public void setGestureReadout(final String text, final double cursorXPx, final double cursorYPx) {
        if (text == null) {
            gestureReadout.removeStyleName(GESTURE_READOUT_VISIBLE);
            return;
        }
        gestureReadout.setText(text);

        // Sit below-right of the cursor by default, flipping to the other side
        // near an edge so the pill is never clipped by the canvas.
        final Element panel = focusPanel.getElement();
        final int width = panel.getOffsetWidth();
        final int height = panel.getOffsetHeight();

        double x = cursorXPx + READOUT_OFFSET_X_PX;
        if (width > 0 && x + READOUT_ASSUMED_WIDTH_PX > width) {
            x = cursorXPx - READOUT_OFFSET_X_PX - READOUT_ASSUMED_WIDTH_PX;
        }
        double y = cursorYPx + READOUT_OFFSET_Y_PX;
        if (height > 0 && y + READOUT_ASSUMED_HEIGHT_PX > height) {
            y = cursorYPx - READOUT_OFFSET_Y_PX - READOUT_ASSUMED_HEIGHT_PX;
        }

        gestureReadout.getElement().getStyle().setLeft(Math.max(0, x), Unit.PX);
        gestureReadout.getElement().getStyle().setTop(Math.max(0, y), Unit.PX);
        gestureReadout.addStyleName(GESTURE_READOUT_VISIBLE);
    }

    /// {@inheritDoc}
    @Override
    public void setEntityNameResolver(final Function<String, String> entityNameResolver) {
        this.entityNameResolver = entityNameResolver;
    }

    /// {@inheritDoc}
    @Override
    public void setHoverTooltip(final String caption,
                                final List<String> lines,
                                final double anchorXPx,
                                final double anchorYPx) {
        if (caption == null) {
            hoverTooltip.removeStyleName(HOVER_TOOLTIP_VISIBLE);
            return;
        }

        hoverTooltip.clear();
        final Label captionLabel = new Label(caption);
        captionLabel.addStyleName(HOVER_TOOLTIP_CAPTION_CLASS);
        hoverTooltip.add(captionLabel);
        if (lines != null) {
            for (final String line : lines) {
                final Label lineLabel = new Label(line);
                lineLabel.addStyleName(HOVER_TOOLTIP_LINE_CLASS);
                hoverTooltip.add(lineLabel);
            }
        }

        // Sit below-right of the glyph by default, flipping near an edge so the
        // panel is never clipped by the canvas. Unlike the gesture readout the
        // height is not roughly fixed — it grows with the member or detail count
        // — so it is measured rather than assumed. That costs a reflow, but this
        // runs when the hovered glyph changes, not on every mouse move.
        final Element panel = focusPanel.getElement();
        final int panelWidth = panel.getOffsetWidth();
        final int panelHeight = panel.getOffsetHeight();
        hoverTooltip.addStyleName(HOVER_TOOLTIP_VISIBLE);
        final int tooltipWidth = hoverTooltip.getOffsetWidth();
        final int tooltipHeight = hoverTooltip.getOffsetHeight();

        double x = anchorXPx + HOVER_TOOLTIP_OFFSET_PX;
        if (panelWidth > 0 && x + tooltipWidth > panelWidth) {
            x = anchorXPx - HOVER_TOOLTIP_OFFSET_PX - tooltipWidth;
        }
        double y = anchorYPx + HOVER_TOOLTIP_OFFSET_PX;
        if (panelHeight > 0 && y + tooltipHeight > panelHeight) {
            y = anchorYPx - HOVER_TOOLTIP_OFFSET_PX - tooltipHeight;
        }

        hoverTooltip.getElement().getStyle().setLeft(Math.max(0, x), Unit.PX);
        hoverTooltip.getElement().getStyle().setTop(Math.max(0, y), Unit.PX);
    }

    /// {@inheritDoc}
    @Override
    public Double getImageAspectRatio(final String imageUrl) {
        // Deliberately does not start a load: this answers "do we already know?"
        // for a dialog, and must not queue work from a non-drawing path.
        return cachedAspectRatio(imageUrl);
    }

    /// {@inheritDoc}
    @Override
    public double[] getSelectionBoundsPx() {
        // Unpadded, unlike the selection frame: that pads small selections out
        // to a minimum size so the drag handles stay separable, which would
        // report a small object as bigger than it is.
        return geometry().selectionFrame(lastFacts, lastSelectedIds, 0);
    }

    /// Shows, hides and words the instruction pill for whichever modal canvas
    /// mode is active. It is an HTML overlay (see the ui.xml) rather than part of
    /// the rebuilt SVG, styled via `stroom-floormap-area-draw-hint` to match
    /// the timeline scrub tooltip.
    ///
    /// A modal mode must be visibly announced from the instant it starts, or
    /// it is indistinguishable from nothing having happened — which is exactly
    /// how area drawing was first reported as broken.
    ///
    /// @param areaDraftPx   the area draft passed to this draw, or `null`
    ///         when the drawing mode is not active
    /// @param measureLinePx the Set Scale line passed to this draw; only
    ///         non-null once the press has landed, so the mode is
    ///         announced by [#setMeasuringScale] instead
    private void updateCanvasHint(final double[] areaDraftPx, final double[] measureLinePx) {
        if (areaDraftPx != null) {
            final int committed = areaDraftPx.length / 2 - 1;
            showHint(committed >= 3
                    ? "Drawing area — click the first point, double-click or press Enter"
                      + " to finish · Esc cancels · right-click undoes the last point"
                    : "Drawing area — click to add points (at least 3)"
                      + " · Esc cancels · right-click undoes the last point");
            return;
        }
        if (measuringScale) {
            showHint(measureLinePx != null
                    ? "Set scale — release at the far end of the distance you know"
                      + " · Esc cancels"
                    : "Set scale — drag a line across something whose real length"
                      + " you know · Esc cancels · right-click leaves this mode");
            return;
        }
        areaDrawHint.removeStyleName(AREA_DRAW_HINT_VISIBLE);
    }

    private void showHint(final String text) {
        areaDrawHint.setText(text);
        areaDrawHint.addStyleName(AREA_DRAW_HINT_VISIBLE);
    }

    /// {@inheritDoc}
    @Override
    public void setMeasuringScale(final boolean measuringScale) {
        this.measuringScale = measuringScale;
    }

    /// Draws the rubber-band selection rectangle at the SVG root in screen space.
    ///
    /// @param svg    the SVG root builder
    /// @param rectPx `{minX, minY, maxX, maxY}` in element pixels
    private void appendMarquee(final HtmlBuilder svg, final double[] rectPx) {
        svg.elem(SafeHtmlUtil.from("rect"),
                new Attribute("x", String.valueOf(rectPx[0])),
                new Attribute("y", String.valueOf(rectPx[1])),
                new Attribute("width", String.valueOf(rectPx[2] - rectPx[0])),
                new Attribute("height", String.valueOf(rectPx[3] - rectPx[1])),
                new Attribute("fill", ACCENT_BLUE),
                new Attribute("fill-opacity", "0.12"),
                new Attribute("stroke", ACCENT_BLUE),
                new Attribute("stroke-width", "1"),
                new Attribute("stroke-dasharray", "4,4"),
                new Attribute("pointer-events", "none"));
    }

    /// Draws the in-progress area-drawing draft at the SVG root in screen space:
    /// a solid polyline through the committed vertices, a dashed rubber-band
    /// segment from the last committed vertex to the live cursor, a small dot on
    /// each committed vertex, and a close-target ring on vertex 0 that fills in
    /// when the polygon can be closed (≥ 3 committed vertices and the cursor
    /// within the close radius). Everything is non-interactive.
    ///
    /// @param svg     the SVG root builder to append to
    /// @param draftPx flat polyline `{x0, y0, ..., xn, yn}` in element
    ///         pixels; the last point is the live cursor position
    private void appendAreaDraft(final HtmlBuilder svg, final double[] draftPx) {
        final int points = draftPx.length / 2;
        final int committed = points - 1;

        // Solid polyline through the committed vertices.
        if (committed >= 2) {
            final StringBuilder committedPoints = new StringBuilder();
            for (int i = 0; i < committed; i++) {
                if (i > 0) {
                    committedPoints.append(" ");
                }
                committedPoints.append(draftPx[i * 2]).append(",").append(draftPx[i * 2 + 1]);
            }
            svg.elem(SafeHtmlUtil.from("polyline"),
                    new Attribute("points", committedPoints.toString()),
                    new Attribute("fill", "none"),
                    new Attribute("stroke", ACCENT_BLUE),
                    new Attribute("stroke-width", "2"),
                    new Attribute("pointer-events", "none"));
        }

        // Dashed rubber-band edge from the last committed vertex to the cursor.
        if (committed >= 1) {
            svg.elem(SafeHtmlUtil.from("line"),
                    new Attribute("x1", String.valueOf(draftPx[(committed - 1) * 2])),
                    new Attribute("y1", String.valueOf(draftPx[(committed - 1) * 2 + 1])),
                    new Attribute("x2", String.valueOf(draftPx[(points - 1) * 2])),
                    new Attribute("y2", String.valueOf(draftPx[(points - 1) * 2 + 1])),
                    new Attribute("stroke", ACCENT_BLUE),
                    new Attribute("stroke-width", "1"),
                    new Attribute("stroke-dasharray", "4,4"),
                    new Attribute("pointer-events", "none"));
        }

        // Committed vertex dots.
        for (int i = 0; i < committed; i++) {
            svg.elem(SafeHtmlUtil.from("circle"),
                    new Attribute("cx", String.valueOf(draftPx[i * 2])),
                    new Attribute("cy", String.valueOf(draftPx[i * 2 + 1])),
                    new Attribute("r", "3"),
                    new Attribute("fill", ACCENT_BLUE),
                    new Attribute("pointer-events", "none"));
        }

        // Close-target ring on vertex 0, highlighted when the polygon is
        // closable (enough vertices and the cursor within the close radius).
        if (committed >= 1) {
            final double dx = draftPx[(points - 1) * 2] - draftPx[0];
            final double dy = draftPx[(points - 1) * 2 + 1] - draftPx[1];
            final boolean closable = committed >= 3
                    && (dx * dx + dy * dy)
                    <= AREA_DRAFT_CLOSE_RADIUS_PX * AREA_DRAFT_CLOSE_RADIUS_PX;
            svg.elem(SafeHtmlUtil.from("circle"),
                    new Attribute("cx", String.valueOf(draftPx[0])),
                    new Attribute("cy", String.valueOf(draftPx[1])),
                    new Attribute("r", String.valueOf(AREA_DRAFT_CLOSE_RADIUS_PX)),
                    new Attribute("fill", closable ? ACCENT_BLUE : "none"),
                    new Attribute("fill-opacity", closable ? "0.4" : "0"),
                    new Attribute("stroke", ACCENT_BLUE),
                    new Attribute("stroke-width", closable ? "2" : "1"),
                    new Attribute("pointer-events", "none"));
        }
    }

    /// Renders a single fact into the given builder, dispatching by content:
    /// image facts render their image; imageless facts with vertices render as
    /// areas; everything else renders as the type's default glyph — which is
    /// itself either the layer's configured image or its shape.
    ///
    /// Note the precedence: a fact carrying its own `img` takes this
    /// first branch, so it always beats its layer's graphic.
    private void renderFact(final HtmlBuilder builder,
                            final Fact fact,
                            final boolean isSelected,
                            final List<TypeStyle> typeStyles,
                            final double scale,
                            final FloorMapHighlight highlight) {
        if (!fact.hasUsablePlacement()) {
            // Skip rather than draw it wrongly, and say so: silently dropping an object the
            // user can see in the Fact List is its own kind of confusing. Once per fact, as
            // this runs every animation frame during playback. It cannot happen to well-formed
            // data - the parser rejects an unusable matrix - so it means a document written
            // before that check, or a hand-edited store value of six valid zeros.
            if (unplaceableFactsReported.add(fact.getKey())) {
                Console.warn(() -> "Skipping fact '" + fact.getKey()
                                   + "': its world-to-map matrix is singular, so it cannot be"
                                   + " placed. Reported once per fact.");
            }
            return;
        }
        // One resolved colour per fact — group membership or area containment,
        // whichever the highlight resolver says wins. Null means no highlight.
        final String highlightColour = highlight.colourFor(fact.getKey());
        if (fact.hasImage()) {
            appendImageFact(builder, fact, isSelected, highlightColour);
        } else if (fact.hasVertices()) {
            appendAreaFact(builder, fact, isSelected, highlightColour,
                    highlight.isDashed(fact.getKey()), typeStyles);
        } else {
            appendDefaultGraphic(builder, fact, isSelected, highlightColour, typeStyles, scale);
        }
    }

    /// Draws an area fact — a filled polygon whose vertices are in the fact's
    /// local frame, placed into map space by its world-to-map matrix inside the
    /// Y-up flip group (so it pans, zooms and rotates with the map, like an
    /// image fact).
    ///
    /// The translucent fill is deliberately non-interactive so a large area
    /// cannot hijack panning, marquee selection or the empty-canvas context
    /// menu; selection is by clicking the border, via an invisible wide "hit"
    /// stroke that carries the fact key as its id (the same click-detection
    /// convention as every other object shape).
    ///
    /// A coloured border marks a highlighted area — green and dashed when it
    /// holds the entity being tracked, or a group's own colour (solid) when the area
    /// is a member of a highlighted group. Selection styling still wins over both —
    /// being *the* selection is more specific. The occupant-count badge is
    /// drawn separately, after every fact and event, by
    /// [#appendOccupantBadge].
    ///
    /// @param highlightColour the non-selection highlight colour, or `null`
    ///         when this area carries no highlight
    /// @param highlightDashed `true` to dash the highlight border, which
    ///         distinguishes area containment from group membership
    private void appendAreaFact(final HtmlBuilder parent,
                                final Fact fact,
                                final boolean isSelected,
                                final String highlightColour,
                                final boolean highlightDashed,
                                final List<TypeStyle> typeStyles) {
        final double[][] vertices = fact.getVertices();
        final StringBuilder points = new StringBuilder();
        for (int i = 0; i < vertices.length; i++) {
            if (i > 0) {
                points.append(" ");
            }
            points.append(vertices[i][0]).append(",").append(vertices[i][1]);
        }

        final String colour = areaColour(fact, typeStyles);
        final double opacity = fact.getOpacity() != null
                ? Math.max(0.0, Math.min(1.0, fact.getOpacity()))
                : DEFAULT_AREA_FILL_OPACITY;
        final boolean highlighted = highlightColour != null;
        // Selection is more specific than any highlight, so it wins the border.
        final String stroke = isSelected
                ? SELECTION_STROKE
                : highlighted
                        ? highlightColour
                        : colour;
        final String strokeWidth = isSelected || highlighted ? "4" : "2";
        // A dash distinguishes "holds what you're tracking" from "is selected" and
        // from "is in a highlighted group", for anyone who can't rely on the colour
        // difference alone.
        final String dashArray = !isSelected && highlightDashed ? "8,4" : "none";
        // A highlight also lifts the fill a little, so the area reads as
        // highlighted when zoomed out too far to see the border clearly.
        final double effectiveOpacity = !isSelected && highlighted
                ? Math.min(1.0, opacity + 0.15)
                : opacity;

        parent.elem(areaGroup -> {
            // Visible polygon — non-interactive.
            areaGroup.elem(SafeHtmlUtil.from("polygon"),
                    new Attribute("points", points.toString()),
                    new Attribute("fill", colour),
                    new Attribute("fill-opacity", String.valueOf(effectiveOpacity)),
                    new Attribute("fill-rule", "evenodd"),
                    new Attribute("stroke", stroke),
                    new Attribute("stroke-width", strokeWidth),
                    new Attribute("stroke-dasharray", dashArray),
                    new Attribute("vector-effect", "non-scaling-stroke"),
                    new Attribute("pointer-events", "none"));
            // Invisible hit polygon — the clickable element. pointer-events
            // "all" makes both the interior and a generous 10px border band
            // clickable regardless of paint, so an area is selected by
            // clicking anywhere inside it. The presenter treats a press on an
            // UNSELECTED area like a background press (drag pans, click
            // selects), so a large area cannot hijack map panning.
            areaGroup.elem(SafeHtmlUtil.from("polygon"),
                    new Attribute("points", points.toString()),
                    new Attribute("fill", "#000000"),
                    new Attribute("fill-opacity", "0"),
                    new Attribute("stroke", "#000000"),
                    new Attribute("stroke-opacity", "0"),
                    new Attribute("stroke-width", "10"),
                    new Attribute("vector-effect", "non-scaling-stroke"),
                    new Attribute("pointer-events", "all"),
                    new Attribute("id", fact.getKey()));
        }, SafeHtmlUtil.from("g"),
                new Attribute("transform", fact.getWorldToMap().toSvgMatrix()),
                new Attribute("id", FloorMapJsonKeys.SVG_GROUP_PREFIX + fact.getKey()));
    }

    /// An area's colour: its own stored fill if set, else the colour configured
    /// for its type (which is `"area"` for areas created by the editor, but
    /// users may retype areas — e.g. `"restricted"` — and expect that
    /// type's Settings colour).
    private static String areaColour(final Fact fact, final List<TypeStyle> typeStyles) {
        return fact.getFill() != null && !fact.getFill().isEmpty()
                ? fact.getFill()
                : TypeStyle.colourForType(fact.getType(), typeStyles);
    }

    /// Draws an area's occupant-count badge at the area's map-space centroid: a
    /// filled disc in the area's own colour with the count in white, at a fixed
    /// screen size so it stays legible at any zoom.
    ///
    /// The count is the number of entities whose position at the current
    /// timeline instant falls inside the polygon. It is deliberately
    /// *not* an occupancy figure — an entity with no event near this
    /// instant has no position and so is not counted (see
    /// [FloorMapAreaMembership]).
    private void appendOccupantBadge(final HtmlBuilder parent,
                                     final Fact fact,
                                     final int occupantCount,
                                     final String colour,
                                     final double scale) {
        final double[] centroid = fact.mapTestPoint();
        appendCountPill(parent, occupantCount, colour,
                centroid[0], centroid[1], 0, 0, scale);
    }

    /// Draws a count pill at fixed screen size, anchored at a map-space point and
    /// offset from it in screen pixels.
    ///
    /// Shared by the area occupant badge (centred on the area) and the cluster
    /// count (offset to the glyph's corner), so the two read as the same kind of
    /// statement — a number this canvas is telling you about the thing underneath
    /// it.
    ///
    /// @param parent    the builder to append to, inside the Y-up flip group
    /// @param count     the number to show
    /// @param colour    the pill's fill, normally the type's own colour
    /// @param mapX      the anchor point in map space
    /// @param mapY      the anchor point in map space
    /// @param offsetXPx screen-pixel offset from the anchor, positive right
    /// @param offsetYPx screen-pixel offset from the anchor, positive down
    /// @param scale     the current zoom factor
    private void appendCountPill(final HtmlBuilder parent,
                                 final int count,
                                 final String colour,
                                 final double mapX,
                                 final double mapY,
                                 final double offsetXPx,
                                 final double offsetYPx,
                                 final double scale) {
        final String text = String.valueOf(count);
        // Widen the disc into a pill for 2+ digits so the text keeps clear of
        // the edge.
        final double radius = OCCUPANT_BADGE_RADIUS_PX;
        final double halfWidth = text.length() > 1
                ? radius + (text.length() - 1) * 4.0
                : radius;

        parent.elem(badgeGroup -> {
            badgeGroup.elem(SafeHtmlUtil.from("rect"),
                    new Attribute("x", String.valueOf(offsetXPx - halfWidth)),
                    new Attribute("y", String.valueOf(offsetYPx - radius)),
                    new Attribute("width", String.valueOf(halfWidth * 2)),
                    new Attribute("height", String.valueOf(radius * 2)),
                    new Attribute("rx", String.valueOf(radius)),
                    new Attribute("ry", String.valueOf(radius)),
                    new Attribute("fill", colour),
                    new Attribute("stroke", "#ffffff"),
                    new Attribute("stroke-width", "1.5"),
                    new Attribute("pointer-events", "none"));
            badgeGroup.elem(text,
                    SafeHtmlUtil.from("text"),
                    new Attribute("x", String.valueOf(offsetXPx)),
                    new Attribute("y", String.valueOf(offsetYPx)),
                    new Attribute("dy", "0.35em"),
                    new Attribute("text-anchor", "middle"),
                    new Attribute("fill", "#ffffff"),
                    new Attribute("font-size", "12px"),
                    new Attribute("font-weight", "bold"),
                    new Attribute("font-family", "sans-serif"),
                    new Attribute("pointer-events", "none"));
        }, SafeHtmlUtil.from("g"),
                new Attribute("transform", fixedSizeTransform(mapX, mapY, scale)));
    }

    /// Orders clusters back-to-front by their type's configured paint order, so a
    /// merged crowd sits in the same layer its members would have.
    ///
    /// The overlay produces clusters in type-*name* order, which is only
    /// a determinism guarantee and says nothing about which layer belongs on
    /// top.
    private static List<FloorMapCluster> paintOrdered(final List<FloorMapCluster> clusters,
                                                      final List<TypeStyle> typeStyles) {
        final List<FloorMapCluster> ordered = new ArrayList<>(clusters);
        // Stable, so clusters of one type keep the overlay's deterministic order.
        ordered.sort(Comparator.comparingInt(
                cluster -> FloorMapZOrder.indexOf(cluster.getType(), typeStyles)));
        return ordered;
    }

    /// Draws the summary glyph standing in for a cluster's members: the type's own
    /// graphic, so a merged crowd of users still reads as users in the user
    /// colour.
    ///
    /// Carries [FloorMapJsonKeys#CLUSTER_PREFIX] on its id rather than a
    /// member's id, because a cluster is not an entity — the object hit-test must
    /// not report it as one.
    ///
    /// The glyph takes the highlight of any highlighted member, so switching a
    /// group's highlight on still shows where its members are when they are too
    /// crowded to draw individually. It is never drawn as selected: the selection
    /// is excluded from clustering upstream, so a selected entity is always its own
    /// glyph.
    ///
    /// It is drawn **bigger the more it stands for** — see
    /// [FloorMapCluster#getSizeFactor()] — so the difference between a pair
    /// and a crowd reads before the count pill is examined.
    private void appendClusterGlyph(final HtmlBuilder parent,
                                    final FloorMapCluster cluster,
                                    final List<TypeStyle> typeStyles,
                                    final double scale,
                                    final FloorMapHighlight highlight) {
        String highlightColour = null;
        for (final String memberId : cluster.getMemberIds()) {
            highlightColour = highlight.colourFor(memberId);
            if (highlightColour != null) {
                break;
            }
        }
        appendStyledGlyph(parent,
                FloorMapJsonKeys.CLUSTER_PREFIX + cluster.getKey(),
                cluster.getType(),
                cluster.getMapX(), cluster.getMapY(),
                // A cluster drawn around the tracked entity carries that entity's
                // selection ring: this glyph IS them as far as the user is
                // concerned, and following someone into a crowd must not look like
                // losing them.
                cluster.hasFocusedMember(), highlightColour, typeStyles, scale,
                cluster.getSizeFactor());
    }

    /// Draws a cluster's count pill and its caption — the pill at the glyph's
    /// top-right corner, the caption centred underneath.
    ///
    /// The caption spells the count out ("10 users") rather than leaving a bare
    /// number to be decoded. It is drawn with a halo (a white stroke painted under
    /// the fill) because it sits outside the glyph, over whatever floor plan
    /// happens to be beneath it.
    private void appendClusterCount(final HtmlBuilder parent,
                                    final FloorMapCluster cluster,
                                    final List<TypeStyle> typeStyles,
                                    final double scale) {
        // The glyph's real box, not an assumed square: a layer drawing an image
        // gets an area-matched box up to twice as tall, and a pill or caption
        // placed at OBJECT_SIZE/2 would sit inside it. Scaled by the same factor
        // the glyph is, or a big cluster's pill sits on its face instead of its
        // corner.
        final double[] box = glyphBoxPx(cluster.getType(), typeStyles);
        final double sizeFactor = cluster.getSizeFactor();
        final double halfWidth = box[0] * sizeFactor / 2.0;
        final double halfHeight = box[1] * sizeFactor / 2.0;

        appendCountPill(parent, cluster.size(),
                TypeStyle.colourForType(cluster.getType(), typeStyles),
                cluster.getMapX(), cluster.getMapY(),
                halfWidth, -halfHeight, scale);

        // The pill is drawn unconditionally — it is small, sits on the glyph's own
        // corner, and is the count that justifies the glyph. Only the caption
        // competes for space.
        collectCaption(cluster.getKey(),
                FloorMapClusterLabel.captionFor(cluster, entityNameResolver),
                cluster.getType(),
                cluster.getMapX(), cluster.getMapY(), halfHeight,
                clusterCaptionPriority(cluster));
    }

    /// A cluster's caption priority: above every lone entity, and — among clusters —
    /// bigger first, because a bigger cluster's caption speaks for more entities. A
    /// cluster drawn around the tracked entity outranks all of them.
    private static int clusterCaptionPriority(final FloorMapCluster cluster) {
        if (cluster.hasFocusedMember()) {
            return CAPTION_PRIORITY_FOCUSED;
        }
        // Bounded so a huge cluster cannot reach into the focused tier.
        return CAPTION_PRIORITY_CLUSTER_BASE - Math.min(cluster.size(), 999);
    }

    /// A geometry helper bound to the last-drawn scale/pan and this view's image
    /// aspect-ratio cache. The projection maths lives in the shared, unit-tested
    /// [FloorMapScreenGeometry]; the view just supplies its current state.
    private FloorMapScreenGeometry geometry() {
        return new FloorMapScreenGeometry(lastScale, lastOffsetX, lastOffsetY,
                IMAGE_DISPLAY_WIDTH, OBJECT_SIZE, this::cachedAspectRatio, lastTypeStyles);
    }

    @Override
    public Set<String> hitTestScreenRect(final double[] rectPx) {
        return geometry().hitTestRect(lastFacts, rectPx);
    }

    @Override
    public double[] getContentMapBounds() {
        return geometry().contentMapBounds(lastFacts);
    }

    @Override
    public double[] getFactMapAnchor(final Fact fact) {
        return fact.mapAnchor(IMAGE_DISPLAY_WIDTH,
                cachedAspectRatio(fact.getImage()));
    }

    @Override
    public double[] getSelectionFrame() {
        return computeSelectionFrame();
    }

    /// Returns the screen-space bounding box `{minX, minY, maxX, maxY}` of
    /// the currently selected facts (union of their on-screen bounds), padded to
    /// a minimum size so the handles stay separable. Returns `null` when
    /// nothing is selected or laid out.
    private double[] computeSelectionFrame() {
        return geometry().selectionFrame(lastFacts, lastSelectedIds, MIN_FRAME_PX);
    }

    /// Draws the selection frame outline, the 4 corner scale handles and the
    /// rotation handle above the top edge, all in screen space at the SVG root.
    /// Each handle carries an id of `FloorMapJsonKeys.HANDLE_PREFIX + role`
    /// so the presenter can route a mousedown on it to a scale/rotate gesture.
    /// Scaling is always aspect-preserving, so only corner handles are offered.
    private void appendSelectionHandles(final HtmlBuilder svg, final boolean enabled,
                                        final boolean areaOffset) {
        final double[] f = computeSelectionFrame();
        if (f == null) {
            return;
        }
        // For a single-area selection, push the frame outward by ~0.5 minor
        // grid units so the scale/rotate handles clear the vertex handles.
        final double off = areaOffset
                ? FloorMapGrid.majorDivisionScreenPx(lastScale, measurementUnits) / 20
                : 0;
        final double minX = f[0] - off;
        final double minY = f[1] - off;
        final double maxX = f[2] + off;
        final double maxY = f[3] + off;
        final double cx = (minX + maxX) / 2;

        // Frame outline (non-interactive).
        svg.elem(SafeHtmlUtil.from("rect"),
                new Attribute("x", String.valueOf(minX)),
                new Attribute("y", String.valueOf(minY)),
                new Attribute("width", String.valueOf(maxX - minX)),
                new Attribute("height", String.valueOf(maxY - minY)),
                new Attribute("fill", "none"),
                new Attribute("stroke", HANDLE_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("stroke-dasharray", "4,3"),
                new Attribute("pointer-events", "none"));

        // Rotation handle above the top edge, with a connector line.
        final double ry = minY - ROTATE_HANDLE_OFFSET_PX;
        svg.elem(SafeHtmlUtil.from("line"),
                new Attribute("x1", String.valueOf(cx)),
                new Attribute("y1", String.valueOf(minY)),
                new Attribute("x2", String.valueOf(cx)),
                new Attribute("y2", String.valueOf(ry)),
                new Attribute("stroke", HANDLE_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("pointer-events", "none"));
        appendRotateHandle(svg, cx, ry, enabled);

        // 4 corner scale handles (aspect-preserving scale about the opposite corner).
        appendScaleHandle(svg, minX, minY, "scale-nw", "nwse-resize", enabled);
        appendScaleHandle(svg, maxX, minY, "scale-ne", "nesw-resize", enabled);
        appendScaleHandle(svg, maxX, maxY, "scale-se", "nwse-resize", enabled);
        appendScaleHandle(svg, minX, maxY, "scale-sw", "nesw-resize", enabled);
    }

    private void appendScaleHandle(final HtmlBuilder svg, final double x, final double y,
                                   final String role, final String cursor, final boolean enabled) {
        svg.elem(
                builder -> {
                    if (!enabled) {
                        builder.elem(HANDLE_DISABLED_TOOLTIP, SafeHtmlUtil.from("title"));
                    }
                },
                SafeHtmlUtil.from("rect"),
                new Attribute("id", FloorMapJsonKeys.HANDLE_PREFIX + role),
                new Attribute("x", String.valueOf(x - HANDLE_SIZE_PX / 2)),
                new Attribute("y", String.valueOf(y - HANDLE_SIZE_PX / 2)),
                new Attribute("width", String.valueOf(HANDLE_SIZE_PX)),
                new Attribute("height", String.valueOf(HANDLE_SIZE_PX)),
                new Attribute("fill", enabled ? HANDLE_FILL : HANDLE_DISABLED_FILL),
                new Attribute("stroke", enabled ? HANDLE_STROKE : HANDLE_DISABLED_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("cursor", enabled ? cursor : "not-allowed"));
    }

    private void appendRotateHandle(final HtmlBuilder svg, final double x, final double y,
                                    final boolean enabled) {
        svg.elem(
                builder -> {
                    if (!enabled) {
                        builder.elem(HANDLE_DISABLED_TOOLTIP, SafeHtmlUtil.from("title"));
                    }
                },
                SafeHtmlUtil.from("circle"),
                new Attribute("id", FloorMapJsonKeys.HANDLE_PREFIX + "rotate"),
                new Attribute("cx", String.valueOf(x)),
                new Attribute("cy", String.valueOf(y)),
                new Attribute("r", String.valueOf(ROTATE_HANDLE_RADIUS_PX)),
                new Attribute("fill", enabled ? HANDLE_FILL : HANDLE_DISABLED_FILL),
                new Attribute("stroke", enabled ? HANDLE_STROKE : HANDLE_DISABLED_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("cursor", enabled ? "grab" : "not-allowed"));
    }

    /// Returns the single selected fact when it is an editable area (exactly one
    /// selection, no image, `>= 3` vertices), or `null` otherwise.
    /// Used to decide whether to draw per-vertex editing handles and to offset
    /// the scale/rotate frame outward.
    private Fact singleSelectedArea() {
        if (lastFacts == null || lastSelectedIds == null || lastSelectedIds.size() != 1) {
            return null;
        }
        for (final Fact f : lastFacts) {
            if (lastSelectedIds.contains(f.getKey())) {
                return !f.hasImage() && f.hasVertices() ? f : null;
            }
        }
        return null;
    }

    /// Projects a fact-local vertex to screen space using the last-drawn scale
    /// and pan (local → map via `worldToMap`, then the Y-up screen flip).
    private double[] vertexToScreen(final Fact area, final double[] vertex) {
        final double[] mapPt = area.getWorldToMap().transformPoint(vertex[0], vertex[1]);
        return new double[]{
                lastOffsetX + lastScale * mapPt[0],
                lastOffsetY - lastScale * mapPt[1]};
    }

    /// Draws the per-vertex editing handles for a single selected area: a square
    /// move handle on each vertex (id `HANDLE_PREFIX + "vertex-" + i`) and
    /// a round "+" insert handle at each edge midpoint (id
    /// `HANDLE_PREFIX + "insert-" + i`), all in screen space at the SVG
    /// root so they paint over the area and win the mousedown. Insert handles on
    /// very short on-screen edges are skipped to avoid crowding.
    private void appendAreaHandles(final HtmlBuilder svg, final Fact area) {
        final double[][] verts = area.getVertices();
        if (verts == null || verts.length < 3) {
            return;
        }
        final double[][] screen = new double[verts.length][];
        for (int i = 0; i < verts.length; i++) {
            screen[i] = vertexToScreen(area, verts[i]);
        }

        // Insert "+" handles at edge midpoints (skip very short edges).
        for (int i = 0; i < screen.length; i++) {
            final double[] a = screen[i];
            final double[] b = screen[(i + 1) % screen.length];
            final double dx = b[0] - a[0];
            final double dy = b[1] - a[1];
            if (Math.sqrt(dx * dx + dy * dy) < MIN_INSERT_EDGE_PX) {
                continue;
            }
            appendInsertHandle(svg, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, i);
        }

        // Vertex move handles.
        for (int i = 0; i < screen.length; i++) {
            appendVertexHandle(svg, screen[i][0], screen[i][1], i);
        }
    }

    private void appendVertexHandle(final HtmlBuilder svg, final double x, final double y,
                                    final int index) {
        svg.elem(SafeHtmlUtil.from("rect"),
                new Attribute("id", FloorMapJsonKeys.HANDLE_PREFIX + "vertex-" + index),
                new Attribute("x", String.valueOf(x - HANDLE_SIZE_PX / 2)),
                new Attribute("y", String.valueOf(y - HANDLE_SIZE_PX / 2)),
                new Attribute("width", String.valueOf(HANDLE_SIZE_PX)),
                new Attribute("height", String.valueOf(HANDLE_SIZE_PX)),
                new Attribute("fill", HANDLE_FILL),
                new Attribute("stroke", HANDLE_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("cursor", "move"));
    }

    private void appendInsertHandle(final HtmlBuilder svg, final double x, final double y,
                                    final int edgeIndex) {
        svg.elem(SafeHtmlUtil.from("circle"),
                new Attribute("id", FloorMapJsonKeys.HANDLE_PREFIX + "insert-" + edgeIndex),
                new Attribute("cx", String.valueOf(x)),
                new Attribute("cy", String.valueOf(y)),
                new Attribute("r", String.valueOf(INSERT_HANDLE_RADIUS_PX)),
                new Attribute("fill", INSERT_HANDLE_FILL),
                new Attribute("stroke", HANDLE_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("cursor", "copy"));
        // "+" glyph, non-interactive so the circle keeps the mousedown.
        final double r = INSERT_HANDLE_RADIUS_PX * 0.6;
        svg.elem(SafeHtmlUtil.from("line"),
                new Attribute("x1", String.valueOf(x - r)),
                new Attribute("y1", String.valueOf(y)),
                new Attribute("x2", String.valueOf(x + r)),
                new Attribute("y2", String.valueOf(y)),
                new Attribute("stroke", HANDLE_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("pointer-events", "none"));
        svg.elem(SafeHtmlUtil.from("line"),
                new Attribute("x1", String.valueOf(x)),
                new Attribute("y1", String.valueOf(y - r)),
                new Attribute("x2", String.valueOf(x)),
                new Attribute("y2", String.valueOf(y + r)),
                new Attribute("stroke", HANDLE_STROKE),
                new Attribute("stroke-width", "1"),
                new Attribute("pointer-events", "none"));
    }

    /// Draws an image fact — an `<image>` at its local size, wrapped in a
    /// `<g>` carrying the fact's world-to-map matrix so it is placed and
    /// scaled in map space. A selection border is added when selected.
    private void appendImageFact(final HtmlBuilder parent,
                                 final Fact fact,
                                 final boolean isSelected,
                                 final String highlightColour) {
        // A highlighted-but-unselected fact borrows the selection border machinery,
        // drawn in the highlight colour instead of the Editor blue.
        appendImageGlyph(parent, fact, fact.getWorldToMap(), false,
                isSelected || highlightColour != null,
                isSelected ? ACCENT_BLUE : highlightColour);
    }

    /// Draws an image glyph — an `<image>` at its local size, wrapped in a
    /// `<g>` carrying the given placement matrix so it is placed and
    /// scaled in map space. Used for image facts (placed by their own
    /// world-to-map) and for event entities with an image-bearing fact twin
    /// (placed by the twin's scale/rotation but the entity's live position).
    ///
    /// @param placement       the full placement matrix to apply
    /// @param centred         when `true`, the image's centre (rather than
    ///         its bottom-left corner) lands on the placement
    ///         translation point — used for event entities so the
    ///         icon sits on the entity position like a shape
    ///         glyph would
    /// @param selectionColour selection border colour when selected (facts use
    ///         the Editor blue, tracked entities the orange
    ///         selection colour)
    private void appendImageGlyph(final HtmlBuilder parent,
                                  final Fact fact,
                                  final FloorMapTransformationMatrix placement,
                                  final boolean centred,
                                  final boolean isSelected,
                                  final String selectionColour) {
        final double[] natural = naturalSize(fact.getImage());
        final double aspectRatio = natural != null
                ? natural[0] / natural[1]
                : 1.0;
        final double imgHeight = IMAGE_DISPLAY_WIDTH / aspectRatio;

        FloorMapTransformationMatrix effective = placement;
        if (centred) {
            // Shift the translation by the placement-transformed local centre
            // so the image's midpoint lands on the translation point.
            final double cx = IMAGE_DISPLAY_WIDTH / 2.0;
            final double cy = imgHeight / 2.0;
            effective = new FloorMapTransformationMatrix(
                    placement.getA(), placement.getB(), placement.getC(), placement.getD(),
                    placement.getE() - (placement.getA() * cx + placement.getC() * cy),
                    placement.getF() - (placement.getB() * cx + placement.getD() * cy));
        }

        parent.elem(imgGroup -> {
            // Drawn via appendScaledImage so an SVG icon that cannot scale itself
            // still fills the box rather than rendering as a speck.
            appendScaledImage(imgGroup, fact.getImage(), fact.getKey(), natural,
                    0, 0, IMAGE_DISPLAY_WIDTH, imgHeight, "none");

            if (isSelected) {
                imgGroup.elem(SafeHtmlUtil.from("rect"),
                    new Attribute("x", "0"),
                    new Attribute("y", "0"),
                    new Attribute("width", String.valueOf(IMAGE_DISPLAY_WIDTH)),
                    new Attribute("height", String.valueOf(imgHeight)),
                    new Attribute("fill", "none"),
                    new Attribute("stroke", selectionColour),
                    new Attribute("stroke-width", "8"),
                    new Attribute("vector-effect", "non-scaling-stroke"),
                    new Attribute("pointer-events", "none"));
            }
        }, SafeHtmlUtil.from("g"),
                // Anchor the raster at its BOTTOM-left in map space: translate up
                // by its own height, then counter-flip (scale 1,-1) to undo the
                // Y-up flip group and keep it upright. So an identity placement
                // places the image in the visible first quadrant (up-and-right of
                // the origin) rather than below it; a real placement matrix
                // (applied outermost) then positions/scales/rotates from there.
                new Attribute("transform", effective.toSvgMatrix()
                        + " translate(0," + imgHeight + ") scale(1,-1)"));
    }

    /// Draws the default graphic for an imageless fact at its map position
    /// (world-to-map applied to the fact's world coordinates), using the image or
    /// the shape and colour configured for its type. The graphic and its label are
    /// drawn at a fixed screen size (independent of zoom) via
    /// [#fixedSizeTransform].
    private void appendDefaultGraphic(final HtmlBuilder parent,
                                      final Fact fact,
                                      final boolean isSelected,
                                      final String highlightColour,
                                      final List<TypeStyle> typeStyles,
                                      final double scale) {
        final double[] pos = fact.getPosition();
        final double worldX = pos != null ? pos[0] : 0;
        final double worldY = pos != null ? pos[1] : 0;
        final FloorMapTransformationMatrix w2m = fact.getWorldToMap();
        final double mapX = w2m.getA() * worldX + w2m.getC() * worldY + w2m.getE();
        final double mapY = w2m.getB() * worldX + w2m.getD() * worldY + w2m.getF();

        appendStyledGlyph(parent, fact.getKey(), fact.getType(), mapX, mapY,
                isSelected, highlightColour, typeStyles, scale);
        collectCaption(fact.getKey(),
                FloorMapEntityList.captionFor(
                        fact.getKey(), fact.getLabelOrNull(), entityNameResolver),
                fact.getType(),
                mapX, mapY, typeStyles, CAPTION_PRIORITY_FACT);
    }

    /// Draws an event entity at its map coordinates, preceded by its movement
    /// trail — a fading path tinted with the entity's type colour. The entity
    /// itself renders as its attached icon (when an image-bearing fact twin is
    /// present, scaled/rotated by that fact's world-to-map but placed at the
    /// live position) or otherwise as the type-styled default graphic, the same
    /// rendering as an imageless fact.
    private void appendEvent(final HtmlBuilder parent,
                             final FloorMapObject obj,
                             final boolean isSelected,
                             final String highlightColour,
                             final List<TypeStyle> typeStyles,
                             final double scale) {
        appendEventTrail(parent, obj, typeStyles);

        final Fact imageFact = obj.getImageFact();
        if (imageFact != null) {
            // The entity has an attached icon: keep the icon's configured
            // scale/rotation (a,b,c,d) but centre it on the entity's live
            // position, so the icon follows the events and animates.
            final FloorMapTransformationMatrix w2m = imageFact.getWorldToMap();
            final FloorMapTransformationMatrix placement = new FloorMapTransformationMatrix(
                    w2m.getA(), w2m.getB(), w2m.getC(), w2m.getD(),
                    obj.getX(), obj.getY());
            appendImageGlyph(parent, imageFact, placement, true,
                    isSelected || highlightColour != null,
                    isSelected ? SELECTION_STROKE : highlightColour);
        } else {
            // The trail above stays in map space so it scales with the map;
            // the glyph itself is fixed screen size.
            appendStyledGlyph(parent, obj.getId(), obj.getType(), obj.getX(), obj.getY(),
                    isSelected, highlightColour, typeStyles, scale);
            collectCaption(obj.getId(),
                    FloorMapEntityList.captionFor(obj.getId(), null, entityNameResolver),
                    obj.getType(),
                    obj.getX(), obj.getY(), typeStyles,
                    isSelected
                            ? CAPTION_PRIORITY_FOCUSED
                            : CAPTION_PRIORITY_EVENT);
        }
    }

    /// Draws an event entity's movement trail — a fading path tinted with its type
    /// colour — in map space, so it scales with the map.
    ///
    /// Separate from [#appendEvent] because the two are wanted apart in one
    /// case: an entity merged into a cluster has its glyph replaced by the cluster's,
    /// but if it is the *tracked* one its trail is still worth drawing. Ten
    /// trails converging on a spot is the mess clustering removes; the one belonging
    /// to the entity the user is following is the reason they are watching.
    ///
    /// Drawn as [#TRAIL_BANDS] sub-paths rather than one, because SVG has no way to vary
    /// stroke opacity along a single path. Previously the whole trail took one opacity - the
    /// maximum over its points - which discarded the animator's alpha ramp entirely and drew the
    /// trail as a uniform block that faded all at once, rather than tapering off behind the
    /// entity.
    private void appendEventTrail(final HtmlBuilder parent,
                                  final FloorMapObject obj,
                                  final List<TypeStyle> typeStyles) {
        final List<double[]> trail = obj.getTrail();
        if (trail == null || trail.size() < 2) {
            return;
        }
        final String stroke = TypeStyle.colourForType(obj.getType(), typeStyles);
        final int lastIndex = trail.size() - 1;
        // One segment per band at least; a short trail simply gets fewer bands.
        final int bands = Math.min(TRAIL_BANDS, lastIndex);

        for (int band = 0; band < bands; band++) {
            final int from = band * lastIndex / bands;
            final int to = (band + 1) * lastIndex / bands;
            // The band takes the opacity of its newest point, so the ramp runs oldest to newest.
            // Alpha already carries the animator's fade factor, so a fading trail dims as a whole
            // while keeping its taper.
            final double opacity = trail.get(to)[2];
            if (opacity <= 0.0) {
                continue;
            }
            final StringBuilder pathD = new StringBuilder();
            for (int i = from; i <= to; i++) {
                final double[] pt = trail.get(i);
                pathD.append(i == from
                                ? "M"
                                : "L")
                        .append(pt[0]).append(",").append(pt[1]);
            }
            parent.elem(SafeHtmlUtil.from("path"),
                new Attribute("d", pathD.toString()),
                new Attribute("fill", "none"),
                new Attribute("stroke", stroke),
                new Attribute("stroke-width", "6"),
                // Butt, not round - see TRAIL_BANDS. Round caps overlap at every band boundary
                // and composite into a bright spot.
                new Attribute("stroke-linecap", "butt"),
                new Attribute("stroke-linejoin", "round"),
                new Attribute("vector-effect", "non-scaling-stroke"),
                new Attribute("opacity", String.valueOf(opacity)),
                new Attribute("pointer-events", "none"));
        }
    }

    /// Draws the type-styled default graphic — the single glyph rendering shared
    /// by imageless facts and event entities — anchored at a map-space point and
    /// drawn at a fixed screen size.
    ///
    /// The graphic comes from the type's [TypeStyle]: its
    /// [image][TypeStyle#getGraphic()] if it has one, otherwise its
    /// [shape][TypeStyle#getShape()] filled with its colour. Either way the
    /// glyph occupies the same `OBJECT_SIZE` box, so switching a layer
    /// between a shape and an image does not change how much room it takes; an
    /// image is letterboxed into that box rather than stretched.
    ///
    /// The graphic element carries `id` so click-detection works. The
    /// glyph's **name is not drawn here**: captions are collected during
    /// the draw and placed together at the end, so that two of them can never be
    /// written on top of each other (see [#collectCaption]).
    private void appendStyledGlyph(final HtmlBuilder parent,
                                   final String id,
                                   final String type,
                                   final double mapX,
                                   final double mapY,
                                   final boolean isSelected,
                                   final String highlightColour,
                                   final List<TypeStyle> typeStyles,
                                   final double scale) {
        appendStyledGlyph(parent, id, type, mapX, mapY, isSelected, highlightColour,
                typeStyles, scale, 1.0);
    }

    /// [As above][#appendStyledGlyph(HtmlBuilder, String, String, double, double, boolean, String, List, double)],
    /// drawn `sizeFactor` times its normal size.
    ///
    /// The factor is applied to the counter-scale of the fixed-size transform
    /// rather than to each shape's own geometry, so every glyph kind — shape, pin,
    /// layer image — grows by construction, and a future one cannot forget to. A
    /// selection or highlight border is unaffected: it is drawn with
    /// `non-scaling-stroke`, so it stays the same weight round a bigger
    /// glyph, which is what keeps a ring reading as a ring rather than as a
    /// band.
    ///
    /// @param sizeFactor the multiplier on the glyph box; `1` for an entity,
    ///         more for a cluster standing for several
    private void appendStyledGlyph(final HtmlBuilder parent,
                                   final String id,
                                   final String type,
                                   final double mapX,
                                   final double mapY,
                                   final boolean isSelected,
                                   final String highlightColour,
                                   final List<TypeStyle> typeStyles,
                                   final double scale,
                                   final double sizeFactor) {
        final String fillColour = TypeStyle.colourForType(type, typeStyles);
        final TypeStyle.Shape shape = shapeForType(type, typeStyles);
        final String graphic = graphicForType(type, typeStyles);
        final FloorMapIcon icon = iconForType(type, typeStyles);
        // Selection (orange) beats any highlight — group colour or containment
        // green: the glyph border shows the most specific state, and the type
        // colour still owns the fill.
        final boolean bordered = isSelected || highlightColour != null;
        final String stroke = isSelected
                ? SELECTION_STROKE
                : highlightColour != null
                        ? highlightColour
                        : "none";
        final String strokeWidth = bordered ? "4" : "0";
        final String vectorEffect = bordered ? "non-scaling-stroke" : "none";
        final String polygon = FloorMapShapes.polygonPoints(shape, OBJECT_SIZE / 2.0);
        final double half = OBJECT_SIZE / 2.0;

        parent.elem(objGroup -> {
            if (graphic != null) {
                // The layer's own image, drawn in a box of the same AREA as a shape
                // glyph's box so the two read at the same size (see
                // graphicBoxSize). The image identifies the entity's TYPE, not the
                // entity, so its name is still drawn — underneath, clear of it.
                final double[] box = graphicBoxSize(graphic);
                final double gw = box[0];
                final double gh = box[1];
                // The box already matches the image's own aspect ratio, so the
                // fallback "meet" fits exactly rather than letterboxing; it only
                // applies while the natural size is still being probed.
                appendScaledImage(objGroup, graphic, id, naturalSize(graphic),
                        -gw / 2.0, -gh / 2.0, gw, gh, "xMidYMid meet");
                if (bordered) {
                    objGroup.elem(SafeHtmlUtil.from("rect"),
                        new Attribute("x", String.valueOf(-gw / 2.0)),
                        new Attribute("y", String.valueOf(-gh / 2.0)),
                        new Attribute("width", String.valueOf(gw)),
                        new Attribute("height", String.valueOf(gh)),
                        new Attribute("fill", "none"),
                        // Same weight as a selected shape's border, so selection
                        // reads identically whichever graphic a layer uses.
                        new Attribute("stroke", stroke),
                        new Attribute("stroke-width", strokeWidth),
                        new Attribute("vector-effect", vectorEffect),
                        new Attribute("pointer-events", "none"));
                }
            } else if (icon != null) {
                // A built-in icon, drawn as a map marker: the type's colour fills
                // a white-outlined teardrop and the icon is knocked out of it in
                // white. Both are authored on the same grid, so one transform
                // places the pair into the glyph box.
                appendIconMarker(objGroup, icon, id, fillColour,
                        bordered ? stroke : null);
            } else if (shape == TypeStyle.Shape.PIN) {
                objGroup.elem(SafeHtmlUtil.from("path"),
                    new Attribute("d", FloorMapShapes.pinPath(half)),
                    new Attribute("fill", fillColour),
                    new Attribute("stroke", stroke),
                    new Attribute("stroke-width", strokeWidth),
                    new Attribute("vector-effect", vectorEffect),
                    new Attribute("id", id));
                // The pin's hole, matching the Layers panel swatch.
                objGroup.elem(SafeHtmlUtil.from("circle"),
                    new Attribute("cx", "0"),
                    new Attribute("cy", String.valueOf(half * FloorMapShapes.PIN_HOLE_CENTRE_Y_RATIO)),
                    new Attribute("r", String.valueOf(half * FloorMapShapes.PIN_HOLE_RADIUS_RATIO)),
                    new Attribute("fill", "#ffffff"),
                    new Attribute("pointer-events", "none"));
            } else if (shape == TypeStyle.Shape.CIRCLE) {
                objGroup.elem(SafeHtmlUtil.from("circle"),
                    new Attribute("cx", "0"),
                    new Attribute("cy", "0"),
                    new Attribute("r", String.valueOf(OBJECT_SIZE / 2)),
                    new Attribute("fill", fillColour),
                    new Attribute("stroke", stroke),
                    new Attribute("stroke-width", strokeWidth),
                    new Attribute("vector-effect", vectorEffect),
                    new Attribute("id", id));
            } else if (polygon != null) {
                objGroup.elem(SafeHtmlUtil.from("polygon"),
                    new Attribute("points", polygon),
                    new Attribute("fill", fillColour),
                    new Attribute("stroke", stroke),
                    new Attribute("stroke-width", strokeWidth),
                    new Attribute("vector-effect", vectorEffect),
                    new Attribute("id", id));
            } else if (shape == TypeStyle.Shape.SQUARE) {
                // Barely-rounded, so a configured SQUARE is distinguishable from
                // the heavily-rounded fallback glyph below.
                objGroup.elem(SafeHtmlUtil.from("rect"),
                    new Attribute("x", String.valueOf(-half)),
                    new Attribute("y", String.valueOf(-half)),
                    new Attribute("width", String.valueOf(OBJECT_SIZE)),
                    new Attribute("height", String.valueOf(OBJECT_SIZE)),
                    new Attribute("fill", fillColour),
                    new Attribute("rx", "2"),
                    new Attribute("ry", "2"),
                    new Attribute("stroke", stroke),
                    new Attribute("stroke-width", strokeWidth),
                    new Attribute("vector-effect", vectorEffect),
                    new Attribute("id", id));
            } else {
                objGroup.elem(SafeHtmlUtil.from("rect"),
                    new Attribute("x", String.valueOf(-OBJECT_SIZE / 2)),
                    new Attribute("y", String.valueOf(-OBJECT_SIZE / 2)),
                    new Attribute("width", String.valueOf(OBJECT_SIZE)),
                    new Attribute("height", String.valueOf(OBJECT_SIZE)),
                    new Attribute("fill", fillColour),
                    new Attribute("rx", "6"),
                    new Attribute("ry", "6"),
                    new Attribute("stroke", stroke),
                    new Attribute("stroke-width", strokeWidth),
                    new Attribute("vector-effect", vectorEffect),
                    new Attribute("id", id));
            }

        },
                SafeHtmlUtil.from("g"),
                // Counter-flip + counter-scale so the graphic + label stay
                // upright and a fixed screen size inside the Y-up flip / zoom group.
                // Dividing the zoom by the size factor leaves the counter-scale
                // that much larger, which is what grows the whole glyph.
                new Attribute("transform", fixedSizeTransform(mapX, mapY, scale / sizeFactor)),
                new Attribute("id", FloorMapJsonKeys.SVG_GROUP_PREFIX + id));
    }

    /// Queues a glyph's caption for placement at the end of the frame.
    ///
    /// Nothing is drawn here: the caption's position depends on where every other
    /// caption ends up, so the decision is deferred to
    /// [#appendPlacedCaptions].
    ///
    /// @param key        the entity id or cluster key — identifies the caption and
    ///         breaks ties between equal priorities, so it must be stable
    ///         between frames
    /// @param text       the caption, or `null`/blank to queue nothing
    /// @param type       the entity type, for sizing the glyph the caption clears
    /// @param mapX       the glyph's anchor in map space
    /// @param mapY       the glyph's anchor in map space
    /// @param typeStyles the layer styles, for the same sizing
    /// @param priority   lower is placed first; see the `CAPTION_PRIORITY_*`
    ///         constants
    private void collectCaption(final String key,
                                final String text,
                                final String type,
                                final double mapX,
                                final double mapY,
                                final List<TypeStyle> typeStyles,
                                final int priority) {
        collectCaption(key, text, type, mapX, mapY,
                glyphBoxPx(type, typeStyles)[1] / 2.0, priority);
    }

    /// [As above][#collectCaption(String, String, String, double, double, List, int)], for a glyph
    /// that is not drawn at its type's normal size — a cluster badge grown by its member count.
    /// Passing the type's box for one of those would tuck the caption under the glyph it is naming.
    ///
    /// @param halfHeightPx half the height of the glyph the caption must clear
    private void collectCaption(final String key,
                                final String text,
                                final String type,
                                final double mapX,
                                final double mapY,
                                final double halfHeightPx,
                                final int priority) {
        if (key != null && text != null && !text.isEmpty()) {
            pendingCaptions.add(new PendingCaption(key, text, type, mapX, mapY,
                    halfHeightPx, priority));
        }
    }

    /// Resolves the frame's queued captions and draws the ones that fit.
    ///
    /// Runs last, for two reasons: a caption must not be painted over by a glyph
    /// drawn after it, and which captions fit can only be decided once they are all
    /// known. Crowding drops the least important names — never the most important —
    /// and zooming in brings them back as the glyphs separate.
    ///
    /// A dimmed layer's captions are dimmed with it and yield space to undimmed
    /// ones — dimming means "push this into the background", which a crisp name
    /// hanging off a ghosted glyph would contradict. The count pills stay crisp:
    /// they are drawn with their glyph, and a number nobody can read is no use.
    ///
    /// @param parent      the flip group, so captions share the glyphs' coordinate space
    /// @param scale       the current zoom
    /// @param offsetX     the current pan, for projecting anchors to screen space
    /// @param offsetY     the current pan
    /// @param dimmedTypes the layers the user has pushed into the background
    private void appendPlacedCaptions(final HtmlBuilder parent,
                                      final double scale,
                                      final double offsetX,
                                      final double offsetY,
                                      final Set<String> dimmedTypes) {
        if (pendingCaptions.isEmpty()) {
            return;
        }
        final List<FloorMapLabelPlacement.Label> candidates =
                new ArrayList<>(pendingCaptions.size());
        for (final PendingCaption caption : pendingCaptions) {
            // Map anchor to screen through the shared projection, so this cannot drift
            // from the one the geometry class uses for bounds and hit-testing.
            final double[] screen = FloorMapScreenGeometry.mapToScreen(
                    caption.mapX, caption.mapY, scale, offsetX, offsetY);
            final double screenX = screen[0];
            final double screenY = screen[1];
            candidates.add(new FloorMapLabelPlacement.Label(
                    caption.key,
                    screenX,
                    screenY + caption.halfHeightPx + GLYPH_CAPTION_GAP_PX,
                    caption.text.length() * CAPTION_CHAR_WIDTH_PX,
                    CAPTION_HEIGHT_PX,
                    isDimmed(caption, dimmedTypes)
                            ? caption.priority + CAPTION_PRIORITY_DIMMED_PENALTY
                            : caption.priority));
        }

        final Element panel = focusPanel.getElement();
        final Set<String> visible = FloorMapLabelPlacement.place(
                candidates, panel.getOffsetWidth(), panel.getOffsetHeight());

        for (final PendingCaption caption : pendingCaptions) {
            if (visible.contains(caption.key)) {
                final HtmlBuilder.Attribute[] attributes = isDimmed(caption, dimmedTypes)
                        ? new Attribute[]{
                                new Attribute("transform",
                                        fixedSizeTransform(caption.mapX, caption.mapY, scale)),
                                new Attribute("opacity", DIMMED_LAYER_OPACITY)}
                        : new Attribute[]{
                                new Attribute("transform",
                                        fixedSizeTransform(caption.mapX, caption.mapY, scale))};
                parent.elem(captionGroup -> appendGlyphCaption(
                                captionGroup, caption.text, caption.halfHeightPx),
                        SafeHtmlUtil.from("g"),
                        attributes);
            }
        }
    }

    private static boolean isDimmed(final PendingCaption caption,
                                    final Set<String> dimmedTypes) {
        return dimmedTypes != null && dimmedTypes.contains(caption.type);
    }

    /// One queued caption, before it is known whether it fits.
    private static final class PendingCaption {

        private final String key;
        private final String text;
        private final String type;
        private final double mapX;
        private final double mapY;
        private final double halfHeightPx;
        private final int priority;

        private PendingCaption(final String key,
                               final String text,
                               final String type,
                               final double mapX,
                               final double mapY,
                               final double halfHeightPx,
                               final int priority) {
            this.key = key;
            this.text = text;
            this.type = type;
            this.mapX = mapX;
            this.mapY = mapY;
            this.halfHeightPx = halfHeightPx;
            this.priority = priority;
        }
    }

    /// Draws a caption centred under a fixed-size glyph, in the theme-aware style
    /// shared by entity names and cluster captions.
    ///
    /// Must be called from inside a [#fixedSizeTransform] group, whose
    /// local space is screen pixels with Y increasing downward — so the positive
    /// offset here puts the text below the glyph.
    ///
    /// Styled by CSS class rather than attributes because this text sits
    /// *outside* the glyph, over whatever floor plan is beneath it: it needs
    /// the theme's text colour and a halo, and neither can be hard-coded here.
    ///
    /// @param parent       the glyph's own group builder
    /// @param text         the caption; escaped by the text-content overload, since
    ///         it carries entity and type names from the data
    /// @param halfHeightPx half the height of the glyph being named — from
    ///         [#glyphBoxPx], **not** assumed to be
    ///         `OBJECT_SIZE / 2`: a layer drawing an image gets an
    ///         area-matched box that can be twice as tall, and a caption
    ///         placed for a square glyph would land on top of it
    private void appendGlyphCaption(final HtmlBuilder parent,
                                    final String text,
                                    final double halfHeightPx) {
        parent.elem(text,
                SafeHtmlUtil.from("text"),
                new Attribute("x", "0"),
                new Attribute("y", String.valueOf(halfHeightPx + GLYPH_CAPTION_GAP_PX)),
                new Attribute("dy", "0.71em"),
                new Attribute("text-anchor", "middle"),
                new Attribute("class", GLYPH_CAPTION_CLASS),
                new Attribute("pointer-events", "none"));
    }

    /// Builds the SVG transform for a **fixed-screen-size** glyph
    /// anchored at a map-space point.
    ///
    /// The `translate` places the glyph's origin in map space, so it
    /// tracks pan/zoom position exactly like everything else. The
    /// `scale(1/zoom, -1/zoom)` then cancels two things at once: the
    /// pan/zoom group's `scale(zoom)` (so the glyph's own geometry renders
    /// at a constant screen size regardless of zoom) and the Y-up
    /// `scale(1,-1)` flip (so the glyph and its label stay upright — the
    /// two negatives cancel).
    ///
    /// @param mapX  map-space X of the anchor point
    /// @param mapY  map-space Y of the anchor point
    /// @param scale the current zoom factor (never zero — clamped by the presenter)
    /// @return the `transform` attribute value
    private static String fixedSizeTransform(final double mapX,
                                             final double mapY,
                                             final double scale) {
        final double inv = 1.0 / scale;
        return "translate(" + mapX + "," + mapY + ") scale(" + inv + "," + (-inv) + ")";
    }

    /// Returns the configured default-graphic shape for the given type. Falls
    /// back to a circle for unconfigured `person` types (continuity with
    /// the traditional person marker), or `null` otherwise (the view then
    /// falls back to the default rounded rectangle).
    private static TypeStyle.Shape shapeForType(final String type, final List<TypeStyle> typeStyles) {
        if (type != null && typeStyles != null) {
            for (final TypeStyle style : typeStyles) {
                if (style != null && type.equals(style.getType()) && style.getShape() != null) {
                    return style.getShape();
                }
            }
        }
        if (FloorMapJsonKeys.PERSON.equalsIgnoreCase(type)) {
            return TypeStyle.Shape.CIRCLE;
        }
        return null;
    }

    /// Returns the asset-store URL of the image configured as the given type's
    /// layer graphic, or `null` to draw a shape instead.
    ///
    /// This only applies to facts and entities with *no image of their own*
    /// — [#renderFact] sends image-bearing facts down the
    /// [#appendImageFact] path first, so a fact's own `img` always wins
    /// over its layer's graphic.
    private static String graphicForType(final String type, final List<TypeStyle> typeStyles) {
        if (type != null && typeStyles != null) {
            for (final TypeStyle style : typeStyles) {
                if (style != null && type.equals(style.getType()) && style.hasGraphic()) {
                    return style.getGraphic();
                }
            }
        }
        return null;
    }

    /// Draws an icon as a map marker: a coloured teardrop with a white outline,
    /// the icon knocked out of it in white.
    ///
    /// Three layers, back to front. A selection or highlight ring first, so it
    /// shows outside the white outline rather than replacing it. Then the marker
    /// itself, which carries the `id` — it is the solid shape the pointer
    /// will actually be over, and the only part of the glyph that should answer a
    /// hit-test. Then the icon, explicitly `pointer-events="none"` so it
    /// cannot become the event target and hide the marker's id from the hit-test
    /// beneath it.
    ///
    /// @param stroke the ring colour, or `null` for no ring
    private void appendIconMarker(final HtmlBuilder parent,
                                  final FloorMapIcon icon,
                                  final String id,
                                  final String fillColour,
                                  final String stroke) {
        parent.elem(markerGroup -> {
            if (stroke != null) {
                markerGroup.elem(SafeHtmlUtil.from("path"),
                    new Attribute("d", FloorMapMarker.getPath()),
                    new Attribute("fill", "none"),
                    new Attribute("stroke", stroke),
                    new Attribute("stroke-width", String.valueOf(FloorMapMarker.SELECTION_WIDTH)),
                    new Attribute("stroke-linejoin", "round"),
                    new Attribute("pointer-events", "none"));
            }
            markerGroup.elem(SafeHtmlUtil.from("path"),
                new Attribute("d", FloorMapMarker.getPath()),
                new Attribute("fill", fillColour),
                new Attribute("stroke", MARKER_OUTLINE),
                new Attribute("stroke-width", String.valueOf(FloorMapMarker.OUTLINE_WIDTH)),
                new Attribute("stroke-linejoin", "round"),
                new Attribute("id", id));
            markerGroup.elem(iconGroup -> iconGroup.elem(SafeHtmlUtil.from("path"),
                    new Attribute("d", icon.getPath()),
                    new Attribute("fill", MARKER_OUTLINE),
                    new Attribute("pointer-events", "none")),
                SafeHtmlUtil.from("g"),
                new Attribute("transform", FloorMapMarker.iconTransform()));
        }, SafeHtmlUtil.from("g"),
                new Attribute("transform", FloorMapIcon.transform(30.0)));
    }

    /// Returns the built-in icon configured as the given type's layer graphic, or
    /// `null` to draw a shape instead.
    ///
    /// An icon is filled with the layer's colour, exactly as a shape is, so
    /// unlike an uploaded image it does not retire the colour control.
    private static FloorMapIcon iconForType(final String type, final List<TypeStyle> typeStyles) {
        if (type != null && typeStyles != null) {
            for (final TypeStyle style : typeStyles) {
                if (style != null && type.equals(style.getType())) {
                    return style.iconOrNull();
                }
            }
        }
        return null;
    }

    /// Appends an `<image>` filling the target box, compensating for SVGs that
    /// cannot scale themselves.
    ///
    /// An SVG `<image>` maps the referenced file into the given
    /// width/height *via that file's `viewBox`*. An SVG declaring only
    /// `width`/`height` and no `viewBox` has no user coordinate
    /// system to map, so it ignores the box and renders at its own small intrinsic
    /// size — for a fact icon, inside a 1000-unit box, that is a barely visible
    /// speck. Setting the box *to* the intrinsic size and scaling the wrapping
    /// group instead sidesteps the problem: the file renders 1:1, and the group
    /// transform does the enlarging.
    ///
    /// This is a no-op in effect for raster images and for well-formed SVGs — the
    /// same pixels either way — so it is applied uniformly rather than trying to
    /// sniff which files need it. When the natural size is unknown (still loading, or
    /// the browser will not report one) it falls back to sizing the box directly.
    ///
    /// @param naturalSize the image's intrinsic `{width, height}`, or
    ///         `null` if unknown
    /// @param x           left edge of the target box, in the parent's coordinates
    /// @param y           top edge of the target box
    /// @param targetWidth  width to draw at; must share the natural aspect ratio with
    ///         `targetHeight` so one uniform scale suffices
    /// @param targetHeight height to draw at
    /// @param preserveAspectRatio value for the attribute of the same name, used only
    ///         on the unknown-size fallback path
    private void appendScaledImage(final HtmlBuilder parent,
                                   final String url,
                                   final String id,
                                   final double[] naturalSize,
                                   final double x,
                                   final double y,
                                   final double targetWidth,
                                   final double targetHeight,
                                   final String preserveAspectRatio) {
        if (naturalSize == null) {
            // Escape the URL: it is document-controlled data going into innerHTML,
            // so an unescaped value allows attribute injection.
            parent.elem(SafeHtmlUtil.from("image"),
                    new Attribute("href", url),
                    new Attribute("x", String.valueOf(x)),
                    new Attribute("y", String.valueOf(y)),
                    new Attribute("width", String.valueOf(targetWidth)),
                    new Attribute("height", String.valueOf(targetHeight)),
                    new Attribute("preserveAspectRatio", preserveAspectRatio),
                    new Attribute("id", id));
            return;
        }

        final double scale = targetWidth / naturalSize[0];
        parent.elem(scaled -> scaled.elem(SafeHtmlUtil.from("image"),
                        new Attribute("href", url),
                        new Attribute("x", "0"),
                        new Attribute("y", "0"),
                        new Attribute("width", String.valueOf(naturalSize[0])),
                        new Attribute("height", String.valueOf(naturalSize[1])),
                        // The box is the intrinsic size, so there is nothing to fit.
                        new Attribute("preserveAspectRatio", "none"),
                        new Attribute("id", id)),
                SafeHtmlUtil.from("g"),
                new Attribute("transform",
                        "translate(" + x + "," + y + ") scale(" + scale + ")"));
    }

    /// The on-screen `{width, height}` a type's point glyph occupies: the
    /// area-matched box of its layer graphic, or the plain square when it draws a
    /// shape.
    ///
    /// Exists so anything positioned *relative to* a glyph — its caption,
    /// a cluster's count pill — is placed against the box the glyph really has.
    /// Assuming a square silently misplaces both on any layer configured with an
    /// image, which is not visible until such a layer exists.
    private double[] glyphBoxPx(final String type, final List<TypeStyle> typeStyles) {
        final String graphic = graphicForType(type, typeStyles);
        return graphic != null
                ? graphicBoxSize(graphic)
                : new double[]{OBJECT_SIZE, OBJECT_SIZE};
    }

    /// The on-screen `{width, height}` to draw a layer graphic at, sized so it
    /// carries the **same visual weight as a shape glyph**.
    ///
    /// Matching the shape's bounding box is not enough: a shape is solid ink out
    /// to its box edge, whereas an image preserving its aspect ratio inside a square
    /// box only reaches the edge on its longer side (a 4:3 icon would fill just
    /// three quarters of the height), and most icons carry transparent margins on
    /// top of that. So the box is sized to match the shape box's *area*
    /// instead — for aspect ratio `r`, that is
    /// `(S·√r, S/√r)`, which has area `S²` for every `r` and
    /// gives a square image exactly the shape's `S × S`.
    ///
    /// The ratio comes from [#naturalSize]; until it resolves the graphic is
    /// drawn square, and [#onImageSizeResolved] triggers a redraw at the true
    /// size.
    ///
    /// @param url the graphic's asset URL
    /// @return a two-element `{width, height}` in SVG user-units
    private double[] graphicBoxSize(final String url) {
        final double[] natural = naturalSize(url);
        // Delegate the arithmetic so the drawn box and the hit-test/selection box
        // can never drift apart; this side only supplies the measured ratio.
        return FloorMapScreenGeometry.graphicBox(OBJECT_SIZE, natural == null
                ? null
                : natural[0] / natural[1]);
    }

    /// Registers a callback that is invoked whenever the canvas needs to be redrawn
    /// (e.g. after an asynchronous image aspect-ratio resolution completes).
    ///
    /// @param redrawListener the callback, or `null` to clear
    @Override
    public void setRedrawListener(final Runnable redrawListener) {
        this.redrawListener = redrawListener;
    }

    /// {@inheritDoc}
    @Override
    public void setResizeListener(final Runnable resizeListener) {
        this.resizeListener = resizeListener;
    }

    /// Callback invoked (via JSNI) when the browser has finished loading an image and
    /// its natural dimensions are known.
    ///
    /// @param url           the image URL that was loaded
    /// @param naturalWidth  the image's natural width in pixels, or `0` if the
    ///         browser would not report one
    /// @param naturalHeight the image's natural height in pixels, or `0`
    @SuppressWarnings("unused")
    void onImageSizeResolved(final String url,
                             final double naturalWidth,
                             final double naturalHeight) {
        imageNaturalSizeCache.put(url, new double[]{naturalWidth, naturalHeight});
        loadingImages.remove(url);
        if (redrawListener != null) {
            redrawListener.run();
        }
    }

    /// The image's natural `{width, height}`, starting a load if it is not yet
    /// known. Returns `null` while unknown, or if the browser reported no
    /// usable size — callers then fall back to letting the `<image>` box do
    /// the scaling.
    ///
    /// Safe to call every frame: [#loadImageAspectRatio] de-duplicates
    /// in-flight loads, and a resolved entry (even an unusable one) is never probed
    /// again.
    private double[] naturalSize(final String url) {
        final double[] size = imageNaturalSizeCache.get(url);
        if (size == null) {
            loadImageAspectRatio(url);
            return null;
        }
        return size[0] > 0 && size[1] > 0
                ? size
                : null;
    }

    /// The image's natural width/height ratio if already known, otherwise
    /// `null`. Unlike [#naturalSize] this never starts a load, so it is
    /// safe to call from hit-testing and other non-drawing paths.
    private Double cachedAspectRatio(final String url) {
        final double[] size = imageNaturalSizeCache.get(url);
        return size != null && size[0] > 0 && size[1] > 0
                ? size[0] / size[1]
                : null;
    }

    /// Starts an asynchronous image load to determine the aspect ratio of the given URL.
    /// No-ops if a load for this URL is already in flight.
    ///
    /// @param url the image URL to load
    private void loadImageAspectRatio(final String url) {
        if (loadingImages.contains(url)) {
            return;
        }
        loadingImages.add(url);
        startImageLoad(url);
    }

    /// JSNI method that creates a browser `Image` element and starts loading the
    /// given URL, reporting the natural size to [#onImageSizeResolved]. A failed
    /// load, or one the browser will not size, reports `0 x 0`, which callers
    /// treat as "unknown".
    private native void startImageLoad(final String url) /*-{
        var self = this;
        var img = new Image();
        img.onload = function() {
            var width = img.naturalWidth || img.width || 0;
            var height = img.naturalHeight || img.height || 0;
            self.@stroom.floormap.client.view.FloorMapCanvasViewImpl::onImageSizeResolved(Ljava/lang/String;DD)
                    (url, width, height);
        };
        img.onerror = function() {
            self.@stroom.floormap.client.view.FloorMapCanvasViewImpl::onImageSizeResolved(Ljava/lang/String;DD)
                    (url, 0, 0);
        };
        img.src = url;
    }-*/;

    /// GWT UiBinder interface for [FloorMapCanvasViewImpl].
    public interface Binder extends UiBinder<Widget, FloorMapCanvasViewImpl> {

    }
}
