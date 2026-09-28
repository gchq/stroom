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

package stroom.floormap.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.HasToolbar;
import stroom.floormap.client.editor.FloorMapDocSession;
import stroom.floormap.client.event.FloorMapDataEvent;
import stroom.floormap.client.event.MapClusterSelectedEvent;
import stroom.floormap.client.event.MapObjectSelectedEvent;
import stroom.floormap.client.event.TimeChangeEvent;
import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapEntityList;
import stroom.floormap.client.model.FloorMapEntityList.EntityEntry;
import stroom.floormap.client.model.FloorMapFactHistory;
import stroom.floormap.client.model.FloorMapLocationResolver;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.client.overlay.FloorMapAreaMembership;
import stroom.floormap.client.overlay.FloorMapGroupOverlay;
import stroom.floormap.client.overlay.FloorMapGroupSnapshot;
import stroom.floormap.client.playback.FloorMapEventsQuery;
import stroom.floormap.client.playback.FloorMapEventsQueryOrder;
import stroom.floormap.client.playback.FloorMapHistogramBuckets;
import stroom.floormap.client.playback.FloorMapStageReporter;
import stroom.floormap.client.presenter.FloorMapMapPresenter.FloorMapMapView;
import stroom.floormap.client.value.FloorMapFactTableParser;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.ValueFormat;
import stroom.query.api.Column;
import stroom.query.api.Param;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryModel;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.svg.shared.SvgImage;
import stroom.util.client.Console;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.InlineSvgToggleButton;
import stroom.widget.histogram.client.HistogramDataModel;
import stroom.widget.histogram.client.HistogramQueryHelper;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Presenter for the Map (visualisation) tab of a {@link FloorMapDoc}.
 *
 * <p>This presenter coordinates the floor-map canvas and the timeline scrubber. Facts are
 * loaded by running a StroomQL query via {@link QueryModel}. Results are parsed by
 * {@link #parseFacts(List, List)}.</p>
 *
 * <p>The Map tab is <strong>read-only</strong>. Unlike
 * {@link FloorMapEditorPresenter}, it sets no edit mode and installs none of the canvas's
 * mutation handlers - no drag, geometry, area or scale handler - so nothing here can alter a
 * fact. Inspection is served by the hover tooltip, the cluster member panel and the Tracking
 * grid. This paragraph used to claim the presenter coordinated the "object properties
 * editor": it held an instance of that dialog, configured it on every document read, and
 * never showed it.</p>
 *
 * <h3>Layout slots</h3>
 * <ul>
 *     <li>{@link #MAP} – the {@link FloorMapCanvasPresenter} (canvas / visualisation)</li>
 *     <li>{@link #DOCK} – the {@link FloorMapDockPresenter} (right-hand dock; hosts the
 *     {@link FloorMapTrackingPresenter} tracking panel as its first tab, then
 *     {@link FloorMapLayersPresenter} and {@link FloorMapGroupsPresenter})</li>
 *     <li>{@link #TIMELINE} – the {@link FloorMapTimelinePresenter} (timeline scrubber)</li>
 * </ul>
 *
 * <p>Three {@link QueryModel}-based searches are maintained: the facts query playback,
 * the events query playback (the entity overlay the canvas animates), and the histogram
 * query that populates the timeline density bars.</p>
 */
public class FloorMapMapPresenter
        extends DocPresenter<FloorMapMapView, FloorMapDoc>
        implements HasToolbar {

    public static final Object MAP = new Object();
    public static final Object DOCK = new Object();
    public static final Object TIMELINE = new Object();
    private static final int HISTOGRAM_BINS = 100;

    private final FloorMapCanvasPresenter floorMapCanvasPresenter;
    private final FloorMapTimelinePresenter floorMapTimelinePresenter;
    private final FloorMapTrackingPresenter floorMapTrackingPresenter;
    private final FloorMapLayersPresenter floorMapLayersPresenter;
    private final FloorMapGroupsPresenter floorMapGroupsPresenter;

    /**
     * The Map tab's staged document-level edits — just the Groups panel's edits.
     * The tab does not otherwise write the document, so a group edit is staged
     * here, merged in by {@link #onWrite}, and dropped once a post-save re-read
     * shows it landed. The Editor tab has its own instance for schema/type-styles;
     * the two stage disjoint fields and so cannot clobber each other.
     */
    private final FloorMapDocSession docSession = new FloorMapDocSession();

    /** Roster of every entity seen on the map, feeding the tracking panel. */
    private final FloorMapEntityList entityList = new FloorMapEntityList();

    /**
     * The latest facts and event entities, kept so area containment can be
     * recomputed when either side refreshes (the two queries refresh
     * independently). See {@link #updateAreaMembership()}.
     */
    private List<Fact> lastFacts;
    private List<FloorMapObject> lastEventObjects;

    /**
     * The event entities exactly as the events query produced them, before
     * {@link FloorMapLocationResolver} placed the ones that reference a fact
     * rather than carrying coordinates.
     *
     * <p>Kept because that placement depends on the facts, which refresh
     * independently: an object moved on the Editor tab changes only the facts,
     * so without re-placing the <em>same</em> events against them the entities
     * would keep visiting where the object used to be until the next events
     * refresh — and on a paused timeline there is no next refresh.</p>
     */
    private List<FloorMapObject> lastRawEventObjects;

    /**
     * Whether the next events read to land follows a discrete time jump.
     *
     * <p>Set by the timeline's discontinuity callback and consumed when a read applies, at which
     * point the canvas is told to discard animation state so entities teleport across the jump
     * rather than sliding. Consumed on <b>apply</b> rather than on request because the facts query
     * returns first and re-pushes the previous entity list, which would otherwise eat the teleport
     * before the read arrived.</p>
     *
     * <p>It also licenses abandoning a read already in flight: that read was issued for a position
     * the user has since left, so replacing it is correct rather than merely tolerable.</p>
     */
    private boolean pendingDiscontinuity;

    /** Whether an events read failure has already been reported for this document. */
    private boolean eventsErrorReported;

    /** Whether the events read hitting its row cap has already been reported for this document. */
    private boolean eventsTruncationReported;

    /**
     * Report-once flags for the facts history read.
     *
     * <p>The read repeats every 60 s, so a persistent fault — a store that cannot be read, or one
     * holding far more history than the cap — would otherwise write to the console once a minute
     * for as long as the document stays open. Same rule as {@link #eventsErrorReported}, and
     * reset per document read.</p>
     */
    private boolean factsHistoryErrorReported;

    /** So a contradictory or malformed location is named once per document, not once per tick. */
    private boolean eventDataFaultReported;

    private boolean factsHistoryTruncationReported;

    /**
     * Names whichever stage of the events pipeline came up empty, once it has stayed empty.
     *
     * <p>Four stages can each produce nothing and all four look the same on screen. Three of them
     * used to say nothing at all, including the two most likely first-run failures, so an empty map
     * gave no clue whether the query, the column mapping, the facts or the location values were at
     * fault. See {@link FloorMapStageReporter} for why it waits rather than reporting on sight.</p>
     */
    private final FloorMapStageReporter stageReporter = new FloorMapStageReporter();

    /** Rows the last applied events read returned, for {@link #stageReporter}. */
    private int lastEventRowCount;

    /** Query text the {@link #arrivalOrderTrusted} answer belongs to. */
    private String orderCheckedQuery;

    /** Whether rows from {@link #orderCheckedQuery} may be reduced by arrival order. */
    private boolean arrivalOrderTrusted;

    /** Whether the note about an untrustworthy row order has been emitted for this document. */
    private boolean orderNoteReported;

    /**
     * The latest area containment, kept so the Groups panel's counts can be
     * recomputed on a group edit — which can happen with the timeline paused, when
     * no query refresh is coming.
     */
    private FloorMapAreaMembership lastAreaMembership = FloorMapAreaMembership.EMPTY;

    /**
     * Reads every version of every fact, on a slow cadence rather than per tick.
     *
     * <p>Replaces a per-tick snapshot query at {@code [T, T]} — about three full fact reads a
     * second. Facts change roughly weekly, so that poll was not serving playback; it was serving
     * external write detection at three times a second. {@link FloorMapFactHistory} holds the
     * history and derives each tick's snapshot locally.</p>
     */
    private final FloorMapFullReadQueryHelper factsHistoryQueryHelper;

    /** The held fact history, and the snapshot arithmetic over it. */
    private final FloorMapFactHistory factHistory = new FloorMapFactHistory();

    /**
     * How often to ask whether a facts re-read is due, while the Map is on screen.
     *
     * <p>Deliberately much shorter than {@link FloorMapFactHistory#REFETCH_INTERVAL_MS}, and only a
     * heartbeat: the decision stays in {@code needsRead}, which is unit-tested, and this only
     * decides how often it is consulted. A heartbeat equal to the interval would be wrong twice
     * over — {@code needsRead} tests {@code >} the interval, so a tick landing exactly on the
     * boundary declines and the effective period doubles to two minutes.</p>
     */
    private static final int FACTS_CADENCE_HEARTBEAT_MS = 10_000;

    /**
     * Drives the facts re-read while the Map is visible.
     *
     * <p><b>A timer is genuinely needed here, unlike for the events baseline.</b> The cadence was
     * first written to be checked from {@link #onTimeChange}, following the events state's
     * timer-free design — but {@code TimeChangeEvent} only fires while the timeline is
     * <em>playing</em>, so on a paused map nothing ever asked and the 60-second re-read never
     * happened at all. The interval was unreachable, not merely long.</p>
     *
     * <p>The events state can live without one because a paused timeline is a frozen view at T and
     * events arriving now almost all carry effective times after T, so they are not being hidden.
     * That argument does not transfer to facts: moving a desk changes the floor plan at
     * <em>every</em> time, the frozen one included.</p>
     *
     * <p>Runs only while the Map is the visible content, so a backgrounded document still does
     * nothing at all — which is the property the timer-free design was protecting.</p>
     */
    private final Timer factsCadenceTimer = new Timer() {
        @Override
        public void run() {
            readFactsHistoryIfDue();
            // Re-evaluate the empty-stage report as well. The reporter waits for a stage to persist
            // for a second before naming it, and it can only notice that second has passed when
            // something asks it again - so on a paused timeline, which issues no events reads at
            // all, this heartbeat is the only thing that ever asks. Without it the canvas line
            // appears and the console explanation never does.
            reassessEmptyStage();
        }
    };

    /**
     * The snapshot rows last handed to {@link #parseFacts}, so an unchanged one is skipped.
     *
     * <p>The guard is on the <b>rows</b>, not on the parsed facts: {@link Row} implements
     * {@code equals} and {@link Fact} does not, so comparing rows needs nothing new and skips the
     * parse as well as the pushes. At the stated change rate this is a hit on essentially every
     * tick, which is what makes deriving the snapshot per tick cheaper than querying for it.</p>
     */
    private List<Row> lastFactSnapshotRows;

    /**
     * Runs the document's events query at the selected time, producing the entity overlay that
     * {@link FloorMapCanvasPresenter} animates.
     *
     * <p>The Map tab owns this query rather than {@link FloorMapQueryPresenter} (the Events Query
     * tab): that presenter is created lazily, the first time its tab is opened, so a Map tab
     * depending on it showed no live entities at all — and therefore no movement animation — until
     * the user happened to visit another tab.</p>
     *
     * <p>A full-read helper rather than a bare {@link QueryModel} because the read replaces the
     * drawn set wholesale, so it must apply only on the searching-to-idle edge and only when the
     * search reported no errors — a partial or failed result would otherwise blank the map. The
     * helper also owns the row cap and the in-flight flag.</p>
     */
    private final FloorMapFullReadQueryHelper eventsQueryHelper;

    /**
     * Builds the cluster member list on demand. Left as a provider rather than
     * resolved up front: a map whose entities never crowd never opens it.
     */
    private final Provider<FloorMapClusterPresenter> floorMapClusterPresenter;

    private final HistogramQueryHelper histogramQueryHelper;
    /**
     * The unbounded read behind "Show All". Separate from {@link #histogramQueryHelper} because
     * {@code QueryModel} state is single-valued, so two concurrent reads need two models — and
     * because the two want opposite ranges: the bars are bounded below at the visible range, the
     * extent must not be bounded at all.
     */
    private final HistogramQueryHelper extentQueryHelper;
    private final HistogramDataModel histogramDataModel;

    /**
     * The bucket width the last histogram query grouped by.
     *
     * <p>Set when the query is issued and read when its result lands, so a result is always placed
     * at the width it was counted at. A range change between the two would otherwise put counts in
     * the wrong bars; the next query corrects it.</p>
     */
    private long histogramBucketWidthMs = FloorMapHistogramBuckets.widthFor(0L);

    /** Set once the wrong events store type has been reported, so it is said once per read. */
    private boolean wrongEventsStoreTypeReported;

    /**
     * Toolbar toggle controlling the canvas grid overlay. Shown next to the
     * document save buttons via {@link HasToolbar} whenever the Map tab is
     * active. Off by default — the Map tab is view-focused, so the grid is
     * opt-in (unlike the Editor tab, where it defaults on).
     */
    private final InlineSvgToggleButton showGridButton;

    /**
     * Toolbar toggle that shows/hides the right-hand dock. On by default on the
     * Map tab, which always has the Tracking panel to show.
     */
    private final InlineSvgToggleButton dockToggleButton;

    /**
     * Toolbar toggle controlling whether entities too close together on screen are
     * merged into one summary glyph. <strong>On</strong> by default: the state it
     * prevents — a stack of glyphs where only the top one is visible and the rest
     * are unreachable — is worse than the state it creates.
     *
     * <p>It has to be switchable, though. Zooming in separates entities that are
     * merely close, but nothing separates entities at the same position, so this
     * button is the only way to look underneath a cluster on the canvas.</p>
     */
    private final InlineSvgToggleButton clusterToggleButton;

    private long selectedTime;

    /**
     * True once the timeline range has been initialised by the first document
     * read. Saving the document triggers a re-read of every tab, and the
     * timeline must not be re-initialised then — it would silently discard
     * the user's chosen range (e.g. after "Show All").
     */
    private boolean timelineInitialised;

    /**
     * UUID of the document this Map is showing, used to ignore
     * {@link FloorMapDataEvent}s fired by other open FloorMap documents on the
     * shared event bus (which would otherwise render another doc's entities here).
     */
    private String docUuid;

    /**
     * True once the document tab has been closed. Closing does not unbind this
     * presenter, so its event-bus handlers stay registered for the lifetime of
     * the session; this stops a closed tab from taking any further part in the
     * document's data flow. See {@link #onClose()}.
     */
    private boolean closed;

    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    /**
     * Returns the document's value schema.
     *
     * @return the active {@link FloorMapFieldMapping} list; may be {@code null}
     *         for legacy documents
     */
    private List<FloorMapFieldMapping> valueSchema() {
        return getEntity().getValueSchema();
    }

    /**
     * Resolves the value path for a given {@link FloorMapFieldMapping.Role} by looking
     * it up in the current {@link #valueSchema()}.
     *
     * @param role the schema role to look up
     * @return the value path, or {@code null} if the role is not present in the schema. Format
     *         neutral: the same path drives the XML accessor when the document's ValueFormat is
     *         XML.
     */
    private String pathForRole(final Role role) {
        return FloorMapFieldMapping.findPath(valueSchema(), role);
    }

    @Inject
    public FloorMapMapPresenter(final EventBus eventBus,
                                final FloorMapMapView view,
                                final RestFactory restFactory,
                                final DateTimeSettingsFactory dateTimeSettingsFactory,
                                final ResultStoreModel resultStoreModel,
                                final Provider<FloorMapCanvasPresenter> floorMapCanvasPresenterProvider,
                                final Provider<FloorMapTimelinePresenter> floorMapTimelinePresenterProvider,
                                final Provider<FloorMapTrackingPresenter> floorMapEntityListPresenterProvider,
                                final Provider<FloorMapLayersPresenter> floorMapLayersPresenterProvider,
                                final Provider<FloorMapGroupsPresenter> floorMapGroupsPresenterProvider,
                                final Provider<FloorMapDockPresenter> floorMapDockPresenterProvider,
                                final Provider<FloorMapClusterPresenter> floorMapClusterPresenter) {
        super(eventBus, view);

        this.floorMapClusterPresenter = floorMapClusterPresenter;
        this.floorMapCanvasPresenter = floorMapCanvasPresenterProvider.get();
        this.floorMapTimelinePresenter = floorMapTimelinePresenterProvider.get();
        this.floorMapTrackingPresenter = floorMapEntityListPresenterProvider.get();
        this.floorMapLayersPresenter = floorMapLayersPresenterProvider.get();
        this.floorMapGroupsPresenter = floorMapGroupsPresenterProvider.get();
        final FloorMapDockPresenter floorMapDockPresenter = floorMapDockPresenterProvider.get();

        // Default initial time
        this.selectedTime = System.currentTimeMillis();

        // The Tracking, Layers and Groups panels live as tabs of the right-hand dock.
        floorMapDockPresenter.addTab("Tracking", floorMapTrackingPresenter);
        floorMapDockPresenter.addTab("Layers", floorMapLayersPresenter);
        floorMapDockPresenter.addTab("Groups", floorMapGroupsPresenter);
        // Layer visibility is a transient view control on the Map tab; push
        // changes to the canvas as hidden / dimmed type sets.
        floorMapLayersPresenter.setChangeHandler(() ->
                floorMapCanvasPresenter.setLayerVisibility(
                        floorMapLayersPresenter.getHiddenTypes(),
                        floorMapLayersPresenter.getDimmedTypes()));

        // Group membership IS persisted (it is document configuration), so an edit
        // is staged and marks the document dirty. Which groups are *highlighted* is
        // not — that is transient view state pushed straight to the canvas.
        floorMapGroupsPresenter.setGroupsEditHandler(this::onGroupsEdited);
        floorMapGroupsPresenter.setHighlightChangeHandler(this::pushGroupOverlay);

        // Let the Tracking panel put its selected row into a group without the
        // user having to switch tabs and find the entity again in the picker.
        floorMapTrackingPresenter.setAddToGroupSupport(
                floorMapGroupsPresenter::getGroups,
                floorMapGroupsPresenter::addMember,
                floorMapGroupsPresenter::createGroupWith);

        setInSlot(MAP, floorMapCanvasPresenter);
        setInSlot(DOCK, floorMapDockPresenter);
        setInSlot(TIMELINE, floorMapTimelinePresenter);

        // Grid on/off toggle, surfaced next to the save buttons (HasToolbar).
        // SvgImage has no dedicated grid glyph; TABLE renders as a grid of cells.
        showGridButton = new InlineSvgToggleButton();
        showGridButton.setSvg(SvgImage.TABLE);
        showGridButton.setTitle("Toggle Grid");
        showGridButton.setState(false);

        // Show/hide the right-hand dock
        dockToggleButton = new InlineSvgToggleButton();
        dockToggleButton.setSvg(SvgImage.SHOW_MENU);
        dockToggleButton.setTitle("Toggle Controls");
        dockToggleButton.setState(true);

        // Merge crowded entities into summary glyphs. On by default; the canvas
        // is told explicitly below rather than relying on its field default, so
        // the button and the canvas cannot start out disagreeing.
        clusterToggleButton = new InlineSvgToggleButton();
        clusterToggleButton.setSvg(SvgImage.FIELDS_GROUP);
        clusterToggleButton.setTitle("Toggle Clustering of Nearby Entities");
        clusterToggleButton.setState(true);
        floorMapCanvasPresenter.setClusterNearbyEntities(true);
        // Cluster tooltips name their members through the same resolver the
        // Tracking and Groups panels use, so one entity reads the same way
        // wherever it is named.
        floorMapCanvasPresenter.setEntityNameResolver(this::entityDisplayName);

        // Histogram data model — buckets timestamps and notifies the timeline.
        this.histogramDataModel = new HistogramDataModel(HISTOGRAM_BINS);
        this.histogramDataModel.setDataHandler(
                floorMapTimelinePresenter::setHistogramData);

        // Histogram query helper. One, for events: the density bars count event activity, and
        // the events store is the only store this tab reads them from.
        this.histogramQueryHelper = new HistogramQueryHelper(
                eventBus, restFactory, dateTimeSettingsFactory, resultStoreModel,
                // The width the in-flight query grouped by, not a constant: it is chosen from the
                // visible range, so a result has to be placed at the width it was counted at.
                result -> histogramDataModel.processBuckets(result, histogramBucketWidthMs));

        this.extentQueryHelper = new HistogramQueryHelper(
                eventBus, restFactory, dateTimeSettingsFactory, resultStoreModel,
                this::applyDataExtent);

        this.eventsQueryHelper = new FloorMapFullReadQueryHelper(
                eventBus, restFactory, dateTimeSettingsFactory, resultStoreModel,
                FloorMapFullReadQueryHelper.MAX_ROWS,
                "eventsTable",
                "Events Query Playback",
                this::applyEventsOutcome);

        this.factsHistoryQueryHelper = new FloorMapFullReadQueryHelper(
                eventBus, restFactory, dateTimeSettingsFactory, resultStoreModel,
                FloorMapFactHistory.MAX_ROWS,
                "factsHistoryTable",
                "Facts History",
                this::applyFactsHistoryOutcome);
    }

    @Override
    protected void onBind() {
        super.onBind();

        // ---- Toolbar ---------------------------------------------------------
        //noinspection unused e
        registerHandler(showGridButton.addClickHandler(e ->
                floorMapCanvasPresenter.setShowGrid(showGridButton.getState())));
        //noinspection unused e
        registerHandler(dockToggleButton.addClickHandler(e ->
                getView().setDockVisible(dockToggleButton.getState())));
        //noinspection unused e
        registerHandler(clusterToggleButton.addClickHandler(e ->
                floorMapCanvasPresenter.setClusterNearbyEntities(
                        clusterToggleButton.getState())));

        // Only react to this tab's own timeline — the Editor tab has its own
        // timeline firing the same event type, and the tabs must not time-sync.
        registerHandler(getEventBus().addHandler(TimeChangeEvent.getType(), e -> {
            if (e.getSource() == floorMapTimelinePresenter) {
                onTimeChange(e.getTime());
            }
        }));
        registerHandler(getEventBus().addHandler(FloorMapDataEvent.getType(), e -> {
            // Fired by the Events Query tab, while it is open, as the user edits/runs the query.
            // Ignore events from other open FloorMap documents (shared bus), and anything at all
            // once this tab has been closed.
            //
            // Also ignore this tab's own fire. That is the outbound notification the Editor tab
            // needs for its layer discovery, not an inbound update: the state has already been
            // applied and pushed by then, and re-entering here would overwrite lastRawEventObjects
            // with a payload the state did not sanction. The Events Query tab's pushes still reach
            // the canvas exactly as before — it is a second producer on this channel, publishing
            // over its own time range without any per-entity reduction, so its rows are a preview
            // to draw and must never be merged into the accumulated state.
            if (closed
                || e.getSource() == this
                || !java.util.Objects.equals(docUuid, e.getDocUuid())) {
                return;
            }
            // Held raw so the placement can be redone against fresher facts —
            // see reanchorEventEntities().
            lastRawEventObjects = e.getObjects();
            pushEventEntities(placeEventEntities());
            // Entities have moved, so which areas they are in may have changed.
            updateAreaMembership();
        }));

        // Re-run the histogram whenever the user changes the visible date range via the settings popup.
        floorMapTimelinePresenter.setTimeRangeChangeHandler(() ->
                runHistogramQuery(floorMapTimelinePresenter.getStartTime(),
                        floorMapTimelinePresenter.getEndTime()));

        // Canvas events are fired on the shared event bus by every FloorMap
        // canvas instance (this tab, the Editor tab, and any other open
        // FloorMap document), so each handler must ignore events from
        // canvases other than its own — without the source guard a selection
        // on the Editor tab's canvas would change this tab's tracking state.
        //
        // Clicking an entity on this tab's canvas selects it in the tracking
        // panel and starts (or resumes) following it. The roster holds both
        // event entities and static facts; ids not (yet) in it are ignored.
        // The canvas never fires this for backgrounds or areas — their
        // clickable surface can cover most of the map, so a press over them
        // must stay a pan — but they can still be tracked from the panel.
        registerHandler(getEventBus().addHandler(MapObjectSelectedEvent.getType(), e -> {
            if (e.getSource() == floorMapCanvasPresenter
                    && e.getObjectId() != null
                    && entityList.contains(e.getObjectId())) {
                floorMapTrackingPresenter.setSelected(e.getObjectId());
                floorMapCanvasPresenter.setTrackedObjectId(e.getObjectId());
            }
        }));

        // Clicking a cluster lists its members, which is what makes entities
        // merged into one glyph reachable at all — until this, the nine users
        // under the tenth could be counted and named but never got at. Same
        // source guard as above: the bus is shared by every open canvas.
        registerHandler(getEventBus().addHandler(MapClusterSelectedEvent.getType(), e -> {
            if (e.getSource() == floorMapCanvasPresenter && e.getCluster() != null) {
                floorMapClusterPresenter.get().show(
                        e.getCluster(),
                        this::entityDisplayName,
                        lastAreaMembership,
                        this::entityType,
                        this::entityGroupNames,
                        // Picking a member does exactly what picking its row in the
                        // Tracking panel does, so the two cannot diverge.
                        memberId -> {
                            floorMapTrackingPresenter.setSelected(memberId);
                            floorMapCanvasPresenter.setTrackedObjectId(memberId);
                        });
            }
        }));


        // Drag-editing is performed on the Editor tab; the Map tab is view-focused,
        // so no drag handler is installed here.

        // Selecting an entity in the tracking panel highlights it on the
        // canvas, centres the camera on it, and follows it as it moves;
        // selecting nothing stops tracking. Re-clicking the selected row
        // re-invokes this consumer, which re-centres and resumes following
        // after a manual pan paused it.
        this.floorMapTrackingPresenter.setSelectionConsumer(entry ->
                floorMapCanvasPresenter.setTrackedObjectId(entry != null
                        ? entry.getId()
                        : null));

        // Keep the canvas informed of play/pause transitions so it can switch
        // between animate-on-move and teleport behaviour.
        floorMapTimelinePresenter.setPlayStateChangeHandler(
                floorMapCanvasPresenter::setPlaying);

        // Discard stale animation state whenever the timeline jumps non-continuously
        // (scrub, step-back/forward, loop-around, stop-at-end).
        floorMapTimelinePresenter.setClearAnimationStateHandler(
                floorMapCanvasPresenter::clearAnimationState);

        // The discrete jumps. Every read is now a snapshot at the selected time, so a jump needs
        // no different query — but it does need the read in flight abandoned rather than waited
        // for, and the canvas told to teleport rather than animate across it. Deliberately not
        // wired to the handler above, which fires per frame while looping.
        floorMapTimelinePresenter.setDiscontinuityHandler(() -> {
            pendingDiscontinuity = true;
            // A jump is new evidence, and the emptiness of the instant we left says nothing about
            // the one we arrived at. Without this, scrubbing through a sparse stretch would
            // accumulate empty observations from unrelated instants and name a configuration
            // problem where there is simply no data at those times.
            //
            // Deliberately here and not in readEvents(), where it used to be. Exactly one
            // observation happens per events read, so resetting every tick restarted the
            // reporter's clock before it could ever run out - nothing was ever reported.
            // Successive playback ticks are not unrelated: a second of them with no rows is a real
            // second of emptiness, which is precisely what is worth saying.
            stageReporter.reset();
            // The line itself is deliberately left standing until the read that follows replaces
            // it. Clearing here would make it blink off and back on for every scrub within one
            // empty stretch, and a flashing explanation is worse than one that is a round trip
            // stale - especially as the handler above has just asked for that read.
        });

        // Name the Tracking grid as the map's text alternative. The canvas is
        // exposed as a single summarised image; this is what tells a screen-reader
        // user that the row-by-row detail behind that summary exists, and where.
        floorMapCanvasPresenter.setTextAlternativeId(
                floorMapTrackingPresenter.getGridElementId());
    }

    /**
     * Returns the Map tab's toolbar widgets. {@link FloorMapPresenter}'s
     * base class ({@code DocTabPresenter}) appends these after the document
     * save buttons whenever this tab is selected.
     */
    @Override
    public List<Widget> getToolbars() {
        final ButtonPanel toolbar = new ButtonPanel();
        toolbar.addButton(showGridButton);
        toolbar.addButton(clusterToggleButton);
        toolbar.addButton(dockToggleButton);
        return Collections.singletonList(toolbar);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Initialises and resets all three query mechanisms — the facts and events
     * {@link QueryModel}s and the histogram query helper — then starts the timeline and
     * triggers an initial time-change to load facts. The timeline range is only initialised on
     * the first read; save-triggered re-reads preserve it. This tab no longer holds an
     * object-edit presenter; that form belongs to the Editor tab.</p>
     */
    @Override
    protected void onRead(final DocRef docRef, final FloorMapDoc document, final boolean readOnly) {
        this.docUuid = docRef != null ? docRef.getUuid() : null;
        // Initialise and reset every query model BEFORE starting any searches, so that the histogram query
        // started inside updateTimelineRange() is not immediately cancelled by the reset() calls below.
        factsHistoryQueryHelper.init(docRef);
        factsHistoryQueryHelper.reset();
        eventsQueryHelper.init(docRef);
        eventsQueryHelper.reset();
        histogramQueryHelper.init(docRef);
        histogramQueryHelper.reset();
        extentQueryHelper.init(docRef);
        extentQueryHelper.reset();

        // A (re-)opened document starts with a fresh entity roster and no
        // inherited area containment.
        entityList.clear();
        floorMapTrackingPresenter.setData(Collections.emptyList());
        lastFacts = null;
        lastEventObjects = null;
        lastRawEventObjects = null;
        // Same reasoning for facts: the document may now point at a different facts store, so the
        // held history is not evidence about this read. clear() also makes a read due immediately,
        // which the onTimeChange at the end of this method then issues - and lastFactSnapshotRows
        // has to go with it, or the unchanged guard could match the new document's first snapshot
        // against the old document's and skip drawing it.
        factHistory.clear();
        lastFactSnapshotRows = null;
        factsHistoryErrorReported = false;
        factsHistoryTruncationReported = false;
        eventDataFaultReported = false;
        wrongEventsStoreTypeReported = false;
        stageReporter.reset();
        floorMapCanvasPresenter.setEmptyStatus(null, false);
        eventsErrorReported = false;
        eventsTruncationReported = false;
        orderNoteReported = false;
        orderCheckedQuery = null;
        lastAreaMembership = FloorMapAreaMembership.EMPTY;
        floorMapTrackingPresenter.clearAreaState();
        floorMapCanvasPresenter.setAreaMembership(FloorMapAreaMembership.EMPTY);

        // What one map unit means in the real world — labels the grid and the
        // scale bar. Null on an uncalibrated map, which measures in the default
        // scale of one centimetre per map unit.
        floorMapCanvasPresenter.setMeasurementUnits(document.getMeasurementUnits());

        // Drop any staged group edit this read has persisted, then show the groups
        // as the session sees them (the staged list if the save has not happened
        // yet, else the document's). setGroups also clears the transient highlight
        // state, so a (re-)opened document starts with nothing highlighted.
        docSession.reconcileAfterRead(document);
        floorMapGroupsPresenter.setGroups(docSession.groups(document.getGroups()));

        // Populate the Layers panel from the document's type styles and sync the
        // canvas with the current (transient) layer visibility.
        floorMapLayersPresenter.setLayers(document.getTypeStyles());
        floorMapCanvasPresenter.setLayerVisibility(
                floorMapLayersPresenter.getHiddenTypes(),
                floorMapLayersPresenter.getDimmedTypes());

        // Start timeline (and histogram query) only after models are ready.
        // Initialise the range on the first read only; on a save-triggered
        // re-read, keep the user's range and current position but still
        // re-run the histogram query in case the settings change altered
        // the underlying queries or stores.
        // Ask what the data's own extent is. Once per read, not per range change: it is unbounded,
        // so the answer does not depend on what is currently shown — which is exactly why "Show All"
        // cannot be served from the bars.
        runExtentQuery();

        if (!timelineInitialised) {
            timelineInitialised = true;
            updateTimelineRange();
        } else {
            runHistogramQuery(floorMapTimelinePresenter.getStartTime(),
                    floorMapTimelinePresenter.getEndTime());
        }
        onTimeChange(selectedTime);
        // The Map may already be the tab on screen, in which case no visibility change is coming
        // to start this. Restarting an already-running timer is harmless.
        factsCadenceTimer.scheduleRepeating(FACTS_CADENCE_HEARTBEAT_MS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Merges any staged Groups-panel edit into the document. Everything else the
     * Map tab can change (object moves, additions) is persisted directly to the
     * temporal store via REST calls rather than through the document save
     * lifecycle, so with no group edit staged the document is returned
     * unchanged.</p>
     */
    @Override
    protected FloorMapDoc onWrite(final FloorMapDoc document) {
        return docSession.applyToWrite(document);
    }

    /**
     * Always returns {@code false} — the Map tab has no <em>associated</em> dirty
     * state (that flag is about pending temporal-store changes, which the Editor
     * tab owns). A staged group edit is ordinary document dirt: it reaches the save
     * button through {@link #onChange()} and {@link #onWrite}.
     */
    @Override
    public boolean hasAssociatedDirty() {
        return false;
    }

    /**
     * Stages an edited group list for save and lights the save button.
     *
     * <p>{@code onChange()} re-runs {@link #onWrite} and diffs the result against
     * the loaded document, so staging first is what makes the change visible to
     * it.</p>
     */
    private void onGroupsEdited(final List<FloorMapGroup> groups) {
        docSession.stageGroups(groups);
        onChange();
        // Recompute the live counts now rather than waiting for the next query
        // refresh: with the timeline paused there may not BE a next refresh, so a
        // member just added would sit at "0 of 1" until the user scrubbed.
        refreshGroupSnapshot();
    }

    /**
     * Pushes the current group highlight to the canvas — the transient
     * "which groups are shown" state, never persisted.
     *
     * <p>Deliberately does not touch the tracked entity: groups highlight, they do
     * not move the camera.</p>
     */
    private void pushGroupOverlay() {
        floorMapCanvasPresenter.setGroupOverlay(FloorMapGroupOverlay.of(
                floorMapGroupsPresenter.getGroups(),
                floorMapGroupsPresenter.getShownGroupIds()));
    }

    /**
     * Returns the facts query to execute, built from the document's value schema on
     * every call.
     *
     * <p>There is no custom-query override and no store-derived template: the query
     * text is always generated by
     * {@link FloorMapQueryBuilder#buildFactsQuery(List, ValueFormat)} from the schema
     * and the document's value format. </p>
     *
     * @return the StroomQL query text, or {@code null} if the value schema is empty
     */
    private String getFactsQueryToUse() {
        final List<FloorMapFieldMapping> schema = valueSchema();
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        return FloorMapQueryBuilder.buildFactsQuery(schema, getEntity().getValueFormat());
    }

    /**
     * Responds to a timeline time-change event. Runs both StroomQL queries at
     * the selected time via their {@link QueryModel}s: the facts query (static
     * floor-plan content) and the events query (the entity overlay that the
     * canvas animates between positions).
     *
     * @param time the new selected time in milliseconds since epoch
     */
    private void onTimeChange(final long time) {
        this.selectedTime = time;
        // Keep the canvas's accessible summary and its live region on the same
        // clock as the timeline's own labels — hence the timeline's formatter
        // rather than a second one here. The canvas suppresses the announcement
        // itself while playing.
        floorMapCanvasPresenter.setCurrentTimeText(
                floorMapTimelinePresenter.formatTime(time));
        // Facts: no query. The history is held and the snapshot at T derived from it, so playback,
        // scrub and step are zero-query for facts. The read below fires only when the cadence is
        // due or the Map has just become visible.
        readFactsHistoryIfDue();
        applyFactSnapshot(time);
        readEvents(time);
    }

    /**
     * Reads every entity's position as at {@code t}.
     *
     * <p>One query per tick, reduced server-side to the latest row per key at or before {@code t}.
     * That is a capability the temporal stores gained after this feature was written; before it,
     * the only options were an instant (which matched nothing on Plan B) or a window (which lost
     * anyone who had not moved lately), and the client kept accumulated positions to work around
     * the absence. It does not any more — see the class javadoc.</p>
     *
     * <p><b>Expiry is the store's, not this request's.</b> The request says which instant to read
     * at; the store says how long an entity lasts, and the server derives the floor from its own
     * document. That is what makes two maps reading one store agree, and it means expiry is decided
     * against real timestamps rather than the date text a result happens to carry. Pinned by
     * {@code TestTemporalStateDbSnapshot} and {@code TestFloorMapEventStoreSearchProvider}.</p>
     *
     * <p>A read already in flight is normally left alone: it answers the same question about a
     * position at most a tick old, and abandoning it per tick would destroy searches faster than
     * they complete. A jump is the exception, because the read in flight is for a position the user
     * has already left.</p>
     *
     * @param t the timeline position being read at
     */
    private void readEvents(final long t) {
        final String query = getEventsQueryToUse();
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        if (!eventsStoreIsReadable()) {
            return;
        }
        if (!eventsQueryHelper.isRunning() || pendingDiscontinuity) {
            // A snapshot at t, asked for rather than inferred. Expiry is not sent: it belongs to the
            // event store document, so the server derives the floor from there - which is what makes
            // two floor maps reading one store agree, and what lets the store check expiry against
            // its own retention.
            eventsQueryHelper.runSnapshot(query, queryParams(), t);
        }
    }

    /**
     * Whether the events store is one this tab can read, reporting once if it is not.
     *
     * <p><b>A floor map written before the FloorMap Event Store existed points at a plain
     * {@code PlanBDoc}</b>, and nothing migrates it. Left alone that fails in the worst way
     * available: the snapshot parameters this tab sends are meaningless to Plan B's own search
     * provider, which ignores them and answers with every row in the store instead of one per
     * entity. The map would draw, slowly and wrongly, with no error anywhere — so it is refused
     * here, where the reason can be said.</p>
     *
     * <p><b>Said on the canvas as well as in the console.</b> The density bars and the floor plan
     * still draw — a range read over the old store is perfectly valid, and only the per-entity
     * snapshot is refused — so what the user sees is a map with bars and no entities. That is
     * exactly the situation the status line exists for; leaving it to the console would mean the
     * only explanation is somewhere they are not looking.</p>
     *
     * <p>Reported once per read rather than per tick. The flag is cleared in {@link #onRead}, so
     * saving a corrected store reference says it again if it is still wrong.</p>
     */
    private boolean eventsStoreIsReadable() {
        final DocRef storeRef = getEntity() == null
                ? null
                : getEntity().getEventsStoreRef();
        if (storeRef == null || FloorMapEventStoreDoc.TYPE.equals(storeRef.getType())) {
            return true;
        }
        if (!wrongEventsStoreTypeReported) {
            wrongEventsStoreTypeReported = true;
            Console.error("Floor map: the events store '" + storeRef.getName() + "' is a "
                          + storeRef.getType() + ", not a " + FloorMapEventStoreDoc.TYPE
                          + ". This map predates the dedicated store type. Create a "
                          + FloorMapEventStoreDoc.TYPE + ", point the pipeline at it, and select it"
                          + " on the Settings tab. Reported once per read.");
            floorMapCanvasPresenter.setEmptyStatus(
                    "This map's events store is a " + storeRef.getType() + ", not a "
                    + FloorMapEventStoreDoc.TYPE + ". Entities cannot be read until it is changed on"
                    + " the Settings tab.",
                    true);
        }
        return false;
    }

    /**
     * The document's store references as query {@link Param}s.
     *
     * <p>Every read this tab makes passes these, including the histogram — a query naming its
     * store as {@code from param('EventStore')} does not resolve without them. They used to be
     * substituted into the query text here as well; the {@code from} clause now resolves
     * {@code param()} server-side, so the text is sent exactly as the user wrote it.</p>
     *
     * @return the params, or {@code null} when the document declares none
     */
    private List<Param> queryParams() {
        final Map<String, String> vars =
                FloorMapQueryPresenter.buildQueryVariables(getEntity());
        if (vars.isEmpty()) {
            return null;
        }
        final List<Param> params = new ArrayList<>(vars.size());
        for (final Map.Entry<String, String> entry : vars.entrySet()) {
            params.add(new Param(entry.getKey(), entry.getValue()));
        }
        return params;
    }

    /**
     * Returns the document's events query — the entity locations over time that
     * become the animated overlay.
     *
     * @return the StroomQL query text, or {@code null} if none is configured
     */
    private String getEventsQueryToUse() {
        return getEntity() != null ? getEntity().getEventsQuery() : null;
    }

    /**
     * Applies — or refuses — a finished events read.
     *
     * <p>Three outcomes, deliberately different:</p>
     * <ul>
     *   <li><b>Failed</b>: keep what is known and report once. A failed baseline is
     *       indistinguishable from an empty horizon by its rows, because
     *       {@code StateSearchProvider} records the error and then still signals completion — so
     *       applying it would silently empty the map of a store that is merely unreachable.</li>
     *   <li><b>Truncated</b>: upsert without pruning. The rows are per-key complete in key order
     *       except possibly at the boundary, so they are real positions worth having; but a
     *       truncated read is no evidence that an absent entity has gone.</li>
     *   <li><b>Complete</b>: replace wholesale, pruning included — <em>including</em> a legitimate
     *       zero-row result. An empty horizon means nobody has been seen within it, and the
     *       positioned count must be allowed to say so.</li>
     * </ul>
     *
     * @param outcome the finished baseline
     */
    private void applyEventsOutcome(final FloorMapFullReadQueryHelper.Outcome outcome) {
        if (closed || getEntity() == null) {
            return;
        }
        if (outcome.failed()) {
            // Keep whatever is on screen: a failed read says nothing about where anyone is, and
            // blanking the map on a transient error would be worse than showing a stale position.
            if (!eventsErrorReported) {
                eventsErrorReported = true;
                Console.error("Floor map: the events query failed, so entity positions are those"
                              + " from before it ran. Check the events store exists and has been"
                              + " written to. Further failures for this document are not"
                              + " reported.");
            }
            return;
        }

        final TableResult tableResult = outcome.result();
        final List<FloorMapObject> entities = tableResult == null
                ? Collections.emptyList()
                : parseEventRows(tableResult);
        lastEventRowCount = tableResult == null ? 0 : NullSafe.size(tableResult.getRows());
        reportUnparsedEvents(tableResult, entities);

        if (outcome.truncated() && !eventsTruncationReported) {
            eventsTruncationReported = true;
            // The read is one row per entity now, so hitting the cap means the store holds more
            // distinct entities than the cap allows - not more history. Narrowing the time range
            // would not help; only a higher cap would.
            Console.error("Floor map: the events query hit its "
                          + FloorMapFullReadQueryHelper.MAX_ROWS + "-row limit, so some entities"
                          + " are missing from the map. The read returns one row per entity, so"
                          + " this store holds more entities than the cap allows."
                          + " Reported once per document.");
        }

        // Re-arm the teleport here rather than when the jump was reported. After a scrub the facts
        // query answers first and reanchorEventEntities() re-pushes the previous list, consuming
        // the pending teleport; the baseline would then land on the animate path and entities would
        // slide across the jump. Never for a periodic baseline, or trails would die every minute.
        if (pendingDiscontinuity) {
            pendingDiscontinuity = false;
            floorMapCanvasPresenter.clearAnimationState();
        }
        publishKnownEntities(entities);
    }

    /**
     * Draws, places and announces everything currently known.
     *
     * <p>Called after every applied read, and the assignment to {@code lastRawEventObjects} is
     * load-bearing rather than bookkeeping: {@link #reanchorEventEntities()} re-pushes that field
     * on <em>every</em> facts tick, so leaving it stale would have the facts path repeatedly
     * redrawing an older set of positions.</p>
     *
     * @param entities every entity the read returned; never {@code null}
     */
    private void publishKnownEntities(final List<FloorMapObject> entities) {
        lastRawEventObjects = entities;
        final List<FloorMapObject> placed = placeEventEntities();
        reportEmptyStage(entities.size(), placed.size());
        pushEventEntities(placed);
        // Entities have moved, so which areas they are in may have changed.
        updateAreaMembership();
        // The outbound notification the Editor tab's layer discovery accumulates from. This tab's
        // own handler ignores it — see onBind().
        FloorMapDataEvent.fire(this, docUuid, entities);
    }

    /**
     * Reduces a result to one row per entity and parses those into map objects.
     *
     * <p>Where the query allows it the reduction goes by <b>arrival order</b> — last row wins —
     * rather than by comparing effective times, because that is the stronger guarantee. Plan B
     * iterates one LMDB cursor whose temporal key is {@code <prefix><big-endian time>}, an ungrouped
     * search keys rows by a monotonic id assigned on the producer thread, and neither the write
     * queue nor the result creator reorders, so rows for an entity arrive oldest-first. Comparing
     * times means comparing the time column as <em>rendered</em> to the viewing user's date-time
     * preference, which can order wrongly for a pattern that is not lexicographic.</p>
     *
     * <p>{@link FloorMapEventsQueryOrder} decides which applies. Both of its preconditions are
     * reachable to break — a {@code sort} clause reroutes the fetch to its sorted path, and an
     * entity id not taken from the store {@code Key} scatters one entity across key prefixes — and
     * either sends this to the time comparison instead. Neither rewrites the query: it belongs to
     * the user, its results table is a separate execution that must keep showing what they wrote,
     * and only the map's reduction needs to differ.</p>
     *
     * <p><b>This reduction is now belt-and-braces, and kept deliberately.</b> The read passes an
     * upper time bound, which both stores lift as a snapshot boundary, so a result should already
     * hold one row per entity and there is nothing for {@code latestPerEntity} to reduce. It is
     * retained because it is O(one row per entity) on a list that size, and because drawing two
     * positions for one entity is the exact failure this whole path was built to prevent — cheap
     * insurance against a server-side behaviour that changed recently. Anyone pruning it should
     * take {@link FloorMapEventsQueryOrder} with it, and should be sure no reachable events query
     * can return an unreduced result first.</p>
     */
    private List<FloorMapObject> parseEventRows(final TableResult tableResult) {
        final FloorMapEventColumns eventColumns = getEntity().getEventColumns();
        return FloorMapQueryPresenter.parseRows(
                tableResult.getColumns(),
                FloorMapQueryPresenter.latestPerEntity(
                        tableResult.getColumns(),
                        tableResult.getRows(),
                        eventColumns.getColumn(FloorMapEventRole.ENTITY_ID),
                        arrivalOrderTrusted() ? null : FloorMapEventsQuery.EFFECTIVE_TIME_COLUMN),
                eventColumns,
                // Report-once, because this runs on every events read - three times a second
                // during playback - and parseRows already caps itself at one message per result.
                this::reportEventDataFault);
    }

    /**
     * Reports a contradictory or malformed location, once per document read.
     *
     * <p>{@code parseRows} caps itself at one message per result, which is not enough on its own:
     * the same rows arrive on every tick, so an unfixed fault would still write to the console
     * three times a second for as long as the map was open.</p>
     */
    private void reportEventDataFault(final String message) {
        if (!eventDataFaultReported) {
            eventDataFaultReported = true;
            Console.error(message + " Reported once per document.");
        }
    }

    /**
     * Whether this document's events query lets the reduction trust arrival order.
     *
     * <p>Cached against the text it was decided for, because it is asked once per result — three
     * times a second during playback — and the answer changes only when the document does.</p>
     */
    private boolean arrivalOrderTrusted() {
        final String query = getEventsQueryToUse();
        if (!java.util.Objects.equals(query, orderCheckedQuery)) {
            orderCheckedQuery = query;
            final boolean sorts = FloorMapEventsQueryOrder.hasSortClause(query);
            final boolean keyed = FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                    query, getEntity().getEventColumns().getColumn(FloorMapEventRole.ENTITY_ID));
            arrivalOrderTrusted = !sorts && keyed;
            if (!arrivalOrderTrusted && !orderNoteReported) {
                orderNoteReported = true;
                Console.info("Floor map: playback picks each entity's latest position by comparing"
                             + " its " + FloorMapEventsQuery.EFFECTIVE_TIME_COLUMN + " column"
                             + " rather than by the order rows arrive in, because "
                             + (sorts
                                     ? "the events query sorts."
                                     : "its "
                                       + getEntity().getEventColumns()
                                               .getColumn(FloorMapEventRole.ENTITY_ID)
                                       + " column is not the store Key.")
                             + " The query is unchanged; only the map's reduction differs. This is"
                             + " the weaker of the two, since the time column is compared as"
                             + " rendered. Reported once per document.");
            }
        }
        return arrivalOrderTrusted;
    }

    /**
     * Re-runs the empty-stage assessment against the state already on screen.
     *
     * <p>Costs a placement pass and nothing else — no query. Exists because
     * {@link FloorMapStageReporter} gates on elapsed time and therefore needs asking more than
     * once, while a paused timeline produces exactly one events read. Cheap enough at the
     * heartbeat's cadence, and a no-op before the first read has landed.</p>
     */
    private void reassessEmptyStage() {
        if (closed || getEntity() == null || lastRawEventObjects == null) {
            return;
        }
        reportEmptyStage(lastRawEventObjects.size(), placeEventEntities().size());
    }

    /**
     * Reports whichever stage of the pipeline came up empty, once it has stayed empty.
     *
     * <p>This is the only place that can see all four stages at once, which is why the
     * classification lives here rather than beside any one of them.</p>
     */
    private void reportEmptyStage(final int entities, final int placed) {
        final FloorMapStageReporter.Stage stage = stageReporter.observe(
                lastEventRowCount, entities, NullSafe.size(lastFacts), placed,
                System.currentTimeMillis());

        refreshEmptyStatus(entities, placed);

        if (stage == null) {
            return;
        }
        switch (stage) {
            case NO_EVENT_ROWS -> Console.error("Floor map: the events query returned no rows at"
                                                + " this time. The read is a snapshot at the"
                                                + " selected time, bounded below by the event"
                                                + " store's expiry, so either the store holds"
                                                + " nothing at or before that time or every"
                                                + " entity's last event is older than the expiry"
                                                + " — check it holds data, that the timeline is not"
                                                + " before the data starts, and that the expiry on"
                                                + " the store is long enough.");
            // The detailed column-mismatch message is emitted by reportUnparsedEvents, which has
            // the result's columns to name. Saying it twice would be worse than saying it once.
            case NO_ENTITIES_PARSED -> {
            }
            case NO_FACTS -> Console.error("Floor map: entities were found but there are no facts"
                                           + " to place them on, so nothing is drawn. Check the"
                                           + " facts store holds data and that the value schema"
                                           + " matches it. Entities carrying literal"
                                           + " 'x, y' coordinates do not need facts;"
                                           + " these ones name a fact key.");
            case NO_PLACEMENTS -> {
                // placeEventEntities() already names the mismatching keys on both sides.
            }
            default -> {
            }
        }
    }

    /**
     * Reports an events query that returned rows but no entities.
     *
     * <p>Every reason a row is discarded — an unmapped column, an entity id or
     * location the query did not select, a location value that is neither
     * coordinates nor an object key — presents identically on the canvas: the
     * entities simply stop appearing, and the map looks as though animation has
     * been switched off. Naming the columns and showing a sample value turns
     * that into something inspectable.</p>
     */
    private void reportUnparsedEvents(final TableResult tableResult,
                                      final List<FloorMapObject> entities) {
        if (!entities.isEmpty()
            || tableResult == null
            || tableResult.getRows() == null
            || tableResult.getRows().isEmpty()) {
            return;
        }
        final StringBuilder columns = new StringBuilder();
        if (tableResult.getColumns() != null) {
            for (final Column column : tableResult.getColumns()) {
                if (!columns.isEmpty()) {
                    columns.append(", ");
                }
                columns.append(column.getName());
            }
        }

        // Name the mapping role by role rather than as two settings. An unmapped role reads as
        // "(not set)", which is the difference between "pointing at the wrong column" and "not
        // pointing anywhere" - remedies that look identical in a message listing only values.
        final StringBuilder mapping = new StringBuilder();
        final FloorMapEventColumns eventColumns = getEntity().getEventColumns();
        for (final FloorMapEventRole role : FloorMapEventRole.values()) {
            if (!mapping.isEmpty()) {
                mapping.append(", ");
            }
            final String column = eventColumns.getColumn(role);
            mapping.append(role.getDisplayName()).append(" = ")
                    .append(column == null ? "(not set)" : "'" + column + "'");
        }

        Console.error("Floor map events query returned "
                      + tableResult.getRows().size()
                      + " rows but no entities. The column mapping is: " + mapping
                      + "; the result has columns: " + columns
                      + ". " + FloorMapEventRole.ENTITY_ID.getDisplayName() + " must name a column"
                      + " the query selects, and so must at least one of "
                      + FloorMapEventRole.LOCATION.getDisplayName() + " and "
                      + FloorMapEventRole.LOCATION_REF.getDisplayName() + ".");
    }

    /**
     * The schema roles that map onto columns of the facts query's result table. Area roles
     * are absent from pre-area schemas, so their aliases can be null and simply go
     * unmatched.
     */
    private static final List<Role> FACT_COLUMN_ROLES = List.of(
            Role.TYPE, Role.POSITION, Role.IMAGE, Role.WORLD_TO_MAP,
            Role.GEOMETRY, Role.FILL, Role.OPACITY, Role.LABEL);

    /**
     * Parses a {@link TableResult} from the facts StroomQL query into canvas-renderable
     * objects. Maps column names to schema roles to extract key, type, coordinates,
     * image, and transformation matrices.
     *
     * <p>Rows become {@link stroom.floormap.client.model.Fact}s carrying world coordinates plus their
     * placement matrix; the canvas applies the transform at render time rather than this method
     * pre-transforming them. A background is simply an image fact, not a special case, and
     * {@link FloorMapObject} is the event-entity type - this method does not produce them.</p>
     */
    private void parseFacts(final List<Column> resultColumns, final List<Row> resultRows) {
        // Same guard as publishEventEntities: a facts result already in flight when the
        // tab was closed has nothing left to update, and this presenter is not unbound
        // on close so the callback still arrives.
        if (closed || getEntity() == null) {
            return;
        }
        // Column matching and row parsing live in FloorMapFactTableParser, on the JVM side
        // of the fence, so they can be tested. What stays here is the part that genuinely
        // needs the presenter: resolving aliases from the document's schema, and the side
        // effects below.
        final ValueFormat vf = getEntity().getValueFormat();
        final Map<Role, String> aliasByRole = new HashMap<>();
        for (final Role role : FACT_COLUMN_ROLES) {
            final String alias = columnAliasForRole(role, vf);
            if (alias != null) {
                aliasByRole.put(role, alias);
            }
        }

        final List<Fact> facts = FloorMapFactTableParser.parse(
                resultColumns,
                resultRows,
                aliasByRole,
                Console::error);

        // Facts paint in the configured type z-order (order backgrounds first on
        // the Settings tab so they sit behind); events draw on top.
        floorMapCanvasPresenter.setTypeStyles(getEntity().getTypeStyles());
        floorMapCanvasPresenter.setFacts(facts);

        // Static facts (objects, backgrounds, areas) belong in the tracking
        // panel alongside the event entities, so merge them into the roster.
        if (entityList.updateFacts(facts)) {
            refreshEntityGrid();
        }

        // Areas and static placements may have changed (a new timeline shard),
        // so recompute containment.
        lastFacts = facts;
        // An entity anchored to a fact is wherever that fact now is, so a facts
        // refresh re-places the entities before containment is recomputed from
        // their positions.
        reanchorEventEntities();
        updateAreaMembership();
    }

    /**
     * Issues a fact history read if one is due, and does nothing otherwise.
     *
     * <p>The decision lives in {@code needsRead} rather than here, so it is unit-testable and this
     * method only asks. Two call sites ask it: {@link #onTimeChange}, which covers playback and
     * every timeline move, and the {@code factsCadenceTimer} heartbeat, which covers a visible but
     * stationary timeline — where no tick fires and nothing else would ever ask.
     * {@link #refresh()} and {@link #onRead} reach it too, but transitively through
     * {@link #onTimeChange}, so they are not separate paths to keep working.</p>
     *
     * <p>Skipped while one is in flight. Issuing again would destroy it and, if a read takes longer
     * than a tick, it would never complete — the livelock the baseline path documents. Note this is
     * the helper's own flag and deliberately not {@code QueryModel.isSearching()}, which the REST
     * failure path never clears.</p>
     */
    private void readFactsHistoryIfDue() {
        if (factsHistoryQueryHelper.isRunning()) {
            return;
        }
        final double nowMs = System.currentTimeMillis();
        if (!factHistory.needsRead(nowMs)) {
            return;
        }
        final String query = getFactsQueryToUse();
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        factHistory.markReadIssued(nowMs);
        factsHistoryQueryHelper.runAll(query, queryParams());
    }

    /**
     * Takes a completed fact history read.
     *
     * <p>Refuses a failed one. The history replaces the floor plan wholesale, and a failed read is
     * indistinguishable from an empty store by its rows — applying it would blank the map and, with
     * nothing else drawing facts, leave it blank until the next cadence. Keeping the previous
     * history is strictly better: stale beats empty, and the previous history is usually still
     * right, since facts change weekly.</p>
     */
    private void applyFactsHistoryOutcome(final FloorMapFullReadQueryHelper.Outcome outcome) {
        if (closed || getEntity() == null) {
            return;
        }
        if (outcome.failed()) {
            if (!factsHistoryErrorReported) {
                factsHistoryErrorReported = true;
                Console.error("Floor Map: the facts query failed, so the floor plan shown is the "
                              + "last that was read successfully. This is reported once; the read "
                              + "is retried every "
                              + (FloorMapFactHistory.REFETCH_INTERVAL_MS / 1000) + " seconds.");
            }
            return;
        }
        final TableResult result = outcome.result();
        if (result == null) {
            return;
        }
        factHistory.setHistory(result.getColumns(), result.getRows(), outcome.truncated());
        if (outcome.truncated() && !factsHistoryTruncationReported) {
            factsHistoryTruncationReported = true;
            Console.error("Floor Map: the facts store holds more than "
                          + FloorMapFactHistory.MAX_ROWS + " historical entries, so the floor plan "
                          + "may be incomplete or show the wrong version of a fact. This is far "
                          + "above the expected volume - check whether the facts store is being "
                          + "written to as though it were an events store.");
        }
        if (factHistory.shouldWarnMissingTimeColumn()) {
            Console.error("Floor Map: the facts query is not returning the \""
                          + FloorMapFactHistory.EFFECTIVE_TIME_MS_COLUMN + "\" column, so the "
                          + "floor plan shows each fact's latest version regardless of the "
                          + "timeline position.");
        }
        // Deliberately no forced redraw. The first history after an open draws because
        // lastFactSnapshotRows is still null and nothing equals null; a history whose rows changed
        // draws because the snapshot differs; and one that came back identical - the normal case at
        // a weekly change rate - is skipped, which is the whole point. Clearing the guard here
        // instead would re-parse and re-push the entire floor plan once a minute for nothing.
        applyFactSnapshot(selectedTime);
    }

    /**
     * Derives the facts current at {@code time} from the held history and pushes them if they
     * differ from the last push.
     *
     * <p>The unchanged guard is the other half of this change. Deriving a snapshot is cheap, but
     * everything downstream of it is not — parsing every row, re-pushing type styles and facts to
     * the canvas, re-placing every event entity and recomputing area containment. At the stated
     * change rate the snapshot is identical on essentially every tick, so skipping is the normal
     * path and doing the work is the exception.</p>
     */
    private void applyFactSnapshot(final long time) {
        if (closed || getEntity() == null || !factHistory.isLoaded()) {
            return;
        }
        final List<Row> snapshot = factHistory.snapshotAt(time);
        if (snapshot.equals(lastFactSnapshotRows)) {
            return;
        }
        lastFactSnapshotRows = snapshot;
        parseFacts(factHistory.columns(), snapshot);
    }

    /**
     * Re-places the last event entities against the current facts and pushes
     * them on if that moved anything.
     *
     * <p>Guarded on the result rather than on the trigger: the facts query
     * re-runs on every playback tick, and re-pushing an unchanged overlay would
     * feed the canvas animator a fresh update ~3 times a second for no
     * movement.</p>
     */
    private void reanchorEventEntities() {
        if (lastRawEventObjects == null) {
            return;
        }
        final List<FloorMapObject> placed = placeEventEntities();
        if (!FloorMapLocationResolver.samePositions(placed, lastEventObjects)) {
            pushEventEntities(placed);
        }
        // Facts have changed, so the classification may have too: an entity that had nowhere to be
        // placed may now have somewhere, and this is also the first point at which "there are no
        // facts" can be told apart from "the facts have not arrived yet".
        refreshEmptyStatus(NullSafe.size(lastRawEventObjects), placed.size());
    }

    /**
     * Puts the current pipeline state on the canvas as a line of text, or takes it down.
     *
     * <p><b>Deliberately not gated on the console's persistence filter.</b> That filter exists
     * because a repeated log line is noise, and a stage must therefore have been empty for
     * {@link FloorMapStageReporter#PERSISTENCE_MS} before it is written. A status line has the
     * opposite property: rewriting the same text is invisible, so there is nothing to suppress and
     * every reason to be immediate — an empty map is precisely when someone is staring at it
     * wondering why, and a second of silence is a second of no explanation.</p>
     *
     * <p>What the tick count was really standing in for is checked directly instead. Facts and
     * events come from independent reads, so "there are no facts" and "the facts have not arrived
     * yet" are indistinguishable until one lands, and naming the first when it is the second would
     * alarm on every single open. {@code factHistory.isLoaded()} answers that exactly — which it
     * could not before F15, when facts were re-queried per tick and there was no held state to
     * ask.</p>
     *
     * <p>It does not flicker during playback either. Every read is now a full latest-per-key
     * snapshot at the selected time, so a map with entities returns one row per entity on every
     * tick rather than only when something moves — and in any case
     * {@link FloorMapStageReporter#classify} tests {@code placed > 0} first, so a map with entities
     * on it never reaches the empty branches. That second guard is what made this safe back when
     * ticks could legitimately return nothing, and it is worth keeping for the same reason.</p>
     */
    private void refreshEmptyStatus(final int entities, final int placed) {
        if (!factHistory.isLoaded()) {
            floorMapCanvasPresenter.setEmptyStatus(null, false);
            return;
        }
        final FloorMapStageReporter.Stage stage = FloorMapStageReporter.classify(
                lastEventRowCount, entities, NullSafe.size(lastFacts), placed);
        floorMapCanvasPresenter.setEmptyStatus(stage.getStatusText(), stage.isFault());
    }

    /**
     * Places the last raw event entities against the current facts, reporting
     * the case where the facts are loaded and <em>nothing</em> matched.
     *
     * @return the placed entities; never {@code null}
     */
    private List<FloorMapObject> placeEventEntities() {
        final List<FloorMapObject> placed =
                FloorMapLocationResolver.resolve(lastRawEventObjects, lastFacts);
        // Facts arriving after the events is normal and self-corrects on the
        // next facts refresh, so only a full miss against facts we actually
        // have says the two sides do not agree on what an object is called.
        if (placed.isEmpty()
            && lastRawEventObjects != null
            && !lastRawEventObjects.isEmpty()
            && lastFacts != null
            && !lastFacts.isEmpty()) {
            //noinspection SequencedCollectionMethodCanBeUsed
            Console.error("Floor map: none of the " + lastRawEventObjects.size()
                          + " event entities could be placed. Their location column names objects"
                          + " like '" + lastRawEventObjects.get(0).getLocationRef()
                          + "', which matches no fact key at this time — the facts query returned"
                          + " keys like '" + lastFacts.get(0).getKey() + "'.");
        }
        return placed;
    }

    /**
     * Pushes a placed entity overlay to the canvas and the tracking roster, and
     * records it as the current one for area containment and the group counts.
     *
     * @param placed the entities, already resolved to map positions
     */
    private void pushEventEntities(final List<FloorMapObject> placed) {
        floorMapCanvasPresenter.setEventObjects(placed);
        // Keep the tracking panel's roster up to date. Only re-push grid
        // data when membership actually changed so playback refreshes
        // (~300ms apart) don't churn the grid.
        if (entityList.update(placed)) {
            refreshEntityGrid();
        }
        lastEventObjects = placed;
    }

    /**
     * Recomputes which entities are inside which areas at the current timeline
     * instant, then pushes the snapshot to the canvas (containment highlight and
     * occupant badges) and the tracking panel (Area column).
     *
     * <p>Driven by query refreshes — a facts reload or an events refresh — not by
     * animation frames, so the cost is bounded by how often the data changes
     * rather than by the frame rate.</p>
     */
    private void updateAreaMembership() {
        final FloorMapAreaMembership membership =
                FloorMapAreaMembership.compute(lastFacts, lastEventObjects);
        floorMapCanvasPresenter.setAreaMembership(membership);
        floorMapTrackingPresenter.setAreaMembership(membership, this::entityDisplayName);

        // The Groups panel's live counts move with the same data, so they are
        // recomputed on the same trigger.
        this.lastAreaMembership = membership;
        refreshGroupSnapshot();
    }

    /**
     * Recomputes the Groups panel's live counts from the latest facts, events and
     * area containment.
     *
     * <p>Positioned-ness comes from the facts and events lists, <strong>not</strong>
     * from the membership snapshot — that is empty on a map with no areas and lists
     * only entities that are inside one. The membership is used purely for the area
     * breakdown.</p>
     */
    private void refreshGroupSnapshot() {
        floorMapGroupsPresenter.setSnapshot(FloorMapGroupSnapshot.compute(
                floorMapGroupsPresenter.getGroups(),
                lastFacts,
                lastEventObjects,
                lastAreaMembership));
    }

    /**
     * Resolves any entity id to the name shown in the tracking panel — for an
     * area, its user-facing {@code LABEL} name where it has one.
     *
     * <p>Reads it back out of the roster rather than re-deriving it, so an area
     * named in the Area column always matches the name on its own row. Falls
     * back to the id-derived name for an id the roster has not seen yet (a facts
     * refresh admits every fact, so this is only the very first frame).</p>
     */
    private String entityDisplayName(final String id) {
        final String name = entityList.getDisplayName(id);
        return name != null
                ? name
                : FloorMapEntityList.displayName(id);
    }

    /**
     * Resolves an entity id to its type, from the same roster the names come
     * from. {@code null} for an id the roster has not seen, which leaves the
     * caller to fall back.
     *
     * <p>Goes through the roster's own keyed lookup, not {@link
     * FloorMapEntityList#getEntities()} — that allocates and sorts the whole
     * roster per call, which a cluster of hundreds of members would pay for once
     * per member.</p>
     */
    private String entityType(final String id) {
        return entityList.getType(id);
    }

    /**
     * The names of the groups an entity belongs to, in the Groups panel's own
     * display order, so the cluster dialog's Group column and filter read the
     * same way as that panel.
     *
     * <p>Membership comes from the groups themselves rather than from the canvas
     * overlay: the overlay holds only the groups the user has switched
     * <em>on</em>, and which groups an entity is in does not depend on whether
     * they are currently highlighted.</p>
     *
     * @param id the entity id
     * @return the group names; empty when the entity is in none
     */
    private List<String> entityGroupNames(final String id) {
        final List<String> names = new ArrayList<>();
        for (final FloorMapGroup group : floorMapGroupsPresenter.getGroups()) {
            if (group.contains(id)) {
                names.add(group.getName());
            }
        }
        return names;
    }

    /**
     * Re-pushes the roster into the tracking panel's grid, preserving the
     * current selection. Restoring the selected id does not re-fire the
     * selection consumer because {@code EntityEntry} equality is id-based.
     */
    private void refreshEntityGrid() {
        final String selectedId = floorMapTrackingPresenter.getSelectedId();
        final List<EntityEntry> entities = entityList.getEntities();
        floorMapTrackingPresenter.setData(entities);
        floorMapTrackingPresenter.setSelected(selectedId);
        // The Groups panel's member picker offers the same roster, and names its
        // members and areas through the same resolver.
        floorMapGroupsPresenter.setRoster(entities, this::entityDisplayName);
    }


    /**
     * The query column alias for a role, or {@code null} when the schema does
     * not map the role (e.g. the area roles on a pre-area document).
     */
    private String columnAliasForRole(final Role role,
                                      final stroom.floormap.shared.ValueFormat vf) {
        final String path = pathForRole(role);
        return path != null ? FloorMapQueryBuilder.buildColumnAlias(path, vf) : null;
    }




    /**
     * Initialises the timeline range to ±24 hours around the currently selected time,
     * then triggers a histogram query to populate the timeline's event density bars.
     */
    private void updateTimelineRange() {
        // By default, the timeline shows a range 24 hours each side of the current system time.
        final long start = selectedTime - ONE_DAY_MS;
        final long end = selectedTime + ONE_DAY_MS;

        floorMapTimelinePresenter.setTimeRange(start, end);
        floorMapTimelinePresenter.setCurrentTime(selectedTime);

        runHistogramQuery(start, end);
    }

    /**
     * Runs the histogram query over the full {@code [start, end]} range.
     *
     * <p><b>The events query is the only source.</b> The density bars say when entities were
     * active, which is a question only the events store answers; a floor map with no events query
     * has no activity to plot, so it correctly shows no bars rather than substituting something
     * else.</p>
     *
     * <p>There used to be a facts fallback — a minimal query against the facts store, plus a second
     * helper for it — justified by a comment saying one query at a time avoided double-counting
     * "when both events and facts are sourced from the same data store". <b>That cannot happen.</b>
     * The two store references are type-disjoint and enforced as such wherever they can be set:
     * the facts store is a {@code SqlTemporalStoreDoc} because the Editor tab writes spatial data
     * back to it, and the events store is a {@code FloorMapEventStoreDoc} that is only ever read — see
     * {@link FloorMapInitPresenter}. So the fallback answered a different question (when was the
     * floor plan last edited) in the place reserved for this one, and its second helper could only
     * ever run a query with an unresolvable {@code from} clause. Both are gone.</p>
     *
     * <p>The store reference travels as a {@link stroom.query.api.Param} like every other read's,
     * so {@code from param('EventStore')} resolves server-side and the query text is sent exactly as
     * written. The bucket width travels the same way. Nothing is substituted into the text, which is
     * what lets a user edit this query without their edits being rewritten under them.</p>
     *
     * <p>The query is generated here rather than being the user's own events query, so there is no
     * longer a requirement on what that query selects: it groups {@code EffectiveTime}, the field
     * the store defines. The old requirement — that the user's {@code select} include a recognised
     * timestamp column, on pain of silently empty bars — is gone with the column sniffing that
     * needed it.</p>
     *
     * <p>Bounded to the visible range at both ends. That used to be unsafe — an upper bound switched
     * the store into a point-in-time read — but the mode is now named rather than inferred, so the
     * bound simply bounds. "Show All" still needs a range this one cannot give, because a bounded
     * read can never reach data outside it, so it has its own query: {@link #runExtentQuery()}.</p>
     */
    private void runHistogramQuery(final long start, final long end) {
        histogramDataModel.setRange(start, end);

        // Counted server-side, one row per bucket. The read this replaced returned every event the
        // store held and bucketed them here, which is the one read whose size grows without bound.
        histogramBucketWidthMs = FloorMapHistogramBuckets.widthFor(end - start);

        // The width is a parameter rather than text: it changes on every zoom, and substituting it
        // would mean rewriting a query the user may have edited.
        final List<Param> params = new ArrayList<>();
        final List<Param> storeParams = queryParams();
        if (storeParams != null) {
            params.addAll(storeParams);
        }
        params.add(new Param(
                FloorMapQueryBuilder.PARAM_BUCKET_WIDTH,
                FloorMapHistogramBuckets.durationFor(end - start)));

        histogramQueryHelper.run(histogramQuery(), params, start, end);
    }

    /**
     * Runs the unbounded read behind "Show All".
     *
     * <p>Deliberately not folded into {@link #runHistogramQuery}: that one is bounded below at the
     * visible range, so what it returns can never start earlier than what is already shown. An
     * extent taken from it could only grow forwards, and "Show All" exists precisely to reach
     * backwards.</p>
     *
     * <p>The answer is one row of two values, so nothing about its size depends on how busy the
     * store is. It is not cheap on the server, though: Plan B iterates the whole store whatever
     * range is asked for, so this costs a full scan — the same scan the bars already pay.</p>
     */
    private void runExtentQuery() {
        extentQueryHelper.run(extentQuery(), queryParams());
    }

    /** The document's histogram query, or the generated default where it sets none. */
    private String histogramQuery() {
        final String configured = getEntity() == null
                ? null
                : getEntity().getHistogramQuery();
        return configured == null || configured.trim().isEmpty()
                ? FloorMapQueryBuilder.defaultHistogramQuery()
                : configured;
    }

    /** The document's extent query, or the generated default where it sets none. */
    private String extentQuery() {
        final String configured = getEntity() == null
                ? null
                : getEntity().getExtentQuery();
        return configured == null || configured.trim().isEmpty()
                ? FloorMapQueryBuilder.defaultExtentQuery()
                : configured;
    }

    /**
     * Hands the timeline the data's own extent, so "Show All" can reach beyond the visible range.
     *
     * <p>A null extent — an empty store, or a read that failed — leaves the previous range alone
     * rather than collapsing the timeline to nothing.</p>
     */
    private void applyDataExtent(final TableResult result) {
        final long[] extent = HistogramDataModel.extentOf(result);
        if (extent != null) {
            floorMapTimelinePresenter.setDataRange(extent[0], extent[1]);
        }
    }

    /**
     * Pauses the timeline if it is currently playing.
     *
     * <p>Called by {@link FloorMapPresenter} when the user navigates away from
     * the Map tab, so that background queries are not issued while the tab
     * is hidden.</p>
     */
    public void pauseTimeline() {
        floorMapTimelinePresenter.pause();
    }

    /**
     * Stops the clock and every search this tab owns when the document is closed.
     *
     * <p>Closing a document tab does not unbind its presenters, so without this a
     * closed Map tab keeps a paused-but-live pipeline: the timeline's playback
     * loop, three result stores on the server, and an event-bus handler that still
     * accepts entity data for this document's UUID. A reopened copy of the
     * document shares that UUID, so the dead tab's query results land on the live
     * tab's canvas.</p>
     */
    @Override
    public void onClose() {
        super.onClose();
        closed = true;
        floorMapTimelinePresenter.pause();
        factsCadenceTimer.cancel();
        factsHistoryQueryHelper.reset();
        eventsQueryHelper.reset();
        histogramQueryHelper.reset();
        extentQueryHelper.reset();
    }

    /**
     * Re-runs the facts query at the current time so the canvas reflects the
     * latest persisted state.
     *
     * <p>Called by {@link FloorMapPresenter} when the user selects the Map tab
     * and after the Editor tab's staged changes are flushed on save — the Map
     * tab otherwise only re-queries on its own timeline changes, so edits made
     * on the Editor tab (moved objects, new icons/backgrounds) would not appear
     * here until the timeline was next moved.</p>
     *
     * <p>Requests a baseline as well, which is what makes returning to this tab catch up on
     * everything that arrived while it was elsewhere. Nothing polls in the background: the events
     * state is corrected on a tick, and no tick fires while the tab is hidden and paused. So this
     * is the catch-up, and it is why no timer is needed.</p>
     */
    public void refresh() {
        if (getEntity() != null) {
            // Becoming visible is the case the 60 s cadence deliberately does not try to serve:
            // someone who has just written a fact and wants to see it is, almost by definition,
            // about to look at the map. So re-read now rather than waiting out the interval.
            factHistory.requestRead();
            onTimeChange(selectedTime);
        }
    }

    /**
     * Called when this document's own content tab is fronted or backgrounded, as distinct from a
     * switch between this document's inner tabs.
     *
     * <p>Both halves matter. Becoming visible catches up on everything that arrived while the tab
     * was elsewhere — the same job {@link #refresh()} does for an inner-tab return. Becoming
     * hidden <b>pauses playback</b>, which it previously did not: {@code afterSelectTab} fires only
     * on inner-tab switches, so switching to a different Stroom document left the
     * requestAnimationFrame loop running and ticks arriving, driving result stores on a document
     * nobody was looking at.</p>
     *
     * @param visible whether this document's tab is now the fronted one
     */
    public void onContentTabVisible(final boolean visible) {
        if (visible) {
            refresh();
            factsCadenceTimer.scheduleRepeating(FACTS_CADENCE_HEARTBEAT_MS);
        } else {
            pauseTimeline();
            factsCadenceTimer.cancel();
        }
    }

    /**
     * Registers a listener notified once with this tab's computed initial view
     * {@code {scale, offsetX, offsetY}}, so the Editor tab can adopt the same
     * initial zoom + translation and nothing jumps on the first tab switch.
     *
     * @param listener the callback, or {@code null} to remove
     */
    public void setInitialViewListener(final java.util.function.Consumer<double[]> listener) {
        floorMapCanvasPresenter.setInitialViewListener(listener);
    }

    public interface FloorMapMapView extends View {

        /**
         * Shows or hides the right-hand dock, preserving its dragged width.
         *
         * @param visible {@code true} to show the dock, {@code false} to hide it
         */
        void setDockVisible(boolean visible);
    }
}
